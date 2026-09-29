# 书库与统计契约

> 只在修改书架、书籍元数据、进度、集合、系列、封面或统计时读取。  
> 如果任务转入正文阅读、导入文件或备份恢复，返回 [`README.md`](README.md) 选择对应能力页。

## 最小读取顺序

1. 先读下表中与任务直接相关的一行。
2. 打开该行的接口和当前实现。
3. 再读代表性测试；涉及 UI 时补读调用方。
4. 不读取表中其他接口，除非修改会改变它们的输入、结果或所有权。

## 接口、实现与测试

| 能力 | 接口 | 当前实现 | 代表性测试/调用方 |
|---|---|---|---|
| 书籍、阅读进度、打开和完成状态 | [`BookRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BookRepository.kt) | [`RoomBookRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomBookRepository.kt) | [`RoomBookRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomBookRepositoryTest.kt)、[`LibraryViewModel`](../../feature/library/src/main/kotlin/com/xinyue/reader/feature/library/LibraryViewModel.kt) |
| 多集合成员关系与批量维护 | [`BookGroupRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BookGroupRepository.kt) | [`RoomBookGroupRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomBookGroupRepository.kt) | [`RoomBookGroupRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomBookGroupRepositoryTest.kt)、[`LibraryViewModelTest`](../../feature/library/src/test/kotlin/com/xinyue/reader/feature/library/LibraryViewModelTest.kt) |
| 设备本地书架布局与网格密度 | [`LibraryLayoutPreferences`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/LibraryLayoutPreferences.kt) | [`SharedPreferencesLibraryLayoutPreferences`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SharedPreferencesLibraryLayoutPreferences.kt) | [`SharedPreferencesLibraryLayoutPreferencesTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/SharedPreferencesLibraryLayoutPreferencesTest.kt)、[`LibraryViewModelTest`](../../feature/library/src/test/kotlin/com/xinyue/reader/feature/library/LibraryViewModelTest.kt) |
| 私有自定义封面 | [`BookCoverRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BookCoverRepository.kt) | [`LocalBookCoverRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookCoverRepository.kt) | [`LocalBookCoverRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBookCoverRepositoryTest.kt) |
| 阅读会话和聚合统计 | [`ReadingSessionRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/ReadingSessionRepository.kt) | [`RoomReadingSessionRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomReadingSessionRepository.kt) | [`RoomReadingSessionRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomReadingSessionRepositoryTest.kt)、[`ReadingSessionTrackerTest`](../../feature/reader/src/test/kotlin/com/xinyue/reader/feature/reader/ReadingSessionTrackerTest.kt) |
| 删除、元数据与私有文件清理协调 | [`BookManager`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BookManager.kt) | [`LocalBookManager`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookManager.kt) | [`DurablePendingBookFileCleanupTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/DurablePendingBookFileCleanupTest.kt) |

## 相关模型

- 书籍、系列与进度：[`Book.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/Book.kt)、[`ReadingProgress.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReadingProgress.kt)。
- 集合：[`BookGroup.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/BookGroup.kt)；历史类型名保留为兼容代码名，界面统一称“集合”。
- 统计：[`ReadingStatistics.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReadingStatistics.kt)。

## 必须保持的语义

- 数据库保存书籍元数据与私有文件相对路径，不保存用户外部 URI 的可变内容。
- 阅读进度使用规范化正文的 UTF-16 偏移和上下文锚点，不保存页面序号作为持久真相。
- 删除数据库记录时必须持久登记私有文件清理；进程中断不能留下无法追踪的正文、封面或索引文件。
- 一本书可属于零个、一个或多个集合；同一书籍—集合关系不得重复。删除集合只解除成员关系，不删除书籍。
- 系列名与系列序号属于书籍元数据；系列视图先按系列分组，再以系列序号、书名和稳定 ID 排序。
- 自定义封面由数据层验证并发布到书籍私有目录，功能层不能直接写文件。
- 统计与阅读进度相互独立；统计失败不得阻断打开正文、翻页或进度保存。
- 时间范围采用 `[start, end)`，正文位置采用 UTF-16 偏移。
- 根统计页和单书统计页共享 `readingStatisticsRange` 的自然日、自然周和自然月范围计算；统计错误不能阻断书架或正文。
- 书架元数据查询先去除首尾空白，再按 Unicode 空白拆词；全部词必须命中，每个词可在书名、作者或系列任一字段中做不区分大小写的子串匹配。该查询不使用正文搜索索引。
- 书架默认使用 `COVER_GRID + STANDARD`；舒适/标准/紧凑密度和紧凑列表均为设备本地偏好。未知存储值回退默认，偏好不进入 Room、完整备份或单书接力。
- “继续”卡只来自全部书中 `lastOpenedAtEpochMillis` 最新、真实进度位于 `(0,1)` 且未完成的书；它独立于当前筛选、排序和元数据编辑。

## 何时联动其他能力页

- 改变 TXT 发布、重复检测或正文路径：补读 [`import-search.md`](import-search.md)。
- 改变阅读器何时保存进度或统计交互：补读 [`reader.md`](reader.md)。
- 改变备份中的书籍、进度、集合、系列、封面或统计结构：补读 [`backup.md`](backup.md)。
