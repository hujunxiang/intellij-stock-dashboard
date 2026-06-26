package com.vermouthx.stocker.views.windows

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.messages.MessageBusConnection
import com.vermouthx.stocker.StockerApp
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.listeners.StockerQuoteDeleteListener
import com.vermouthx.stocker.listeners.StockerQuoteDeleteNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteReloadListener
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateListener
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class StockerToolWindow : ToolWindowFactory {

    companion object {
        private var instance: StockerToolWindow? = null

        fun refreshGroupButtons() {
            instance?.refreshGroupButtons0()
        }

        fun rebuildTabs() {
            instance?.rebuildTabs0()
        }
    }

    private val messageBus = ApplicationManager.getApplication().messageBus

    private lateinit var allView: StockerSimpleToolWindow
    private var tabViewMap: MutableMap<StockerMarketType, StockerSimpleToolWindow> = mutableMapOf()
    private lateinit var myApplication: StockerApp
    private val messageBusConnections = mutableListOf<MessageBusConnection>()
    private lateinit var allUpdateListeners: List<StockerQuoteUpdateListener>
    private lateinit var groupPanel: JPanel
    private lateinit var tabbedPane: JBTabbedPane

    override fun init(toolWindow: ToolWindow) {
        super.init(toolWindow)
        instance = this
        allView = StockerSimpleToolWindow()
        myApplication = StockerApp()
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val contentFactory = ContentFactory.getInstance()

        val disposable = Disposer.newDisposable("StockerToolWindow")
        toolWindow.disposable.let { Disposer.register(it, disposable) }

        // Create tabbed pane (will be populated by rebuildTabs)
        tabbedPane = JBTabbedPane()

        // Create group selector panel at the top
        groupPanel = createGroupPanel()

        // Main panel: group selector on top, market tabs below
        val mainPanel = JPanel(BorderLayout()).apply {
            add(groupPanel, BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }

        val content = contentFactory.createContent(mainPanel, "", false)
        contentManager.addContent(content)

        // Build initial tabs and subscriptions
        rebuildTabs0()

        Disposer.register(disposable) {
            cleanup()
        }

        StockerAppManager.register(project, myApplication)
        myApplication.schedule()
    }

    private fun rebuildTabs0() {
        val setting = StockerSetting.instance

        // Disconnect existing message bus connections
        messageBusConnections.forEach { it.disconnect() }
        messageBusConnections.clear()

        // Dispose old market views
        tabViewMap.values.forEach { it.tableView.dispose() }
        tabViewMap.clear()

        // Create views for enabled markets only
        val enabledMarkets = StockerMarketType.entries.filter { setting.isMarketEnabled(it) }
        enabledMarkets.forEach { market ->
            tabViewMap[market] = StockerSimpleToolWindow()
        }

        // Rebuild tabs
        tabbedPane.removeAll()
        tabbedPane.addTab("ALL", allView.component)
        tabViewMap.forEach { (market, view) ->
            tabbedPane.addTab(market.title, view.component)
        }

        // Re-subscribe to message bus
        subscribeMessage()
    }

    private fun createGroupPanel(): JPanel {
        val setting = StockerSetting.instance
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        val buttons = mutableListOf<javax.swing.JButton>()

        val selectedBg = javax.swing.UIManager.getColor("Component.accentColor")
                ?: java.awt.Color(70, 130, 220)
        val defaultBg = javax.swing.UIManager.getColor("Button.background")
                ?: javax.swing.UIManager.getColor("Panel.background")
                ?: java.awt.Color(200, 200, 200)
        val selectedFg = javax.swing.UIManager.getColor("Button.foreground")
                ?: java.awt.Color.BLACK
        val defaultFg = javax.swing.UIManager.getColor("Button.foreground")
                ?: java.awt.Color.BLACK

        fun updateButtonSelection() {
            val selectedGroup = setting.lastSelectedGroup
            buttons.forEach { btn ->
                val name = btn.getClientProperty("group.name") as? String?
                val isActive = (name == null && selectedGroup.isEmpty()) ||
                        (name == selectedGroup)
                btn.background = if (isActive) selectedBg else defaultBg
                btn.foreground = if (isActive) selectedFg else defaultFg
            }
        }

        fun buildGroupButtons() {
            buttonPanel.removeAll()
            buttons.clear()

            val groupNames = mutableListOf<String?>(null) // null = "全部"
            groupNames.addAll(setting.stockGroupNames)

            for (name in groupNames) {
                val displayName = name ?: StockerBundle.msg("manage.group.all")
                val btn = object : javax.swing.JButton(displayName) {
                    override fun paintComponent(g: java.awt.Graphics) {
                        val name = getClientProperty("group.name") as? String?
                        val selectedGroup = setting.lastSelectedGroup
                        val isActive = (name == null && selectedGroup.isEmpty()) ||
                                (name == selectedGroup)
                        background = if (isActive) selectedBg else defaultBg
                        foreground = if (isActive) selectedFg else defaultFg
                        super.paintComponent(g)
                    }
                }
                btn.isFocusPainted = false
                btn.isContentAreaFilled = true
                btn.isBorderPainted = false
                btn.putClientProperty("group.name", name)
                btn.addActionListener {
                    setting.lastSelectedGroup = name ?: ""
                    val filterValue = name ?: ""
                    allView.tableView.setLastActiveGroupFilter(filterValue)
                    tabViewMap.values.forEach { it.tableView.setLastActiveGroupFilter(filterValue) }
                    allUpdateListeners.forEach { l ->
                        l.setGroupFilter(filterValue)
                        l.refreshGroupFilter()
                    }
                    updateButtonSelection()
                }
                buttons.add(btn)
                buttonPanel.add(btn)
            }
            updateButtonSelection()

            buttonPanel.revalidate()
            buttonPanel.repaint()
        }

        buildGroupButtons()

        refreshGroupButtonsFn = { buildGroupButtons() }

        // Wrap in scroll pane with horizontal scrolling
        val scrollPane = JScrollPane(buttonPanel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            preferredSize = java.awt.Dimension(0, 52)
            border = null
        }

        val wrapper = JPanel(BorderLayout())
        wrapper.add(scrollPane, BorderLayout.CENTER)
        return wrapper
    }

    private var refreshGroupButtonsFn: (() -> Unit)? = null

    private fun refreshGroupButtons0() {
        refreshGroupButtonsFn?.invoke()
    }

    private fun cleanup() {
        allView.tableView.dispose()
        tabViewMap.values.forEach { it.tableView.dispose() }
        messageBusConnections.forEach { it.disconnect() }
        messageBusConnections.clear()
    }

    private fun subscribeMessage() {
        val allUpdateListener = StockerQuoteUpdateListener(allView.tableView)

        messageBusConnections.add(messageBus.connect().apply {
            subscribe(STOCK_ALL_QUOTE_UPDATE_TOPIC, allUpdateListener)
        })
        messageBusConnections.add(messageBus.connect().apply {
            subscribe(STOCK_ALL_QUOTE_DELETE_TOPIC, StockerQuoteDeleteListener(allView.tableView))
        })
        messageBusConnections.add(messageBus.connect().apply {
            subscribe(STOCK_ALL_QUOTE_RELOAD_TOPIC, StockerQuoteReloadListener(allView.tableView))
        })

        val updateListeners = mutableMapOf<StockerMarketType, StockerQuoteUpdateListener>()

        tabViewMap.forEach { (market, myTableView) ->
            val listener = StockerQuoteUpdateListener(myTableView.tableView)
            updateListeners[market] = listener

            val (updateTopic, deleteTopic, reloadTopic) = when (market) {
                StockerMarketType.AShare -> Triple(STOCK_CN_QUOTE_UPDATE_TOPIC, STOCK_CN_QUOTE_DELETE_TOPIC, STOCK_CN_QUOTE_RELOAD_TOPIC)
                StockerMarketType.HKStocks -> Triple(STOCK_HK_QUOTE_UPDATE_TOPIC, STOCK_HK_QUOTE_DELETE_TOPIC, STOCK_HK_QUOTE_RELOAD_TOPIC)
                StockerMarketType.USStocks -> Triple(STOCK_US_QUOTE_UPDATE_TOPIC, STOCK_US_QUOTE_DELETE_TOPIC, STOCK_US_QUOTE_RELOAD_TOPIC)
                StockerMarketType.Crypto -> Triple(CRYPTO_QUOTE_UPDATE_TOPIC, CRYPTO_QUOTE_DELETE_TOPIC, STOCK_CRYPTO_QUOTE_RELOAD_TOPIC)
            }

            messageBusConnections.add(messageBus.connect().apply { subscribe(updateTopic, listener) })
            messageBusConnections.add(messageBus.connect().apply { subscribe(deleteTopic, StockerQuoteDeleteListener(myTableView.tableView)) })
            messageBusConnections.add(messageBus.connect().apply { subscribe(reloadTopic, StockerQuoteReloadListener(myTableView.tableView)) })
        }

        allUpdateListeners = listOf(allUpdateListener) + updateListeners.values.toList()
    }
}
