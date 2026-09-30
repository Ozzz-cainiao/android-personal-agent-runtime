package io.github.ozzz.personalagent

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Local only; five bounded runs. No uploads. Every screenshot has its own timestamp/status. */
internal class TaskDiagnostics(context: Context) {
    private var terminal = false
    val directory: File
    init {
        val parent = File(context.filesDir, "runs").apply { mkdirs() }
        directory = File(parent, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) +
            "-" + UUID.randomUUID().toString().take(6)).apply { mkdirs() }
        parent.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.deleteRecursively() }
        File(directory, "device.txt").writeText("点到 ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n")
        status("运行中")
    }
    @Synchronized fun log(message: String) {
        val file = File(directory, "events.log")
        if (file.length() < 512_000) file.appendText("${stamp()} $message\n")
    }
    @Synchronized fun status(value: String) {
        terminal = value != "运行中"
        File(directory, "status.txt").writeText("${stamp()} $value")
    }
    @Synchronized fun stopIfRunning(reason: String) { if (!terminal) status(reason) }
    @Synchronized fun page(xml: String, key: String) {
        File(directory, "latest-ui.xml").writeText(xml)
        File(directory, "page.txt").writeText("${stamp()} $key")
    }
    @Synchronized fun screenshot(fd: ParcelFileDescriptor, reason: String) {
        val temp = File(directory, "capture.tmp")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    var size = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        check(size <= 8_000_000) { "截图超过8MB限制" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temp.length() > 0) { "截图为空" }
            check(temp.renameTo(File(directory, "latest-screen.png"))) { "截图保存失败" }
            File(directory, "screenshot.txt").writeText("${stamp()} $reason")
        } finally { temp.delete() }
    }
    companion object {
        private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
        fun runs(context: Context): List<File> = File(context.filesDir, "runs").listFiles()
            ?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
    }
}
