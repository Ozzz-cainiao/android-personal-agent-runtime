package io.github.ozzz.personalagent

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.w3c.dom.Element

data class UiNode(val text: String, val description: String, val id: String,
                  val left: Int, val top: Int, val right: Int, val bottom: Int,
                  val clickable: Boolean, val enabled: Boolean, val visible: Boolean) {
    val x get() = (left + right) / 2
    val y get() = (top + bottom) / 2
    val usable get() = visible && enabled && left >= 0 && top >= 0 && right > left && bottom > top
    fun named(value: String) = text == value || description == value
}

data class UiSnapshot(val nodes: List<UiNode>) {
    fun findExact(value: String): UiNode? = nodes.filter { it.usable && it.named(value) }
        .sortedByDescending { it.clickable }.firstOrNull()

    companion object {
        fun parse(xml: String): UiSnapshot {
            require(xml.length <= 220_000 && !xml.contains("<!DOCTYPE", true))
            val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val elements = doc.getElementsByTagName("node")
            return UiSnapshot((0 until elements.length).mapNotNull { i ->
                val e = elements.item(i) as Element
                val b = e.getAttribute("bounds").split(',').mapNotNull(String::toIntOrNull)
                if (b.size != 4) null else UiNode(e.getAttribute("text"), e.getAttribute("desc"), e.getAttribute("id"),
                    b[0], b[1], b[2], b[3], e.getAttribute("clickable") == "true",
                    e.getAttribute("enabled") == "true", e.getAttribute("visible") == "true")
            })
        }
    }
}
