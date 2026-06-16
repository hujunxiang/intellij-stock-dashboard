# Stocker 插件 — Agent 指南

## 常用命令

| 任务 | 命令 |
|---|---|
| 编译检查 | `./gradlew compileKotlin compileJava` |
| 运行全部单元测试 | `./gradlew test` |
| 运行单个测试类 | `./gradlew test --tests "com.vermouthx.stocker.utils.StockerQuoteParserTest"` |
| 完整构建 + 打包 | `./gradlew build` |
| 插件兼容性验证 | `./gradlew verifyPlugin` |
| 启动沙箱 IDE 手动测试 | `./gradlew runIdeLatest` |

工具链：JDK 21、Kotlin 2.2.21、Gradle wrapper。始终使用 `./gradlew`。

## 架构

**Stocker** — JetBrains 插件（`com.vermouthx.intellij-investor-dashboard`）。Kotlin/Java 混合代码库，目标平台 IntelliJ 2025.3+（IU 发行版；IC 已停止发布）。

### 数据流

1. `StockerApp` 每个项目运行一个 `ScheduledExecutorService`，以配置的刷新间隔在一个合并任务中拉取所有市场数据。
2. `StockerQuoteHttpUtil` → `StockerQuoteParser` → `StockerQuote` 实体。HTTP 通过 `StockerHttpClientPool`（Apache `PoolingHttpClientConnectionManager`，最大 20 连接）。新浪 API 需要 `Referer: https://finance.sina.com.cn` 请求头。
3. 结果通过 IntelliJ **消息总线** 发布：5 个市场（ALL/CN/HK/US/Crypto）× 3 种事件类型（update/reload/delete）= 15 个 topic，定义在 `listeners/` 下 Java 接口的 `Topic<>` 字段中。
4. Java 监听器（`StockerQuoteUpdateListener` 等）对 Swing `StockerTableModel` 执行单元格级别的差异更新。
5. `StockerSetting` — 应用级 `PersistentStateComponent`，存储在 `stockerplus-config.xml`。保存自选股列表、自定义名称、成本价、持仓量、配色方案、可见列、刷新间隔、语言覆盖。

### 语言边界

- **Kotlin**（`src/main/kotlin/…`）：actions、activities、settings、dialogs、tool windows、HTTP/解析工具、entities、enums、notifications、`StockerApp`、`StockerAppManager`、`StockerBundle`
- **Java**（`src/main/java/…`）：`StockerTableView`、`StockerTableModel`、单元格渲染器、消息总线 notifiers/listeners、`StockerTableModelUtil`、`StockerActionUtil`、`StockerSortState`、`StockerStockOperation`

除非明确要求，不要将 Java 表格/视图/监听器类迁移到 Kotlin。

## 约定

- **用户可见文本** 必须通过 `StockerBundle.message(key)` → `messages/StockerBundle.properties` / `StockerBundle_zh_CN.properties`。保持两个语言包同步。
- **插件注册变更**（actions、services、startup、notification groups、tool window）需要同步更新 `src/main/resources/META-INF/plugin.xml`。
- **表格/弹出菜单变更** 必须同时检查两侧：UI 事件处理（`views`/`components`）和消息总线监听器（`listeners/`）。
- **`DefaultTableModel`** 已自动触发表格事件——不要重复触发手动通知。
- **`StockerQuote`** 相等性仅按 `code` 判定（非全部字段）；处理差异更新时务必注意。
- **`StockerTableColumn`** 在设置中以 enum `name` 字符串持久化；`visibleTableColumns` 有从旧本地化标题迁移的路径（`migrateLocalizedTitle()`）。
- **`StockerQuoteProvider.TENCENT`** 不支持 Crypto（`providerPrefixMap` 中无前缀）；仅新浪提供加密货币行情。

## 测试

纯 JUnit 5，无 IntelliJ 平台测试夹具。仅 **不依赖平台** 的逻辑可测试。

| 可测试（无平台依赖） | 不可测试（需要平台） |
|---|---|
| `StockerQuoteParser`、`StockerTableModelUtil`、`StockerQuote`、`StockerPinyinUtil`、`StockerTableColumn` 非本地化部分 | `StockerSetting`、消息总线监听器、`StockerActionUtil`、任何使用 `StockerBundle.message()` 或 `*.title` getter 的代码 |

**`StockerQuoteParser`** 按硬编码数组索引解析未文档化的新浪/腾讯响应格式。其测试即契约——修改解析器时必须同步更新测试夹具。

如需添加平台测试支持：添加 `testFramework(TestFrameworkType.Platform)` 依赖（尚未配置）。

## 发布

`gradle.properties` 中的 `pluginVersion` 是唯一的版本真相来源。版本升级时，**三处必须同步更新**并在一次提交中完成：

1. `gradle.properties` → `pluginVersion=X.Y.Z`
2. `CHANGELOG.md` → 新增 `## X.Y.Z` 章节，使用 emoji 前缀标题（`### ✨ New Features`、`### 🐛 Bug Fixes`、`### 🔧 Maintenance`），条目采用 `English / 中文` 双语格式。`org.jetbrains.changelog` Gradle 插件会自动提取。
3. `src/main/kotlin/…/notifications/StockerNotification.kt` → 更新 `buildReleaseNote()` 正文（`zh_CN` 和英文 HTML 均需修改）。版本号在运行时来自 `StockerMeta.currentVersion`，只需编辑描述文本。

然后：`./gradlew test build` → 提交 → `git tag vX.Y.Z && git push origin master --tags`

CI（`.github/workflows/build.yml`）在 `v1.*` tag 时触发：构建插件 → 带 `.zip` 的 GitHub Release → `publishPlugin` 发布到 JetBrains Marketplace（`JETBRAINS_TOKEN` secret）。手动备用：`./gradlew publishPlugin -Djetbrains.token=<token>`。

## 常见陷阱

- 右键弹出菜单操作可能在执行前丢失选中行——务必提前捕获选中状态。
- `StockerBundle` 和 `StockerNotification.isChinese()` 均遵循 `StockerSetting.languageOverride`（空 = 跟随系统、`"zh_CN"`、`"en"`）。通知 HTML 在显示时构建，不会预缓存。
- `StockerAppManager` 维护 `Project → StockerApp` 映射；`StockerProjectManagerListener` 在项目关闭时调用 `shutdownThenClear()` 以防止执行器服务泄漏。
- 设置变更必须立即生效（不要求静默重启），除非明确设计为需要重启。
