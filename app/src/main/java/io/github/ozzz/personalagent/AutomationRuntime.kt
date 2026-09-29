package io.github.ozzz.personalagent

/** Small synchronous task API; implemented by the client on its worker thread. */
interface AutomationRuntime {
    fun returnHome(expectedPackage: String)
    fun launch(packageName: String)
    fun readUi(packageName: String): String
    fun tap(packageName: String, x: Int, y: Int)
    fun swipe(packageName: String, startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int)
    fun pause(milliseconds: Long)
    fun log(message: String)
}
