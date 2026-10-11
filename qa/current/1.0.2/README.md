# 1.0.2 定向 QA 记录

更新时间：2026-09-30
状态：本页主体保留 9 月 29 日的 UI 实现与定向验证记录。用户已接受新版视觉为细化基础，朗读与定时认可通过；9 月 30 日的朗读修复、3 项新增方法、音频、当前 APK 见 [补充 QA](followup-2026-09-30/README.md)，其余三个手工场景及图库整理见 [接续验收](remaining-2026-09-30/README.md)。本轮 handoff 的接续项已完成，本记录不代表 Release。

## 范围与交付身份

- 构建：1.0.2-debug，versionCode=10；设备为 API 37、16 KB page-size 模拟器。
- 测试资料：公开夹具 [fixtures/bookpage-long.txt](fixtures/bookpage-long.txt)、user13 公开样例；空状态使用独立空白 user14。没有私人正文、私人字体、备份、凭据或可识别私人输入进入资料。
- 构建日志：[redesign/build-delivery.log](redesign/build-delivery.log) 以 BUILD SUCCESSFUL 结束。
- APK：[redesign/XinYue-1.0.2-debug.apk](redesign/XinYue-1.0.2-debug.apk)，SHA-256：8514B8A0A7891512173CAA43DF72F962164EA35F794C958163A284B5CF26997E。
- 设备记录已保存：[redesign/installed-version.txt](redesign/installed-version.txt)、[redesign/device-api.txt](redesign/device-api.txt)、[redesign/device-page-size.txt](redesign/device-page-size.txt)、[redesign/device-cleanup.txt](redesign/device-cleanup.txt)。清理后仅剩 user0，未清理 user0 的应用数据。
- 本轮没有发布 Release 或 GitHub Release；APK 仅作为 1.0.2-debug 交付产物保存。首轮灰绿色方案保存在 [iterations/01-rejected/](iterations/01-rejected/)，不属于当前验收证据。

## 定向测试计数

本轮共 **16 项独立精确测试**：JVM 9 项、设备 UI 7 项；没有运行原仓库自带的 107 项全量旧测试。

### JVM：9 项

- 主题明暗对比 1 项：[color-contrast-test.xml](color-contrast-test.xml)。
- 自动翻页和定时控制器 3 项：[auto-page-pause-test-final.log](auto-page-pause-test-final.log)。控制器测试只验证到期回调时序，不等同于真实 TTS 已停止。
- 全书页码索引 3 项：[redesign/page-index-results.xml](redesign/page-index-results.xml)，覆盖章节边界、分批读取边界和不完整尾页。
- 数字控件尾零 1 项：ReaderNumericDisplayTest 的定向验证。
- 页码索引 generation 1 项：[redesign/page-jump-generation-test.log](redesign/page-jump-generation-test.log) 与 [redesign/page-jump-generation-results.xml](redesign/page-jump-generation-results.xml)。

### 设备 UI：7 项

书架 2 项见 [redesign/library-targeted.log](redesign/library-targeted.log)；阅读快捷设置、完整设置和页码跳转 3 项见 [redesign/reader-targeted.log](redesign/reader-targeted.log)，选择可达性 1 项见 [reader-targeted-tests.log](reader-targeted-tests.log)；页码对话框重跑 [redesign/page-jump-dialog-final.log](redesign/page-jump-dialog-final.log)；备份导出 1 项见 [backup-targeted-test.log](backup-targeted-test.log)。页码对话框重跑是同一项的补充证据，不重复计数。

## 功能和设备证据

### 全书页码、续读和返回原点

公开长篇实测为 389,842 个 UTF-16 code units、1,150,040 bytes、300 章、1,200 页。第 1 页跳到第 900 页时目标跨越正文窗口仍正确；继续到第 901 页后可返回原来的第 1 页；再次跳到第 901 页并强制停止重开，重算后仍保持 901。证据见 [after/89-page-jump-long.png](after/89-page-jump-long.png)、[after/89-page-jump-return.png](after/89-page-jump-return.png)、[after/89-page-jump-restored.png](after/89-page-jump-restored.png)。未生成索引时界面明确显示本章页码或约数，生成后显示全书页码；旧阅读进度滑杆入口已移除。

### 自动翻页和定时停止

- 10 秒自动翻页至少连续完成两次，触摸正文或打开菜单会暂停，证据见 [after/80-auto-page-first.png](after/80-auto-page-first.png)、[after/80-auto-page-second.png](after/80-auto-page-second.png)、[after/80-auto-page-paused.png](after/80-auto-page-paused.png)。
- 通过系统设置真正切出后，Settings 位于顶部；相隔 11 秒的两张阅读截图均为 41%，自动翻页保持暂停，见 [redesign/background-activity.txt](redesign/background-activity.txt)、[after/80-auto-background-resume.png](after/80-auto-background-resume.png)、[after/80-auto-background-still.png](after/80-auto-background-still.png)。
- 15 分钟定时于 2026-09-29 21:17:19 +08:00 开始，21:34 检查已到期。到期后状态为未设置、已暂停 10 秒；KEEP_SCREEN_ON 消失、mHoldScreenWindow=null，阅读进度仍为 41%，见 [after/81-timer-expired-status.png](after/81-timer-expired-status.png)、[after/81-timer-expired-reader.png](after/81-timer-expired-reader.png)、[redesign/timer-window-after.txt](redesign/timer-window-after.txt)。该截图只显示“开始朗读”按钮文案，不代表已启动中文发声。
- 到期时自动翻页和 TTS 已处于暂停，所以活动 TTS 被定时器停止、中文正文实际发声仍未做设备证明；控制器单测提供补充时序验证。

### 导入、备份、恢复和接力

- [after/41-import-batch-duplicate.png](after/41-import-batch-duplicate.png)、[after/41-import-batch-end.png](after/41-import-batch-end.png)、[after/75-import-return.png](after/75-import-return.png) 确认批量导入会跳过重复项并保留返回结果。
- [after/55-backup-result.png](after/55-backup-result.png) 和 [after/60-handoff-result.png](after/60-handoff-result.png) 是本轮实际完整写入与校验成功的结果。
- 恢复预览显示一条《雾港书简》加日期冲突标签；安全合并结果见 [after/87-restore-result.png](after/87-restore-result.png)，新增 0、跳过 6。

## 视觉复核覆盖

当前 after/ 的 PNG/XML 均为真实设备采集，已覆盖书架、导入、阅读、全局外观、主题、统计、备份与恢复页面族。空状态使用 user14，其他公开样例使用 user13；代表性最终证据包括：

- [after/01-library-empty.png](after/01-library-empty.png)、[after/03-statistics-empty.png](after/03-statistics-empty.png)、[after/13-import-empty.png](after/13-import-empty.png) 的空状态。
- [after/85-theme-time-picker.png](after/85-theme-time-picker.png) 使用中文“时/分”；[after/85-theme-time-invalid.png](after/85-theme-time-invalid.png) 输入 99 时确认按钮不可用；[after/65-theme-time-cancelled.png](after/65-theme-time-cancelled.png) 取消后保留 07:00。
- [after/90-reader-tap-grid.png](after/90-reader-tap-grid.png) 和 [after/05-reading-behavior.png](after/05-reading-behavior.png) 展示新的 3×3 触控示意；[after/91-tap-grid-large-text.png](after/91-tap-grid-large-text.png) 在 200% 字体下仍显示全部 9 区；[after/91-tap-grid-action.png](after/91-tap-grid-action.png) 确认左上区域为“下一页”。
- [after/91-settings-discard.png](after/91-settings-discard.png) 确认放弃草稿；[after/91-appearance-large-text.png](after/91-appearance-large-text.png) 确认大字体下数值行无裁剪；[after/29-reader-progress.png](after/29-reader-progress.png) 明确全书目标页码与当前位置。

截图数量不是测试数量，首轮废稿不计入当前验收。[截图对照图库](gallery.html)已生成；临时 Android 用户已移除并回到用户0。9 月 29 日末的暂停已于 9 月 30 日解除，接续结果见[交接文档](../../../docs/HANDOFF.md)。

## 真实限制与发布边界

- 9 月 29 日尚缺中文音频和活动中的定时停止设备证明；9 月 30 日补充音频和模式切换观察后，用户明确认可朗读与定时通过，不继续这两项。活动中的定时到期停止未继续设备实测的事实保留，见补充 QA；其他定向检查按用户要求已补齐。
- 本轮只交付源码和 1.0.2-debug 构建，不宣称已发布 Release 或安装包。
