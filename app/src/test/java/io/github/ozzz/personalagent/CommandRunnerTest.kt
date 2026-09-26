package io.github.ozzz.personalagent

import org.junit.Assert.*
import org.junit.Test

class CommandRunnerTest {
    @Test fun tapRequiresUnambiguousTargetInForeground() {
        fun output(text: String) = CommandRunner.Result(0, "Display #0 (activities from top to bottom):\n" + text, "", false)
        assertTrue(TapProtocol.isForeground("com.example.app", output("  topResumedActivity=ActivityRecord{123 u0 com.example.app/.Main t1}")))
        assertFalse(TapProtocol.isForeground("com.example.app", output("topResumedActivity=ActivityRecord{123 u0 com.example.app.other/.Main t1}")))
        assertFalse(TapProtocol.isForeground("com.example.app", output("mResumedActivity=null")))
        assertFalse(TapProtocol.isForeground("com.example.app", CommandRunner.Result(1, "topResumedActivity=ActivityRecord{123 u0 com.example.app/.Main t1}", "", false)))
    }

    @Test fun drainsLargeOutputWithoutDeadlock() {
        val result = CommandRunner.run(listOf("/bin/sh", "-c",
            "i=0; while [ \"\$i\" -lt 4000 ]; do echo abcdefghijklmnopqrstuvwxyz; echo error-output >&2; i=\$((i+1)); done"), 5_000)
        assertEquals(0, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(result.stdout.endsWith("[output truncated]"))
        assertTrue(result.stderr.endsWith("[output truncated]"))
        assertTrue(result.stdout.length < 8300)
    }

    @Test fun timesOutAndTerminatesCommand() {
        val result = CommandRunner.run(listOf("/bin/sleep", "5"), 100)
        assertTrue(result.timedOut)
        assertEquals(-1, result.exitCode)
    }

    @Test fun preservesNonzeroExitAndStderr() {
        val result = CommandRunner.run(listOf("/bin/sh", "-c", "echo failure >&2; exit 7"), 1_000)
        assertEquals(7, result.exitCode)
        assertEquals("failure", result.stderr)
    }

    @Test fun rejectsMissingOrForeignLauncherAndInvalidPackage() {
        fun output(text: String) = CommandRunner.Result(0, text, "", false)
        assertNull(LaunchProtocol.component("com.example.app", output("No activity found")))
        assertNull(LaunchProtocol.component("com.example.app", output("other.app/.Main")))
        assertEquals("com.example.app/.Main", LaunchProtocol.component("com.example.app", output("com.example.app/.Main")))
        assertFalse(LaunchProtocol.validPackage("com.example;am start"))
    }

    @Test fun requiresExplicitLaunchSuccess() {
        assertFalse(LaunchProtocol.succeeded(CommandRunner.Result(0, "", "", false)))
        assertFalse(LaunchProtocol.succeeded(CommandRunner.Result(0, "Status: timeout", "", false)))
        assertFalse(LaunchProtocol.succeeded(CommandRunner.Result(0, "Status: ok", "Error: failed", false)))
        assertTrue(LaunchProtocol.succeeded(CommandRunner.Result(0, "Warning: Activity not started, its current task has been brought to the front\nStatus: ok", "", false)))
    }
}
