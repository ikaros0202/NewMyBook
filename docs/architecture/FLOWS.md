# 新阅关键调用链

> 状态：当前实现（基线 1.0.0）
> 用途：按任务定位跨模块调用，不替代源码、测试或接口 KDoc。  
> 建议：只读取与当前任务相关的小节，再从链接进入真实代码。

## 1. TXT 导入

```text
ImportScreen / DocumentsUI
  → ImportViewModel.prepareImport
  → ImportPreflightService.analyze
  → 编码确认与无效文件反馈
  → ImportTaskScheduler.enqueueRequests
  → WorkManager + ImportTaskDao
  → ImportTxtWorker
  → ImportBookUseCase.importReliably
  → BookFileStore.stage
  → 内容哈希与重复书籍决策
  → BookFileStore.commit
  → BookRepository.addBook / replaceBook
```

关键代码：

- UI 状态与用户决策：[`ImportViewModel.kt`](../../feature/import/src/main/kotlin/com/xinyue/reader/feature/importing/ImportViewModel.kt)。
- 有界预检和编码分析：[`ImportPreflightService.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportPreflightService.kt)。
- 持久任务模型与调度契约：[`ImportTaskModels.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportTaskModels.kt)。
- Worker 执行与重试：[`ImportTxtWorker.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportTxtWorker.kt)。
- 文件、数据库和重复处理协调：[`ImportBookUseCase.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportBookUseCase.kt)。
- 私有文件暂存与发布：[`LocalBookFileStore.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookFileStore.kt)。

必须保持的约束：

- 只接受 TXT，单文件实际读取上限 50 MiB；不能只信任提供方声明的大小。
- 用户原始 URI 内容只读；应用保存 `original.txt` 和统一编码后的 `content.txt` 私有副本。
- 先暂存并计算内容哈希，再决定跳过、替换或保留副本。
- 数据库写入失败时删除刚发布的私有文件；替换成功后才清理旧文件并尝试修复锚点。
- 导入任务状态在数据库和 WorkManager 中可恢复，UI 不直接执行长时间文件复制。

代表性测试：[`ImportBookUseCaseTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/ImportBookUseCaseTest.kt)、[`ImportTxtWorkerTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/ImportTxtWorkerTest.kt)、[`ImportViewModelTest.kt`](../../feature/import/src/test/kotlin/com/xinyue/reader/feature/importing/ImportViewModelTest.kt)。

## 1A. 一级导航、书架首页与统计

```text
MainActivity.RootDestination
  → MainRootShell(MainTab.LIBRARY / STATISTICS / SETTINGS)
  → 手机 72dp 无胶囊图标底栏 / 宽屏带文字 NavigationRail
  → SaveableStateProvider(tab)

LibraryViewModel
  → LibraryLayoutPreferences.observe（网格/列表 + 2/3/4 列密度）
  → BookRepository.observeBooks + observeProgress
  → 真实 lastOpenedAt 选择 continueBookId
  → query / filter / sort / view
  → 书籍 / 作者 / 系列 / 集合视图
  → Unicode 空白分词
  → 每个词命中 title、author 或 seriesName
  → 集合成员筛选或作者/系列聚合
  → 当前视图稳定排序

StatisticsViewModel
  → ReadingSessionRepository.observeStatistics(day/week/month)
  → 根统计（无返回）/ 单书统计（有返回）

集合维护
  → BookGroupRepository
  → 书籍—集合多对多成员关系
  → 单书或批量 setMembership
```

- 根标签切换不增加导航栈；根级系统返回交给 Activity 退出。书架是默认根页，搜索时根导航临时隐藏。
- `feature:home` 不再是运行入口；继续/最近阅读由书架的真实最近打开排序和“继续”卡片标记承接，聚合统计由统计根页承接。
- 书架布局偏好是设备 UI 状态，不写 Room、不进入备份或接力；写入失败不能阻断书架或正文。
- 书架搜索只匹配书名、作者和系列，不调用正文 FTS，也不支持拼音或模糊匹配。
- 一本书可加入多个集合；删除集合只解除成员关系。系列序号是书籍元数据，不由集合顺序替代。
- 二级目的地只压入 `NavBackStack`，返回后由根壳恢复来源标签及其可保存 UI 状态。

代表性测试：[`LibraryViewModelTest.kt`](../../feature/library/src/test/kotlin/com/xinyue/reader/feature/library/LibraryViewModelTest.kt)、[`LibraryPresentationTest.kt`](../../feature/library/src/test/kotlin/com/xinyue/reader/feature/library/LibraryPresentationTest.kt)、[`StatisticsFormattingTest.kt`](../../feature/library/src/test/kotlin/com/xinyue/reader/feature/library/StatisticsFormattingTest.kt)、[`V13MainNavigationInstrumentedTest.kt`](../../app/src/androidTest/kotlin/com/xinyue/reader/V13MainNavigationInstrumentedTest.kt)。

## 2. 打开、分页与保存阅读进度

```text
MainActivity.ReaderDestination
  → ReaderRoute
  → ReaderViewModel.open(bookId)
  → BookRepository + ReaderSettingsRepository
  → AnchorRepairCoordinator（失败不阻断正文）
  → TextSource.readWindow(content.txt)
  → ReaderLayoutWhitespaceNormalizer（章节硬边界感知的等长排版副本）
  → readerChapterTitleRanges（真实标题物理行范围）
  → ReaderStyledTextFactory（标题/正文统一样式）
  → BookPaginator / AndroidTextPaginator（同一 styled text 测量）
  → ReaderUiState（raw content + layout content + title ranges + 连续页面）
  → ReaderPageSurface（显示 layout，选择/复制映射到 raw）

阅读外观
  → ReaderQuickSettings（高频项）
  → 更多设置 / ReaderSettingsSheet（完整项）
  → 同一个 appearance edit 草稿
  → GLOBAL 或 CURRENT_BOOK 原子保存 / 取消回滚

稳定翻页位置
  → ProgressWriteCoordinator（短延迟合并）
  → TextFingerprint.capture
  → BookRepository.saveProgress
```

关键代码：

- 阅读会话总协调：[`ReaderViewModel.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderViewModel.kt)。
- 有界正文窗口：[`BookTextSource.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BookTextSource.kt) 与 [`LocalBookTextSource.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookTextSource.kt)。
- UI 尺寸驱动分页：[`AndroidTextPaginator.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/AndroidTextPaginator.kt)。
- 标题与正文共享样式：[`ReaderStyledTextFactory.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderStyledTextFactory.kt)。
- 原文/排版双文本渲染与选择：[`ReaderPageSurface.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderPageSurface.kt)。
- 高频外观入口：[`ReaderQuickSettings.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReaderQuickSettings.kt)。
- TXT 空白排版副本：[`ReaderLayoutWhitespaceNormalizer.kt`](../../core/text/src/main/kotlin/com/xinyue/reader/core/text/ReaderLayoutWhitespaceNormalizer.kt)。
- 进度写入合并：[`ProgressWriteCoordinator.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ProgressWriteCoordinator.kt)。
- 上下文指纹：[`TextFingerprint.kt`](../../core/text/src/main/kotlin/com/xinyue/reader/core/text/TextFingerprint.kt)。
- 阅读统计隔离：[`ReadingSessionTracker.kt`](../../feature/reader/src/main/kotlin/com/xinyue/reader/feature/reader/ReadingSessionTracker.kt)。

必须保持的约束：

- 翻页热路径使用已加载窗口和分页结果，不重新读取全书、分析章节或查询数据库。
- 正文打开优先；设置、锚点修复、章节、统计等附属能力失败不得阻断可读正文。
- 持久进度使用 UTF-16 偏移和上下文指纹，不只保存页面序号。
- 排版副本与正文窗口 UTF-16 长度相同，只替换布局空白；分页/显示使用副本，选择/复制、搜索、朗读、标注和持久进度仍使用原文。
- 真实章节标题范围参与分页和渲染的同一测量；章节边界前不可见空白并入相邻可见页，页面必须连续且不能跨越章节起点。
- 标题页不重复显示运行标题；续页是否显示运行标题只由真实标题范围和页面起点判断。
- 快速设置与完整设置共享同一草稿和稳定锚点；只有显式保存写入选定作用域，取消或保存失败不得污染其他书或更新的编辑会话。
- 临时跳转不会立即覆盖稳定阅读位置；用户选择继续阅读后才提交新位置。
- 离开阅读器前显式刷新待保存进度并关闭统计会话；保存失败时保留页面并提示重试。

代表性测试：[`ReaderViewModelTest.kt`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderViewModelTest.kt)、[`ProgressWriteCoordinatorTest.kt`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ProgressWriteCoordinatorTest.kt)、[`ReaderProcessRecoveryTest.kt`](../../app/src/androidTest/kotlin/com/xinyue/reader/ReaderProcessRecoveryTest.kt)。

## 3. 章节、搜索、书签、标注与导出

```text
ReaderViewModel
  ├── ChapterIndexStore ↔ ChapterIndexDao
  ├── BookmarkRepository ─┐
  └── AnnotationRepository ┴→ AnnotationDao

搜索请求
  → BookSearchRepository.ensureIndexed
  → SearchIndexScheduler / WorkManager
  → SearchIndexCoordinator
  → TextSource 分窗读取 + SearchChunker
  → SearchIndexDao / FTS5
  → BookSearchRepository.search
  → ReaderViewModel 跳转到 UTF-16 偏移

当前书笔记 / 本地数据
  → AnnotationExportService
  → AnnotationRepository + ChapterIndexStore + TextSource 有界窗口
  → AnnotationExportFormatter(Markdown / JSON)
  → DocumentsUI 用户选择的目标
```

关键代码：

- 章节快照与人工编辑保护：[`ChapterIndexStore.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ChapterIndexStore.kt)。
- 搜索仓库：[`RoomBookSearchRepository.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomBookSearchRepository.kt)。
- 索引生成：[`SearchIndexCoordinator.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SearchIndexCoordinator.kt) 与 [`SearchIndexWork.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SearchIndexWork.kt)。
- 统一标注持久化：[`AnnotationDao.kt`](../../core/database/src/main/kotlin/com/xinyue/reader/core/database/dao/AnnotationDao.kt)。

必须保持的约束：

- 书签是统一标注数据中的 `BOOKMARK` 视图；不要建立第二套独立真相源。
- 搜索索引按内容哈希和 generation 管理；新 generation 完成后才切换为 active。
- 索引失败或取消不能破坏上一份可用 generation。
- 搜索和章节位置都使用正文 UTF-16 偏移，以便与进度、书签和标注互操作。
- 自动章节重建不能静默覆盖用户手工编辑的章节。
- 批注导出按书籍、章节和 UTF-16 位置稳定排序；普通书签默认排除，显式开启后才加入。
- 导出正文片段必须有界读取；导出结果或错误日志不得包含外部 URI。

代表性测试：[`RoomBookSearchRepositoryTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomBookSearchRepositoryTest.kt)、[`SearchIndexCoordinatorTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/SearchIndexCoordinatorTest.kt)、[`ReaderNavigationControllerTest.kt`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReaderNavigationControllerTest.kt)。

## 4. 备份与单书接力

```text
BackupViewModel
  ├── BackupTaskScheduler → BackupExportWorker → BackupService.export
  ├── BackupService.inspect → 暂存目录 + RestorePreview + stagedPlanToken
  └── BackupTaskScheduler → BackupRestoreWorker
          → BackupService.restore(RestoreRequest)
          → RestorePublisher
          → 快照 + 日志 + 数据库/文件发布 + 等价性验证

单书接力
  ├── HandoffTaskScheduler → HandoffExportWorker
  │     → ReadingHandoffService.export
  │     → 独立 HANDOFF manifest + DocumentsUI
  ├── ReadingHandoffService.inspect
  │     → 严格归档校验 + contentSha256 身份绑定 + 只读预览
  └── HandoffImportWorker
        → ReadingHandoffService.import
        → RestorePublisher（复用快照、日志、发布、回滚）

应用下次启动
  → XinYueApplication
  → RestoreRecovery.reconcile
```

关键代码：

- UI 状态、冲突决策和任务恢复：[`BackupViewModel.kt`](../../feature/backup/src/main/kotlin/com/xinyue/reader/feature/backup/BackupViewModel.kt)。
- 领域边界：[`BackupService.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BackupService.kt) 与 [`BackupModels.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/BackupModels.kt)。
- 本地实现：[`LocalBackupService.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBackupService.kt)。
- 持久任务：[`BackupTaskScheduler.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupTaskScheduler.kt)、[`BackupExportWorker.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupExportWorker.kt)、[`BackupRestoreWorker.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupRestoreWorker.kt)。
- 接力服务与任务：[`LocalReadingHandoffService.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalReadingHandoffService.kt)、[`HandoffTaskScheduler.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/HandoffTaskScheduler.kt)、[`HandoffPlanner.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/HandoffPlanner.kt)。
- 发布与恢复：[`RestorePublisher.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestorePublisher.kt)、[`RestoreRecovery.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestoreRecovery.kt)。
- 安全边界：[`BackupSafetyLimits.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupSafetyLimits.kt)、[`BackupPathPolicy.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupPathPolicy.kt)。

必须保持的约束：

- `inspect` 只生成预览和暂存计划，不修改现有书架；恢复必须使用该预览返回的令牌。
- 预览放弃、失效或完成后清理暂存计划，不能无限保留私人正文副本。
- 恢复不假设数据库和文件系统共享事务；必须先快照并写持久日志，再按阶段发布和补偿。
- 未包含正文的备份不能执行覆盖整个书架的恢复。
- 对归档条目数、单项大小、总展开大小、压缩比、路径、校验和和文件类型执行固定上限。
- 应用启动先协调未完成恢复，再执行普通文件清理，避免清理恢复仍需的数据。
- `.xinyuebackup` 写出 v2 并兼容读取 v1；旧单分组关系升级为集合成员关系。
- `.xinyuehandoff` 与完整备份使用不同归档类型。默认不含正文；没有完全一致正文哈希时预检拒绝。显式包含正文时才允许导入为新书。
- 接力不携带全局设置、统计、字体或其他书籍；自定义字体覆盖在导出时移除，其他单书外观继续保留。

代表性测试：[`BackupRestoreEndToEndTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/BackupRestoreEndToEndTest.kt)、[`LocalBackupServiceRestoreTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBackupServiceRestoreTest.kt)、[`LocalReadingHandoffServiceTest.kt`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalReadingHandoffServiceTest.kt)、[`BackupRestoreInstrumentedTest.kt`](../../app/src/androidTest/kotlin/com/xinyue/reader/BackupRestoreInstrumentedTest.kt)。

## 5. 维护原则

- 新增跨模块流程时，在本文增加入口、主要边界、失败隔离和代表性测试，不复制具体函数实现。
- 调用链只发生内部重排且外部边界不变时，更新相关小节即可，不写 ADR。
- 所有权、持久格式或难以撤销的失败语义发生变化时，同时更新架构总览、契约索引，并评估是否需要 ADR。
