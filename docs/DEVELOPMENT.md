# 新阅开发参考

本页是当前工作区的简短操作参考。当前产品基线见 [`BASELINE.md`](BASELINE.md)，当前任务和接力状态见 [`tasks/current.md`](../tasks/current.md)；本页不替代用户对任务范围和发布动作的决定。

## 工具链

- 使用 JDK 17，并通过 `JAVA_HOME` 指向本机 JDK。
- 使用 Android SDK 37，并通过 `ANDROID_SDK_ROOT` 指向本机 SDK。
- 账号配置中的工作区路径通过 `XINYUE_WORKSPACE` 提供。
- 使用仓库自带的 `gradlew.bat`，不要求项目内保存 Gradle、JDK 或 SDK 的绝对路径。
- 设备交互回归默认使用项目现有的 `XinYue_API37` 16 KB 模拟器；设备结论只覆盖实际运行的环境。

常用的环境检查和构建入口：

```powershell
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to JDK 17' }
if (-not $env:ANDROID_SDK_ROOT) { throw 'ANDROID_SDK_ROOT must point to Android SDK 37' }
.\gradlew.bat --version
```

## 验证范围

一次改动只验证新增代码、修改代码和直接受影响的调用链。纯 Kotlin 或文本算法优先运行对应模块的 JVM 测试；涉及 Android、文件、数据库或用户可见交互时，再增加对应的集成测试、迁移测试或 API 37 模拟器回归。没有相关改动时不例行执行全量测试。

私人长篇 TXT 按风险决定是否补充回归。需要使用时，从 `XINYUE_PRIVATE_QA_ROOT` 指向的仓库外目录读取；正文、文件名、路径、指纹和原始设备证据不写入仓库。仓库内自动化优先使用合成或公开夹具。

`qa/scripts/run-v1.6-api37-structured-txt.ps1` 是保留的历史结构化 TXT runner，会组合公开夹具和登记的私人用例，固定执行八项检查，不是普通改动的默认命令。`-PublicFixturePath`、`-PrivateRegistryPath` 用于指定输入，`-DryRun` 用于检查计划；它们不等于单项测试筛选。日常改动直接选择相关测试类或方法，只有确需覆盖这组场景时才使用该 runner。

## 构建和发布

构建目标跟随本次任务：只改 JVM 代码时不必构建 APK，只改 Android 资源时选择受影响的变体；需要发布或安装验证时，再执行目标 APK/AAB、签名/权限/隐私和安装启动检查。构建通过不能代替用户可见交互证据。

当前源码基线按 [`BASELINE.md`](BASELINE.md) 记录为 1.0.0；暂不据此推断已经生成或发布安装包。Git 可按当前任务用于版本管理；GitHub 仓库重建和公开发布由主任务单独执行，本页不记录其完成状态。

## 文档入口

- 当前基线：[`BASELINE.md`](BASELINE.md)
- 当前任务和交接：[`tasks/current.md`](../tasks/current.md)
- 文档路由：[`docs/README.md`](README.md)
- 当前实现架构：[`architecture/README.md`](architecture/README.md)
- 运行时隐私：[`PRIVACY.md`](PRIVACY.md)
- 公开发布时再读取：[`PUBLIC_REPOSITORY_PRIVACY.md`](PUBLIC_REPOSITORY_PRIVACY.md)
