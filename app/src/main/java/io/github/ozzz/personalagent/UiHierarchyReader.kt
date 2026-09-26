package io.github.ozzz.personalagent

import android.app.UiAutomation
import android.graphics.Rect
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Xml
import android.util.Log
import android.os.SystemClock
import java.io.StringWriter

/** Shell-only UiAutomation session; read the current tree without waiting for animation idle. */
internal object UiHierarchyReader {
    @Suppress("DEPRECATION")
    fun read(expectedPackage: String): String {
        val thread = HandlerThread("ui-reader").apply { start() }
        var automation: UiAutomation? = null
        try {
            val connectionType = Class.forName("android.app.IUiAutomationConnection")
            val connection = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance()
            automation = UiAutomation::class.java.getConstructor(Looper::class.java, connectionType)
                .newInstance(thread.looper, connection)
            UiAutomation::class.java.getMethod("connect", Int::class.javaPrimitiveType)
                .invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            Log.i("PersonalAgent", "UI_CONNECTED")
            val deadline = SystemClock.elapsedRealtime() + 12_000
            var root: AccessibilityNodeInfo? = null
            repeat(5) {
                if (root == null) {
                    root = automation.rootInActiveWindow
                    if (root == null) Thread.sleep(200)
                }
            }
            val node = checkNotNull(root) { "当前页面没有可读取的控件树" }
            try {
                check(node.packageName?.toString() == expectedPackage) { "当前控件树不属于目标 App" }
                Log.i("PersonalAgent", "UI_ROOT_READY")
                val writer = StringWriter()
                val xml = Xml.newSerializer().apply { setOutput(writer); startDocument("UTF-8", true); startTag(null, "hierarchy") }
                var count = 0
                fun append(current: AccessibilityNodeInfo, depth: Int) {
                    check(SystemClock.elapsedRealtime() < deadline) { "页面读取超时" }
                    check(++count <= 1800 && depth <= 60) { "页面控件过多，停止解析" }
                    val bounds = Rect().also(current::getBoundsInScreen)
                    xml.startTag(null, "node")
                    xml.attribute(null, "text", current.text?.toString()?.take(240).orEmpty())
                    xml.attribute(null, "desc", current.contentDescription?.toString()?.take(240).orEmpty())
                    xml.attribute(null, "id", current.viewIdResourceName.orEmpty())
                    xml.attribute(null, "bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
                    xml.attribute(null, "clickable", current.isClickable.toString())
                    xml.attribute(null, "enabled", current.isEnabled.toString())
                    xml.attribute(null, "visible", current.isVisibleToUser.toString())
                    for (i in 0 until current.childCount) {
                        current.getChild(i)?.let { child -> try { append(child, depth + 1) } finally { child.recycle() } }
                    }
                    xml.endTag(null, "node")
                }
                append(node, 0)
                xml.endTag(null, "hierarchy"); xml.endDocument()
                Log.i("PersonalAgent", "UI_READ_DONE nodes=$count")
                return writer.toString().also { check(it.length <= 220_000) { "页面数据过大" } }
            } finally { node.recycle() }
        } finally {
            automation?.let { runCatching { UiAutomation::class.java.getMethod("disconnect").invoke(it) } }
            thread.quitSafely()
        }
    }
}
