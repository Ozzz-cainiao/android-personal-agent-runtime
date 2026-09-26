package io.github.ozzz.personalagent

import android.os.Bundle
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/** Instantiated by Shizuku in a separate shell/root process, not an Android Service. */
class AutomationUserService : IAutomationService.Stub() {
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
