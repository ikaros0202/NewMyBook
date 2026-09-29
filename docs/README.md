# 新阅文档导航

当前统一基线为 **1.0.0**。产品现状以 [BASELINE.md](BASELINE.md) 为入口，具体实现以源码和构建配置为准；本地历史归档只用于追溯。

| 要了解的内容 | 入口 |
|---|---|
| 产品目标、现有功能、版本与验证边界 | [当前基线](BASELINE.md) |
| 当前任务与交接 | [tasks/current.md](../tasks/current.md) |
| 构建、定向验证、协作参考 | [开发说明](DEVELOPMENT.md)、[账号交接](ACCOUNT_COORDINATION.md) |
| 模块、入口、数据所有权 | [架构](architecture/README.md) |
| 导入、阅读、搜索、备份等跨模块流程 | [调用链](architecture/FLOWS.md) |
| 准备修改某项能力的接口与测试 | [契约导航](contracts/README.md) |
| 当前纸页/墨色界面规范 | [设计系统](../design-system/xinyue/MASTER.md) |
| 应用运行时隐私、依赖许可 | [隐私说明](PRIVACY.md)、[组件说明](OPEN_SOURCE_NOTICES.md) |
| 上传源码时的公开范围与检查 | [公开源码隐私说明](PUBLIC_REPOSITORY_PRIVACY.md) |
| 当前验证记录与可复用脚本 | [QA 入口](../qa/README.md) |

按当次问题选读即可。架构和契约页帮助定位代码，不要求每次全部阅读；开发说明是操作参考，不追加流程门禁。

## 历史与维护方式

本机归档入口为 `archive/2026-09-baseline/README.md`，归档保留旧目录结构和迁移清单。它包含旧版规格、计划、研究、ADR、发布说明与验收证据；旧原型及重复概念图已清理。归档中的“当前”“必须”“待完成”等词只表达当时语境。

以后直接维护当前基线和相关能力文档；任务页只保存当下进展。只有变化值得长期追溯时再留下独立设计或决策记录，不再为每次修改新建一套版本规格、计划和验收清单。
