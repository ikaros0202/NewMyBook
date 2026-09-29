# 阅读体验契约

> 只在修改阅读、分页、章节、书签/标注、设置、主题或字体时读取。  
> 全文搜索索引属于 [`import-search.md`](import-search.md)；书籍生命周期和统计聚合属于 [`library.md`](library.md)。

## 最小读取顺序

1. 确认任务属于正文导航、标注、外观或字体中的哪一项。
2. 只打开对应行的接口、实现、调用方和测试。
3. 涉及跨模块流程时补读 [`../architecture/FLOWS.md`](../architecture/FLOWS.md) 的阅读小节。

## 接口、实现与测试

| 能力 | 接口 | 当前实现 | 代表性测试/调用方 |
|---|---|---|---|
| 书签视图 | [`BookmarkRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BookmarkRepository.kt) | [`RoomBookmarkRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomBookmarkRepository.kt) | [`ReaderViewModelTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderViewModelTest.kt) |
| 高亮、批注和重叠查询 | [`AnnotationRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/AnnotationRepository.kt) | [`RoomAnnotationRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomAnnotationRepository.kt) | [`ReaderViewModelTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderViewModelTest.kt) |
| 当前书/全部书籍批注导出 | [`AnnotationExportService`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/AnnotationExportService.kt) | [`LocalAnnotationExportService`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalAnnotationExportService.kt)、[`AnnotationExportFormatter`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/AnnotationExportFormatter.kt) | [`LocalAnnotationExportServiceTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalAnnotationExportServiceTest.kt)、[`AnnotationExportFormatterTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/AnnotationExportFormatterTest.kt) |
| 全局设置和单书覆盖 | [`ReaderSettingsRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/ReaderSettingsRepository.kt) | [`RoomReaderSettingsRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomReaderSettingsRepository.kt)、[`GlobalReaderSettingsRoute`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/GlobalReaderSettingsScreen.kt) | [`RoomReaderSettingsRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomReaderSettingsRepositoryTest.kt)、[`GlobalReaderSettingsSectionTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/GlobalReaderSettingsSectionTest.kt) |
| 命名阅读主题 | [`ReaderThemeRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/ReaderThemeRepository.kt) | [`RoomReaderThemeRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomReaderThemeRepository.kt) | [`RoomReaderThemeRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomReaderThemeRepositoryTest.kt) |
| 自动主题与手动覆盖 | [`ReaderThemeScheduleRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/ReaderThemeScheduleRepository.kt) | [`RoomReaderThemeScheduleRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomReaderThemeScheduleRepository.kt) | [`ReaderThemeScheduleControllerTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderThemeScheduleControllerTest.kt) |
| 私有字体导入与删除 | [`ImportedFontRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/ImportedFontRepository.kt) | [`LocalImportedFontRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalImportedFontRepository.kt) | [`LocalImportedFontRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalImportedFontRepositoryTest.kt) |
| 有界正文窗口 | [`TextSource`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BookTextSource.kt) | [`LocalTextSource`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookTextSource.kt) | [`LocalBookTextSourceTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBookTextSourceTest.kt) |
| 章节索引快照与替换 | [`ChapterIndexStore`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ChapterIndexStore.kt) | 同文件中的 `RoomChapterIndexStore` | [`ReaderViewModelTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderViewModelTest.kt) |
| TXT 阅读排版空白 | [`ReaderLayoutWhitespaceNormalizer`](../../core/text/src/main/kotlin/com/xinyue/reader/core/text/ReaderLayoutWhitespaceNormalizer.kt) | 同文件纯文本实现 | [`ReaderLayoutWhitespaceNormalizerTest`](../../core/text/src/test/kotlin/com/xinyue/reader/core/text/ReaderLayoutWhitespaceNormalizerTest.kt)、[`AndroidTextPaginatorTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/AndroidTextPaginatorTest.kt) |
| 标题语义、测量与原文选择 | [`ReaderChapterTitleRange`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderChapterTypography.kt) | [`ReaderStyledTextFactory`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderStyledTextFactory.kt)、[`AndroidTextPaginator`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/AndroidTextPaginator.kt)、[`ReaderPageSurface`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderPageSurface.kt) | [`ReaderStyledTextFactoryTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderStyledTextFactoryTest.kt)、[`ReaderSelectableTextViewTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderSelectableTextViewTest.kt) |
| 阅读器快速/完整外观设置 | `ReaderActions.beginAppearanceEdit / commitAppearanceEdit / cancelAppearanceEdit` | [`ReaderQuickSettings`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderQuickSettings.kt)、[`ReaderSettingsSheet`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderSettingsSheet.kt)、[`ReaderViewModel`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderViewModel.kt) | [`ReaderQuickSettingsTest`](../../feature/reader/src/androidTest/kotlin/com/xinyue/reader/feature/reader/ReaderQuickSettingsTest.kt)、[`ReaderViewModelTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderViewModelTest.kt) |

阅读状态总协调位于 [`ReaderViewModel.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderViewModel.kt)；分页布局位于 [`AndroidTextPaginator.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/AndroidTextPaginator.kt)；进度写入合并位于 [`ProgressWriteCoordinator.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ProgressWriteCoordinator.kt)。

## 相关模型

- 标注与书签：[`ReaderAnnotation.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReaderAnnotation.kt)、[`Bookmark.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/Bookmark.kt)。
- 外观、单书覆盖与主题：[`ReaderSettings.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReaderSettings.kt)、[`ReaderSettingsOverrides.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReaderSettingsOverrides.kt)、[`ReaderThemePreset.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReaderThemePreset.kt)、[`ReaderThemeSchedule.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReaderThemeSchedule.kt)。
- 私有字体元数据：[`ImportedFont.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ImportedFont.kt)。

## 必须保持的语义

- 翻页热路径只使用已加载正文窗口和分页结果，不读正文磁盘、不重查数据库、不重建全书索引。
- 打开正文优先；设置、主题、章节、锚点修复和统计失败不能阻断可读正文。
- 书签是统一标注存储中的 `BOOKMARK` 视图，不能形成第二套持久真相源。
- 批注导出按书籍、章节、UTF-16 位置稳定排序；默认排除普通书签，只有用户显式开启“包含书签”才导出。
- JSON 使用版本化顶层 `schemaVersion`；Markdown 和 JSON 的摘录/上下文都必须通过有界正文窗口取得，不能一次加载整本正文。
- 正文、进度、章节、搜索结果和标注统一使用规范化正文的 UTF-16 偏移。
- 阅读状态同时保留原文窗口和等长排版副本：排版副本只能把布局空白替换为 `U+2060`，必须保持 UTF-16 长度、所有非空白字符和绝对偏移不变。分页与显示读取排版副本；选择/复制、搜索、朗读、标注、书签和持久进度必须按同一范围读取原文。
- 章节标题范围从真实章节起点延伸到其物理行末；合成的“正文”回退章节不得获得标题样式。标题与正文必须由同一份 styled text 同时用于测量和渲染：标题为正文 `1.45 ×`、粗体、零首行缩进，并以标题后距替代普通段距。
- 章节起点是硬分页边界。页区间必须连续、不重叠且覆盖已加载窗口一次；标题页隐藏重复的小号运行标题，续页才显示运行标题。
- 新安装的首行缩进默认值为 `0em`，已持久化的显式设置不迁移或重写；行高下限为 `1.2`，默认仍为 `1.6`。
- 快速设置按“作用域、字号、行距、段距、缩进、翻页、字体、主题/亮度、更多设置”排序；完整设置保留低频能力。两级页面共享同一个外观草稿、原始稳定锚点和保存/取消事务，切换页面不得提前写库。
- `CURRENT_BOOK` 只写稀疏单书覆盖，不能写全局主题手动覆盖；`GLOBAL` 写全局设置并按既有单书覆盖重新解析。异步保存只允许关闭启动它的同一草稿；失败保留当前草稿和待重试主题选择，旧保存结果不得覆盖更新的编辑会话。
- resolved 单书外观由全局设置加稀疏覆盖派生，不能反向保存为第二份完整设置。
- 主题计划与手动覆盖共享协调写入，不能覆盖同一设置行中的无关字段。
- 字体文件只由数据层验证、引用计数和删除；缺失字体必须安全回退，不能阻断阅读。
- 临时跳转不覆盖稳定进度，离开阅读器前刷新待写入进度。
- 全局设置子页只预览本页草稿；保存通过 `ReaderSettingsRepository.updateGlobalFromLatest` 在同一次协调写入内读取最新全局设置并只合并当前分区字段，调用方不得在仓库外重复取得同一写锁。取消或放弃不得写库。
- 主题增删改、主题计划、字体导入/删除和备份恢复属于独立即时管理动作，不进入普通设置草稿；字体被引用时删除必须失败且保留文件。

## 何时联动其他能力页

- 修改进度数据库、书籍完成状态或统计聚合：补读 [`library.md`](library.md)。
- 修改正文导入格式、搜索 generation 或索引 Worker：补读 [`import-search.md`](import-search.md)。
- 修改设置、主题、字体或标注的备份格式：补读 [`backup.md`](backup.md)。
