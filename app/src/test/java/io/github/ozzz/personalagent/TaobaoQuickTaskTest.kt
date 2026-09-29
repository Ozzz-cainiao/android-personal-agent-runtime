package io.github.ozzz.personalagent

import org.junit.Assert.*
import org.junit.Test

class TaobaoQuickTaskTest {
    @Test fun recognizesActualSplitEarnedBadgeWithoutExtraCompletionText() {
        val label = UiNode("已得", "", "", 919, 1386, 981, 1428, false, true, true)
        val amount = UiNode("30", "", "", 975, 1386, 1015, 1423, false, true, true)
        assertTrue(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(label, amount))))
        assertFalse(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(label, amount.copy(visible = false)))))
        assertFalse(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(label, amount.copy(top = 1500, bottom = 1540)))))
        assertFalse(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(label, amount.copy(left = 100, right = 140)))))
    }

    @Test fun purchaseOfferAndUnrelatedAmountsAreNotEarnedReward() {
        assertFalse(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(node("下单再得", 800, 1300), node("500", 920, 1300)))))
        assertFalse(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(node("未得30", 800, 1300)))))
        assertFalse(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(node("浏览15秒", 800, 1300), node("30", 920, 1300)))))
        assertTrue(TaobaoQuickTask.browseRewardEarned(UiSnapshot(listOf(node("已得30", 800, 1300)))))
    }

    private fun node(text: String, x: Int, y: Int) = UiNode(text, "", "", x, y, x + 100, y + 50, false, true, true)
    @Test fun rewardMustBelongToNamedRow() {
        val target = node("+30", 500, 100)
        val page = UiSnapshot(listOf(node("好物沉浸看", 100, 100), target, node("+30", 500, 300)))
        assertEquals(target, TaobaoQuickTask.reward(page, "好物沉浸看", "+30"))
        assertNull(TaobaoQuickTask.reward(page, "未知任务", "+30"))
    }
    @Test fun ambiguousRewardsAreRejected() {
        val page = UiSnapshot(listOf(node("好物沉浸看", 100, 100), node("+30", 500, 100), node("+30", 650, 100)))
        assertNull(TaobaoQuickTask.reward(page, "好物沉浸看", "+30"))
    }
}
