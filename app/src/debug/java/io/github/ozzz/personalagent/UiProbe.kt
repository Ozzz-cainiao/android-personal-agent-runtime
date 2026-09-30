package io.github.ozzz.personalagent

/** Debug-only shell entry point: reuse the runtime reader without waiting for UI animation idle. */
object UiProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1 && LaunchProtocol.validPackage(args[0]))
        @Suppress("DEPRECATION")
        android.os.Looper.prepareMainLooper()
        Thread {
            try {
                print(UiHierarchyReader.read(args[0]))
                kotlin.system.exitProcess(0)
            } catch (e: Exception) {
                System.err.println(e.message)
                kotlin.system.exitProcess(1)
            }
        }.start()
        android.os.Looper.loop()
    }
}
