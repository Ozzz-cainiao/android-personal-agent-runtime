package io.github.ozzz.personalagent

/** Only the two task types observed on-device; unknown offers are never clicked. */
object TaobaoQuickTask {
    private const val PACKAGE = "com.taobao.taobao"
    internal fun reward(page: UiSnapshot, title: String, amount: String): UiNode? {
        val label = page.findExact(title) ?: return null
        return page.nodes.filter { it.usable && it.named(amount) && it.left > label.right &&
            it.top <= label.bottom && it.bottom >= label.top }.singleOrNull()
    }

    fun run(runtime: AutomationRuntime, record: (String) -> Unit = {}): String {
        fun read(): UiSnapshot {
            val xml = runtime.readUi(PACKAGE)
            record(xml)
            return UiSnapshot.parse(xml)
        }
        fun tap(node: UiNode) = runtime.tap(PACKAGE, node.x, node.y)
        runtime.log("[快速赚] 进入淘金币")
        TaobaoCoinTask.run(runtime)
        val entry = checkNotNull(read().findExact("40秒快速赚")) { "未找到快速赚入口" }
        tap(entry)
        runtime.pause(1500)
        var page = read()
        check(page.findExact("今日速赚") != null) { "未确认快速赚任务面板" }
        var arrival = false
        reward(page, "任务到访得金币", "+10")?.let {
            runtime.log("[快速赚] 领取到访奖励一次")
            tap(it)
            runtime.pause(1500)
            page = read()
            check(page.findExact("今日速赚") != null &&
                page.findExact("任务到访得金币每日来任务面板") != null &&
                reward(page, "任务到访得金币", "+10") == null) { "到访领奖结果未确认，停止" }
            arrival = true
        }
        val video = reward(page, "好物沉浸看", "+30")
        if (video == null) return if (arrival) "到访任务已完成；当前无可执行的好物沉浸看任务" else
            "当前没有支持的待领奖任务；清单、答题、跨 App 任务尚未支持"
        runtime.log("[快速赚] 进入好物沉浸看，等待页面确认完成（最多30秒）")
        tap(video)
        repeat(10) {
            runtime.pause(3000)
            page = read()
            val completed = page.nodes.any { it.usable && it.text.contains("任务已完成") }
            val earned = page.nodes.any { it.usable && it.text.contains("已得30") }
            if (completed && earned) return "好物沉浸看页面确认：任务已完成，已得30；其他类型未执行"
        }
        error("浏览任务未提供可确认的完成结果，已停止；未重复点击")
    }
}
