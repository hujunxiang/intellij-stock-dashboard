package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerTableColumn
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerPinyinUtil
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import com.vermouthx.stocker.views.StockerTableView
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.ListSelectionListener

class StockerManagementDialog(val project: Project?) : DialogWrapper(project) {

    private val log = Logger.getInstance(StockerManagementDialog::class.java)
    private val setting = StockerSetting.instance

    private val tabMap: MutableMap<StockerMarketType, JPanel> = mutableMapOf()
    private val currentSymbols: MutableMap<StockerMarketType, DefaultListModel<StockerQuote>> = mutableMapOf()
    private var currentMarketSelection: StockerMarketType = StockerMarketType.AShare
    private var selectedGroup: String? = null
    private var groupListModel: DefaultListModel<String> = DefaultListModel()
    private var groupList: JBList<String>? = null
    private var moveUpBtn: JButton? = null
    private var moveDownBtn: JButton? = null
    private var pinTopBtn: JButton? = null
    private var pinBottomBtn: JButton? = null
    private var renameBtn: JButton? = null

    // Bumped on every reloadAllTabs(); in-flight async market loads capture the
    // token at dispatch time and abandon their render when it has changed — this
    // stops a newly-created (empty) group from being repainted with a previous
    // group's quotes that resolve late.
    private var loadToken = 0

    init {
        title = StockerBundle.msg("action.manage.favorite.stocks")
        isModal = true
        init()
    }

    override fun createCenterPanel(): DialogPanel {
        // Restore last selected group
        if (setting.lastSelectedGroup.isNotEmpty() && setting.stockGroupNames.contains(setting.lastSelectedGroup)) {
            selectedGroup = setting.lastSelectedGroup
        }

        // Left panel: group list
        val leftPanel = createGroupListPanel()

        // Right panel: market tabs
        val tabbedPane = JBTabbedPane()
        val enabledMarkets = StockerMarketType.entries.filter { setting.isMarketEnabled(it) }
        for (market in enabledMarkets) {
            tabbedPane.addTab(market.title, createTabContent(market))
        }

        tabbedPane.addChangeListener {
            val idx = tabbedPane.selectedIndex
            if (idx in enabledMarkets.indices) {
                currentMarketSelection = enabledMarkets[idx]
            }
        }

        loadAllMarketData()
        tabbedPane.selectedIndex = 0

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, tabbedPane)
        splitPane.resizeWeight = 0.0
        splitPane.dividerLocation = 200
        // Allow the left panel to shrink below the toolbar buttons' natural
        // minimum width — otherwise the split pane clamps the divider at the
        // toolbar's preferred width and dividerLocation is ignored.
        leftPanel.minimumSize = Dimension(0, 0)

        return panel {
            row {
                cell(splitPane).align(AlignX.FILL).align(AlignY.FILL)
                    .applyToComponent { preferredSize = java.awt.Dimension(750, 450) }
            }
        }
    }

    private fun createGroupListPanel(): JPanel {
        populateGroupListModel()

        val list = JBList(groupListModel)
        groupList = list
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION

        if (selectedGroup != null) {
            val idx = getGroupDisplayNames().indexOf(selectedGroup!!)
            if (idx >= 0) list.selectedIndex = idx
        } else {
            list.selectedIndex = 0
        }
        val movable = selectedGroup != null
        moveUpBtn?.isEnabled = movable
        moveDownBtn?.isEnabled = movable
        pinTopBtn?.isEnabled = movable
        pinBottomBtn?.isEnabled = movable
        renameBtn?.isEnabled = movable

        list.addListSelectionListener(ListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val idx = list.selectedIndex
                val names = getGroupDisplayNames()
                selectedGroup = if (idx <= 0 || idx >= names.size) null else names[idx]
                setting.lastSelectedGroup = selectedGroup ?: ""
                val movable = selectedGroup != null
                moveUpBtn?.isEnabled = movable
                moveDownBtn?.isEnabled = movable
                pinTopBtn?.isEnabled = movable
                pinBottomBtn?.isEnabled = movable
                renameBtn?.isEnabled = movable
                reloadAllTabs()
            }
        })

        // Double-click to rename group
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val idx = list.locationToIndex(e.point)
                    if (idx > 0) { // idx 0 = "全部", can't rename
                        renameGroup(list, idx)
                    }
                }
            }

            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) showPopupMenu(list, e)
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) showPopupMenu(list, e)
            }
        })

        val scrollPane = JBScrollPane(list)

        // Button panel
        val btnPanel = JPanel()
        btnPanel.layout = BoxLayout(btnPanel, BoxLayout.Y_AXIS)

        val addGroupBtn = JButton(StockerBundle.msg("manage.group.add"))
        addGroupBtn.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        addGroupBtn.addActionListener {
            val name = Messages.showInputDialog(
                list,
                StockerBundle.msg("manage.group.add.prompt"),
                StockerBundle.msg("manage.group.add.title"),
                Messages.getQuestionIcon()
            )
            if (!name.isNullOrBlank()) {
                if (setting.addGroup(name)) {
                    refreshGroupList()
                    selectedGroup = name
                    setting.lastSelectedGroup = name
                    val newIdx = getGroupDisplayNames().indexOf(name)
                    if (newIdx >= 0) list.selectedIndex = newIdx
                    reloadAllTabs()
                }
            }
        }

        val deleteGroupBtn = JButton(StockerBundle.msg("manage.group.delete"))
        deleteGroupBtn.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        deleteGroupBtn.addActionListener {
            if (selectedGroup == null) return@addActionListener
            val result = Messages.showYesNoDialog(
                list,
                StockerBundle.msg("manage.group.delete.confirm", selectedGroup!!),
                StockerBundle.msg("manage.group.delete"),
                Messages.getQuestionIcon()
            )
            if (result == Messages.YES) {
                setting.removeGroup(selectedGroup!!)
                selectedGroup = null
                setting.lastSelectedGroup = ""
                refreshGroupList()
                list.selectedIndex = 0
                reloadAllTabs()
            }
        }

        // Icon-only buttons matching the right-side ToolbarDecorator style.
        moveUpBtn = JButton(com.intellij.icons.AllIcons.Actions.MoveUp).apply {
            toolTipText = StockerBundle.msg("manage.group.move.up")
            addActionListener {
                val g = selectedGroup ?: return@addActionListener
                if (setting.moveGroupUp(g)) {
                    refreshGroupList()
                    list.selectedIndex = getGroupDisplayNames().indexOf(g)
                    list.requestFocusInWindow()
                    reloadAllTabs()
                }
            }
        }

        moveDownBtn = JButton(com.intellij.icons.AllIcons.Actions.MoveDown).apply {
            toolTipText = StockerBundle.msg("manage.group.move.down")
            addActionListener {
                val g = selectedGroup ?: return@addActionListener
                if (setting.moveGroupDown(g)) {
                    refreshGroupList()
                    list.selectedIndex = getGroupDisplayNames().indexOf(g)
                    list.requestFocusInWindow()
                    reloadAllTabs()
                }
            }
        }

        pinTopBtn = JButton(com.intellij.icons.AllIcons.Actions.MoveToTopLeft).apply {
            toolTipText = StockerBundle.msg("manage.group.pin.top")
            addActionListener {
                val g = selectedGroup ?: return@addActionListener
                if (setting.pinGroupToTop(g)) {
                    refreshGroupList()
                    list.selectedIndex = getGroupDisplayNames().indexOf(g)
                    list.requestFocusInWindow()
                    reloadAllTabs()
                }
            }
        }

        pinBottomBtn = JButton(com.intellij.icons.AllIcons.Actions.MoveToBottomLeft).apply {
            toolTipText = StockerBundle.msg("manage.group.pin.bottom")
            addActionListener {
                val g = selectedGroup ?: return@addActionListener
                if (setting.pinGroupToBottom(g)) {
                    refreshGroupList()
                    list.selectedIndex = getGroupDisplayNames().indexOf(g)
                    list.requestFocusInWindow()
                    reloadAllTabs()
                }
            }
        }

        renameBtn = JButton(com.intellij.icons.AllIcons.Actions.EditSource).apply {
            toolTipText = StockerBundle.msg("manage.group.rename")
            addActionListener {
                val idx = list.selectedIndex
                if (idx > 0) renameGroup(list, idx)
            }
        }

        // Compact icon buttons: no margin, no border, no focus, no focus-paint.
        // focusable=false keeps focus on the list after a click — otherwise the
        // focus traversal policy would park on the next button.
        for (b in listOf(moveUpBtn, moveDownBtn, pinTopBtn, pinBottomBtn, renameBtn)) {
            b?.margin = java.awt.Insets(2, 2, 2, 2)
            b?.isBorderPainted = false
            b?.isContentAreaFilled = false
            b?.isFocusPainted = false
            b?.isFocusable = false
            b?.preferredSize = Dimension(28, 28)
        }

        // Toolbar above the group list (mirrors the right-side table toolbar)
        val toolbar = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
        toolbar.border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
        toolbar.add(moveUpBtn)
        toolbar.add(moveDownBtn)
        toolbar.add(pinTopBtn)
        toolbar.add(pinBottomBtn)
        toolbar.add(renameBtn)
        toolbar.preferredSize = Dimension(toolbar.preferredSize.width, 32)

        val clearGroupBtn = JButton(StockerBundle.msg("manage.clear.group"))
        clearGroupBtn.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        clearGroupBtn.addActionListener {
            if (selectedGroup == null) return@addActionListener
            val result = Messages.showYesNoDialog(
                list,
                StockerBundle.msg("manage.clear.group.confirm", selectedGroup!!),
                StockerBundle.msg("manage.clear.group"),
                Messages.getWarningIcon()
            )
            if (result == Messages.YES) {
                setting.removeGroup(selectedGroup!!)
                selectedGroup = null
                setting.lastSelectedGroup = ""
                refreshGroupList()
                list.selectedIndex = 0
                reloadAllTabs()
            }
        }

        val clearAllBtn = JButton(StockerBundle.msg("manage.clear.all"))
        clearAllBtn.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        clearAllBtn.addActionListener {
            val result = Messages.showYesNoDialog(
                list,
                StockerBundle.msg("manage.clear.all.confirm"),
                StockerBundle.msg("manage.clear.all"),
                Messages.getWarningIcon()
            )
            if (result == Messages.YES) {
                setting.clearAllStocks()
                selectedGroup = null
                refreshGroupList()
                list.selectedIndex = 0
                reloadAllTabs()
            }
        }

        btnPanel.add(addGroupBtn)
        btnPanel.add(deleteGroupBtn)
        btnPanel.add(Box.createVerticalStrut(8))
        btnPanel.add(clearGroupBtn)
        btnPanel.add(clearAllBtn)

        val leftInner = JPanel(BorderLayout(0, 4))
        leftInner.add(toolbar, BorderLayout.NORTH)
        leftInner.add(scrollPane, BorderLayout.CENTER)

        val panel = JPanel(BorderLayout(0, 4))
        panel.add(leftInner, BorderLayout.CENTER)
        panel.add(btnPanel, BorderLayout.SOUTH)
        panel.preferredSize = Dimension(200, 0)
        panel.minimumSize = Dimension(0, 0)
        return panel
    }

    private fun renameGroup(list: JBList<String>, idx: Int) {
        val names = getGroupDisplayNames()
        if (idx <= 0 || idx >= names.size) return
        val oldName = names[idx]
        val newName = Messages.showInputDialog(
            list,
            StockerBundle.msg("manage.group.rename.prompt"),
            StockerBundle.msg("manage.group.rename.title"),
            Messages.getQuestionIcon(),
            oldName,
            null
        )
        if (!newName.isNullOrBlank() && newName != oldName) {
            if (setting.renameGroup(oldName, newName)) {
                if (selectedGroup == oldName) selectedGroup = newName
                refreshGroupList()
                val newIdx = getGroupDisplayNames().indexOf(newName)
                if (newIdx >= 0) list.selectedIndex = newIdx
                reloadAllTabs()
            } else {
                Messages.showErrorDialog(
                    list,
                    StockerBundle.msg("manage.group.rename.duplicate"),
                    StockerBundle.msg("manage.group.rename.title")
                )
            }
        }
    }

    private fun showPopupMenu(list: JBList<String>, e: java.awt.event.MouseEvent) {
        val idx = list.locationToIndex(e.point)
        if (idx <= 0) return // idx 0 = "全部", no popup
        list.selectedIndex = idx

        val popup = JPopupMenu()
        val renameItem = JMenuItem(StockerBundle.msg("manage.group.rename"))
        renameItem.addActionListener { renameGroup(list, idx) }
        popup.add(renameItem)
        popup.show(list, e.x, e.y)
    }

    private fun getGroupDisplayNames(): List<String> {
        val names = mutableListOf(StockerBundle.msg("manage.group.all"))
        names.addAll(setting.stockGroupNames)
        return names
    }

    private fun populateGroupListModel() {
        groupListModel.clear()
        for (name in getGroupDisplayNames()) {
            groupListModel.addElement(name)
        }
    }

    private fun refreshGroupList() {
        val list = groupList ?: return
        val previousSelection = selectedGroup
        populateGroupListModel()
        if (previousSelection != null && setting.stockGroupNames.contains(previousSelection)) {
            val idx = getGroupDisplayNames().indexOf(previousSelection)
            if (idx >= 0) list.selectedIndex = idx
        } else {
            list.selectedIndex = 0
            selectedGroup = null
        }
    }

    private fun getFilteredCodes(marketType: StockerMarketType): List<String> {
        val marketList = when (marketType) {
            StockerMarketType.AShare -> setting.aShareList
            StockerMarketType.HKStocks -> setting.hkStocksList
            StockerMarketType.USStocks -> setting.usStocksList
            StockerMarketType.Crypto -> setting.cryptoList
        }
        if (selectedGroup == null) return marketList
        val groupCodes = setting.getGroupStocks(selectedGroup!!)
        return marketList.filter { groupCodes.contains(it) }
    }

    private fun loadAllMarketData() {
        loadMarketData(StockerMarketType.AShare, getFilteredCodes(StockerMarketType.AShare))
        loadMarketData(StockerMarketType.HKStocks, getFilteredCodes(StockerMarketType.HKStocks))
        loadMarketData(StockerMarketType.USStocks, getFilteredCodes(StockerMarketType.USStocks))
        loadMarketData(StockerMarketType.Crypto, getFilteredCodes(StockerMarketType.Crypto))
    }

    private fun reloadAllTabs() {
        loadToken++
        loadAllMarketData()
    }

    private fun loadMarketData(marketType: StockerMarketType, codes: List<String>) {
        val token = loadToken
        val listModel = DefaultListModel<StockerQuote>()
        currentSymbols[marketType] = listModel

        tabMap[marketType]?.let { pane ->
            showLoadingState(pane)
        }

        CompletableFuture.supplyAsync {
            try {
                val provider = if (marketType == StockerMarketType.Crypto) {
                    setting.cryptoQuoteProvider
                } else {
                    setting.quoteProvider
                }
                StockerQuoteHttpUtil.get(marketType, provider, codes)
            } catch (e: Exception) {
                log.warn("Failed to load quotes for market type $marketType", e)
                emptyList()
            }
        }.thenAccept { quotes ->
            // Discard results belonging to a superseded reload — otherwise a
            // stale future from a previously-selected group can repaint a
            // freshly-loaded (e.g. newly-created empty) group's pane.
            if (token != loadToken) return@thenAccept
            SwingUtilities.invokeLater {
                listModel.addAll(quotes)
                tabMap[marketType]?.let { pane ->
                    renderTabPane(pane, listModel)
                }
            }
        }
    }

    private fun showLoadingState(pane: JPanel) {
        pane.removeAll()
        pane.add(
            panel {
                row {
                    label("Loading...").align(AlignX.CENTER)
                }
            }, BorderLayout.CENTER
        )
        pane.revalidate()
        pane.repaint()
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : OkAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    val myApplication = StockerAppManager.myApplication(project)
                    if (myApplication != null) {
                        myApplication.shutdownThenClear()

                        if (selectedGroup == null) {
                            // Viewing "全部": rebuild market lists from displayed stocks
                            // Save original market→code mapping to re-add group stocks later
                            val originalMarketMap = mutableMapOf<String, StockerMarketType>()
                            for (marketType in StockerMarketType.entries) {
                                val list = when (marketType) {
                                    StockerMarketType.AShare -> setting.aShareList
                                    StockerMarketType.HKStocks -> setting.hkStocksList
                                    StockerMarketType.USStocks -> setting.usStocksList
                                    StockerMarketType.Crypto -> setting.cryptoList
                                }
                                list.forEach { originalMarketMap[it] = marketType }
                            }

                            val displayedCodes = mutableSetOf<String>()
                            for (marketType in StockerMarketType.entries) {
                                currentSymbols[marketType]?.let { symbols ->
                                    displayedCodes.addAll(symbols.elements().asSequence().map { it.code })
                                }
                            }

                            // Replace market lists with displayed codes
                            setting.aShareList = currentSymbols[StockerMarketType.AShare]
                                ?.elements()?.asSequence()?.map { it.code }?.toMutableList() ?: mutableListOf()
                            setting.hkStocksList = currentSymbols[StockerMarketType.HKStocks]
                                ?.elements()?.asSequence()?.map { it.code }?.toMutableList() ?: mutableListOf()
                            setting.usStocksList = currentSymbols[StockerMarketType.USStocks]
                                ?.elements()?.asSequence()?.map { it.code }?.toMutableList() ?: mutableListOf()
                            setting.cryptoList = currentSymbols[StockerMarketType.Crypto]
                                ?.elements()?.asSequence()?.map { it.code }?.toMutableList() ?: mutableListOf()

                            // Re-add group stocks that aren't displayed
                            // (added while viewing a specific group, not yet in "全部" view)
                            val allGroupCodes = setting.stockGroupMap.values.flatten().toSet()
                            for (code in allGroupCodes) {
                                if (!displayedCodes.contains(code) && !setting.containsCode(code)) {
                                    val market = originalMarketMap[code]
                                    if (market != null) {
                                        when (market) {
                                            StockerMarketType.AShare -> setting.aShareList.add(code)
                                            StockerMarketType.HKStocks -> setting.hkStocksList.add(code)
                                            StockerMarketType.USStocks -> setting.usStocksList.add(code)
                                            StockerMarketType.Crypto -> setting.cryptoList.add(code)
                                        }
                                    }
                                }
                            }
                            // Clean up group map but preserve empty groups (user may have just created them)
                            for ((_, codes) in setting.stockGroupMap) {
                                codes.removeAll { !displayedCodes.contains(it) && !setting.containsCode(it) }
                            }
                        } else {
                            // Viewing a specific group: only update this group, don't touch market lists
                            val displayedCodes = mutableSetOf<String>()
                            for (marketType in StockerMarketType.entries) {
                                currentSymbols[marketType]?.let { symbols ->
                                    displayedCodes.addAll(symbols.elements().asSequence().map { it.code })
                                }
                            }
                            val existingGroupCodes = setting.getGroupStocks(selectedGroup!!).toMutableSet()
                            val mergedCodes = existingGroupCodes + displayedCodes
                            setting.stockGroupMap[selectedGroup!!] = mergedCodes.toMutableList()

                            // Add new stocks to market lists if not already present
                            for (code in mergedCodes) {
                                if (!setting.containsCode(code)) {
                                    for (marketType in StockerMarketType.entries) {
                                        val model = currentSymbols[marketType] ?: continue
                                        if (model.elements().asSequence().any { it.code == code }) {
                                            when (marketType) {
                                                StockerMarketType.AShare -> setting.aShareList.add(code)
                                                StockerMarketType.HKStocks -> setting.hkStocksList.add(code)
                                                StockerMarketType.USStocks -> setting.usStocksList.add(code)
                                                StockerMarketType.Crypto -> setting.cryptoList.add(code)
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                        }

                        myApplication.schedule()
                    }
                    com.vermouthx.stocker.views.windows.StockerToolWindow.refreshGroupButtons(project)
                    super.actionPerformed(e)
                }
            }, cancelAction
        )
    }

    private fun createTabContent(marketType: StockerMarketType): JComponent {
        val pane = JPanel(BorderLayout())
        tabMap[marketType] = pane
        return panel {
            row {
                cell(pane).align(AlignX.FILL).align(AlignY.FILL)
            }
        }
    }

    private fun renderTabPane(pane: JPanel, listModel: DefaultListModel<StockerQuote>) {
        pane.removeAll()

        val list = JBList(listModel)
        list.cellRenderer = ListCellRenderer<StockerQuote> { jList, symbol, _, isSelected, _ ->
            val originalName = if (setting.displayNameWithPinyin) {
                StockerPinyinUtil.toPinyin(symbol.name)
            } else {
                symbol.name
            }

            val customName = setting.getCustomName(symbol.code)
            val costPrice = setting.getCostPrice(symbol.code)
            val holdings = setting.getHoldings(symbol.code)

            val panel = panel {
                row {
                    label(symbol.code)
                        .applyToComponent {
                            minimumSize = Dimension(80, 0)
                            preferredSize = Dimension(80, preferredSize.height)
                        }
                    label(
                        if (originalName.length <= 25) originalName else "${originalName.substring(0, 25)}..."
                    ).applyToComponent {
                        minimumSize = Dimension(150, 0)
                        preferredSize = Dimension(150, preferredSize.height)
                    }
                    label(
                        customName?.let { if (it.length <= 15) it else "${it.substring(0, 15)}..." } ?: "-"
                    ).applyToComponent {
                        minimumSize = Dimension(120, 0)
                        preferredSize = Dimension(120, preferredSize.height)
                    }
                    label(costPrice?.let { String.format("%.3f", it) } ?: "-")
                        .applyToComponent {
                            minimumSize = Dimension(80, 0)
                            preferredSize = Dimension(80, preferredSize.height)
                        }
                    label(holdings?.toString() ?: "-")
                        .applyToComponent {
                            minimumSize = Dimension(80, 0)
                            preferredSize = Dimension(80, preferredSize.height)
                        }
                }
            }.withBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8))

            if (isSelected) {
                panel.background = jList.selectionBackground
                panel.foreground = jList.selectionForeground
                panel.components.filterIsInstance<javax.swing.JLabel>().forEach {
                    it.foreground = jList.selectionForeground
                }
            }
            panel
        }

        val headerPanel = panel {
            row {
                label(StockerTableColumn.SYMBOL.title).bold()
                    .applyToComponent { minimumSize = Dimension(80, 0); preferredSize = Dimension(80, preferredSize.height) }
                label(StockerTableColumn.NAME.title).bold()
                    .applyToComponent { minimumSize = Dimension(150, 0); preferredSize = Dimension(150, preferredSize.height) }
                label(StockerBundle.msg("column.custom.name")).bold()
                    .applyToComponent { minimumSize = Dimension(120, 0); preferredSize = Dimension(120, preferredSize.height) }
                label(StockerTableColumn.COST_PRICE.title).bold()
                    .applyToComponent { minimumSize = Dimension(80, 0); preferredSize = Dimension(80, preferredSize.height) }
                label(StockerTableColumn.HOLDINGS.title).bold()
                    .applyToComponent { minimumSize = Dimension(80, 0); preferredSize = Dimension(80, preferredSize.height) }
            }
        }.withBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ))

        val decorator = ToolbarDecorator.createDecorator(list)
            .setAddAction { _ ->
                val searchDialog = StockerSuggestionDialog(project)
                searchDialog.show()
                reloadAllTabs()
            }
            .setRemoveAction { _ ->
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0) {
                    val selectedQuote = listModel.getElementAt(selectedIndex)
                    val code = selectedQuote.code
                    if (selectedGroup != null) {
                        // Viewing a specific group: remove from this group only
                        setting.removeStockFromGroup(code, selectedGroup!!)
                        // Only remove from market list if not in any other group
                        if (!setting.isInAnyGroup(code)) {
                            val market = setting.marketOf(code)
                            if (market != null) {
                                setting.removeCode(market, code)
                            }
                        }
                    } else {
                        // Viewing "全部": remove from all groups and market list
                        setting.removeStockFromGroup(code)
                        val market = setting.marketOf(code)
                        if (market != null) {
                            setting.removeCode(market, code)
                        }
                    }
                    reloadAllTabs()
                }
            }
            .setEditAction { _ ->
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0) {
                    val selectedQuote = listModel.getElementAt(selectedIndex)
                    val currentCustomName = setting.getCustomName(selectedQuote.code)
                    val currentCostPrice = setting.getCostPrice(selectedQuote.code)
                    val currentHoldings = setting.getHoldings(selectedQuote.code)

                    val nameField = JTextField(currentCustomName ?: "", 20)
                    val costPriceField = JTextField(currentCostPrice?.let { String.format("%.3f", it) } ?: "", 20)
                    val holdingsField = JTextField(currentHoldings?.toString() ?: "", 20)

                    val editPanel = panel {
                        row {
                            label(StockerBundle.msg("edit.stock.custom.name")).widthGroup("editLabels")
                            cell(nameField)
                        }.layout(RowLayout.LABEL_ALIGNED)
                        row {
                            label(StockerBundle.msg("edit.stock.cost.price")).widthGroup("editLabels")
                            cell(costPriceField)
                        }.layout(RowLayout.LABEL_ALIGNED)
                        row {
                            label(StockerBundle.msg("edit.stock.holdings")).widthGroup("editLabels")
                            cell(holdingsField)
                        }.layout(RowLayout.LABEL_ALIGNED)
                    }

                    val result = JOptionPane.showConfirmDialog(
                        pane, editPanel, StockerBundle.msg("edit.stock.dialog.title", selectedQuote.code),
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
                    )

                    if (result == JOptionPane.OK_OPTION) {
                        val newName = nameField.text.trim()
                        if (newName.isNotBlank()) {
                            setting.setCustomName(selectedQuote.code, newName)
                        } else if (currentCustomName != null) {
                            setting.removeCustomName(selectedQuote.code)
                        }

                        val costPriceText = costPriceField.text.trim()
                        if (costPriceText.isNotBlank()) {
                            try { setting.setCostPrice(selectedQuote.code, costPriceText.toDouble()) } catch (_: NumberFormatException) {}
                        } else if (currentCostPrice != null) {
                            setting.removeCostPrice(selectedQuote.code)
                        }

                        val holdingsText = holdingsField.text.trim()
                        if (holdingsText.isNotBlank()) {
                            try { setting.setHoldings(selectedQuote.code, holdingsText.toInt()) } catch (_: NumberFormatException) {}
                        } else if (currentHoldings != null) {
                            setting.removeHoldings(selectedQuote.code)
                        }

                        StockerTableView.refreshAllFinancialColumns()
                        list.repaint()
                    }
                }
            }
            .setEditActionUpdater { list.selectedIndex >= 0 }

        val decoratedPanel = decorator.createPanel()
        pane.add(headerPanel, BorderLayout.NORTH)
        pane.add(decoratedPanel, BorderLayout.CENTER)

        pane.revalidate()
        pane.repaint()
    }

}
