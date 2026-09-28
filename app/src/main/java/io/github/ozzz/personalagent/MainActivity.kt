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
            text = "Personal Agent · 淘金币验证"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "通过 Shizuku 进入淘金币并领取每日签到奖励。\n返回本 App 查看结果；遇到无法识别的页面停止。"
            textSize = 16f
            setPadding(0, spacing, 0, spacing)
        })
        val test = Button(this).apply { text = "测试 Shizuku 连接" }
        val launch = Button(this).apply { text = "启动淘宝（不点击）" }
        val inspect = Button(this).apply { text = "领取今日淘金币" }
        val quick = Button(this).apply { text = "快速赚金币（测试）" }
        val disconnect = Button(this).apply { text = "取消任务 / 断开连接" }
        val setup = Button(this).apply {
            text = "配置检查 / 使用引导"
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, SetupActivity::class.java)) }
        }
        root.addView(setup)
        root.addView(test)
        root.addView(launch)
        root.addView(inspect)
        root.addView(quick)
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
            setup.isEnabled = !busy
            test.isEnabled = !busy
            launch.isEnabled = !busy
            inspect.isEnabled = !busy
            quick.isEnabled = !busy
        }
        test.setOnClickListener { client.testConnection() }
        launch.setOnClickListener { TaobaoSmokeTask.launch(client) }
        inspect.setOnClickListener {
            client.runTask { runtime ->
                TaobaoCoinTask.run(runtime) { xml ->
                    java.io.File(filesDir, "last-ui.xml").writeText(xml)
                }
            }
        }
        quick.setOnClickListener { client.runTask { runtime ->
            TaobaoQuickTask.run(runtime) { xml -> java.io.File(filesDir, "quick-ui.xml").writeText(xml) }
        } }
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
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
}
