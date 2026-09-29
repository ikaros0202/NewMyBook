# 新阅当前实现架构

> 状态：当前实现（as-built）  
> 已核对基线：1.0.0 / Room schema 12
> 用途：让新开发者或 AI 在不读取全部规格和历史计划的情况下，定位真实模块、入口和所有权。  
> 真相优先级：源码与构建配置高于本文；版本设计说明“为什么做”，本文只说明“现在怎样实现”。

## 1. 读取方式

- 第一次接手项目：先读根目录 `AGENTS.md`、`tasks/current.md` 和 `docs/README.md`，再读本文。
- 修改跨模块流程：继续读 [`FLOWS.md`](FLOWS.md)。
- 已确定编码任务并准备写代码：从 [`../contracts/README.md`](../contracts/README.md) 只选择一个相关能力页，再读取该页指向的接口、实现和测试。
- 当前产品事实以 [`../BASELINE.md`](../BASELINE.md) 为入口；历史规格、计划和决策见本机 [`../../archive/2026-09-baseline/docs/README.md`](../../archive/2026-09-baseline/docs/README.md)，不定义当前实现。

## 2. 运行形态

新阅是纯离线 Android 应用，采用单 Activity、Navigation 3、Compose、MVVM/UDF、Hilt、Room 3、Bundled SQLite 和 WorkManager。

- 应用与导航入口：[`MainActivity.kt`](../../app/src/main/kotlin/com/xinyue/reader/MainActivity.kt)。
- 进程初始化入口：[`XinYueApplication.kt`](../../app/src/main/kotlin/com/xinyue/reader/XinYueApplication.kt)。
- `MainActivity` 持有一个根目的地和二级导航栈。根目的地默认为书架，在书架、统计、设置三标签间切换；手机使用 72dp 高的无胶囊图标底栏，宽度 ≥600dp 使用带文字的 NavigationRail。标签切换不写入返回历史，`SaveableStateHolder` 保存各标签 UI 状态。
- 导入、阅读、单书统计、备份和六个全局阅读设置页是二级全屏目的地，进入后不显示根导航，返回时恢复来源标签；全局统计本身是根页。书架搜索期间也临时隐藏根导航。
- `XinYueApplication` 配置 Hilt Worker，并在后台执行恢复日志协调、遗留设置迁移、文件清理调度和陈旧阅读会话关闭。

## 3. 实际模块

模块清单以 [`settings.gradle.kts`](../../settings.gradle.kts) 为准。

| 模块 | 当前职责 | 主要入口 |
|---|---|---|
| `app` | 进程、主题、导航组装、发布变体 | `MainActivity`、`XinYueApplication` |
| `core:domain` | 领域模型、稳定仓库契约、时钟契约 | `model/`、`repository/` |
| `core:text` | 不依赖 Android 的文本解码、规范化、等长结构化排版空白、章节、分页辅助、搜索分块和锚点算法 | `TxtDecoder`、`ReaderLayoutWhitespaceNormalizer`、`ChapterDetector`、`TextAnchorRepairer` |
| `core:database` | Room schema 12、DAO、实体、迁移和映射 | `XinYueDatabase`、`DatabaseModule` |
| `core:data` | 仓库实现、私有文件、设备 UI 偏好、导入/搜索/批注导出/备份/接力协调、WorkManager 任务和 Hilt 绑定 | `DataModule`、`Room*Repository`、`Local*Service`、`SharedPreferencesLibraryLayoutPreferences` |
| `core:ui` | 跨功能复用的纸页/墨色 Material 主题、默认/自定义封面和通用 UI | `XinYueTheme`、`BookCover` |
| `feature:home` | 保留的历史继续阅读/统计概览实现；不再由运行时根导航调用 | `HomeRoute`、`HomeViewModel` |
| `feature:library` | 默认根书架、2/3/4 列封面网格与紧凑列表、书籍/作者/系列/集合浏览、筛选、批量集合维护和根/单书统计 | `LibraryRoute`、`StatisticsRoute` |
| `feature:settings` | 固定七入口设置首页及真实能力摘要 | `SettingsScreen` |
| `feature:import` | DocumentsUI 选择后的预检、编码确认、重复处理和导入状态 | `ImportRoute`、`ImportViewModel` |
| `feature:reader` | 原文/排版双文本阅读、标题参与测量的分页、目录、搜索、书签/标注、当前书批注导出、TTS、快速/完整设置、主题和阅读会话；提供六个全局设置分区页 | `ReaderRoute`、`ReaderViewModel`、`ReaderQuickSettings`、`GlobalReaderSettingsRoute` |
| `feature:backup` | 全部批注导出、备份 v2、v1 兼容恢复、单书接力、冲突选择、进度和结果 | `BackupRoute`、`BackupViewModel` |
| `benchmark` | Macrobenchmark 与 Baseline Profile 生成 | `BaselineProfileGenerator`、启动基准 |

早期规格中的 `core:files`、`core:reader`、`feature:bookshelf` 是设计阶段的职责名称，不是当前 Gradle 模块；对应实现分别落在 `core:data`、`core:text`/`feature:reader`、`feature:library`。

## 4. 依赖方向

```text
core:domain
├── core:text
├── core:database
├── core:data ──→ core:text + core:database
└── core:ui

feature:* ──→ core:domain + core:data + core:ui
feature:import / feature:reader ──→ core:text

app ──→ core:* + feature:*
benchmark ──targets──→ app
```

功能模块的共同依赖由 [`AndroidFeatureConventionPlugin.kt`](../../build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt) 注入。功能 UI 可以调用 `core:data` 暴露的用例和调度接口，但不得直接操作 DAO、Room 数据库或应用私有文件。

## 5. 数据与文件所有权

| 数据或文件 | 所有者 | 约束 |
|---|---|---|
| `xinyue.db`、schema 与迁移 | `core:database` | 当前 schema 12；书籍—集合为多对多并保存系列字段；正式历史版本必须迁移，不允许破坏式回退 |
| Repository 实现与实体映射 | `core:data` | 功能层只依赖领域契约或数据层用例 |
| `files/books/<bookId>/original.txt` | `core:data` | 用户导入内容的私有副本；用户原始 URI 内容永不修改 |
| `files/books/<bookId>/content.txt` | `core:data` | 统一编码后的阅读正文；分页和搜索读取该文件 |
| 自定义封面 | `core:data` | 位于对应书籍私有目录，数据库仅保存受约束的相对路径 |
| `files/fonts/<fontId>/` | `core:data` | 私有字体内容不得进入日志、测试夹具或文档 |
| `backup-staging`、`handoff-staging`、`restore-journals`、`restore-snapshots` | `core:data` | 备份/接力恢复采用暂存、日志、快照、发布和补偿，不假设文件系统与数据库原子提交 |
| 搜索分块、章节索引、导入任务状态 | `core:database` | 属于可查询或可重建的应用数据，不由 UI 直接维护 |

文件系统路径必须通过 `core:data` 的安全解析和边界检查；任何跨数据库/文件系统写入都必须保留补偿或后续清理路径。

## 6. 稳定边界与实现绑定

- `core:domain/repository` 保存书籍、集合、书架布局偏好、标注、批注导出、接力、搜索、设置、主题、字体、统计和备份的领域契约。
- [`DataModule.kt`](../../core/data/src/main/kotlin/com/xinyue/reader/core/data/di/DataModule.kt) 是领域契约到当前 `Room*`/`Local*` 实现的主要 Hilt 绑定入口。
- [`DatabaseModule.kt`](../../core/database/src/main/kotlin/com/xinyue/reader/core/database/di/DatabaseModule.kt) 创建数据库并显式注册迁移。
- `core:data` 还包含仅供应用内部使用的边界，例如 `BookFileStore`、`TextSource`、`ImportTaskScheduler`、`BackupTaskScheduler` 和 `HandoffTaskScheduler`；它们不是跨项目公共 API，但修改时仍需核对调用方和测试。

## 7. 修改文档的触发条件

- 新增、删除或重命名 Gradle 模块：更新模块表和依赖方向。
- 改变 Activity、导航、Hilt 绑定或后台初始化：更新运行形态和入口。
- 改变数据库、私有目录或跨存储提交策略：更新所有权表，并在难以撤销时新增 ADR。
- 只改模块内部实现且入口、依赖和所有权不变：通常不更新本文。
- 改变接口签名或语义：更新源码 KDoc、相关测试和对应契约能力页；不要在本文复制完整签名。
