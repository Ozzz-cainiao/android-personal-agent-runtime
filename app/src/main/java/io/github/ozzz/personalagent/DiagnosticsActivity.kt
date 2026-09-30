package io.github.ozzz.personalagent

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Offline run history, screenshot preview, and user-directed ZIP export through Android's picker. */
class DiagnosticsActivity : Activity() {
    private var selected: File? = null
    private var exporting: File? = null
    private lateinit var details: TextView
    private lateinit var screenshot: ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 48) }
        val scroll = ScrollView(this).apply {
            addView(root)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        root.addView(TextView(this).apply { text = "运行记录与排查\n最近5次；截图和页面文字仅保存在本机。导出前请注意个人信息。"; textSize = 18f })
        val runs = TaskDiagnostics.runs(this)
        for (run in runs) root.addView(Button(this).apply {
            text = run.name
            setOnClickListener { show(run) }
        })
        root.addView(Button(this).apply {
            text = "导出选中记录 ZIP"
            isEnabled = runs.isNotEmpty()
            setOnClickListener {
                exporting = selected
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "diandao-${selected?.name}.zip")
                }, 1)
            }
        })
        details = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        screenshot = ImageView(this).apply { adjustViewBounds = true; contentDescription = "最后成功采集的任务截图，采集时间见日志" }
        screenshot.visibility = android.view.View.GONE
        root.addView(Button(this).apply {
            text = "展开 / 收起最后截图"
            setOnClickListener { screenshot.visibility = if (screenshot.visibility == android.view.View.GONE)
                android.view.View.VISIBLE else android.view.View.GONE }
        })
        root.addView(screenshot)
        root.addView(details)
        setContentView(scroll)
        selected = runs.firstOrNull { it.name == savedInstanceState?.getString("selected") } ?: runs.firstOrNull()
        exporting = runs.firstOrNull { it.name == savedInstanceState?.getString("exporting") }
        selected?.let(::show) ?: run { details.text = "还没有任务记录。运行一键领取后可在这里查看。" }
    }
    private fun show(run: File) {
        selected = run
        fun read(name: String) = File(run, name).takeIf { it.exists() }?.readText().orEmpty()
        details.text = "${run.name}\n${read("device.txt")}\n${read("status.txt")}\n最后页面：${read("page.txt")}\n截图：${read("screenshot.txt").ifBlank { "未采集成功，查看下面日志" }}\n\n${read("events.log")}"
        val file = File(run, "latest-screen.png")
        screenshot.setImageBitmap(if (file.exists()) BitmapFactory.decodeFile(file.path,
            BitmapFactory.Options().apply { inSampleSize = 2 }) else null)
    }
    @Deprecated("Activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val source = exporting ?: return
        Thread {
            val result = runCatching {
                ZipOutputStream(checkNotNull(contentResolver.openOutputStream(uri))).use { zip ->
                    source.listFiles()?.filter { it.isFile && it.extension != "tmp" }?.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            runOnUiThread {
                android.widget.Toast.makeText(this, if (result.isSuccess) "诊断包已导出" else
                    "导出失败：${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }.start()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected?.name)
        outState.putString("exporting", exporting?.name)
        super.onSaveInstanceState(outState)
    }
}
