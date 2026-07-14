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
                <h4 style="${Styles.HEADING}">🐛 v${v} 修复</h4>
                <ul style="margin: 0; padding-left: 18px;">
                    <li style="${Styles.LIST_ITEM}">🐛 <strong>修复</strong>
                        <ul style="${Styles.SUB_LIST}">
                            <li>修复取消分组、删除操作导致跨分组删除的问题</li>
                            <li>修复批量导入时因股票存在于其他分组而无法添加到当前分组的问题</li>
                            <li>修复插件启动时未分组股票也被显示的问题</li>
                            <li>修复"全部"视图下右键菜单错误显示分组相关操作的问题</li>
                            <li>修复建议对话框"全部"视图删除后分组引用残留的问题</li>
                        </ul>
                    </li>
                </ul>
            </div>
        """.trimIndent() else """
            <div style="${Styles.CONTAINER}">
                <p style="${Styles.PARAGRAPH}">🎉 <strong>Welcome to StockerPlus v${v}! Here's what's new:</strong></p>
                <h4 style="${Styles.HEADING}">🐛 Bug Fixes in v${v}</h4>
                <ul style="margin: 0; padding-left: 18px;">
                    <li style="${Styles.LIST_ITEM}">🐛 <strong>Bug Fixes</strong>
                        <ul style="${Styles.SUB_LIST}">
                            <li>Fixed cross-group deletion when using Ungroup / Delete on stocks in multiple groups</li>
                            <li>Fixed batch import skipping stocks that exist in other groups</li>
                            <li>Fixed initial table view showing ungrouped stocks</li>
                            <li>Fixed right-click menu showing group-scoped actions in "All" view</li>
                            <li>Fixed stale group references after deleting from "All" view in suggestion dialog</li>
                        </ul>
                    </li>
                </ul>
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
            try {
                triggerPluginUpdate()
            } catch (e: Exception) {
                BrowserUtil.browse("https://plugins.jetbrains.com/plugin/32417-stockerplus")
            }
        }
        notification.addAction(updateAction)
        addNotificationActions(notification)
        notification.icon = notificationIcon
        notification.notify(project)
    }

    private fun triggerPluginUpdate() {
        // Use IntelliJ's PluginDownloader to download and install the update in-IDE
        val downloaderClass = Class.forName("com.intellij.openapi.updateSettings.impl.PluginDownloader")
        val createMethod = downloaderClass.getMethod(
            "createDownloader",
            Long::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        val downloader = createMethod.invoke(null, 32417L, "", version, true)
        if (downloader != null) {
            val installMethod = downloaderClass.getMethod("installPluginAndUpdateOld")
            installMethod.invoke(downloader)
            // Prompt restart
            com.intellij.openapi.application.ApplicationManager.getApplication().restart()
        }
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
