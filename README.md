# Android Personal Agent Runtime

一个逐步开发的 Android 个人自动化实验项目，优先使用确定性自动化。

## 当前状态

- 已阅读 MAA-Meow 源码，参考提交：`ad0c95b2230f9d3aa10d546dc0d226e83d4988a8`。
- 已验证 Mac 可通过 USB adb 连接测试手机（Android 16 / API 36）。
- 已确认手机安装淘宝且 Shizuku 服务正在运行。
- 尚未实现 Android App；UserService 绑定和输入注入仍需真机验证。

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
