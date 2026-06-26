package com.vermouthx.stocker.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.vermouthx.stocker.StockerMeta
import com.vermouthx.stocker.settings.StockerSetting
import org.intellij.lang.annotations.Language
import java.util.*

object StockerNotification {

    private object Colors {
        const val PRIMARY = "#4CAF50"
        const val BACKGROUND = "rgba(33, 150, 243, 0.08)"
        const val BORDER = "#2196F3"
    }

    private object Styles {
        const val CONTAINER = "margin: 8px 0; line-height: 1.4;"
        const val HEADING = "margin: 0 0 8px 0; color: ${Colors.PRIMARY}; font-size: 14px; font-weight: 600;"
        const val PARAGRAPH = "margin: 0 0 12px 0; font-size: 13px;"
        const val SMALL_TEXT = "margin: 12px 0 0 0; font-size: 12px; font-style: italic; opacity: 0.7;"
        const val LIST_ITEM = "margin: 6px 0;"
        const val SUB_LIST = "margin: 4px 0 0 0; padding-left: 18px; font-size: 12px;"
        const val INFO_BOX = "background: ${Colors.BACKGROUND}; border-left: 3px solid ${Colors.BORDER}; padding: 10px 12px; margin: 12px 0; border-radius: 3px;"
    }

    private val version get() = StockerMeta.currentVersion

    private fun isChinese(): Boolean {
        val override = try { StockerSetting.instance.languageOverride } catch (_: Exception) { "" }
        if (override == "zh_CN") return true
        if (override == "en") return false
        return Locale.getDefault().language == "zh"
    }

    @Language("HTML")
    private fun buildReleaseNote(): String {
        val v = version
        return if (isChinese()) """
            <div style="${Styles.CONTAINER}">
                <p style="${Styles.PARAGRAPH}">🎉 <strong>欢迎使用 StockerPlus v${v}！本次更新内容：</strong></p>
                <h4 style="${Styles.HEADING}">✨ v${v} 新功能</h4>
                <ul style="margin: 0; padding-left: 18px;">
                    <li style="${Styles.LIST_ITEM}">✨ <strong>新功能</strong>
                        <ul style="${Styles.SUB_LIST}">
                            <li>右键菜单新增股票排序：置顶/置底/上移/下移</li>
                            <li>右键菜单新增「移动到分组」子菜单</li>
                            <li>新增重启插件按钮</li>
                            <li>启动时自动检查插件更新并提示升级</li>
                        </ul>
                    </li>
                    <li style="${Styles.LIST_ITEM}">🐛 <strong>修复</strong>
                        <ul style="${Styles.SUB_LIST}">
                            <li>修复打开设置对话框时界面冻结 21 秒的问题</li>
                            <li>修复关闭一个项目时其他项目行情不更新的问题</li>
                            <li>修复涨跌幅提醒逻辑：改为与上次请求价比较</li>
                            <li>修复语言切换后部分界面仍显示中文的问题</li>
                            <li>修复删除分组时未同时删除分组内股票的问题</li>
                        </ul>
                    </li>
                </ul>
                <div style="${Styles.INFO_BOX}">
                    <p style="margin: 0; font-size: 12px;">💡 <strong>说明：</strong>右键点击表格中的股票即可使用排序和分组功能。</p>
                </div>
            </div>
        """.trimIndent() else """
            <div style="${Styles.CONTAINER}">
                <p style="${Styles.PARAGRAPH}">🎉 <strong>Welcome to StockerPlus v${v}! Here's what's new:</strong></p>
                <h4 style="${Styles.HEADING}">✨ New in v${v}</h4>
                <ul style="margin: 0; padding-left: 18px;">
                    <li style="${Styles.LIST_ITEM}">✨ <strong>New Features</strong>
                        <ul style="${Styles.SUB_LIST}">
                            <li>Right-click reorder: pin to top/bottom, move up/down</li>
                            <li>Right-click "Move to Group" submenu</li>
                            <li>Added restart plugin button</li>
                            <li>Automatic plugin update notification on startup</li>
                        </ul>
                    </li>
                    <li style="${Styles.LIST_ITEM}">🐛 <strong>Bug Fixes</strong>
                        <ul style="${Styles.SUB_LIST}">
                            <li>Fixed 21-second freeze when opening settings dialog</li>
                            <li>Fixed other projects losing data when one project is closed</li>
                            <li>Fixed price alert comparing against daily change instead of previous fetch</li>
                            <li>Fixed language switching not applying to all UI elements</li>
                            <li>Fixed delete group not removing stocks from market lists</li>
                        </ul>
                    </li>
                </ul>
                <div style="${Styles.INFO_BOX}">
                    <p style="margin: 0; font-size: 12px;">💡 <strong>Tip:</strong> Right-click a stock in the table to use reorder and grouping features.</p>
                </div>
            </div>
        """.trimIndent()
    }

    @Language("HTML")
    private fun buildWelcomeMessage(): String {
        return if (isChinese()) """
            <div style="${Styles.CONTAINER}">
                <p style="${Styles.PARAGRAPH}">🎉 <strong>欢迎使用 StockerPlus！</strong>您的投资仪表板已安装完成，可以开始跟踪您喜爱的股票了。</p>
                <div style="${Styles.INFO_BOX}">
                    <p style="margin: 0 0 8px 0; font-size: 12px;">💡 <strong>快速设置：</strong></p>
                    <ul style="margin: 0; padding-left: 16px; font-size: 12px;">
                        <li style="margin: 4px 0;">从左侧面板打开 <strong>StockerPlus</strong> 工具窗口</li>
                        <li style="margin: 4px 0;">点击<strong>添加自选股</strong>来搜索和添加股票</li>
                        <li style="margin: 4px 0;">在<strong>设置 → 工具 → StockerPlus</strong> 中配置选项</li>
                        <li style="margin: 4px 0;">开始实时跟踪您的投资！</li>
                    </ul>
                </div>
//                <p style="${Styles.SMALL_TEXT}">💖 如果您觉得这个插件有帮助，请考虑点击下方的 <strong>Donate</strong> 按钮以支持开发。谢谢！📊</p>
            </div>
        """.trimIndent() else """
            <div style="${Styles.CONTAINER}">
                <p style="${Styles.PARAGRAPH}">🎉 <strong>Welcome to StockerPlus!</strong> Your investment dashboard is now installed and ready to track your favorite stocks.</p>
                <div style="${Styles.INFO_BOX}">
                    <p style="margin: 0 0 8px 0; font-size: 12px;">💡 <strong>Quick Setup:</strong></p>
                    <ul style="margin: 0; padding-left: 16px; font-size: 12px;">
                        <li style="margin: 4px 0;">Open the <strong>StockerPlus</strong> tool window from the left panel</li>
                        <li style="margin: 4px 0;">Click <strong>Add Favorite Stocks</strong> to search and add stocks</li>
                        <li style="margin: 4px 0;">Configure settings at <strong>Settings → Tools → StockerPlus</strong></li>
                        <li style="margin: 4px 0;">Start tracking your investments in real-time!</li>
                    </ul>
                </div>
//                <p style="${Styles.SMALL_TEXT}">💖 If you find this plugin helpful, please consider clicking the <strong>Donate</strong> button below to support its development. Thank you! 📊</p>
            </div>
        """.trimIndent()
    }

    private const val NOTIFICATION_GROUP_ID = "StockerPlus"

    @JvmField
    val notificationIcon = IconLoader.getIcon("/icons/logo.png", javaClass)

    private const val GITHUB_LINK = "https://github.com/hujunxiang/intellij-stock-dashboard"
    private const val DONATE_LINK = "https://www.buymeacoffee.com/nszihan"

    fun notifyReleaseNote(project: Project) {
        val title = if (isChinese()) "StockerPlus v${version} - 版本说明" else "StockerPlus v${version} - Release Notes"
        val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, buildReleaseNote(), NotificationType.INFORMATION)
        addNotificationActions(notification)
        notification.icon = notificationIcon
        notification.notify(project)
    }

    fun notifyWelcome(project: Project) {
        val title = if (isChinese()) "StockerPlus 安装成功" else "StockerPlus Successfully Installed"
        val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, buildWelcomeMessage(), NotificationType.INFORMATION)
        addNotificationActions(notification)
        notification.icon = notificationIcon
        notification.notify(project)
    }

    fun notifyUpdate(project: Project, currentVersion: String, latestVersion: String) {
        val title = if (isChinese()) "StockerPlus 有新版本可用" else "StockerPlus Update Available"
        val content = if (isChinese()) {
            "StockerPlus v$latestVersion 已发布（当前版本 v$currentVersion）。建议更新以获取最新功能和修复。"
        } else {
            "StockerPlus v$latestVersion is available (current: v$currentVersion). Update to get the latest features and fixes."
        }
        val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)
        val updateAction = NotificationAction.createSimple(if (isChinese()) "🔄 更新插件" else "🔄 Update Plugin") {
            BrowserUtil.browse("https://plugins.jetbrains.com/plugin/32417-stockerplus")
        }
        notification.addAction(updateAction)
        addNotificationActions(notification)
        notification.icon = notificationIcon
        notification.notify(project)
    }

    private fun addNotificationActions(notification: Notification) {
        val github = NotificationAction.createSimple("📖 GitHub") {
            BrowserUtil.browse(GITHUB_LINK)
        }
        val actionDonate = NotificationAction.createSimple("☕ Donate") {
            BrowserUtil.browse(DONATE_LINK)
        }
        notification.addAction(github)
//        notification.addAction(actionDonate)
    }
}
