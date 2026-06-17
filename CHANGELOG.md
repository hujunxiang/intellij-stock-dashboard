# Changelog

## 2.0.0

### ✨ New Features / 新功能

- Added stock grouping support in the management dialog: create/delete groups, assign stocks to groups, and filter by group / 在管理对话框中新增分组功能：创建/删除分组、将股票分配到分组、按分组筛选
- Added one-click clear for a specific group or all stocks across all markets / 支持一键清空指定分组或全部市场的所有股票
- Changed refresh interval setting to a dropdown selector (1-10 seconds, default 1) / 刷新间隔设置改为下拉选择器（1-10 秒，默认 1 秒） 
- Added stock detail message / F10功能，点击股票右键->F10打开浏览器（可以是idea内置浏览器，不过需要在idea中配置浏览器）
- Batch add stock by code / 批量添加股票功能，支持批量添加股票代码,比如输入：sz301099,sz000670,sz002413或者301099,000670,002413或者301099 000670 sz002413或sz301099 sz000670 sz002413，都能识别成功

### Thanks

Forked from WhiteVermouth/intellij-investor-dashboard (v1.21.0), thanks the author!
