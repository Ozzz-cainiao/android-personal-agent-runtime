package io.github.ozzz.personalagent

import org.junit.Assert.*
import org.junit.Test

class TaobaoCoinTaskTest {
    private fun page(vararg labels: String) = "<hierarchy>" + labels.joinToString("") {
        "<node text=\"$it\" bounds=\"0,0,100,100\" enabled=\"true\" visible=\"true\"/>"
    } + "</hierarchy>"

    private class Runtime(val pages: List<String>) : AutomationRuntime {
        var index = 0
        var taps = 0
        override fun launch(packageName: String) {}
        override fun readUi(packageName: String) = pages[minOf(index++, pages.lastIndex)]
        override fun tap(packageName: String, x: Int, y: Int) { taps++ }
        override fun pause(milliseconds: Long) {}
        override fun log(message: String) {}
    }

    @Test fun hiddenClaimedTextDoesNotSkipClaim() {
        val runtime = Runtime(listOf(page("淘金币标题", "签到领金币", "已领取"),
            page("淘金币标题", "赚更多金币", "今天")))
        assertTrue(TaobaoCoinTask.run(runtime).contains("签到按钮已消失"))
        assertEquals(1, runtime.taps)
    }

    @Test fun alreadyClaimedDoesNotTap() {
        val runtime = Runtime(listOf(page("淘金币标题", "赚更多金币", "今天")))
        assertTrue(TaobaoCoinTask.run(runtime).contains("无需重复"))
        assertEquals(0, runtime.taps)
    }

    @Test fun unchangedPageStopsAfterOneClaimAttempt() {
        val runtime = Runtime(listOf(page("淘金币标题", "签到领金币", "已领取")))
        assertTrue(runCatching { TaobaoCoinTask.run(runtime) }.isFailure)
        assertEquals(1, runtime.taps)
    }
}
