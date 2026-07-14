# Changelog

## 2.0.4

### ✨ New Features / 新功能
- Added support for adding stocks from the Beijing exchanges / 支持添加北交所股票
- Added support for adding the same stock to different groups / 支持不同分组添加相同股票
- Added support for disabling group-scoped actions in "All" view / 支持在“全部”分组视图下禁用分组相关操作，比如删除、移动到分组、编辑

### 🐛 Bug Fixes / 修复

- Fixed cross-group deletion when using Ungroup / Delete on stocks belonging to multiple groups / 修复取消分组、删除操作导致跨分组删除的问题
- Fixed "already exists" in batch import preventing re-adding to current group / 修复批量导入时因股票存在于其他分组而无法添加到当前分组的问题
- Fixed initial table view showing ungrouped stocks / 修复插件启动时未分组股票也被显示的问题
- Fixed right-click menu showing group-scoped actions in "All" view / 修复"全部"视图下右键菜单错误显示分组相关操作的问题
- Fixed delete from suggestion dialog "All" view leaving stale group references / 修复建议对话框"全部"视图删除后分组引用残留的问题

## 2.0.3

### ✨ New Features / 新功能

- Added import favorites from JSON: merge or replace / 新增导入自选功能：支持合并或替换现有数据
- Added export favorites to JSON: all, specific group(s), or checked stock(s) / 新增导出自选功能：支持导出全部、指定分组或勾选的股票
- Added group reorder in management dialog: move up/down, pin to top/bottom / 分组管理界面新增分组排序：上移/下移/置顶/置底
- Added group rename button in management dialog / 分组管理界面新增重命名分组按钮
- Added current-time clock below index dropdown in main tool window / 主界面指数下拉框下方新增当前时间显示 
- Added stock by click blank place / 主界面的空白处点击鼠标右键，可快速添加股票信息
- Added group sort / 支持分组排序

### 🐛 Bug Fixes / 修复

- Fixed export dialog stock picker: unchecked by default, shows code/name/group columns / 修复导出股票选择器：默认不勾选，展示代码/名称/分组列
- Fixed export dialog group picker to use checkbox list / 修复导出分组选择器使用复选框列表
- Fixed export to use directory chooser with auto-generated filename / 修复导出使用目录选择器并自动生成文件名
- Fixed import to merge deduped stocks when group name matches / 修复导入时分组名称相同时合并去重
- Fixed management dialog toolbar button focus-stealing on click / 修复分组管理工具栏按钮点击后焦点跳转问题

## 2.0.2

### ✨ New Features / 新功能

- Added stock edit dialog via right-click: edit cost price and holdings / 右键菜单新增编辑股票功能：编辑成本价和持仓
- Plugin update now installs directly in IDE instead of opening browser / 插件更新改为 IDE 内直接安装，不再跳转浏览器
- Added automatic update notification on startup / 启动时自动检测新版本并提示更新

### 🐛 Bug Fixes / 修复

- Fixed group button tooltips not showing in tool window / 修复工具窗口按钮悬浮提示不显示的问题
- Fixed group button text invisible on light theme / 修复白色主题下分组按钮文字不可见的问题
- Fixed management dialog stock list selection highlight missing / 修复分组管理界面列表选中无高亮的问题
- Fixed management dialog edit labels not following language setting / 修复分组管理编辑界面文字不跟随语言设置的问题
- Fixed update notification showing when current version is newer / 修复当前版本更新时仍提示更新的问题
- Fixed update Marketplace URL returning 404 / 修复更新链接跳转 404 的问题
- Removed internal API usage (`PluginManagerCore.getPlugin`) / 移除内部 API 使用

## 2.0.1

### ✨ New Features / 新功能

- Added right-click context menu to reorder stocks: pin to top/bottom, move up/down / 右键菜单新增股票排序：置顶/置底/上移/下移
- Added right-click "Move to Group" submenu / 右键菜单新增"移动到分组"子菜单
- Added restart plugin button / 新增重启插件按钮
- Added automatic plugin update notification on startup / 启动时自动检查插件更新并提示
- Added batch import result i18n / 批量导入结果支持多语言
- Added stock edit feature / 新增股票编辑功能：支持股票编辑功能，点击股票右键，可以编辑股票的成本是多少，持仓是多少

### 🐛 Bug Fixes / 修复

- Fixed EDT freeze when opening settings dialog (prewarm settings framework classes) / 修复打开设置对话框时 EDT 冻结 21 秒的问题
- Fixed HTTP connection pool shared across projects being destroyed when one project closes / 修复关闭一个项目时其他项目的 HTTP 连接池被销毁的问题
- Fixed stock price alert comparing against daily change instead of previous fetch price / 修复涨跌幅提醒使用日涨跌幅而非与上次请求价比较的问题
- Fixed "All" group showing all stocks instead of aggregating from all groups / 修复"全部"分组未聚合去重所有分组数据的问题
- Fixed language localization: resolved ResourceBundle fallback issue causing Chinese text in English mode / 修复语言本地化：解决 ResourceBundle fallback 导致英文模式显示中文的问题
- Fixed management dialog table headers always in English / 修复分组管理界面表头不跟随语言设置的问题
- Fixed management dialog market tabs not respecting enabled markets / 修复分组管理界面市场标签页不跟随设置的问题
- Fixed F10 URL for ETF stocks using eastmoney.com / 修复 ETF 股票 F10 使用东方财富链接
- Fixed delete group not removing stocks from market lists / 修复删除分组时未同时删除分组内股票的问题
- Fixed first-install auto-creating "默认" group / 修复首次安装时自动创建"默认"分组的问题
- Fixed right-click Delete menu not localized / 修复右键 Delete 菜单未国际化的问题

## 2.0.0

### ✨ New Features / 新功能

- Added stock grouping support in the management dialog: create/delete groups, assign stocks to groups, and filter by group / 在管理对话框中新增分组功能：创建/删除分组、将股票分配到分组、按分组筛选
- Added one-click clear for a specific group or all stocks across all markets / 支持一键清空指定分组或全部市场的所有股票
- Changed refresh interval setting to a dropdown selector (1-10 seconds, default 1) / 刷新间隔设置改为下拉选择器（1-10 秒，默认 1 秒） 
- Added stock detail message / F10功能，点击股票右键->F10打开浏览器（可以是idea内置浏览器，不过需要在idea中配置浏览器）
- Batch add stock by code / 批量添加股票功能，支持批量添加股票代码,比如输入：sz301099,sz000670,sz002413或者301099,000670,002413或者301099 000670 sz002413或sz301099 sz000670 sz002413，都能识别成功

### Thanks

Forked from WhiteVermouth/intellij-investor-dashboard (v1.21.0), thanks the author!
