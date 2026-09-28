package io.github.ozzz.personalagent

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

/** Configuration only; never starts an automation task. */
class SetupActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var authorize: Button
    private lateinit var finishButton: Button
    private lateinit var confirmed: CheckBox
    private val received = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val dead = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val space = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space, space, space, space)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        fun label(value: String, size: Float = 16f) = TextView(this).also {
            it.text = value; it.textSize = size; it.setPadding(0, space / 2, 0, space / 2); root.addView(it)
        }
        fun button(value: String, action: () -> Unit) = Button(this).also {
            it.text = value; it.setOnClickListener { action() }; root.addView(it)
        }
        label("首次使用配置", 26f)
        label("只需配置一次；手机重启后需确认 Shizuku 已重新启动。\n当前为预览版，仅实测小米 15 Ultra / Android 16。")
        status = label("正在检查…")
        label("1. 安装并启动 Shizuku", 20f)
        label("Shizuku 让本 App 经你的授权控制其他应用。Android 11+ 可通过无线调试配对启动；较旧系统可连接电脑启动。无需输入淘宝密码或 API Key。")
        button("打开 Shizuku / 前往官网下载") { openPackage("moe.shizuku.privileged.api", "https://shizuku.rikka.app/") }
        button("查看 Shizuku 配对与启动教程") { openWeb("https://shizuku.rikka.app/guide/setup/") }
        label("2. 允许本 App 使用 Shizuku", 20f)
        authorize = button("授权 Shizuku") {
            runCatching { Shizuku.requestPermission(1010) }
                .onFailure { toast("授权未成功，请到 Shizuku 应用管理中允许 Personal Agent") }
        }
        label("授权后可读取当前页面及模拟点击。你可随时在 Shizuku 中撤销授权。小米若不能点击，请检查开发者选项中的“USB 调试（安全设置）”。")
        label("3. 准备淘宝", 20f)
        button("打开淘宝，确认已登录") { openPackage("com.taobao.taobao", "https://www.taobao.com/") }
        label("请手动登录、关闭广告并回到淘宝首页。运行时保持解锁亮屏，不要操作屏幕。App 无法自动确认登录，也不会处理验证码、支付或自动解锁。")
        confirmed = CheckBox(this).apply {
            text = "我已确认淘宝登录，了解运行时需保持亮屏"
            isChecked = getSharedPreferences("setup", MODE_PRIVATE).getBoolean("complete", false)
            setOnCheckedChangeListener { _, _ -> refresh() }
        }
        root.addView(confirmed)
        finishButton = button("完成配置，进入任务") {
            refresh()
            if (finishButton.isEnabled) {
                getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean("complete", true).apply()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        }
        button("重新检查配置") { refresh() }
        label("只检测安装、服务连接与授权；登录状态由你确认。未知页面会停止。任务页提供取消按钮和日志。配置过程不执行签到。")
        setContentView(scroll)
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        refresh()
    }

    private fun installed(name: String) = runCatching { packageManager.getPackageInfo(name, 0); true }.getOrDefault(false)
    private fun refresh() {
        if (!::finishButton.isInitialized || isFinishing || isDestroyed) return
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val supported = running && runCatching { Shizuku.getVersion() >= 13 }.getOrDefault(false)
        val allowed = supported && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        val taobao = installed("com.taobao.taobao")
        status.text = "Shizuku：${if (!running) "未连接，请启动" else if (!supported) "版本过旧，需要13或更新版" else "已连接"}\n授权：${if (allowed) "已允许" else "未允许"}\n淘宝：${if (taobao) "已安装（登录需手动确认）" else "未安装"}"
        authorize.isEnabled = supported && !allowed
        finishButton.isEnabled = allowed && taobao && confirmed.isChecked
    }
    private fun openPackage(name: String, fallback: String) {
        val intent = packageManager.getLaunchIntentForPackage(name)
        if (intent == null) openWeb(fallback) else runCatching { startActivity(intent) }.onFailure { toast("无法打开应用") }
    }
    private fun openWeb(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("未找到浏览器") }
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
        super.onDestroy()
    }
}
