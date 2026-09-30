package io.github.ozzz.personalagent

/** Only the two task types observed on-device; unknown offers are never clicked. */
object TaobaoQuickTask {
    private const val PACKAGE = "com.taobao.taobao"
    /** The reward badge can expose '已得' and '30' as separate adjacent nodes. */
    internal fun browseRewardEarned(page: UiSnapshot): Boolean {
        if (page.findExact("已得30") != null || page.findExact("已得 30") != null) return true
        return page.nodes.filter { it.usable && it.named("已得") }.any { label ->
            val height = label.bottom - label.top
            page.nodes.count { amount ->
                amount.usable && amount.named("30") && amount.x > label.x &&
                    amount.left >= label.right - height / 2 && amount.left <= label.right + height &&
                    amount.top < label.bottom && amount.bottom > label.top
            } == 1
        }
    }
    internal fun reward(page: UiSnapshot, title: String, amount: String): UiNode? {
        val label = page.findExact(title) ?: return null
        return page.nodes.filter { it.usable && it.named(amount) && it.left > label.right &&
            it.top <= label.bottom && it.bottom >= label.top }.singleOrNull()
    }

    fun runAndReturnHome(runtime: AutomationRuntime, record: (String) -> Unit = {}): String {
        val result = run(runtime, record)
        runtime.log("[快速赚] $result；准备返回桌面")
        try { runtime.returnHome(PACKAGE) }
        catch (e: Exception) { throw IllegalStateException("$result；但返回桌面失败：${e.message}", e) }
        return "$result；已发送返回桌面指令"
    }

    /** Swipe only inside the observed product-video page, away from purchase controls. */
    internal fun browseViewport(page: UiSnapshot): UiNode {
        val root = checkNotNull(page.nodes.firstOrNull { it.usable && it.left == 0 && it.top == 0 }) {
            "未确认屏幕边界，停止滑动"
        }
        val videoContent = page.nodes.any { it.usable && it.named("图片，按钮。双击可进入详情页。") &&
            it.left <= root.right * 0.46 && it.right >= root.right * 0.46 &&
            it.top <= root.bottom * 0.36 && it.bottom >= root.bottom * 0.58 }
        check(videoContent || (page.findExact("加入购物车") != null && page.findExact("立即购买") != null)) {
            "浏览页面已变化，停止滑动"
        }
        val badge = page.nodes.filter { it.usable && it.left >= root.right * 0.7 &&
            it.top > root.bottom * 0.2 && it.bottom < root.bottom * 0.8 }
        check(badge.any { it.text.contains("浏览") || it.description.contains("浏览") } &&
            badge.any { it.named("30") || it.named("得30") || it.named("得 30") }) {
            "未确认右侧浏览奖励计时，停止滑动"
        }
        return root
    }

    internal fun browse(runtime: AutomationRuntime, read: () -> UiSnapshot): String {
        // Ten bounded gestures; the client also enforces the overall task timeout.
        repeat(11) { step ->
            val page = read()
            if (browseRewardEarned(page)) {
                runtime.log("[快速赚] 右侧奖励确认已得30，停止滑动")
                return "好物沉浸看页面确认：已得30；其他类型未执行"
            }
            check(step < 10) { "浏览已达10次滑动上限，未确认奖励，停止任务" }
            val screen = browseViewport(page)
            runtime.log("[快速赚] 浏览滑动 ${step + 1}/10")
            runtime.swipe(PACKAGE, screen.right * 46 / 100, screen.bottom * 58 / 100,
                screen.right * 46 / 100, screen.bottom * 36 / 100, 650)
            runtime.pause(2000)
        }
        error("未确认浏览结果")
    }

    fun run(runtime: AutomationRuntime, record: (String) -> Unit = {}): String {
        fun read(): UiSnapshot {
            val xml = runtime.readUi(PACKAGE)
            record(xml)
            return UiSnapshot.parse(xml)
        }
        fun tap(node: UiNode) = runtime.tap(PACKAGE, node.x, node.y)
        runtime.log("[快速赚] 进入淘金币")
        val signIn = TaobaoCoinTask.run(runtime, record)
        runtime.log("[签到结果] $signIn")
        var entry = read().findExact("40秒快速赚")
        for (attempt in 1..5) {
            if (entry != null) break
            runtime.log("[快速赚] 等待签到动画结束和入口加载 $attempt/5")
            runtime.pause(1000)
            entry = read().findExact("40秒快速赚")
        }
        checkNotNull(entry) { "签到已确认，但等待后仍未找到快速赚入口" }
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
        runtime.log("[快速赚] 进入好物沉浸看，分段滑动并检查奖励（最多10次）")
        tap(video)
        runtime.pause(1500)
        return browse(runtime, ::read)
    }
}
