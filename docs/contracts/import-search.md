# 导入与全文搜索契约

> 只在修改 TXT 选择/编码/发布、私有正文读取、重复处理、搜索索引或搜索结果时读取。  
> 阅读器怎样消费窗口和跳转结果属于 [`reader.md`](reader.md)。

## 最小读取顺序

1. 导入任务只读“导入边界”；搜索任务只读“搜索边界”。
2. 打开相关接口、当前实现和代表性测试。
3. 只有修改正文路径或内容哈希时，才同时读取导入和搜索两组边界。

## 导入边界

| 能力 | 接口/协调器 | 当前实现 | 代表性测试/调用方 |
|---|---|---|---|
| 有界预检与编码分析 | [`ImportPreflightService`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportPreflightService.kt) | 同文件中的 `AndroidImportPreflightService` | [`ImportPreflightServiceTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/ImportPreflightServiceTest.kt)、[`ImportViewModel`](../../feature/import/src/main/kotlin/com/xinyue/reader/feature/importing/ImportViewModel.kt) |
| 持久批量导入任务 | [`ImportTaskScheduler`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportTaskModels.kt) | [`WorkManagerImportTaskScheduler`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/WorkManagerImportTaskScheduler.kt) | [`ImportTxtWorkerTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/ImportTxtWorkerTest.kt) |
| TXT 暂存、发布和补偿删除 | [`BookFileStore`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BookFileStore.kt) | [`LocalBookFileStore`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookFileStore.kt) | [`LocalBookFileStoreTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBookFileStoreTest.kt) |
| 文件、哈希、重复书籍和数据库协调 | [`BookImporter`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportBookUseCase.kt) | 同文件中的 `ImportBookUseCase` | [`ImportBookUseCaseTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/ImportBookUseCaseTest.kt) |
| 重复书籍查询和元数据发布 | [`BookRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BookRepository.kt) | [`RoomBookRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomBookRepository.kt) | [`RoomBookRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomBookRepositoryTest.kt) |

导入 Worker 入口：[`ImportTxtWorker.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/ImportTxtWorker.kt)。导入源领域模型：[`ImportSource.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ImportSource.kt)。

## 搜索边界

| 能力 | 接口/协调器 | 当前实现 | 代表性测试/调用方 |
|---|---|---|---|
| 全书搜索与索引状态 | [`BookSearchRepository`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BookSearchRepository.kt) | [`RoomBookSearchRepository`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RoomBookSearchRepository.kt) | [`RoomBookSearchRepositoryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RoomBookSearchRepositoryTest.kt) |
| 搜索 generation 后台调度 | [`SearchIndexScheduler`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SearchIndexWork.kt) | 同文件中的 `WorkManagerSearchIndexScheduler` | [`SearchIndexCoordinatorTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/SearchIndexCoordinatorTest.kt) |
| 有界正文窗口 | [`TextSource`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BookTextSource.kt) | [`LocalTextSource`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBookTextSource.kt) | [`LocalBookTextSourceTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBookTextSourceTest.kt) |
| 分窗、分块和 generation 发布 | [`SearchIndexCoordinator`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SearchIndexCoordinator.kt) | `core:data` 内部协调器 | [`SearchIndexCoordinatorTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/SearchIndexCoordinatorTest.kt) |

搜索结果模型：[`BookSearch.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/BookSearch.kt)。纯文本分块算法：[`SearchChunker.kt`](../../core/text/src/main/kotlin/com/xinyue/reader/core/text/SearchChunker.kt)。

## 必须保持的语义

- 只接受 TXT，实际读取大小必须在 `1..50 MiB`，不能只信任 DocumentsProvider 声明值。
- 用户原始 URI 只读；应用私有目录保存原始副本和统一编码后的规范化正文。
- 导入先暂存并计算规范化内容哈希，再处理跳过、替换或副本决策。
- 不含正文的 `.xinyuehandoff` 只能绑定 `contentSha256` 完全相同的既有书籍；不得按书名、作者或文件名模糊匹配。含正文接力包可在没有同哈希书籍时发布为新书。
- 文件发布与数据库提交必须有补偿；失败不能留下可见半成品或无法追踪的私有正文。
- 导入状态持久化在数据库/WorkManager，UI 不直接执行长时间复制。
- 搜索、阅读、进度和标注共享规范化正文的 UTF-16 偏移空间。
- 搜索索引按内容哈希和 generation 管理；新 generation 完成后才替换 active，失败或取消保留上一份可用索引。
- 搜索和索引处理必须分窗、有界，不能把50 MiB正文一次性加载到热路径。

## 何时联动其他能力页

- 修改阅读器窗口、分页或搜索结果跳转：补读 [`reader.md`](reader.md)。
- 修改书籍生命周期、删除清理或持久进度：补读 [`library.md`](library.md)。
- 修改备份/接力中的正文、内容哈希绑定、索引省略/重建策略或导入文件格式：补读 [`backup.md`](backup.md)。
