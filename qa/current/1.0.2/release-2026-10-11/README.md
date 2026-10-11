# 1.0.2 Release 构建与源码交付

日期：2026-10-11。用户授权将当前工作区提交 Git、推送 GitHub、构建 Release，并补齐 APK 发布。版本保持 `1.0.2 / versionCode 10`，应用 ID 为 `com.xinyue.reader`。[GitHub Release v1.0.2](https://github.com/ikaros0202/NewMyBook/releases/tag/v1.0.2) 已发布，附件包含 APK 和 SHA-256 校验文件。

Release 标签指向实际构建源码提交 `8bfa6b76471425335bd2f61cbc478b5490f669c7`。上传后重新下载 APK，SHA-256 与本机产物完全一致；重新核对包名、版本、最低 API、目标 API 和签名均通过。后续下载入口与发布记录修改属于文档更新，不改变该 APK。

## 交付内容

当前提交包含此前的阅读器 UI、全书页码、朗读与自动翻页协调、备份恢复、封面保留、跨块搜索、目录识别、系统选择器状态及 QA 数据保护修复，也包含本轮私人 QA 位置规则与历史资料删除说明。

合并 Manifest 核对发现依赖引入 `ACCESS_NETWORK_STATE`，当前源码明确移除该权限及 `INTERNET`，保持离线产品底线。公开源码只包含维护中的 QA Markdown、脚本和合成夹具；原始设备证据、日志、截图、音频和 APK 留在本机，不进入本次源码提交。部分历史 Markdown 附件链接仅在对应本机资料存在时可用。

## 本机 APK

- 可安装产物：`dist/release-1.0.2/XinYue-1.0.2-release.apk`。
- SHA-256：`AA4880FA5F00D42399118B832692D9BD910CBA7393A92821B9DFAB9253868A1D`。
- 文件大小：7,653,251 字节。
- 原始未签名产物：`app/build/outputs/apk/release/app-release-unsigned.apk`。
- Release 启用 R8 与资源裁剪，最终 APK 不可调试。
- 使用本机已有 Android Debug 证书签名；适合本地安装验证，不是商店发布证书。证书 SHA-256：`F156A6EFD9EB66DCF814F32999EE43FFE05A5E1AB2E30A8F7F9B9E24F3C9E1D3`。
- `apksigner verify` 通过 v2/v3 签名验证；`zipalign -c -p 4` 通过。
- 最低 API 26，目标 API 37。实际 APK 无 `INTERNET`、`ACCESS_NETWORK_STATE` 或宽泛存储权限；剩余权限为唤醒锁、启动广播、前台服务及应用内部动态接收器权限。

## 本轮验证

```powershell
.\gradlew.bat testDebugUnitTest :core:domain:test :core:text:test lintDebug :app:lintVitalRelease :app:assembleRelease --no-configuration-cache --console=plain
.\gradlew.bat :app:processDebugMainManifest :app:lintDebug :app:lintVitalRelease :app:assembleRelease --no-configuration-cache --console=plain
```

- 首次完整命令及权限修正后的定向构建均 `BUILD SUCCESSFUL`，退出码 0。
- 指定 JVM 任务结果：111 个套件、505 项测试，0 failures、0 errors、0 skipped。
- Debug Lint 与 Release 关键 Lint 通过。应用 Debug Lint 为 0 errors、43 warnings；依赖版本与旧 DSL 等警告仍存在，不宣称零警告。
- 隐私扫描回归测试通过，覆盖 Python 源码和被 Git 忽略的项目内 QA 标记清单。
- 验收数据保护、V1.6 结构化 TXT 入口、V1.2 设备矩阵与无障碍矩阵契约检查通过；本轮没有执行这些脚本的设备验收部分。
- 最终待公开文件执行通用凭据、私人路径与忽略范围检查，`git diff --cached --check` 通过。旧私人标记清单已按用户要求删除，本轮没有该清单的专有标记覆盖。

构建、签名、权限与审计原始日志位于本机 `private-local/release-2026-10-11-*`，被 Git 忽略。本轮未启动模拟器、未播放朗读或自动翻页，没有设备安装、Release 运行、性能或长期真实朗读验证。

## 历史资料与后续

此前迁入的私人回归、10 月 2 日原始审查证据、旧 Git/Release 备份及迁移清单已按用户明确要求删除，不作为本次验证依据。主项目原有的其他 QA、源码和用户数据保留。需要撤回源码改动时可基于本次提交执行正常 Git revert；删除的历史资料未另建恢复副本。
