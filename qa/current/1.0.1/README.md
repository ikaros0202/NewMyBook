# 1.0.1 连续阅读体验验证

日期：2026-09-29。修复目标及原始问题见 [1.0.1 计划](../../../docs/plans/1.0.1-reading-continuity.md)和 [1.0.0 体验记录](../2026-09-29-reader-experience/README.md)。本页记录新增、修改代码及直接影响的定向验证，不代表全量回归或发布验收。

## 环境与范围

- 版本：`1.0.1 / 9`，实际安装版本名 `1.0.1-debug`，应用 ID `com.xinyue.reader.debug`。
- 设备：现有 `XinYue_API37`，API 37、16 KB 页，1080 × 2400、density 420、系统字体缩放 1.0。大字体可达性由 Compose 测试注入 2.0 缩放。
- 使用独立临时 Android 用户，保留主用户书库。公开输入为原创小说《雾港书简》；分页变更另选一份已登记的私人长篇，原始证据仅存仓库外。
- 本轮不构建或发布 Release，不上传 GitHub，不改变数据库 schema 或备份格式。

## A：正文完整显示

根因包括两个层次：`TextView` 与 `StaticLayout` 的回退字体行距默认值不同；候选窗口中的排版结果与独立页面片段的末行结果也可能不同。修复共用断行、回退字体行距和行距参数，再以实际页面片段复测末端，仅把放不下的文字交给下一页。

真实设备测量显示，原分页配置下同一默认字号片段高度为 1658 px，而实际 `TextView` 为 1918 px。第一次统一参数后，较大字号的一个页面仍为 2068 px，高于 2035 px 视口；加入片段复测后，两组字号/行距全部通过。

- 设备测试：`ReaderPageLayoutDeviceTest`，20sp / 1.6 和 26sp / 1.8，954 × 2035 px 正文视口，逐页对照实际 `TextView` 的末行底部。见[失败测量](page-layout-device.txt)、[修复后结果](page-layout-device-after.txt)。
- 公开小说前三章共九页，`public-page-01` 至 `public-page-09` 的截图末行完整可见。XML 正文去掉排版占位字符 U+2060 和空白后，拼接共 2920 字符，与原文第一章起至第四章前完全一致，未发现缺口或重叠。
- 第 4 页从第二章开始，第 7 页从第三章开始。反向滑动回到第 9 页，与此前该页正文完全一致，见 [reverse-public2.xml](reverse-public2.xml)。
- 代表截图：[第 1 页](public-page-01.png)、[第 2 页](public-page-02.png)、[第 7 页](public-page-07.png)、[第 9 页](public-page-09.png)。

## B：目录与续读

实际设备已确认：目录选择第三章后没有“从这里继续”临时栏；菜单提供“返回跳转前”。不翻页退出、翻页后退出，重启进程均恢复到对应位置；返回跳转前的位置后退出、重启也保持一致。

- [目录选择后](nav-chapter3-selected.png)、[不翻页重启](nav-noswipe-resume.xml)。
- [第三章下一页](nav-chapter3-next.xml)、[退出后书架](nav-chapter3-library.xml)、[重启恢复](nav-chapter3-next-resume.xml)。
- [菜单中的返回入口](chapter-return-menu.png)、[返回位置](chapter-returned.xml)、[该位置重启恢复](chapter-return-reopened.xml)。
- 用户补充确认：从搜索/书签临时位置再目录换章时，返回点取此前正式阅读位置，避免把临时浏览意外提升为续读进度。

## C：快速设置与菜单

菜单打开后等待 11 秒仍可操作，见 [menu-wait11.xml](menu-wait11.xml)；返回键关闭菜单而保留正文，见 [menu-back.xml](menu-back.xml)；菜单打开时左滑能关闭菜单并前进一页，见 [menu-swipe.xml](menu-swipe.xml)。深色目录见 [directory-final.png](directory-final.png)。

快速设置按实际内容收紧底部面板，首屏保留作用范围、字号、五种主题、亮度和更多设置；保存/取消固定在底部，完整设置使用独立的有界滚动视口。面板背景的关闭触区与内容分开，避免把完整设置的控件错误合并成一个按钮。

- [深色快捷面板](settings-quick-dark.png)：首屏高频项全部可见，上方正文仍可预览。
- [纸张主题草稿预览](settings-preview-paper.png)、[取消后](settings-cancelled.png)：取消恢复深色主题和原页面。
- [保存纸张主题](settings-saved-paper.png)、[重启恢复](settings-saved-resume.png)：主题保留，正文片段与保存前一致。
- [完整设置](settings-full.png)：保留原有字体、行段距、缩进、主题管理等功能。设备 UI 测试进一步覆盖完整设置的主题复制/创建/重命名/删除回调，以及 200% 字体下的可达性。

## D：导入后直接阅读

成功结果提供“开始阅读”，实际点击后直接进入刚导入的书；按返回回到原导入结果，“编辑书名和作者”继续可用。失败结果没有阅读按钮。

- [成功结果截图](03-import-success.png)、[直接阅读](import-direct-reader.xml)、[返回结果](import-results-returned.xml)。
- `ImportScreenTest` 两项设备测试通过，见 [import-ui-tests.txt](import-ui-tests.txt)。

## 私人长篇

按分页和位置恢复风险选取一份已登记的私人长篇，使用系统文件选择器导入独立测试用户。已确认打开正文、连续三次向前滑动、反向返回相同页面；退出并重启后，在分页完成时恢复到相同正文片段。源文件前后完整性比较一致。

首次较早抓取处于分页尚未稳定时，不能拿该中间状态判定恢复失败或通过；最终结论使用分页完成后的相同页面对照。私人文件名、路径、正文、指纹和原始截图/XML 均不写入本页或仓库。

## 自动化与交付状态

| 检查 | 结果与证据 |
|---|---|
| 分页、选择映射、分页状态、导航及阅读状态 JVM 测试 | 107 项通过，0 失败；[构建记录](build-tests-final.log)、[分组计数](unit-test-summary.json) |
| 快捷设置、完整设置、菜单和真实 TextView 排版 | 19 项 API 37 设备测试通过；[设备结果](device-tests-verified.txt) |
| 导入成功/失败结果入口 | 2 项 API 37 设备测试通过；[导入结果](import-ui-tests.txt) |
| Reader / Import / App Debug Lint 与最终 Debug 构建 | 通过；[最终静态检查和构建](lint-build-verified.log)。Reader 为 0 errors / 9 warnings / 2 hints，App 为 0 errors / 20 warnings；未把警告表述为已全部消除 |
| 最终安装包 | 安装后版本为 `1.0.1-debug / 9`，冷启动及打开公开小说通过；最后的排版常量声明修正后再次通过[真实排版设备测试](final-layout-device.txt) |

新增的失败恢复测试同时暴露出原 `ReaderViewModelTest` 的私有 `@get:Rule` 没有生成可供 JUnit 发现的 getter，导致调度规则未生效。已改为公开规则，并验证整类 51 项通过及强制重跑通过；最终六个相关类合计 107 项通过。没有以删掉失败断言代替修复。

连续阅读实现批次的 Debug APK SHA-256 为 `C6041B9FEEF71FF0611F54F40F22EAB34140F97ADCB803AC422966C3C54620F8`，另见 [当时的哈希文件](debug-apk.sha256)。后续目录定位修复已重新生成 `app/build/outputs/apk/debug/app-debug.apk`，当前包以[后续记录及哈希](navigation-followup/README.md)为准。两次均为本地开发验证，没有创建 Release 或上传产物。

独立测试用户 11 已停止并删除，设备已切回用户 0；[清理后用户列表](device-cleanup.txt)只包含 Owner。私人原始证据已留在仓库外，设备临时目录中的对应私人 XML/截图也已清理。未删除或重置主用户书库。

文档影响已同步当前基线、计划、任务页、导入/阅读契约和调用链。模块所有权、数据库 schema、备份格式未变，无需为本次行为优化新增迁移或 ADR。

早期失败日志用于定位；未在本页索引的过程抓取、`exploratory/` 和诊断日志不作为独立通过证据。

## 后续独立排查项（已处理）

用户随后授权处理这两项，独立记录见 [目录定位与书签核实](navigation-followup/README.md)。

- 书签删除一项是此前代码审查的误报：书签本就是统一标注表的 `BOOKMARK` 视图，共享标注 ID 和删除入口。原安装包已完成添加、抽屉删除、重启后保持删除的实际验证，不能仅凭 `onDeleteAnnotation` 回调名称判断接错。
- 目录定位问题已通过单个设备测试复现并修复：搜索已经为空时，每次点击也会发出明确的滚动请求。同一用例修复后通过，结果见独立记录；没有重跑上表中的整批测试。
