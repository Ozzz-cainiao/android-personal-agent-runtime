package io.github.ozzz.personalagent

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var client: ShizukuRuntimeClient
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private val entries = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spacing = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(spacing + insets.systemWindowInsetLeft,
                spacing + insets.systemWindowInsetTop,
                spacing + insets.systemWindowInsetRight,
                spacing + insets.systemWindowInsetBottom)
            insets
        }
        root.addView(TextView(this).apply {
            text = "Personal Agent · 连接验证"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "第一步：验证 Shizuku 特权进程。\n本版本只读取 UID/PID，不启动淘宝、不执行点击。"
            textSize = 16f
            setPadding(0, spacing, 0, spacing)
        })
        val test = Button(this).apply { text = "测试 Shizuku 连接" }
        val disconnect = Button(this).apply { text = "断开连接" }
        root.addView(test)
        root.addView(disconnect)
        logView = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply {
            addView(logView, android.view.ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        savedInstanceState?.getStringArrayList("logs")?.let { entries.addAll(it) }
        appendLog("[就绪] 点击按钮开始验证。")
        client = ShizukuRuntimeClient(this, ::appendLog) { busy ->
            test.isEnabled = !busy
        }
        test.setOnClickListener { client.testConnection() }
        disconnect.setOnClickListener { client.disconnect() }
    }

    private fun appendLog(message: String) {
        Log.i("PersonalAgent", message)
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        entries.addLast("$time $message")
        while (entries.size > 150) entries.removeFirst()
        logView.text = entries.joinToString("\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("logs", ArrayList(entries))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
}
