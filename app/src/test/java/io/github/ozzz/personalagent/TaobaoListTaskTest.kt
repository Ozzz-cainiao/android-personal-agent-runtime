package io.github.ozzz.personalagent

import org.junit.Assert.*
import org.junit.Test

class TaobaoListTaskTest {
    private fun node(text: String, l: Int, t: Int, r: Int, b: Int) =
        UiNode(text, "", "", l, t, r, b, false, true, true)
    private fun panel(n: Int) = UiSnapshot(listOf(
        node("今日速赚", 45, 286, 289, 371),
        node("逛清单，每15秒30金币($n/2)", 196, 666, 731, 728),
        node("足迹加抵", 39, 773, 180, 810),
        node("足迹加抵", 196, 739, 458, 984)))
    private fun list(done: Boolean) = UiSnapshot(listOf(
        node("淘宝购物清单", 0, 0, 1080, 2358),
        node("已得30", 112, 157, 317, 219),
        node(if (done) "30淘金币已到账" else "每浏览15秒得30", 551, 160, 1040, 216)))
    private class Runtime : AutomationRuntime {
        var swipes = 0
        var taps = 0
        var backs = 0
        override fun returnHome(expectedPackage: String) {}
        override fun back(expectedPackage: String) { backs++ }
        override fun launch(packageName: String) {}
        override fun readUi(packageName: String): String = error("unused")
        override fun tap(packageName: String, x: Int, y: Int) { taps++ }
        override fun swipe(packageName: String, startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) { swipes++ }
        override fun pause(milliseconds: Long) {}
        override fun log(message: String) {}
    }
    @Test fun cumulativeThirtyIsNotThisRoundCompletion() {
        val runtime = Runtime()
        val pages = java.util.ArrayDeque(listOf(list(false), list(false), list(true)))
        TaobaoListTask.browse(runtime) { pages.removeFirst() }
        assertEquals(2, runtime.swipes)
    }
    @Test fun waitsForBannerWithoutWaitingForPageIdle() {
        val runtime = Runtime()
        val loading = UiSnapshot(list(false).nodes.take(2))
        val generic = UiSnapshot(list(false).nodes.map { if (it.text == "每浏览15秒得30") it.copy(text = "每浏览15秒得奖励") else it })
        val pages = java.util.ArrayDeque(listOf(loading, generic, list(true)))
        TaobaoListTask.browse(runtime) { pages.removeFirst() }
        assertEquals(1, runtime.swipes)
    }
    @Test fun finalRoundCounterSixtyEndsWithoutTransientBanner() {
        val runtime = Runtime()
        val done = UiSnapshot(listOf(node("淘宝购物清单", 0, 0, 1080, 2358),
            node("已得", 112, 157, 202, 219), node("60", 258, 157, 320, 219),
            node("当前页下单另得500", 483, 160, 1040, 216)))
        assertTrue(TaobaoListTask.finalRewardShown(done))
        assertFalse(TaobaoListTask.finalRewardShown(UiSnapshot(done.nodes.map {
            if (it.text == "60") it.copy(text = "30") else it
        })))
        TaobaoListTask.browse(runtime) { done }
        assertEquals(0, runtime.swipes)
    }
    @Test fun cardExcludesUnderlyingHomepageDuplicate() {
        assertEquals(196, TaobaoListTask.card(panel(1)).left)
    }
    @Test fun secondRoundRequiresPanelIncrementAndStopsAtTwo() {
        val runtime = Runtime()
        val pages = java.util.ArrayDeque(listOf(list(false), list(true), panel(1), panel(2)))
        val result = TaobaoListTask.run(runtime, panel(1)) { pages.removeFirst() }
        assertEquals(1, result.second)
        assertEquals(2, TaobaoListTask.progress(result.first))
        assertEquals(1, runtime.taps)
        assertEquals(1, runtime.backs)
        assertEquals(1, runtime.swipes)
    }
    @Test fun unchangedPanelDoesNotRepeatRewardOrReportSuccess() {
        val runtime = Runtime()
        var reads = 0
        assertTrue(runCatching { TaobaoListTask.run(runtime, panel(1)) {
            if (reads++ == 0) list(true) else panel(1)
        } }.isFailure)
        assertEquals(1, runtime.taps)
    }
    @Test fun completedPanelDoesNotEnterAndUnknownPageDoesNotSwipe() {
        val runtime = Runtime()
        assertEquals(0, TaobaoListTask.run(runtime, panel(2)) { error("must not read") }.second)
        assertTrue(runCatching { TaobaoListTask.browse(runtime) { panel(1) } }.isFailure)
        assertEquals(0, runtime.swipes)
    }
    @Test fun boundedWhenCoinsNeverArrive() {
        val runtime = Runtime()
        assertTrue(runCatching { TaobaoListTask.browse(runtime) { list(false) } }.isFailure)
        assertEquals(10, runtime.swipes)
    }
}
