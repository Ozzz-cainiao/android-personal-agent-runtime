package io.github.ozzz.personalagent

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/** All mutable connection state and UI callbacks live on the main thread. */
class ShizukuRuntimeClient(
    context: Context,
    private val report: (String) -> Unit,
    private val busyChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.applicationContext, AutomationUserService::class.java)
    ).daemon(false).tag("automation-runtime").processNameSuffix("runtime")
        .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private var closed = false
    private var busy = false
    private var activeConnection: ServiceConnection? = null
    private var generation = 0
    private var remote: IAutomationService? = null
    private var requestedPackage: String? = null

    private val timeout = Runnable {
        if (!closed && busy) {
            disconnect()
            report("[失败] 操作超时，已断开连接，请查看 Logcat 后重试。")
        }
    }

    private fun newConnection() = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed || activeConnection !== this) return
            remote = IAutomationService.Stub.asInterface(binder)
            report("[服务] Binder 已连接，读取远端身份…")
            readIdentity()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (closed || activeConnection !== this) return
            generation++
            remote = null
            finish()
            report("[断开] UserService 已断开，可重新测试。")
        }
    }

    private val received = Shizuku.OnBinderReceivedListener {
        if (!closed) report("[连接] Shizuku Binder 已就绪。")
    }
    private val dead = Shizuku.OnBinderDeadListener {
        if (!closed) {
            generation++
            remote = null
            finish()
            report("[断开] Shizuku 服务已停止，请启动 Shizuku 后重试。")
        }
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (!closed && code == REQUEST_CODE && busy) {
            if (grant == PackageManager.PERMISSION_GRANTED) {
                report("[授权] 已允许。")
                bind()
            } else {
                finish()
                report("[授权] 未允许；未启动特权服务。")
            }
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(received, main)
        Shizuku.addBinderDeadListener(dead, main)
        Shizuku.addRequestPermissionResultListener(permissionResult, main)
    }

    fun testConnection() = start(null)

    fun launchApp(packageName: String) = start(packageName)

    private fun start(packageName: String?) {
        if (closed || busy) return
        requestedPackage = packageName
        busy = true
        busyChanged(true)
        report("[本机] App uid=${Process.myUid()} pid=${Process.myPid()}")
        try {
            check(Shizuku.pingBinder()) { "Shizuku 未连接，请确认已启动。" }
            check(Shizuku.getVersion() >= 13) { "本版本需要 Shizuku 13 或更新版本。" }
            report("[连接] Shizuku API=${Shizuku.getVersion()}，服务 uid=${Shizuku.getUid()}")
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bind()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                finish()
                report("[授权] 请在 Shizuku 的应用管理中允许 Personal Agent，然后重试。")
            } else {
                report("[授权] 请在手机弹窗中选择允许。")
                // Human authorization is not subject to the service's 15-second timeout.
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            fail("连接检查", e)
        }
    }

    private fun bind() {
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 15_000)
        if (remote?.asBinder()?.isBinderAlive == true) {
            readIdentity()
            return
        }
        try {
            releaseService()
            val connection = newConnection()
            activeConnection = connection
            report("[服务] 正在绑定 UserService…")
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            fail("绑定服务", e)
        }
    }

    private fun readIdentity() {
        val service = remote ?: return
        val attempt = ++generation
        worker.execute {
            val result = runCatching {
                val identity = service.identity
                val uid = identity.getInt("uid", -1)
                val pid = identity.getInt("pid", -1)
                check(uid == 2000 || uid == 0) { "远端不是 shell/root：uid=$uid" }
                check(pid > 0 && pid != Process.myPid()) { "远端 PID 无效：$pid" }
                "[通过] UserService uid=$uid pid=$pid（${if (uid == 2000) "shell" else "root"}）"
            }
            main.post {
                if (!closed && generation == attempt) {
                    result.onSuccess {
                        report(it)
                        val packageName = requestedPackage
                        if (packageName == null) finish() else launchRemote(service, packageName)
                    }
                        .onFailure { fail("读取身份", it) }
                }
            }
        }
    }

    private fun launchRemote(service: IAutomationService, packageName: String) {
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 20_000)
        val attempt = ++generation
        report("[启动] package=$packageName，正在通过 UserService 启动…")
        worker.execute {
            val result = runCatching { service.launchApp(packageName) }
            main.post {
                if (!closed && generation == attempt) {
                    result.onSuccess { value ->
                        finish()
                        report("[启动] stage=${value.getString("stage")} exit=${value.getInt("exitCode")} timeout=${value.getBoolean("timedOut")} 耗时=${value.getLong("elapsedMs")}ms")
                        for (key in listOf("stdout", "stderr")) {
                            value.getString(key)?.takeIf { it.isNotBlank() }?.let { report("[$key] $it") }
                        }
                        if (value.getBoolean("success")) {
                            report("[启动完成] ${value.getString("component")}；请在手机确认页面。")
                        } else {
                            report("[启动失败] ${value.getString("error")}")
                        }
                    }.onFailure { fail("启动应用", it) }
                }
            }
        }
    }

    fun disconnect() {
        generation++
        remote = null
        releaseService()
        finish()
        if (!closed) report("[服务] 已释放本 App 的连接。")
    }

    private fun releaseService() {
        activeConnection?.let { connection ->
            activeConnection = null
            runCatching { Shizuku.unbindUserService(args, connection, true) }
                .onFailure { Log.w("PersonalAgent", "UNBIND_FAILED", it) }
            // Also clear local SDK callbacks, including when the server has already died.
            runCatching { Shizuku.unbindUserService(args, connection, false) }
                .onFailure { Log.w("PersonalAgent", "DETACH_FAILED", it) }
        }
    }

    private fun fail(step: String, error: Throwable) {
        Log.e("PersonalAgent", "FAILED step=$step", error)
        disconnect()
        report("[失败] $step：${error.javaClass.simpleName}: ${error.message}")
    }

    private fun finish() {
        main.removeCallbacks(timeout)
        busy = false
        requestedPackage = null
        if (!closed) busyChanged(false)
    }

    override fun close() {
        closed = true
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        disconnect()
        worker.shutdownNow()
    }

    companion object { private const val REQUEST_CODE = 1001 }
}
