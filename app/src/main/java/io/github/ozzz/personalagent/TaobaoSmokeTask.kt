package io.github.ozzz.personalagent

/** Business-specific target stays outside the generic privileged runtime. */
object TaobaoSmokeTask {
    fun launch(client: ShizukuRuntimeClient) {
        client.launchApp("com.taobao.taobao")
    }
}
