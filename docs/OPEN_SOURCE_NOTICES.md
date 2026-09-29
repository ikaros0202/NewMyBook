# 开源组件说明

本项目的运行时依赖均来自 AndroidX、JetBrains 和 Google 的常用开源组件，主要包括：

- AndroidX Core、Activity、Lifecycle、Compose、Material 3、Navigation 3、Room 3、SQLite、WorkManager、Hilt AndroidX：Apache License 2.0。
- Kotlin、Kotlin Coroutines、Kotlin Serialization：Apache License 2.0。
- Dagger / Hilt：Apache License 2.0。
- Google Material Icons / Material Symbols：Apache License 2.0。应用只打包本次界面使用的本地 Android Vector Drawable，来源为 `google/material-design-icons` 官方仓库，不在运行时联网加载。

仅用于构建或测试的依赖包括 JUnit 4（Eclipse Public License 1.0）、Robolectric（MIT License）、Google Truth 和 Turbine（Apache License 2.0）。

V1.6 的 TXT 结构化排版研究参考了 [Anx Reader v1.14.0](https://github.com/Anxcye/anx-reader/tree/v1.14.0) 的公开 TXT→章节→段落处理思路。Anx Reader 采用 [MIT License](https://github.com/Anxcye/anx-reader/blob/v1.14.0/LICENSE)。新阅没有复制 Anx 的资源、字体、图标、Foliate 文件或 EPUB/WebView 实现，而是在保留本项目等长 UTF-16 偏移契约的前提下独立实现空白折叠、章节标题语义和设置分层。

精确版本以 `gradle/libs.versions.toml` 和 Gradle 的 `releaseRuntimeClasspath` 解析结果为准。分发应用时应随包提供相应许可证全文；本说明不替代各项目原始许可证。
