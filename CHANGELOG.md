# Changelog

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
