# 双账号项目交接

两个账号共用同一工作区。仓库文件是交接真相源，账号私有对话记忆只作为提示，不能覆盖当前页、代码或已验证证据。

## 当前真相源

- 当前产品基线：[`BASELINE.md`](BASELINE.md)。
- 当前任务和交接状态：[`tasks/current.md`](../tasks/current.md)。
- 文档路由：[`README.md`](README.md)。
- 账号别名和共享环境约定：[`config/codex-accounts.json`](../config/codex-accounts.json)。
- 任务直接产生的验证结果：对应的 `qa/` 文档或任务页链接。

## 开始工作

打开同一个 `NewMyBook` 工作区，阅读当前基线和任务页，再按任务需要读取代码、测试和文档。无需为每次交接强制运行 Gradle 版本检查；构建时使用 `JAVA_HOME`、`ANDROID_SDK_ROOT` 等本机环境变量，具体操作参见 [`DEVELOPMENT.md`](DEVELOPMENT.md)。

## 结束或暂停

在 `tasks/current.md` 记录已经完成的工作、下一步、直接证据和真实阻塞。只写已经验证的事实；如果代码、测试或文档仍有未完成部分，明确写出范围，不用历史计划的复选框代替当前状态。

## 共享配置与隐私

- `account_a`、`account_b` 是本地别名，并继承同一 `sharedProfile`；每个账号可以通过 `XINYUE_WORKSPACE`、`JAVA_HOME`、`ANDROID_SDK_ROOT` 和 `XINYUE_PRIVATE_QA_ROOT` 提供本机路径。
- `XINYUE_PRIVATE_QA_ROOT` 必须指向仓库外目录。私人正文、字体、备份、原始 XML、日志、截图、唯一文件指纹和脱敏前证据不写入接力页或仓库。
- 不在配置或交接页保存邮箱、密码、令牌、Cookie、API key、恢复码、签名密钥或其他登录凭据。
- 如果两个账号使用不同 Windows 用户，只调整各自环境变量；当前任务、基线和代码仍以共享工作区为准。
