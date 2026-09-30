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
    private var exitAfterTask = false
    private val entries = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!getSharedPreferences("setup", MODE_PRIVATE).getBoolean("complete", false)) {
            startActivity(android.content.Intent(this, SetupActivity::class.java))
            finish()
            return
        }
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
            text = "点到 · 淘金币"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "一键启动淘宝 → 签到 → 快速赚 → 返回桌面并关闭点到页面。\n单页最多60秒，总任务最多120秒；失败保留记录。"
            textSize = 16f
            setPadding(0, spacing, 0, spacing)
        })
        val test = Button(this).apply { text = "测试 Shizuku 连接" }
        val launch = Button(this).apply { text = "启动淘宝（不点击）" }
        val inspect = Button(this).apply { text = "签到并返回桌面" }
        val quick = Button(this).apply { text = "一键领取淘金币" }
        val disconnect = Button(this).apply { text = "取消任务 / 断开连接" }
        val setup = Button(this).apply {
            text = "配置检查 / 使用引导"
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, SetupActivity::class.java)) }
        }
        val history = Button(this).apply {
            text = "运行记录 / 截图 / 导出日志"
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, DiagnosticsActivity::class.java)) }
        }
        root.addView(quick)
        root.addView(history)
        root.addView(setup)
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            addView(test); addView(launch); addView(inspect)
        }
        root.addView(Button(this).apply {
            text = "展开 / 收起单步调试"
            setOnClickListener { tools.visibility = if (tools.visibility == android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE }
        })
        root.addView(tools)
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
            val serviceIntent = android.content.Intent(this, TaskService::class.java)
            if (busy) startForegroundService(serviceIntent) else stopService(serviceIntent)
            history.isEnabled = !busy
            setup.isEnabled = !busy
            test.isEnabled = !busy
            launch.isEnabled = !busy
            inspect.isEnabled = !busy
            quick.isEnabled = !busy
        }
        test.setOnClickListener { client.testConnection() }
        launch.setOnClickListener { TaobaoSmokeTask.launch(client) }
        inspect.setOnClickListener {
            exitAfterTask = false
            client.runTask { runtime ->
                TaobaoCoinTask.runAndReturnHome(runtime) { xml ->
                    java.io.File(filesDir, "last-ui.xml").writeText(xml)
                }
            }
        }
        quick.setOnClickListener { exitAfterTask = true; client.runTask { runtime ->
            TaobaoQuickTask.runAndReturnHome(runtime) { xml -> java.io.File(filesDir, "quick-ui.xml").writeText(xml) }
        } }
        disconnect.setOnClickListener { exitAfterTask = false; client.disconnect() }
    }

    private fun appendLog(message: String) {
        Log.i("PersonalAgent", message)
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        entries.addLast("$time $message")
        while (entries.size > 150) entries.removeFirst()
        logView.text = entries.joinToString("\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        if (exitAfterTask && message.startsWith("[任务结果]")) {
            exitAfterTask = false
            finishAndRemoveTask()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("logs", ArrayList(entries))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
}
