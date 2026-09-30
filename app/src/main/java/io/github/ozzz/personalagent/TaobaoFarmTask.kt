package io.github.ozzz.personalagent

/** Visit only: no taps, payments, permissions or game actions inside Alipay. */
internal object TaobaoFarmTask {
    private const val TAOBAO = "com.taobao.taobao"
    private const val ALIPAY = "com.eg.android.AlipayGphone"
    fun run(runtime: AutomationRuntime, readTaobao: () -> UiSnapshot): UiSnapshot {
        var visited = false
        for (attempt in 1..8) {
            runtime.pause(750)
            val page = try { UiSnapshot.parse(runtime.readUi(ALIPAY)) }
            catch (e: Exception) {
                // Only the observed transition errors are retryable; cancellation is not.
                if (e.message.orEmpty().contains("当前控件树不属于目标 App") ||
                    e.message.orEmpty().contains("当前页面没有可读取的控件树")) continue
                throw e
            }
            check(page.findExact("蚂蚁庄园") != null) { "支付宝未进入蚂蚁庄园；停止，不操作其他页面" }
            visited = true
            break
        }
        check(visited) { "未确认跳转到蚂蚁庄园，停止" }
        runtime.log("[庄园] 已确认访问，只返回淘宝核对奖励")
        runtime.pause(1500)
        runtime.launch(TAOBAO)
        runtime.pause(1500)
        var page = readTaobao()
        repeat(6) {
            if (!TaobaoQuickTask.allDone(page)) {
                runtime.pause(750)
                page = readTaobao()
            }
        }
        check(TaobaoQuickTask.allDone(page)) { "已访问庄园，但未确认今日快速赚奖励已拿完；停止，不重复跳转" }
        return page
    }
}
