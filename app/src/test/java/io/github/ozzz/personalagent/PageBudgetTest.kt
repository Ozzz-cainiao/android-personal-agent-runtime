package io.github.ozzz.personalagent

import org.junit.Assert.*
import org.junit.Test

class PageBudgetTest {
    @Test fun sixtySecondsStopsEvenWhenPageIsReadRepeatedly() {
        var now = 0L
        val budget = PageBudget({ now })
        assertTrue(budget.observe("taobao/清单浏览"))
        now = 59_999
        assertFalse(budget.observe("taobao/清单浏览"))
        budget.check()
        now = 60_000
        assertTrue(runCatching { budget.check() }.isFailure)
        assertTrue(runCatching { budget.observe("taobao/快速赚面板") }.isFailure)
    }
    @Test fun newSemanticPageGetsItsOwnBudget() {
        var now = 0L
        val budget = PageBudget({ now })
        budget.observe("taobao/每日签到")
        now = 40_000
        budget.observe("taobao/快速赚面板")
        assertEquals(60_000L, budget.remaining())
        now = 90_000
        assertEquals(10_000L, budget.remaining())
    }
    @Test fun transientLoadingCannotResetKnownPageTimer() {
        var now = 0L
        val budget = PageBudget({ now })
        budget.observe("taobao/清单浏览")
        now = 40_000
        assertFalse(budget.observe("taobao/未识别/加载"))
        assertEquals(20_000L, budget.remaining())
    }
    @Test fun animationAndCoinNumbersDoNotChangePageIdentity() {
        fun page(amount: String) = UiSnapshot(listOf("淘宝购物清单", amount).map {
            UiNode(it, "", "", 0, 0, 100, 100, false, true, true)
        })
        assertEquals(PageBudget.key("taobao", page("已得30")), PageBudget.key("taobao", page("已得60")))
    }
}
