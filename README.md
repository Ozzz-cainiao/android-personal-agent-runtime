# Android Personal Agent Runtime

一个用 Shizuku 在手机上执行确定性自动化的小型 Android 实验项目。当前用淘宝每日签到验证“启动其他 App → 读取页面 → 点击 → 检查结果”的完整链路。

**早期预览版，欢迎少量用户试用和反馈。只在小米 15 Ultra / Android 16 上实测，不保证其他设备或淘宝版本兼容。** 与淘宝、阿里巴巴无隶属关系。

- [下载预览 APK](https://github.com/Ozzz-cainiao/android-personal-agent-runtime/releases)
- [新手安装与配置](docs/GETTING_STARTED.md)
- [反馈问题](https://github.com/Ozzz-cainiao/android-personal-agent-runtime/issues)
- [开发存档与下一步](docs/CHECKPOINT.md)

## 0.0.5：安装后按软件内引导配置

首次打开显示配置向导：启动 Shizuku → 授权 → 打开淘宝确认登录 → 进入任务。自动检测服务连接、授权和淘宝安装状态，登录由用户确认。任务页可随时重新打开引导。

## 现在可以做什么

| 功能 | 状态 |
| --- | --- |
| Shizuku 授权、独立 shell UserService、启动淘宝 | 已实机验证 |
| 自动进入淘金币、每日签到、判断今日已签到 | 已实机验证 |
| 快速赚：到访 +10、好物沉浸看 +30 | 实验代码已加入；用 adb 探索确认奖励到账，新代码领奖分支待任务刷新后验收 |
| 趣味课堂 | 手动编排 adb 测试成功，尚未实现通用答题代码 |
| 清单浏览、蚂蚁庄园、额外奖励 | 未实现；清单浏览测试未计入任务进度 |
| 自动关闭广告 | 未实现；请关闭弹窗后重试 |

当前需要手机解锁亮屏，用户点击按钮开始。没有定时、自动解锁、无人值守、AI、云服务或 VirtualDisplay。MaaFwApp 版本尚未实现。

## 使用与反馈

先按新手指南启动 Shizuku，再安装 APK、授权并测试连接，最后点击“领取今日淘金币”。运行时不要切换页面或操作其他 App。返回本 App 看日志，可点“取消任务 / 断开连接”。未知页面停止执行，避免盲点。

请反馈：手机型号、Android/系统版本、淘宝版本、本 App 版本、操作步骤、实际日志。截图请遮盖昵称、订单、余额和其他个人信息，不要上传完整控件树。

## 架构与构建

`MainActivity → ShizukuRuntimeClient → AIDL → AutomationUserService`。普通 App 不会变成 shell；通过 Shizuku 绑定独立特权进程执行系统命令及读取控件树。`TaobaoCoinTask` / `TaobaoQuickTask` 保存业务规则，`AutomationRuntime` 提供通用操作。

任务切入淘宝后由短任务前台服务维持运行，结束后停止。页面选择器依赖当前淘宝布局，UI 改版可能失效。当前 App 不申请互联网权限、无遥测；调试控件树保存在 App 私有目录，卸载会删除。目标淘宝自身仍使用网络。

开发环境：JDK 17、Android SDK Platform 36、Build Tools 35.0.0。Gradle Wrapper 固定 8.11.1；AGP 8.10.1、Kotlin 2.1.20。设置 JAVA_HOME、ANDROID_HOME（或 local.properties）后：

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -v time 'PersonalAgent:I' '*:S'
```

Android Studio 可直接打开项目。`scripts/build-local.sh` 仅是作者 Mac 的工具链入口，其他机器使用 Wrapper。更新 Shizuku 服务代码请安装完整 APK。

## 发布、参考与授权

预览附件为 **Debug 测试 APK**，可安装但不是正式生产签名版本。未来切换签名可能需要卸载旧版；当前无需保留业务数据。不要用于敏感业务自动化。

参考 [MAA-Meow](https://github.com/Aliothmoon/MAA-Meow)（AGPL-3.0）和 [MaaFwApp](https://github.com/Aliothmoon/MaaFwApp)（AGPL-3.0）；当前未复制这两个项目源码。使用 [Shizuku API](https://github.com/RikkaApps/Shizuku-API) 13.1.5（Apache-2.0），遵守依赖自身许可证。后续引入第三方代码时逐项记录来源及许可证。

**本项目自身许可证尚未确定。公开源码不等于已授予任意复制、修改和分发许可。** 许可证选择列入后续发布事项。
