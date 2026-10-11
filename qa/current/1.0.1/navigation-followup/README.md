# 目录定位与书签删除核实

日期：2026-09-29。范围为上一轮记录的两项待查行为；设备为现有 API 37 / 16 KB 模拟器，使用独立临时 Android 用户 12，公开原创 TXT 夹具。没有运行整类或全量测试，没有使用私人长篇。

## 书签删除：更正误报

原安装包 `1.0.1-debug / 9` 的 SHA-256 与上一轮交付一致：`C6041B9FEEF71FF0611F54F40F22EAB34140F97ADCB803AC422966C3C54620F8`。在修改任何生产代码前执行：添加当前页书签 → 打开目录抽屉的书签页 → 删除并确认 → 强制停止应用 → 重启后重新检查书签列表。

- [删除前](bookmark-before-delete.png)：书签存在。
- [删除后](bookmark-after-delete.png)：列表为空。
- [重启后](bookmark-after-restart.png)：列表仍为空；对应 [XML](bookmark-after-restart.xml)包含“当前书籍还没有书签”。

实现链路为 `ReaderScreen.onDeleteBookmark` → `ReaderViewModel.deleteAnnotation` → `RoomAnnotationRepository.delete` → `AnnotationDao.delete`。`RoomBookmarkRepository` 同样通过该 DAO 读写 `BOOKMARK` 类型的标注，书签与批注共享 ID 和存储。此前“回调接错”的判断不成立，不需要为此增加删除接口或改动存储。

## 目录定位：单项回归

唯一新增用例为 `ReaderV14MenuTest#directoryLocateCurrentChapterWorksAfterClearingSearchAndWhenAlreadyEmpty`，验证有搜索词时定位会清除搜索并回到当前章，以及再次滚远、搜索已为空时仍能定位当前章。

修复前只运行该方法：[失败证据](directory-before.txt)记录 `Tests run: 1, Failures: 1`。清除搜索后的定位已通过，最后一次空搜索定位断言失败，符合根因：重复 `query = ""` 不改变现有 `LaunchedEffect` 的 key。

修复在 `ReaderNavigationDrawer` 增加独立的定位请求计数，并加入滚动副作用的依赖。按钮清空搜索并递增请求，确保每次点击都会基于当前列表定位。测试代码保持不变，[修复后证据](directory-after.txt)记录 `OK (1 test)`。

只执行以下方法，修复前后各一次，没有运行整个 `ReaderV14MenuTest` 或其他测试类：

```text
adb -s emulator-5554 shell am instrument --user 12 -w -r -e class com.xinyue.reader.feature.reader.ReaderV14MenuTest#directoryLocateCurrentChapterWorksAfterClearingSearchAndWhenAlreadyEmpty com.xinyue.reader.feature.reader.test/androidx.test.runner.AndroidJUnitRunner
```

## 交付与文档影响

- [Debug 构建记录](build-after.log)：`:feature:reader:assembleDebugAndroidTest :app:assembleDebug` 通过，修复包已安装到测试用户。
- 最新 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，版本仍为 `1.0.1-debug / 9`。SHA-256：`CB2796C8A241EEDC45A60FF3A7425981CCBDF0957C07C8CFE9EB0C3A3A06EAD4`，见[哈希文件](debug-apk.sha256)。没有发布或上传 GitHub。
- 本轮未修改分页、正文、进度存储或导入算法，不需要私人长篇回归。
- 已同步当前基线行为、任务状态和此前误报。稳定接口、架构、数据库及备份格式均未改变，无需修改契约、迁移或新增 ADR。
- 临时用户清理结果见[设备状态](device-cleanup.txt)，主用户数据未重置。
