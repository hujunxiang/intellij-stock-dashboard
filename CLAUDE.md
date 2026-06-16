# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

完整的贡献者指南是唯一的权威来源，请先阅读：

@AGENTS.md

以下各节是面向 Claude 的速查参考；若与 `AGENTS.md` 冲突，以 `AGENTS.md` 为准，
并应修复此处的偏差。

## 项目简介

`Stocker` — JetBrains IDE 插件 (`com.vermouthx.intellij-investor-dashboard`)，
在工具窗口中实时展示股票/加密货币自选股（A 股、港股、美股、加密货币）。
Kotlin/Java 混合代码库（Kotlin 负责逻辑/UI 串联，Java 负责表格渲染/消息监听），
基于 IntelliJ Platform Gradle 插件构建，目标平台 `2025.3`。

## 常用命令

| 任务 | 命令 |
| --- | --- |
| 编译（快速检查） | `./gradlew compileKotlin compileJava` |
| 运行单元测试 | `./gradlew test` |
| 运行单个测试类 | `./gradlew test --tests "com.vermouthx.stocker.utils.StockerQuoteParserTest"` |
| 完整构建（含打包） | `./gradlew build` |
| 插件兼容性验证 | `./gradlew verifyPlugin` |
| 启动沙箱 IDE 进行手动测试 | `./gradlew runIdeLatest` |

工具链：JDK 21，Gradle wrapper 9.5.0。始终使用 wrapper（`./gradlew`）。

## 插件工作原理（数据流）

- `StockerApp` 每个项目运行一个合并的 `ScheduledExecutorService` 任务，
  按配置的刷新间隔拉取所有市场的自选+指数数据，通过 IntelliJ **消息总线** 发布结果。
- `StockerQuoteHttpUtil` 构建数据源 URL（新浪/腾讯）→
  `StockerQuoteParser` 将文本响应解析为 `StockerQuote`。
  HTTP 连接通过 `StockerHttpClientPool`（Apache `PoolingHttpClientConnectionManager`，
  最大 20 个连接）进行池化管理。
- 15 个消息总线 topic（5 个市场 × 3 种事件类型）：ALL/CN/HK/US/Crypto 各有
  update、delete、reload 三个 topic，定义在 `listeners/` 下的 Java 通知接口中。
  各市场工具窗口标签页订阅对应的 topic；Java 监听器对 Swing `StockerTableModel`
  执行单元格级别的差异更新。
- `StockerSetting` 是应用级 `PersistentStateComponent`（`stockerplus-config.xml`），
  保存自选股列表、自定义名称、成本价、持仓量、配色方案、可见列和刷新间隔。

## 编辑时最重要的约定

- **保持 Kotlin/Java 分工。** 除非明确要求，不要将 Java 表格/视图/监听类迁移到 Kotlin。
- **用户可见文本** 放在 `src/main/resources/messages/StockerBundle*.properties` 中，
  不要硬编码——并保持 `zh_CN` 包同步。
- **版本升级需同时修改三个文件：** `gradle.properties`、`CHANGELOG.md`、
  `notifications/StockerNotification.kt`。
- **工具窗口/表格/弹出菜单变更** 需同时检查 UI 事件侧（`views`/`components`）
  和消息总线监听侧（`listeners`）。
- **插件注册变更**（action、startup、service、notification group）
  必须同步更新 `src/main/resources/META-INF/plugin.xml`。

## 发布流程

发布由 **tag 驱动**：在 `master` 上升版本号，推送 `v*` tag，
CI（`.github/workflows/build.yml`）会构建插件、创建 GitHub Release 并发布到
JetBrains Marketplace。完整规则见 `AGENTS.md` → "Release And Versioning"。

1. **同时修改三个文件**（保持一致）：
   - `gradle.properties` → `pluginVersion`（唯一的版本真相来源）。
   - `CHANGELOG.md` → 新增 `## X.Y.Z` 章节，沿用现有格式：
     emoji 前缀的分类标题（`### ✨ New Features`、`### 🐛 Bug Fixes`、
     `### 🔧 Maintenance`……），每条包含 **中英双语** `English / 中文` 条目。
     Marketplace 变更说明由 `org.jetbrains.changelog` 插件从此文件最新条目自动提取，
     无需手动复制。
   - `notifications/StockerNotification.kt` → 更新 `buildReleaseNote()`，
     同时编辑 `zh_CN` 和英文 HTML 段落。（这是升级后 IDE 内弹窗的内容；
     版本号本身来自 `StockerMeta`，只需修改描述文本。）
2. **tag 前验证：**
   - `./gradlew test` 和 `./gradlew build`（以及 `./gradlew verifyPlugin` 做兼容性检查）。
3. **提交、打 tag、推送。** 使用 `vX.Y.Z` tag——CI 的 release 作业仅在匹配 `v1.*` 时触发：
   - `git commit -am "🔖 Release version X.Y.Z: …"`
   - `git tag vX.Y.Z && git push origin master --tags`
4. **CI 自动完成剩余工作**：`buildPlugin` → 带 `.zip` 附件的 GitHub Release →
   `publishPlugin` 发布到 Marketplace（使用 `JETBRAINS_TOKEN` 仓库 secret）。
   如需手动发布：`./gradlew publishPlugin -Djetbrains.token=<token>`。

## 测试说明

- 测试使用纯 JUnit 5，无 IntelliJ 测试夹具，因此目前仅 **平台无关** 的逻辑可测试
  （不能涉及 `ApplicationManager`/服务、消息总线或 `StockerBundle` 本地化标题）。
  完整说明见 `AGENTS.md` 的"Testing"章节。
- **当前可测试：** `StockerQuoteParser`、`StockerTableModelUtil`、`StockerQuote` 实体、
  `StockerPinyinUtil`，以及 `StockerTableColumn` 中未本地化的部分。
- **无平台夹具不可测试：** `StockerSetting`、消息总线监听器、`StockerActionUtil`、
  任何依赖 `StockerBundle` 标题的内容。
- `StockerQuoteParser` 按硬编码字段索引解析未文档化的数据源格式——
  将其测试视为契约，任何解析器变更都需同步更新测试夹具。
