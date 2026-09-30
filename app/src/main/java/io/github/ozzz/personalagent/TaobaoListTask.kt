package io.github.ozzz.personalagent

/** The observed two-round shopping-list task. Never treats cumulative coins as a new reward. */
internal object TaobaoListTask {
    private const val PACKAGE = "com.taobao.taobao"
    private val progressPattern = Regex("逛清单，每15秒30金币\\(([0-2])/2\\)")

    fun progress(page: UiSnapshot): Int? = page.nodes.filter { it.usable }
        .mapNotNull { progressPattern.matchEntire(it.text)?.groupValues?.get(1)?.toInt() }
        .singleOrNull()

    internal fun card(page: UiSnapshot): UiNode {
        check(page.findExact("今日速赚") != null) { "未确认快速赚面板" }
        val header = page.nodes.single { it.usable && progressPattern.matches(it.text) }
        // The underlying coin homepage also exposes 足迹加抵; use only cards below this task row.
        return checkNotNull(page.nodes.firstOrNull { it.usable &&
            (it.named("足迹加抵") || it.named("户外清仓折扣")) &&
            it.left >= header.left && it.right <= header.right + 30 &&
            it.top >= header.bottom && it.bottom <= header.bottom + (header.bottom - header.top) * 5 }) {
            "清单任务卡片未确认，停止"
        }
    }

    internal fun earnedTotal(page: UiSnapshot): Int? {
        val root = page.findExact("淘宝购物清单") ?: return null
        val label = page.nodes.singleOrNull { it.usable && it.named("已得") &&
            it.right < root.right / 2 && it.bottom < root.bottom / 6 } ?: return null
        return page.nodes.filter { it.usable && it.left >= label.right &&
            it.right < root.right / 2 && it.top < label.bottom && it.bottom > label.top }
            .mapNotNull { it.text.toIntOrNull() }.singleOrNull()
    }

    internal fun finalRewardShown(page: UiSnapshot): Boolean {
        val root = page.findExact("淘宝购物清单") ?: return false
        return earnedTotal(page) == 60 && page.nodes.any { it.usable &&
            it.named("当前页下单另得500") && it.top < root.bottom / 7 && it.left > root.right / 3 }
    }

    internal fun browse(runtime: AutomationRuntime, read: () -> UiSnapshot) {
        repeat(11) { step ->
            var page = read()
            // The shopping list loads its task banner after the product list itself.
            for (attempt in 1..5) {
                val root = checkNotNull(page.findExact("淘宝购物清单")) { "未确认购物清单页面，停止滑动" }
                val top = page.nodes.filter { it.usable && it.left >= root.right / 2 && it.bottom <= root.bottom / 6 }
                if (finalRewardShown(page)) break
                if (top.any { it.text.contains("浏览") || it.text.contains("已到账") }) break
                runtime.log("[清单] 等待奖励关键词加载 $attempt/5")
                runtime.pause(750)
                page = read()
            }
            val screen = checkNotNull(page.findExact("淘宝购物清单")) { "未确认购物清单页面，停止滑动" }
            val banner = page.nodes.filter { it.usable && it.left >= screen.right / 2 &&
                it.top < screen.bottom / 7 && it.bottom <= screen.bottom / 6 }
            if (finalRewardShown(page) || banner.any { it.named("30淘金币已到账") || it.named("完成! 30淘金币已到账") ||
                    it.named("完成！30淘金币已到账") }) {
                runtime.log("[清单] 奖励完成标志已确认（累计=${earnedTotal(page)}），立即停止滑动")
                return
            }
            check(step < 10) { "清单浏览已达10次上限，未确认到账" }
            check(banner.any { (it.text.contains("浏览") && it.text.contains("30")) || it.named("每浏览15秒得奖励") }) {
                "未确认清单浏览计时，停止滑动"
            }
            runtime.log("[清单] 浏览滑动 ${step + 1}/10")
            runtime.swipe(PACKAGE, screen.right * 48 / 100, screen.bottom * 73 / 100,
                screen.right * 48 / 100, screen.bottom * 42 / 100, 650)
            runtime.pause(2000)
        }
    }

    fun run(runtime: AutomationRuntime, initial: UiSnapshot, read: () -> UiSnapshot): Pair<UiSnapshot, Int> {
        var page = initial
        var completed = 0
        repeat(2) {
            val before = progress(page) ?: return page to completed
            if (before == 2) return page to completed
            val target = card(page)
            runtime.log("[清单] 开始第 ${before + 1}/2 轮")
            runtime.tap(PACKAGE, target.x, target.y)
            runtime.pause(1500)
            browse(runtime, read)
            runtime.back(PACKAGE)
            runtime.pause(1000)
            page = read()
            for (attempt in 1..4) {
                if (page.findExact("今日速赚") != null && progress(page) == before + 1) break
                runtime.pause(750)
                page = read()
            }
            check(page.findExact("今日速赚") != null && progress(page) == before + 1) {
                "清单已显示到账，但面板进度未确认增加；停止，不重复领奖"
            }
            completed++
            runtime.log("[清单] 面板确认进度 ${before + 1}/2")
        }
        return page to completed
    }
}
