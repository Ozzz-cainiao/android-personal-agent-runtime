# Android Personal Agent Runtime

一个逐步开发的 Android 个人自动化实验项目，优先使用确定性自动化。

## 当前状态

- 已阅读 MAA-Meow 源码，参考提交：`ad0c95b2230f9d3aa10d546dc0d226e83d4988a8`。
- 已验证 Mac 可通过 USB adb 连接测试手机（Android 16 / API 36）。
- 已确认手机安装淘宝且 Shizuku 服务正在运行。
- 已实现第一步连接测试：授权、绑定 UserService、读取远端 UID/PID、断开连接。
- 真机验证结果见下方记录；尚未实现启动淘宝和输入注入。

## 第一版代码入口

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/io/github/ozzz/personalagent/MainActivity.kt` | 两个按钮、状态日志；页面销毁时释放连接 |
| `app/src/main/java/io/github/ozzz/personalagent/ShizukuRuntimeClient.kt` | Shizuku 授权、绑定、超时、断连与身份验证 |
| `app/src/main/java/io/github/ozzz/personalagent/AutomationUserService.kt` | 由 Shizuku 启动的特权对象，只返回自己的 UID/PID |
| `app/src/main/aidl/io/github/ozzz/personalagent/IAutomationService.aidl` | 跨进程接口契约 |

UI 和 UserService 运行在不同进程。普通 App 的 UID 不会因授权而改变；通过按钮确认远端 UID 为 `2000`（shell）或 `0`（root），且 PID 与 App 不同，才算特权连接通过。本阶段 Activity 销毁会断开服务，尚未实现后台任务生命周期。

## 构建与调试

固定构建版本：JDK 17、Gradle 8.11.1、Android Gradle Plugin 8.10.1、Kotlin 2.1.20、Android SDK Platform 36、Build Tools 35.0.0。仅需要命令行工具，不需要 NDK 或模拟器。

本次已将工具安装到 Mac 的 `~/Library/Android/personal-agent-env`。在这台 Mac 上可直接执行 `./scripts/build-local.sh`，默认构建 Debug APK 并运行 Lint；也可传入 Gradle 参数。该脚本不会修改全局 shell 配置。Android Studio 的 Gradle JDK 可选择此目录下的 `amazon-corretto-17.jdk/Contents/Home`，SDK 选择 `sdk` 子目录。本机 SDK 路径在被 Git 忽略的 `local.properties` 中。

Gradle 8.11.1 下载包已对照官方 SHA-256 校验：`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`，Wrapper 也固定了该校验值。

配置 `JAVA_HOME` 指向 JDK，`ANDROID_HOME` 指向 Android SDK 后：

```sh
./gradlew assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.ozzz.personalagent/.MainActivity
adb logcat -v time 'PersonalAgent:I' 'AndroidRuntime:E' '*:S'
```

也可以使用 Android Studio 打开项目根目录。使用 Shizuku UserService 时应通过完整 APK 安装更新代码，不依赖 Apply Changes 更新远端进程。

首次测试：打开 **Personal Agent** → 点击 **测试 Shizuku 连接** → 在 Shizuku 弹窗允许 → 等待页面出现 `[通过] UserService uid=2000 pid=...`。

点击 **断开连接** 后应能再次测试。拒绝授权应显示明确提示；不要为了测试本 App 而停止其他 App 正在使用的 Shizuku 服务。若要验证 Shizuku 停止场景，应先确认其他任务可以中断。

### 验证记录

- USB adb、Android 16 / API 36、淘宝安装状态、Shizuku 服务进程：已确认。
- `assembleDebug lintDebug`：通过。Lint 为 0 errors、8 warnings（版本更新提示、备份配置、占位图标和测试页面国际化提示）。
- 持久工具目录与依赖缓存：已通过 `./scripts/build-local.sh --offline --console=plain assembleDebug lintDebug` 离线复验。
- APK 安装与启动：通过。首次安装曾被小米的 USB 安装限制拒绝；用户开启 USB 安装后重试成功。
- 2026-09-26 真机首次授权与 UserService 绑定：通过。App `uid=10417 pid=24276`；远端 `uid=2000 pid=32256`，确认为独立 shell 进程。
- 断开后重连：通过。日志确认旧服务执行 `SERVICE_DESTROY`，重连创建新进程 `uid=2000 pid=315`；`ps` 确认进程名为 `io.github.ozzz.personalagent:runtime`。PID 仅是本次运行证据，不应写入程序。
- 未测试：拒绝授权、服务超时、Shizuku 停止、屏幕旋转；尚未实现淘宝启动、点击和脱离电脑的完整流程。

## MVP 0

点击“测试自动化” → 通过 Shizuku 启动淘宝 → 等待约 2.5 秒 → 指定坐标点击一次 → 记录结果。

计划采用官方 Shizuku UserService，在特权进程中执行系统 `am start` 和 `input tap`。淘宝任务参数与通用执行器分离。命令完成不代表页面验证成功，第一阶段采用人工验收。

## 分步验收

1. 最小 App：申请 Shizuku 授权、绑定 UserService、显示远端 UID/PID。
2. 单独验证启动淘宝。
3. 单独验证安全位置点击。
4. 串联完整流程，并验证拔掉 USB 后仍可运行。

第一阶段不引入 AI、OCR、截图识别、虚拟显示器、解锁、定时任务、数据库或云服务。

## 参考与许可证

- [MAA-Meow](https://github.com/Aliothmoon/MAA-Meow)：整体采用 AGPL-3.0，第三方代码另有声明。
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API)：官方接口与 UserService 文档。

当前没有复制 MAA-Meow 源码。未来若引入第三方代码，将记录来源、版本、修改和适用许可证。本项目自身许可证尚未确定。

## 开发方式

每次只完成一个最小闭环：明确目标 → 阅读相关源码 → 最少改动 → 构建与真机验证 → 提交并同步 GitHub。提交说明区分已验证结果与待验证事项。
