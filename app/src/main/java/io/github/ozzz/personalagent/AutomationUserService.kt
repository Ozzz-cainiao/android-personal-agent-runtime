package io.github.ozzz.personalagent

import android.os.Bundle
import android.os.Binder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlin.system.exitProcess

/** Instantiated by Shizuku in a separate shell/root process, not an Android Service. */
class AutomationUserService : IAutomationService.Stub() {
    @Synchronized
    override fun swipe(expectedPackage: String, startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Bundle {
        val started = SystemClock.elapsedRealtime()
        var result: CommandRunner.Result? = null
        return try {
            val foreground = CommandRunner.run(listOf("/system/bin/dumpsys", "activity", "activities"), 3000, 256000)
            val command = SwipeProtocol.command(expectedPackage, startX, startY, endX, endY, durationMs, foreground)
            Log.i("PersonalAgent", "SWIPE_BEGIN package=$expectedPackage display=0 from=$startX,$startY to=$endX,$endY durationMs=$durationMs")
            result = CommandRunner.run(command, 4000)
            check(!result.timedOut && result.exitCode == 0 && result.stderr.isBlank() &&
                !result.stdout.contains("Error", ignoreCase = true)) { "滑动命令未确认成功" }
            Bundle().apply { putBoolean("success", true) }
        } catch (e: Exception) {
            Log.e("PersonalAgent", "SWIPE_FAILED", e)
            Bundle().apply { putBoolean("success", false); putString("error", e.message) }
        }.apply {
            putInt("exitCode", result?.exitCode ?: -1)
            putBoolean("timedOut", result?.timedOut ?: false)
            putString("stderr", result?.stderr.orEmpty())
            putLong("elapsedMs", SystemClock.elapsedRealtime() - started)
            Log.i("PersonalAgent", "SWIPE_RESULT success=${getBoolean("success")} exit=${getInt("exitCode")}")
        }
    }

    @Synchronized
    override fun returnHome(expectedPackage: String): Bundle {
        val started = SystemClock.elapsedRealtime()
        return try {
            require(LaunchProtocol.validPackage(expectedPackage))
            val foreground = CommandRunner.run(listOf("/system/bin/dumpsys", "activity", "activities"), 3000, 256000)
            check(TapProtocol.isForeground(expectedPackage, foreground)) { "目标已不在前台，未发送HOME" }
            val result = CommandRunner.run(listOf("/system/bin/input", "-d", "0", "keyevent", "KEYCODE_HOME"), 3000)
            check(!result.timedOut && result.exitCode == 0 && result.stderr.isBlank()) { "HOME命令失败" }
            Bundle().apply { putBoolean("success", true); putInt("exitCode", 0) }
        } catch (e: Exception) {
            Bundle().apply { putBoolean("success", false); putInt("exitCode", -1); putString("error", e.message) }
        }.apply { putLong("elapsedMs", SystemClock.elapsedRealtime() - started) }
    }

    @Synchronized
    override fun dumpUi(expectedPackage: String): String {
        require(LaunchProtocol.validPackage(expectedPackage))
        val identity = Binder.clearCallingIdentity()
        return try { UiHierarchyReader.read(expectedPackage) } finally { Binder.restoreCallingIdentity(identity) }
    }
    @Synchronized
    override fun tap(expectedPackage: String, x: Int, y: Int): Bundle {
        val started = SystemClock.elapsedRealtime()
        var command: CommandRunner.Result? = null
        return try {
            require(LaunchProtocol.validPackage(expectedPackage) && x >= 0 && y >= 0)
            val foreground = CommandRunner.run(listOf("/system/bin/dumpsys", "activity", "activities"), 3_000, 256_000)
            check(TapProtocol.isForeground(expectedPackage, foreground)) { "目标 App 未处于前台，取消点击" }
            Log.i("PersonalAgent", "TAP_BEGIN package=$expectedPackage display=0 x=$x y=$y uid=${Process.myUid()} pid=${Process.myPid()}")
            command = CommandRunner.run(listOf("/system/bin/input", "-d", "0", "tap", x.toString(), y.toString()), 3_000)
            val success = !command.timedOut && command.exitCode == 0 && command.stderr.isBlank() &&
                !command.stdout.contains("Error", ignoreCase = true)
            Bundle().apply {
                putBoolean("success", success)
                if (!success) putString("error", "点击命令未确认成功")
            }
        } catch (e: Exception) {
            Log.e("PersonalAgent", "TAP_FAILED", e)
            Bundle().apply {
                putBoolean("success", false)
                putString("error", "${e.javaClass.simpleName}: ${e.message}")
            }
        }.apply {
            putInt("exitCode", command?.exitCode ?: -1)
            putBoolean("timedOut", command?.timedOut ?: false)
            putString("stdout", command?.stdout.orEmpty())
            putString("stderr", command?.stderr.orEmpty())
            putLong("elapsedMs", SystemClock.elapsedRealtime() - started)
            Log.i("PersonalAgent", "TAP_RESULT success=${getBoolean("success")} exit=${getInt("exitCode")} elapsedMs=${getLong("elapsedMs")}")
        }
    }

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
