# 新阅 · XinYue Reader

新阅是一款纯离线的 Android TXT 阅读器，围绕本地书库、沉浸阅读和可靠续读设计。没有账号、广告、分析上报、云同步或在线书源。

**当前源码基线：1.0.0。** 此版本以现有完整软件为起点，统一了过去多轮迭代的产品与文档命名。它包含书架优先导航和“纸页索引”界面；本轮没有发布 1.0.0 APK/AAB 或 GitHub Release。

## 已有能力

- 本地 TXT 导入：系统文件选择器、编码识别与确认、重复处理，单文件实际读取上限 50 MiB；保留用户原始文件。
- 书库管理：书名/作者搜索、状态筛选、排序、作者/系列/集合、多选管理、自定义封面，以及可记住的 2/3/4 列网格和紧凑列表。
- 沉浸阅读：结构化段落与章节标题、连续分页、目录跳转、稳定进度、正文搜索、选择复制、书签、高亮、批注与设备 TTS。
- 个性化与数据：快速/完整阅读设置、字体和主题、本地阅读统计、Markdown/JSON 批注导出、完整备份恢复和单书接力。

备份与接力文件不加密，由用户自行保管。TTS 使用设备上的引擎，该引擎的数据处理方式由其自身决定。详情见[隐私说明](docs/PRIVACY.md)。

## 构建与验证

使用 JDK 17、Android SDK 37 和项目自带 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug --no-configuration-cache
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

SDK 路径在本机 `local.properties` 或环境中配置。依赖版本以 [Gradle 版本目录](gradle/libs.versions.toml) 为准。`minSdk 26` 是构建声明；已有设备证据主要来自 API 37、16 KB 模拟器，不代表所有设备均已验证。

每次修改只验证新增/修改代码及其直接影响，不例行运行全量测试；私人长篇 TXT 按风险选择。构建、设备和交接参考见[开发说明](docs/DEVELOPMENT.md)。

## 文档入口

- [当前产品基线](docs/BASELINE.md)：功能、版本与证据边界。
- [当前任务](tasks/current.md)：进行中的工作与交接。
- [文档导航](docs/README.md)：架构、契约、设计和隐私资料。
- [当前架构](docs/architecture/README.md)与[调用链](docs/architecture/FLOWS.md)。
- [代码契约](docs/contracts/README.md)与[界面设计规范](design-system/xinyue/MASTER.md)。
- [公开源码隐私说明](docs/PUBLIC_REPOSITORY_PRIVACY.md)与[开源组件说明](docs/OPEN_SOURCE_NOTICES.md)。

旧规格、计划、决策和验收资料已集中保存在本地 `archive/2026-09-baseline/`，不作为当前要求，也不进入新的公开源码历史。旧原型已清理，当前界面以实现和设计规范为准。
