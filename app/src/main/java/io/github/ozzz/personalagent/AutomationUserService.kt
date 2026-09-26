package io.github.ozzz.personalagent

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlin.system.exitProcess

/** Instantiated by Shizuku in a separate shell/root process, not an Android Service. */
class AutomationUserService : IAutomationService.Stub() {
    @Synchronized
    override fun launchApp(packageName: String): Bundle {
        val started = SystemClock.elapsedRealtime()
        var stage = "resolve"
        var command: CommandRunner.Result? = null
        return try {
            require(LaunchProtocol.validPackage(packageName)) { "Invalid package name" }
            Log.i("PersonalAgent", "LAUNCH_BEGIN package=$packageName uid=${Process.myUid()} pid=${Process.myPid()}")
            command = CommandRunner.run(listOf(
                "/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "current",
                "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", packageName,
            ), 5_000)
            val component = LaunchProtocol.component(packageName, command)
                ?: error("未找到可启动入口，或解析命令失败")
            stage = "start"
            Log.i("PersonalAgent", "LAUNCH_COMPONENT $component")
            command = CommandRunner.run(listOf(
                "/system/bin/am", "start", "-W", "--user", "current", "-n", component,
                "-f", "0x10000000",
            ), 8_000)
            val success = LaunchProtocol.succeeded(command)
            Bundle().apply {
                putBoolean("success", success)
                putString("component", component)
                if (!success) putString("error", "启动命令未确认成功，请检查输出")
            }
        } catch (e: Exception) {
            Log.e("PersonalAgent", "LAUNCH_FAILED stage=$stage", e)
            Bundle().apply {
                putBoolean("success", false)
                putString("error", "${e.javaClass.simpleName}: ${e.message}")
            }
        }.apply {
            putString("stage", stage)
            putInt("exitCode", command?.exitCode ?: -1)
            putBoolean("timedOut", command?.timedOut ?: false)
            putString("stdout", command?.stdout.orEmpty())
            putString("stderr", command?.stderr.orEmpty())
            putLong("elapsedMs", SystemClock.elapsedRealtime() - started)
            Log.i("PersonalAgent", "LAUNCH_RESULT success=${getBoolean("success")} stage=$stage exit=${getInt("exitCode")} elapsedMs=${getLong("elapsedMs")}")
        }
    }

    override fun getIdentity(): Bundle {
        val uid = Process.myUid()
        val pid = Process.myPid()
        Log.i("PersonalAgent", "SERVICE_IDENTITY uid=$uid pid=$pid")
        return Bundle().apply {
            putInt("uid", uid)
            putInt("pid", pid)
        }
    }

    override fun destroy() {
        Log.i("PersonalAgent", "SERVICE_DESTROY pid=${Process.myPid()}")
        exitProcess(0)
    }
}
