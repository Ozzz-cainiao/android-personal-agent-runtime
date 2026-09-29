# 双线开发存档（2026-09-29）

已创建独立公开仓库 https://github.com/Ozzz-cainiao/diandao-maafw ，本地位于 `parallel/diandao-maafw`（独立 Git 仓库，本仓库通过本地 exclude 忽略）。继续框架迁移时先读新仓库的 `docs/CHECKPOINT.md`。

旧版点到代码、包名和现有发布不变。新版点到实验版使用不同包名并采用资源 Pipeline，实现每日淘金币签到与返回桌面的实验流程。6 项离线测试及已有截图 OCR 检查通过；不代表真机领取已验收。新仓库 CI 正在处理首次 APK 构建，最新结果以对应 Actions 为准。

短期双线并行，正式切换条件在新仓库 `docs/MIGRATION.md`；未达到可发布验收前不替换本仓库。

---

# 最新存档：0.0.7-preview 橙色小助手图标

用户选定六款图标中的02。已用内置image_gen精修为橙色底白色奔跑小助手，保存于app/src/main/res/drawable-nodpi/mascot_icon.png，以bitmap drawable适配Android adaptive icon。README展示同一图片，旧docs/icon.svg仅为历史方案。

本轮只改图标与版本，不更改签到逻辑。发布目标v0.0.7-preview。无线调试尚未配置；新领取后返回桌面仍待次日实测。

---

# 最新存档：点到 0.0.6-preview

仓库已改名 Ozzz-cainiao/diandao；App显示名称改为点到，Gradle项目为Diandao。包名保持io.github.ozzz.personalagent，支持原安装覆盖升级。新增原创矢量自适应图标（绿底白色勾选卡片、黄色圆点），文档预览docs/icon.svg。

每日签到按钮改为“签到并返回桌面”。确认成功或已签到后经Shizuku发送主屏HOME；前台已切换则不发送，返回失败单独报错。失败不返回桌面。快速赚继续使用原始签到函数，不自动退出。

2026-09-28 11:47实测已签到分支：返回桌面exit=0，dumpsys确认com.miui.home/.launcher.Launcher。今日奖励之前已领，新领取后返回分支仅单元测试，待新一天实机验证。构建/Lint/JVM测试通过。无线adb尚未配置，按用户要求留到下一步。不要改动其他正在运行的自动化。

发布目标v0.0.6-preview，APK为Debug预览包。以下保留历史记录供恢复。

---

## 2026-09-28 每日签到复验

已发布0.0.5版本在测试手机11:15完整执行：启动淘宝、进入淘金币、点击签到一次、读取页面确认成功。shell UserService日志与屏幕“今天”打勾相互印证。未采集领取前余额，不推断本次金币增量。随后出现连续推广弹窗，由调试端手动关闭；App自动关闭广告仍未实现。此次不涉及快速赚任务验收。

# 最新恢复点：0.0.5-preview（2026-09-28）

- 新增 SetupActivity：首次启动配置引导、Shizuku连接/版本/授权与淘宝安装检查、官方教程与应用跳转、登录由用户勾选确认。
- MainActivity 首次转入向导，完成后记住配置；任务页保留配置入口，运行期间禁用配置按钮。
- 实机确认：首次向导显示、已有授权状态检测、未勾选时完成按钮禁用、勾选后进入任务页。
- 未实机覆盖：全新设备无Shizuku/无淘宝、首次授权拒绝、配对启动、重启后断连、屏幕旋转。未声称这些已通过。
- 此轮不继续赚币任务测试，不改变之前任务功能和已知限制。下一轮先补以上新用户分支验证，再继续旧清单。
- 0.0.5 发布附件仍为Debug测试签名，正式签名/项目许可证仍待处理。
- 本轮构建、Lint和JVM测试见发布提交；发布URL为 releases/tag/v0.0.5-preview，最终完成状态以GitHub Release为准。

---

# 开发存档点：0.0.4-preview

恢复时先读本文件、README，再运行 `git status` / `git log -5 --oneline`。不要根据旧聊天猜测已完成状态。每轮结束更新此文件、提交并推送；仅记录验证事实，不提交手机截图或账号数据。

## 当前结果

- 独立 Android App；每日签到成功、已签到分支成功；前台短任务服务解决切入淘宝后调用进程暂停。
- 快速赚按钮和 `TaobaoQuickTask` 已实现到访 +10 / 好物沉浸看 +30 选择器、等待及结果检查；构建、Lint、JVM 测试通过。
- adb 实机探索：到访10、视频30、趣味课堂30均成功。它们不是新增 App 领奖分支全链路验收；当天奖励已领，待下一天验证。
- 新版 App 在任务已领状态能打开快速赚面板；最终“无支持任务”日志仍需核对留证。
- 清单任务点卡片/奖励入口、等待和滑动都没增加0/2进度，原因未确认。不要重复盲试或误报成功。
- 趣味课堂当天题目已答对，但未编写通用答题。未知题需人工选择或未来可选知识层，不能固定选A。
- 蚂蚁庄园尚未点入；上次手机停在快速赚面板。MAA-Meow 正在另一个显示器运行，勿停止它。
- 广告自动关闭未实现；首页消费券弹窗未复现，尚无可靠关闭选择器。
- MaaFwApp 仅研究，尚未创建第二实现。

## 下次顺序

1. 验证预览版安装、Shizuku授权和无支持任务分支，收集不同手机反馈。
2. 下一天验证 App 自主领取到访/视频两项，包含取消、超时和断连；未确认就不升级为稳定功能。
3. 测试蚂蚁庄园入口，记录任务条件；不涉及支付、验证码或绕过风控。
4. 复查清单任务计时，采集弹窗关闭控件。每种任务单独闭环。
5. 确定项目许可证，建立正式发布签名和维护流程，增加CI。
6. 再按已验证流程做 MaaFwApp 资源包。

## 开发环境

作者 Mac 可用 `./scripts/build-local.sh --offline --console=plain assembleDebug lintDebug testDebugUnitTest`。工具链默认 `~/Library/Android/personal-agent-env`，含 JDK17/SDK/Gradle；其他机器用 README 的 Wrapper 构建。

截图和控件树在本机被忽略的 `captures/`；不推送。App 的 `last-ui.xml` / `quick-ui.xml` 在设备私有目录，可能含个人内容。仅导出去敏最小测试夹具。

## 这一轮发布范围

公开仓库、重写新手指南、发布标为 prerelease 的 Debug APK和校验值。正式生产签名、项目许可证、跨机适配及完整240金币自动化不在本次完成声明内。查看 GitHub Release 与提交记录确认最终发布状态。

## 发布完成记录

- 仓库已确认为 PUBLIC。
- 代码存档：`6efcc73eb9d4a1105c9d2e8ebbe36ba5cd08daf0`；发布标签 `v0.0.4-preview` 固定此提交。
- [预览 Release](https://github.com/Ozzz-cainiao/diandao/releases/tag/v0.0.4-preview) 已发布，含APK与SHA256SUMS；确认为prerelease。
- APK约2.56MB；SHA-256：`2ce414ed5d53e75eac5aac6f11d965e35f5aa77392fdd2d08c0412c855eedc15`。
- 发布APK在测试手机覆盖安装成功；本轮未重新跑全部手机自动化流程。构建、Lint、JVM测试通过。
- 下一轮从“下次顺序”第1项继续；不再重复创建本版本Release。
