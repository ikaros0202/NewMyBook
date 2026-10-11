# 1.0.2 手工验收清单

更新：2026-09-30。设备范围：API 37、16 KB page-size 模拟器。主体条目保留 9 月 29 日 user13/user14 的证据；当前 APK 和朗读/定时用户验收见 [补充 QA](followup-2026-09-30/README.md)，剩余定向检查见 [接续验收](remaining-2026-09-30/README.md)。不使用私人正文或私人备份。

状态标记：[x] 已有证据；[~] 仍有明确实机限制；[ ] 本轮未做且不应写成失败。

## 构建与测试

- [x] [redesign/build-delivery.log](redesign/build-delivery.log) 以 BUILD SUCCESSFUL 结束。
- [x] [redesign/XinYue-1.0.2-debug.apk](redesign/XinYue-1.0.2-debug.apk) 为 1.0.2-debug/code10，SHA-256 为 8514B8A0A7891512173CAA43DF72F962164EA35F794C958163A284B5CF26997E。
- [x] 当前独立精确测试共 16 项：JVM 9 项、设备 UI 7 项；新增页码索引 generation 单方法见 [redesign/page-jump-generation-test.log](redesign/page-jump-generation-test.log) 和 [redesign/page-jump-generation-results.xml](redesign/page-jump-generation-results.xml)。
- 原仓库自带 107 项旧测试本轮未运行，不纳入 16 项计数，也不属于本次待办。
- [x] 首轮灰绿色方案已归档到 [iterations/01-rejected/](iterations/01-rejected/)，不与当前 after/ 混用。
- [x] 本轮没有发布 Release 或 GitHub Release；APK 仅作为 1.0.2-debug 交付产物保存。

## 阅读、页码和续读

- [x] 目录选章和页码跳转可用，旧阅读进度滑杆入口已移除。
- [x] 无索引时明确显示本章页码或约数，索引生成后显示全书页码。
- [x] 公开长篇为 389,842 UTF-16 code units、1,150,040 bytes、300 章、1,200 页。
- [x] 第 1 页跳到第 900 页、继续到第 901 页并返回原点的结果见 after/89-page-jump-long、after/89-page-jump-next、after/89-page-jump-return。
- [x] 强制停止重开后仍保持第 901 页，见 [after/89-page-jump-restored.png](after/89-page-jump-restored.png)。
- [x] [after/29-reader-progress.png](after/29-reader-progress.png) 清楚区分全书目标页码和当前位置。

## 自动翻页和定时停止

- [x] 10 秒自动翻页至少连续翻过两页；触摸正文或打开菜单会暂停，见 after/80-auto-page-first、after/80-auto-page-second、after/80-auto-page-paused。
- [x] 使用 am start --user13 -a android.settings.SETTINGS 真正切出后，Settings 位于顶部；相隔 11 秒的两张阅读图均为 41%，状态保持暂停，见 [redesign/background-activity.txt](redesign/background-activity.txt) 和 after/80-auto-background-resume、after/80-auto-background-still。
- [x] 15 分钟定时于 2026-09-29 21:17:19 +08:00 启动，21:34 检查已到期；到期状态为未设置、已暂停 10 秒。
- [x] 到期前存在 KEEP_SCREEN_ON，到期后没有该标志且 mHoldScreenWindow=null；进度保持 41%，见 [redesign/timer-window-before.txt](redesign/timer-window-before.txt)、[redesign/timer-window-after.txt](redesign/timer-window-after.txt)、[after/81-timer-expired-reader.png](after/81-timer-expired-reader.png)。
- [~] 到期时 TTS 和自动翻页本来已暂停，因此设备没有证明活动中文 TTS 被定时器停止；控制器单测提供补充验证。阅读截图只显示“开始朗读”按钮，不证明实际发声，也没有把过期 Snackbar 当作已观察证据。

## 导入、备份与恢复

- [x] after/41-import-batch-duplicate、after/41-import-batch-end、after/75-import-return 确认重复项跳过且返回结果保留。
- [x] [after/55-backup-result.png](after/55-backup-result.png) 和 [after/60-handoff-result.png](after/60-handoff-result.png) 确认实际完整写入与校验成功。
- [x] 恢复预览显示一条《雾港书简》加日期冲突标签；[after/87-restore-result.png](after/87-restore-result.png) 显示新增 0、跳过 6。
- [x] 备份 schema 和恢复策略没有因本轮视觉或页码入口调整而改变。

## 最终视觉复核

- [x] after/ 的真实 PNG/XML 已覆盖书架、导入、阅读、外观、主题、统计、备份与恢复页面族。
- [x] [after/01-library-empty.png](after/01-library-empty.png)、[after/03-statistics-empty.png](after/03-statistics-empty.png)、[after/13-import-empty.png](after/13-import-empty.png) 使用独立空白 user14；其余公开样例使用 user13。
- [x] [after/85-theme-time-picker.png](after/85-theme-time-picker.png) 的时间选择为中文“时/分”；[after/85-theme-time-invalid.png](after/85-theme-time-invalid.png) 输入 99 时确认禁用；[after/65-theme-time-cancelled.png](after/65-theme-time-cancelled.png) 取消后保留 07:00。
- [x] [after/90-reader-tap-grid.png](after/90-reader-tap-grid.png) 和 [after/05-reading-behavior.png](after/05-reading-behavior.png) 为新的 3×3 示意；[after/91-tap-grid-large-text.png](after/91-tap-grid-large-text.png) 在 200% 下显示 9 区；[after/91-tap-grid-action.png](after/91-tap-grid-action.png) 左上为“下一页”。
- [x] [after/91-settings-discard.png](after/91-settings-discard.png) 保留放弃草稿；[after/91-appearance-large-text.png](after/91-appearance-large-text.png) 的大字体数值行无裁剪；最终主题页面为 [after/21-reader-full-settings-themes.png](after/21-reader-full-settings-themes.png)。较早的 themes-2 已在图库标明历史状态，不能作为最终按钮样式。
- [x] 截图数量不计测试项；首轮废稿只作历史记录，不计验收。

## 收尾边界

- [x] 9 月 30 日朗读与定时由用户明确认可通过，不再继续这两项测试；活动中的到期联动未继续设备观察的事实保留在补充 QA，不冒充设备证据。
- [x] [gallery](gallery.html)已汇总。9 月 30 日修复六组 after 别名误报未采集、旧主题截图标识；132 张 after 和 118 组均有有效引用，原截图未修改。
- 107 项旧测试未运行；原 16 项属于 9 月 29 日批次，9 月 30 日新增的 3 项朗读方法和三个手工场景另行记录，不混算。
