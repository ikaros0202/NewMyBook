# 备份、批注导出与阅读接力契约

> 只在修改 `.xinyuebackup`、`.xinyuehandoff`、批注导出、检查、冲突预览、恢复、回滚或中断协调时读取。  
> 这是数据携带与恢复的高风险边界；本页描述当前契约，实施时按改动范围阅读 [`../architecture/FLOWS.md`](../architecture/FLOWS.md) 的备份小节、源码和代表性测试。

## 最小读取顺序

1. 先读领域接口和模型，确认结果、令牌、恢复模式和冲突选择。
2. 按任务只选择导出、检查或发布/恢复实现。
3. 读取对应安全边界与代表性测试；不要顺带扫描其他功能接口。

## 领域边界

| 能力 | 接口/模型 | 当前实现 | 代表性测试/调用方 |
|---|---|---|---|
| 导出、检查、恢复和暂存清理 | [`BackupService`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/BackupService.kt) | [`LocalBackupService`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalBackupService.kt) | [`LocalBackupServiceExportTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBackupServiceExportTest.kt)、[`LocalBackupServiceInspectTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBackupServiceInspectTest.kt)、[`LocalBackupServiceRestoreTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalBackupServiceRestoreTest.kt) |
| Manifest、进度、预览、冲突和结果 | [`BackupModels.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/BackupModels.kt) | 领域序列化模型 | [`BackupManifestCodecTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/BackupManifestCodecTest.kt) |
| 持久导出/恢复任务 | [`BackupTaskScheduler`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupTaskScheduler.kt) | 同文件中的 `WorkManagerBackupTaskScheduler` | [`BackupTaskSchedulerTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/BackupTaskSchedulerTest.kt)、[`BackupViewModel`](../../feature/backup/src/main/kotlin/com/xinyue/reader/feature/backup/BackupViewModel.kt) |
| Markdown/JSON 批注导出 | [`AnnotationExportService`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/AnnotationExportService.kt)、[`AnnotationExportModels.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/AnnotationExportModels.kt) | [`LocalAnnotationExportService`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalAnnotationExportService.kt) | [`LocalAnnotationExportServiceTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalAnnotationExportServiceTest.kt)、[`AnnotationExportFormatterTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/AnnotationExportFormatterTest.kt) |
| 单书接力导出、检查与导入 | [`ReadingHandoffService`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/repository/ReadingHandoffService.kt)、[`ReadingHandoffModels.kt`](../../core/domain/src/main/kotlin/com/xinyue/reader/core/domain/model/ReadingHandoffModels.kt) | [`LocalReadingHandoffService`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/LocalReadingHandoffService.kt)、[`HandoffTaskScheduler`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/HandoffTaskScheduler.kt) | [`LocalReadingHandoffServiceTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/LocalReadingHandoffServiceTest.kt)、[`HandoffPlannerTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/HandoffPlannerTest.kt) |

## 按阶段选择实现

| 阶段 | 只读这些入口 | 代表性测试 |
|---|---|---|
| 目录快照与归档导出 | [`BackupCatalogDataSource.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupCatalogDataSource.kt)、[`SecureBackupWriter.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SecureBackupWriter.kt) | [`BackupRestoreEndToEndTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/BackupRestoreEndToEndTest.kt)、[`SecureBackupWriterTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/SecureBackupWriterTest.kt) |
| 归档校验与安全解包 | [`SecureBackupArchive.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/SecureBackupArchive.kt)、[`BackupPathPolicy.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupPathPolicy.kt)、[`BackupSafetyLimits.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupSafetyLimits.kt) | [`SecureBackupArchiveTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/SecureBackupArchiveTest.kt)、[`BackupSafetyLimitsTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/BackupSafetyLimitsTest.kt) |
| 冲突计划与预览 | [`RestorePlanner.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestorePlanner.kt)、[`DefaultRestorePlanResolver.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/DefaultRestorePlanResolver.kt) | [`RestorePlannerTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RestorePlannerTest.kt)、[`DefaultRestorePlanResolverTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/DefaultRestorePlanResolverTest.kt) |
| 快照、发布、验证与回滚 | [`RestorePublisher.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestorePublisher.kt)、[`RestoreSnapshotStore.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestoreSnapshotStore.kt)、[`BackupEquivalenceVerifier.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/BackupEquivalenceVerifier.kt) | [`RestorePublisherTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RestorePublisherTest.kt)、[`BackupEquivalenceVerifierTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/BackupEquivalenceVerifierTest.kt) |
| 中断后的启动协调 | [`RestoreRecovery.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestoreRecovery.kt)、[`RestoreJournalStore.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/RestoreJournalStore.kt) | [`RestoreRecoveryTest`](../../core/data/src/test/kotlin/com/xinyue/reader/core/data/RestoreRecoveryTest.kt)、[`BackupRecoveryForceStopInstrumentedTest`](../../app/src/androidTest/kotlin/com/xinyue/reader/BackupRecoveryForceStopInstrumentedTest.kt) |

## 必须保持的语义

- `inspect` 只验证、暂存并生成预览，不修改当前书架；`restore` 只能使用预览返回的 staged token。
- 放弃、失效或消费预览后必须清理暂存私人内容；显式清理对缺失 token 幂等。
- 未包含正文的备份不能覆盖整个书架。
- `.xinyuebackup` 当前写出 format v2，并继续读取 v1；v1 的单值 `groupId` 恢复为一条集合成员关系，未知未来主版本必须拒绝。
- `.xinyuehandoff` 必须使用独立归档类型与扩展名，只包含一书身份、稳定进度、完成状态、标注、单书设置、系列和集合关系，不得混入全局设置、统计、字体或其他书籍。
- 不含正文的接力包只有在本地存在完全一致 `contentSha256` 时才能生成可消费预览；找不到时在发布前拒绝且不修改数据。含正文时可导入为新书。
- 接力不携带字体文件；导出时自定义字体覆盖必须安全移除，保留字号、行距等其他单书设置，并由目标设备回退到可用字体。
- 归档条目数、单项大小、总展开大小、压缩比、路径、方法、类型和 SHA-256 都受固定上限与校验约束。
- 数据库和文件系统没有共同事务；恢复必须通过暂存、快照、持久日志、分阶段发布、验证和补偿协调。
- 应用启动先协调未完成恢复，再启动普通文件清理。
- 进度回调与错误信息不得包含私人正文、字体内容或外部 URI。
- schema、逻辑目录或 `formatVersion` 变化是兼容性决策，必须同步迁移/兼容性测试，并更新当前基线和架构说明。

## 何时联动其他能力页

- 改变书籍、分组、封面、进度或统计的备份目录：补读 [`library.md`](library.md)。
- 改变设置、主题、字体、书签或标注的备份结构：补读 [`reader.md`](reader.md)。
- 改变正文文件、内容哈希或搜索索引重建规则：补读 [`import-search.md`](import-search.md)。
