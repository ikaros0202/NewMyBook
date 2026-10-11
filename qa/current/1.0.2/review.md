# 1.0.2 最终设计审查记录

审查日期：2026-09-29；接续更正：2026-09-30。

这份记录收敛本轮设计问题、决定和最终设备截图。功能测试、构建身份和交付边界以 [QA README](README.md) 为准。当前 after/ 的 PNG/XML 是真实设备采集；首轮灰绿色稿在 [iterations/01-rejected/](iterations/01-rejected/) 中，仅作历史对照。系统文件选择器、Gboard、系统状态栏和系统导航栏不属于应用 UI。截图数量不计测试项。

## 主要问题与决定

| 问题 | 最终证据 | 设计决定 | 状态 |
| --- | --- | --- | --- |
| 灰绿工具界面 | [after/02-settings.png](after/02-settings.png)、[after/18-reading.png](after/18-reading.png)、[after/20-reader-quick-settings.png](after/20-reader-quick-settings.png)、[after/40-library-grid.png](after/40-library-grid.png) | 工具界面采用白、柔浅灰和少量暖橙强调；正文纸白、米黄、护眼、深灰、OLED 黑等用户主题保持独立。 | 已实现并复核 |
| 封面太小、进度条占位 | [after/40-library-grid.png](after/40-library-grid.png)、[after/43-library-list.png](after/43-library-list.png) | 网格使用有书脊和完整书名的书封，列表承担长书名、作者和续读信息；移除横向大进度条。 | 已实现并复核 |
| 设置长页和滑杆墙 | [after/04-global-appearance.png](after/04-global-appearance.png)、[after/05-reading-behavior.png](after/05-reading-behavior.png)、[after/08-advanced-typography-3.png](after/08-advanced-typography-3.png)、[after/21-reader-full-settings.png](after/21-reader-full-settings.png)、[after/91-appearance-large-text.png](after/91-appearance-large-text.png) | 按外观、行为、主题、字体和精细排版分组；字号、字距、边距使用紧凑数值行，滑杆只保留给亮度或确需连续调节的项目。 | 已实现并复核 |
| 颜色按钮过密 | [after/20-reader-quick-settings.png](after/20-reader-quick-settings.png)、[after/08-advanced-typography-3.png](after/08-advanced-typography-3.png) | 快捷面板保留 5 个实际背景色样；自定义颜色和对比度收进详细设置，每个色样保持 48dp 点击区和可读选中语义。 | 已实现并复核 |
| 时间标签难以理解或无法精确输入 | [after/85-theme-time-picker.png](after/85-theme-time-picker.png)、[after/85-theme-time-invalid.png](after/85-theme-time-invalid.png)、[after/65-theme-time-cancelled.png](after/65-theme-time-cancelled.png) | 时间输入使用中文“时/分”和 HH:mm；输入 99 时禁止确认，取消后保留原来的 07:00，保留固定时段调度语义。 | 已实现并复核 |
| 九宫格按钮墙 | [after/90-reader-tap-grid.png](after/90-reader-tap-grid.png)、[after/05-reading-behavior.png](after/05-reading-behavior.png)、[after/91-tap-grid-large-text.png](after/91-tap-grid-large-text.png)、[after/91-tap-grid-action.png](after/91-tap-grid-action.png) | 保留 9 个触控区域和可访问描述，改成轻量 3×3 区域示意；200% 字体下 9 区仍可见，左上动作明确为“下一页”。 | 已实现并复核 |
| 页码目标和当前位置混淆 | [after/29-reader-progress.png](after/29-reader-progress.png)、[after/89-page-jump-confirmed.png](after/89-page-jump-confirmed.png)、[after/89-page-jump-restored.png](after/89-page-jump-restored.png) | 明确区分全书目标页码、当前位置、目标章节和返回原点；正式跳转、重启续读和当前版式保持一致。 | 已实现并复核 |

## 页面族和按钮取舍

| 页面族 | 最终覆盖证据 | 保留与收敛理由 |
| --- | --- | --- |
| 书架 | [after/01-library-empty.png](after/01-library-empty.png)、[after/40-library-grid.png](after/40-library-grid.png)、[after/42-library-layout.png](after/42-library-layout.png)、[after/43-library-list.png](after/43-library-list.png)、[after/67-library-search.png](after/67-library-search.png) | 保留搜索、导入、网格/列表、排序、筛选、集合和每本书更多菜单，分别服务找书、加入书架、浏览密度和整理；移除宽进度条与重复状态卡。 |
| 导入 | [after/13-import-empty.png](after/13-import-empty.png)、[after/13-import.png](after/13-import.png)、[after/14-import-encoding.png](after/14-import-encoding.png)、[after/41-import-batch-duplicate.png](after/41-import-batch-duplicate.png)、[after/41-import-batch-end.png](after/41-import-batch-end.png)、[after/75-import-return.png](after/75-import-return.png) | 保留文件选择、编码确认、书名作者编辑和开始阅读；重复项明确跳过，返回结果保留；不增加在线书源、账号或自制文件选择器。 |
| 阅读页 | [after/18-reading.png](after/18-reading.png)、[after/19-reader-menu.png](after/19-reader-menu.png)、[after/28-reader-more.png](after/28-reader-more.png)、[after/78-reader-auto-page.png](after/78-reader-auto-page.png)、[after/79-reader-stop-timer.png](after/79-reader-stop-timer.png)、[after/90-reader-tap-grid.png](after/90-reader-tap-grid.png) | 顶部保留返回、书签、更多，底部保留目录、搜索、朗读、外观和笔记；章节管理、定位、触控锁、自动翻页和定时停止收进更多菜单，移除常驻进度滑杆。朗读按钮文案只代表入口。 |
| 外观和阅读设置 | [after/02-settings.png](after/02-settings.png)、[after/04-global-appearance.png](after/04-global-appearance.png)、[after/05-reading-behavior.png](after/05-reading-behavior.png)、[after/08-advanced-typography-3.png](after/08-advanced-typography-3.png)、[after/09-reading-info.png](after/09-reading-info.png)、[after/20-reader-quick-settings.png](after/20-reader-quick-settings.png)、[after/21-reader-full-settings.png](after/21-reader-full-settings.png)、[after/91-settings-discard.png](after/91-settings-discard.png) | 快捷设置保留字号两端控制、当前值、5 个色样、亮度和更多设置；完整设置保留作用域、排版、主题、行为、信息开关及保存/取消事务；去掉重复长说明和大滑杆墙。 |
| 主题和自动切换 | [after/06-theme-schedule-2.png](after/06-theme-schedule-2.png)、[after/62-theme-copy.png](after/62-theme-copy.png)、[after/63-theme-rename.png](after/63-theme-rename.png)、[after/64-theme-delete.png](after/64-theme-delete.png)、[after/65-theme-time-cancelled.png](after/65-theme-time-cancelled.png)、[after/85-theme-time-picker.png](after/85-theme-time-picker.png) | 主题整行可应用；日间/夜间主题用行入口；复制、重命名、更新和删除收进每行更多；关闭、跟随系统和固定时段保留。 |
| 统计 | [after/03-statistics-empty.png](after/03-statistics-empty.png)、[after/51-statistics-populated.png](after/51-statistics-populated.png)、[after/52-statistics-book.png](after/52-statistics-book.png)、[after/53-statistics-clear.png](after/53-statistics-clear.png) | 保留日/周/月切换、单书查看和清除确认；空状态只说明没有数据，不堆零值图表或重复“查看全部”。 |
| 备份与恢复 | [after/10-backup.png](after/10-backup.png)、[after/11-backup-export-options.png](after/11-backup-export-options.png)、[after/12-annotation-export.png](after/12-annotation-export.png)、[after/55-backup-result.png](after/55-backup-result.png)、[after/60-handoff-result.png](after/60-handoff-result.png)、[after/57-restore-preview-current.png](after/57-restore-preview-current.png)、[after/87-restore-result.png](after/87-restore-result.png) | 保留书架备份、恢复、批注导出、单书接力和应用接力包；先说明内容，再由系统文件选择器确认位置；恢复展示影响、冲突和可取消路径，技术选项不铺成卡片墙。 |

## 审查边界

- 所有页面族均已有 actual after 截图；[README](README.md) 保留原 16 项定向测试，当前交付及接续结果由补充 QA 记录。[gallery](gallery.html) 已生成，六组别名误报已修正，最终主题截图直接展示，themes-2 标为历史。
- 9 月 29 日实机已验证定时到期清状态、解除 KEEP_SCREEN_ON 和保持进度 41%；9 月 30 日已补真实音频，用户明确认可朗读与定时通过，不继续这两项测试。活动中的到期联动未继续设备观察的事实保留在补充 QA。
- 设备系统页、截图数量和 contact sheet 不作为应用功能或测试项；首轮废稿不计入当前结论。
