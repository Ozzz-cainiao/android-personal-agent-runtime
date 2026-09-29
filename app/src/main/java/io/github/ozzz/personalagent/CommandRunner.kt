package io.github.ozzz.personalagent

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Argument arrays only; concurrently drain both pipes, with bounded retained output. */
internal object CommandRunner {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean)

    fun run(args: List<String>, timeoutMs: Long, outputLimit: Int = 8192): Result {
        require(args.isNotEmpty() && timeoutMs > 0 && outputLimit in 1..256_000)
        val process = ProcessBuilder(args).start()
        val readers = Executors.newFixedThreadPool(2)
        try {
            process.outputStream.close()
            val stdout = readers.submit<String> { capture(process.inputStream, outputLimit) }
            val stderr = readers.submit<String> { capture(process.errorStream, outputLimit) }
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(500, TimeUnit.MILLISECONDS)
            }
            return Result(
                if (finished) process.exitValue() else -1,
                stdout.get(1, TimeUnit.SECONDS), stderr.get(1, TimeUnit.SECONDS), !finished,
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.inputStream.close()
            process.errorStream.close()
            readers.shutdownNow()
        }
    }

    private fun capture(stream: InputStream, limit: Int): String = stream.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var truncated = false
        while (true) {
            val size = it.read(buffer)
            if (size == -1) break
            val retained = minOf(size, limit - output.size())
            output.write(buffer, 0, retained)
            if (retained < size) truncated = true
        }
        output.toString("UTF-8").trim() + if (truncated) "\n[output truncated]" else ""
    }
}

/** Parse command output conservatively; exit code 0 alone is not launch success. */
internal object LaunchProtocol {
    fun validPackage(value: String): Boolean =
        value.length <= 255 && Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value)

    fun component(packageName: String, result: CommandRunner.Result): String? {
        if (result.timedOut || result.exitCode != 0) return null
        val pattern = Regex(Regex.escape(packageName) + "/[A-Za-z0-9_.$]+")
        return result.stdout.lineSequence().map(String::trim).filter(pattern::matches).singleOrNull()
    }

    fun succeeded(result: CommandRunner.Result): Boolean =
        !result.timedOut && result.exitCode == 0 &&
            result.stdout.lineSequence().any { it.trim() == "Status: ok" } &&
            (result.stdout + "\n" + result.stderr).lineSequence().none {
                it.trim().startsWith("Error:") || it.contains("current activity is being kept") ||
                    it.contains("should be handled by the caller")
            }
}

internal object TapProtocol {
    fun isForeground(packageName: String, result: CommandRunner.Result): Boolean {
        if (result.timedOut || result.exitCode != 0) return false
        var primary = false
        val tops = result.stdout.lineSequence().filter { line ->
            if (line.startsWith("Display #")) primary = line.startsWith("Display #0 ")
            primary && line.trim().startsWith("topResumedActivity=")
        }.toList()
        // Only display 0: another app may be running on a virtual display.
        return tops.size == 1 && Regex("\\s" + Regex.escape(packageName) + "/").containsMatchIn(tops.single())
    }
}

internal object SwipeProtocol {
    fun command(packageName: String, startX: Int, startY: Int, endX: Int, endY: Int,
                durationMs: Int, foreground: CommandRunner.Result): List<String> {
        require(LaunchProtocol.validPackage(packageName))
        require(listOf(startX, startY, endX, endY).all { it >= 0 })
        require(startX != endX || startY != endY) { "滑动起终点不能相同" }
        require(durationMs in 100..1500) { "滑动时长必须在100至1500毫秒内" }
        check(TapProtocol.isForeground(packageName, foreground)) { "目标 App 未处于主屏前台，取消滑动" }
        return listOf("/system/bin/input", "-d", "0", "swipe", startX.toString(), startY.toString(),
            endX.toString(), endY.toString(), durationMs.toString())
    }
}
