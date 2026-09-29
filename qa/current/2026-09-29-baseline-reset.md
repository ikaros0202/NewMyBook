# 1.0.0 基线与文档整理记录

日期：2026-09-29。范围：文档、归档、约束、版本命名和公开源码历史；未修改阅读业务逻辑。

## 用户确认

- 历史规格、计划和验收证据归档，过时原型清理。
- AGENTS 只保留产品底线，其他执行细节自主判断；有产品或交付理解分歧及时提问。
- 统一为 1.0.0 基线，本轮不发布安装包或 Release。
- 只验证新增/修改及直接受影响代码，不例行全量回归；私人长篇按风险。
- 旧 Git 元数据在仓库外备份后退出当前工作区；删除旧 GitHub 仓库，重建同名公开仓库。

## 已完成整理

- 555 个历史文件按原层级移入本地 `archive/2026-09-baseline/`，逐文件校验 SHA-256；另保留原 AGENTS、README、文档导航和任务页四份快照。
- 删除 10 个过时 HTML 原型和概念图；当前设计系统与生产 UI 保留。
- 当前文档收敛为产品基线、文档导航、架构/契约、设计系统和开发/隐私参考；AGENTS 只保留五项产品底线。
- 公开 TXT 夹具、QA 脚本、Room schema 1–12 和迁移保持完整；历史裸证据、候选包、原型工具状态及旧交付不进入新源码历史。
- 应用版本改为 `1.0.0`，`versionCode` 从 7 增至 8；应用 ID、数据库和备份格式未变。
- 本地原 Git 元数据、原公开 checkout 的 Git 元数据，以及远端全部 refs 和旧 Release 附件已备份到仓库外；当前工作区重新初始化 Git。归档方式保留恢复可能，不销毁备份。

## 定向验证

- `:app:processDebugMainManifest :app:processReleaseMainManifest --no-configuration-cache --console=plain`：通过。处理了版本配置；这不是 APK 构建、安装或设备验证。
- Gradle 仍提示旧 Android DSL/variant API 和 Baseline Profile 插件版本适配警告；本轮未变更这些依赖，不把警告当成本次新增故障。
- 公开源码隐私审计使用仓库外 denylist，通过；514 个公开文件，508 个文本文件、0 个归档、0 张图片，未纳入历史裸证据或私人材料。
- 20 份当前 Markdown 的 291 个本地链接无断链；555 个归档逐项 SHA-256 一致；8 份公开 TXT、10 个 QA 脚本、12 份 Room schema 保留。归档原文中的历史路径通过归档映射查找，不改写历史记录。
- 合并后的 Manifest 已核对：Debug 为 `1.0.0-debug / 8`，Release 为 `1.0.0 / 8`。
- 未跑全量测试、私人 TXT 或模拟器，因为没有改动对应业务逻辑；旧版本验证结果仅作为历史证据引用。

## GitHub 状态

旧 [ikaros0202/NewMyBook](https://github.com/ikaros0202/NewMyBook) 已删除并同名重建，仓库 ID 与备份中的旧 ID 不同。新默认分支为 `main`，公开文件集为 514 个；已通过 GitHub tree API 将每个文件路径和 blob SHA 与本地提交逐项对比，差异为 0。新仓库的 tags 和 releases 数量均为 0。

旧提交、`v0.1.4` 标签、Release 元数据和 APK 附件保存在仓库外备份。旧的 `NewMyBook-public` checkout 已退出 Git 管理，当前工作区是新源码的维护入口。本轮只公开源码；没有创建 1.0.0 安装包或 Release。
