package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerStockOperation
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerActionUtil
import com.vermouthx.stocker.utils.StockerPinyinUtil
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import com.vermouthx.stocker.utils.StockerSuggestHttpUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

class StockerSuggestionDialog(val project: Project?) : DialogWrapper(project) {

    private val log = Logger.getInstance(StockerSuggestionDialog::class.java)
    private val service: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val setting = StockerSetting.instance

    private var suggestions: List<StockerSuggestion> = emptyList()
    private var searchTask: ScheduledFuture<*>? = null
    private var isLoading: Boolean = false
    private var searchMode: SearchMode = SearchMode.STOCKS
    private var selectedGroup: String? = null
    private var batchResults: List<BatchResult>? = null

    private data class BatchResult(
        val rawCode: String,
        val market: StockerMarketType?,
        val normalizedCode: String,
        val success: Boolean,
        val message: String
    )

    private enum class SearchMode(val displayName: String) {
        STOCKS("Stocks (CN/HK/US)"),
        CRYPTO("Crypto")
    }

    init {
        title = "Search Assets"
        init()
    }

    override fun createCenterPanel(): DialogPanel {
        val dialogPanel = DialogPanel(BorderLayout())
        val searchTextField = SearchTextField(true)
        val scrollPane = JBScrollPane()
        
        // Function to perform search
        val performSearch = { text: String ->
            // Cancel any pending search task
            searchTask?.cancel(false)

            if (text.isEmpty()) {
                isLoading = false
                suggestions = emptyList()
                batchResults = null
                SwingUtilities.invokeLater { refreshScrollPane(scrollPane) }
            } else if (isBatchInput(text)) {
                // Batch import mode
                isLoading = true
                batchResults = null
                suggestions = emptyList()
                SwingUtilities.invokeLater { refreshScrollPane(scrollPane) }

                searchTask = service.schedule({
                    val results = processBatchImport(text)
                    SwingUtilities.invokeLater {
                        isLoading = false
                        batchResults = results
                        refreshScrollPane(scrollPane)
                    }
                }, 300, TimeUnit.MILLISECONDS)
            } else {
                // Show loading state immediately
                isLoading = true
                batchResults = null
                SwingUtilities.invokeLater { refreshScrollPane(scrollPane) }

                // Debounce: schedule search after 300ms delay
                searchTask = service.schedule({
                    try {
                        // Use appropriate provider and filter based on search mode
                        val (provider, marketTypeFilter) = if (searchMode == SearchMode.CRYPTO) {
                            setting.cryptoQuoteProvider to setOf(StockerMarketType.Crypto)
                        } else {
                            setting.quoteProvider to setOf(
                                StockerMarketType.AShare,
                                StockerMarketType.HKStocks,
                                StockerMarketType.USStocks
                            )
                        }

                        // Fetch filtered suggestions
                        val filteredSuggestions = StockerSuggestHttpUtil.suggest(text, provider, marketTypeFilter)

                        // Update UI on EDT
                        SwingUtilities.invokeLater {
                            isLoading = false
                            suggestions = filteredSuggestions
                            refreshScrollPane(scrollPane)
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to fetch suggestions", e)
                        SwingUtilities.invokeLater {
                            isLoading = false
                            refreshScrollPane(scrollPane)
                        }
                    }
                }, 300, TimeUnit.MILLISECONDS)
            }
        }
        
        // Create mode selector panel
        val modePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        modePanel.add(JLabel("Search for:"))
        val modeComboBox = ComboBox(arrayOf(SearchMode.STOCKS.displayName, SearchMode.CRYPTO.displayName))
        modeComboBox.selectedIndex = 0
        modeComboBox.addActionListener {
            searchMode = when (modeComboBox.selectedIndex) {
                0 -> SearchMode.STOCKS
                1 -> SearchMode.CRYPTO
                else -> SearchMode.STOCKS
            }
            // Trigger search again with new mode if there's text
            val text = searchTextField.text.trim()
            if (text.isNotEmpty()) {
                performSearch(text)
            }
        }
        modePanel.add(modeComboBox)

        // Group selector
        modePanel.add(javax.swing.Box.createHorizontalStrut(16))
        modePanel.add(JLabel(StockerBundle.msg("manage.group.label")))
        val allLabel = StockerBundle.msg("manage.group.all")
        val groupNames = setting.stockGroupNames.toMutableList()
        val displayNames = mutableListOf(allLabel).apply { addAll(groupNames) }
        val groupComboBox = ComboBox(displayNames.toTypedArray())
        if (setting.lastSelectedGroup.isNotEmpty() && groupNames.contains(setting.lastSelectedGroup)) {
            groupComboBox.selectedIndex = groupNames.indexOf(setting.lastSelectedGroup) + 1
            selectedGroup = setting.lastSelectedGroup
        } else {
            groupComboBox.selectedIndex = 0
            selectedGroup = null
        }
        groupComboBox.addActionListener {
            val idx = groupComboBox.selectedIndex
            if (idx == 0) {
                selectedGroup = null
                setting.lastSelectedGroup = ""
            } else if (idx > 0 && idx - 1 < groupNames.size) {
                selectedGroup = groupNames[idx - 1]
                setting.lastSelectedGroup = groupNames[idx - 1]
            }
        }
        modePanel.add(groupComboBox)

        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val text = searchTextField.text.trim()
                performSearch(text)
            }
        })

        // Initialize with empty state instead of hardcoded search
        refreshScrollPane(scrollPane)

        // Create top panel with mode selector and search field
        val topPanel = JPanel(BorderLayout())
        topPanel.add(modePanel, BorderLayout.NORTH)
        searchTextField.border = BorderFactory.createEmptyBorder(8, 0, 8, 0)
        topPanel.add(searchTextField, BorderLayout.CENTER)
        
        dialogPanel.add(topPanel, BorderLayout.NORTH)
        dialogPanel.add(scrollPane, BorderLayout.CENTER)
        dialogPanel.preferredSize = Dimension(550, 500)
        return dialogPanel
    }

    override fun createActions(): Array<Action> {
        return emptyArray()
    }

    override fun dispose() {
        try {
            searchTask?.cancel(true)
            service.shutdown()
            if (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                service.shutdownNow()
            }
        } catch (e: InterruptedException) {
            service.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            StockerSuggestHttpUtil.closeConnections()
        }
        super.dispose()
    }

    private fun isBatchInput(text: String): Boolean {
        // Contains commas (English or Chinese) → definitely batch
        if (text.contains(",") || text.contains("、")) return true
        // Split by any separator (space, comma, Chinese comma)
        val tokens = text.trim().split("[\\s,、]+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val codePattern = Regex("(?i)^(sh|sz|bj)?\\d{5,6}$|^[A-Z]{2,6}(USD|USDT)$")
            // Batch if all tokens are codes OR all tokens contain Chinese characters (stock names)
            return tokens.all { codePattern.matches(it.trim()) } ||
                    tokens.all { containsChinese(it) }
        }
        return false
    }

    private fun parseBatchCodes(text: String): List<String> {
        // Split by commas (English/Chinese) or whitespace
        return text.trim().split("[\\s,、]+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { char ->
            val ub = Character.UnicodeBlock.of(char)
            ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    ub === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                    ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                    ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                    ub === Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                    ub === Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
                    ub === Character.UnicodeBlock.GENERAL_PUNCTUATION
        }
    }

    private fun normalizeCode(raw: String): Pair<StockerMarketType, String> {
        val upper = raw.uppercase()
        return when {
            upper.startsWith("SH") -> StockerMarketType.AShare to upper
            upper.startsWith("SZ") -> StockerMarketType.AShare to upper
            upper.startsWith("BJ") -> StockerMarketType.AShare to upper
            upper.matches(Regex("^\\d{6}$")) -> {
                // 6-digit code without prefix → infer market
                val market = when {
                    upper.startsWith("6") -> "SH"
                    upper.startsWith("0") || upper.startsWith("3") -> "SZ"
                    upper.startsWith("4") || upper.startsWith("8") || upper.startsWith("9") -> "BJ"
                    else -> "SZ"
                }
                StockerMarketType.AShare to "$market$upper"
            }
            upper.matches(Regex("^[A-Z]{2,6}(USD|USDT)$")) -> StockerMarketType.Crypto to "BTC$upper"
            upper.matches(Regex("^[A-Z]{1,5}$")) -> StockerMarketType.USStocks to upper
            else -> StockerMarketType.AShare to upper
        }
    }

    private fun processBatchImport(text: String): List<BatchResult> {
        val rawCodes = parseBatchCodes(text)
        val results = mutableListOf<BatchResult>()
        val groupName = selectedGroup ?: setting.lastSelectedGroup.takeIf { it.isNotEmpty() }

        for (raw in rawCodes) {
            try {
                // Detect whether the token is a Chinese stock name or a code
                if (containsChinese(raw)) {
                    // Look up the code by Chinese name via the suggestion API
                    val suggestionResult = lookupChineseName(raw, groupName)
                    results.add(BatchResult(raw, suggestionResult.market, suggestionResult.code, suggestionResult.success, suggestionResult.message))
                } else {
                    val (market, normalizedCode) = normalizeCode(raw)
                    if (setting.containsCode(normalizedCode)) {
                        if (groupName != null && setting.stockGroupNames.contains(groupName) && !setting.getGroupStocks(groupName).contains(normalizedCode)) {
                            setting.assignStockToGroup(normalizedCode, groupName)
                            results.add(BatchResult(raw, market, normalizedCode, true, StockerBundle.msg("batch.add.success")))
                        } else {
                            results.add(BatchResult(raw, market, normalizedCode, true, StockerBundle.msg("batch.already.exists")))
                        }
                        continue
                    }
                    // Validate via HTTP
                    val provider = if (market == StockerMarketType.Crypto) {
                        setting.cryptoQuoteProvider
                    } else {
                        setting.quoteProvider
                    }
                    val valid = StockerQuoteHttpUtil.validateCode(market, provider, normalizedCode)
                    if (valid) {
                        val suggest = StockerSuggestion(normalizedCode, normalizedCode, market)
                        StockerActionUtil.addStock(market, suggest, project, groupName)
                        results.add(BatchResult(raw, market, normalizedCode, true, StockerBundle.msg("batch.add.success")))
                    } else {
                        results.add(BatchResult(raw, market, normalizedCode, false, StockerBundle.msg("batch.invalid.code")))
                    }
                }
            } catch (e: Exception) {
                log.warn("Batch import failed for $raw", e)
                results.add(BatchResult(raw, null, raw, false, StockerBundle.msg("batch.validation.failed")))
            }
        }

        // Restart scheduler after batch add
        val myApplication = StockerAppManager.myApplication(project)
        if (myApplication != null) {
            myApplication.shutdownThenClear()
            myApplication.schedule()
        }

        return results
    }

    private data class NameLookupResult(
        val market: StockerMarketType?,
        val code: String,
        val success: Boolean,
        val message: String
    )

    private fun lookupChineseName(name: String, groupName: String?): NameLookupResult {
        val provider = setting.quoteProvider
        val marketTypeFilter = setOf(
            StockerMarketType.AShare,
            StockerMarketType.HKStocks,
            StockerMarketType.USStocks
        )
        return try {
            val suggestions = StockerSuggestHttpUtil.suggest(name, provider, marketTypeFilter)
            // Find the best match: exact name match first, then first result
            val match = suggestions.firstOrNull { it.name == name }
                ?: suggestions.firstOrNull()
            if (match != null) {
                if (setting.containsCode(match.code)) {
                    if (groupName != null && setting.stockGroupNames.contains(groupName) && !setting.getGroupStocks(groupName).contains(match.code)) {
                        setting.assignStockToGroup(match.code, groupName)
                        NameLookupResult(match.market, match.code, true, StockerBundle.msg("batch.add.success"))
                    } else {
                        NameLookupResult(match.market, match.code, true, StockerBundle.msg("batch.already.exists"))
                    }
                } else {
                    StockerActionUtil.addStock(match.market, match, project, groupName)
                    NameLookupResult(match.market, match.code, true, StockerBundle.msg("batch.add.success"))
                }
            } else {
                NameLookupResult(null, name, false, StockerBundle.msg("batch.name.not.found"))
            }
        } catch (e: Exception) {
            log.warn("Chinese name lookup failed for $name", e)
            NameLookupResult(null, name, false, StockerBundle.msg("batch.validation.failed"))
        }
    }

    private fun updateButtonText(button: JButton, suggestion: StockerSuggestion) {
        val currentGroupForBtn = selectedGroup ?: setting.lastSelectedGroup.takeIf { it.isNotEmpty() }
        button.text = if (currentGroupForBtn != null && setting.getGroupStocks(currentGroupForBtn).contains(suggestion.code)) {
            StockerStockOperation.STOCK_DELETE.operation
        } else if (currentGroupForBtn == null && StockerSetting.instance.containsCode(suggestion.code)) {
            StockerStockOperation.STOCK_DELETE.operation
        } else {
            StockerStockOperation.STOCK_ADD.operation
        }
    }

    private fun refreshScrollPane(scrollPane: JBScrollPane) {
        val batchRes = batchResults
        val contentPanel = if (isLoading) {
            panel {
                row {
                    label("Searching...").align(AlignX.CENTER)
                }
            }.withBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8))
        } else if (batchRes != null) {
            // Show batch import results
            panel {
                row {
                    label("Code").bold()
                        .applyToComponent { minimumSize = java.awt.Dimension(120, 0); preferredSize = java.awt.Dimension(120, preferredSize.height) }
                    label("Market").bold()
                        .applyToComponent { minimumSize = java.awt.Dimension(80, 0); preferredSize = java.awt.Dimension(80, preferredSize.height) }
                    label("Status").bold()
                        .applyToComponent { minimumSize = java.awt.Dimension(200, 0); preferredSize = java.awt.Dimension(200, preferredSize.height) }
                }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)
                separator()
                batchRes.forEach { result ->
                    row {
                        label(result.normalizedCode)
                            .applyToComponent { minimumSize = java.awt.Dimension(120, 0); preferredSize = java.awt.Dimension(120, preferredSize.height) }
                        label(result.market?.title ?: "-")
                            .applyToComponent { minimumSize = java.awt.Dimension(80, 0); preferredSize = java.awt.Dimension(80, preferredSize.height) }
                        label(if (result.success) "✓ ${result.message}" else "✗ ${result.message}")
                            .applyToComponent {
                                minimumSize = java.awt.Dimension(200, 0)
                                preferredSize = java.awt.Dimension(200, preferredSize.height)
                                foreground = if (result.success) javax.swing.UIManager.getColor("Label.foreground") else com.intellij.ui.JBColor.RED
                            }
                    }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)
                }
            }.withBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8))
        } else if (suggestions.isEmpty()) {
            val message = when (searchMode) {
                SearchMode.STOCKS -> "Type to search for stocks (CN/HK/US)..."
                SearchMode.CRYPTO -> "Type to search for crypto..."
            }
            panel {
                row {
                    label(message).align(AlignX.CENTER)
                }
            }.withBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8))
        } else {
            panel {
                // Add header row
                row {
                    label("Code").bold()
                        .applyToComponent {
                            minimumSize = java.awt.Dimension(100, 0)
                            preferredSize = java.awt.Dimension(100, preferredSize.height)
                        }
                    label("Name").bold()
                        .applyToComponent {
                            minimumSize = java.awt.Dimension(300, 0)
                            preferredSize = java.awt.Dimension(300, preferredSize.height)
                        }
                    label("Action").bold()
                        .applyToComponent {
                            minimumSize = java.awt.Dimension(100, 0)
                            preferredSize = java.awt.Dimension(100, preferredSize.height)
                        }
                }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)
                
                separator()
                
                // Add suggestion rows
                suggestions.forEach { suggestion ->
                    val actionButton = JButton()
                    val displayName = setting.getDisplayName(suggestion.code, suggestion.name)
                    
                    row {
                        label(suggestion.code)
                            .applyToComponent {
                                minimumSize = java.awt.Dimension(100, 0)
                                preferredSize = java.awt.Dimension(100, preferredSize.height)
                            }
                        label(
                            if (displayName.length <= 30) {
                                displayName
                            } else {
                                "${displayName.substring(0, 30)}..."
                            }
                        ).applyToComponent {
                            minimumSize = java.awt.Dimension(300, 0)
                            preferredSize = java.awt.Dimension(300, preferredSize.height)
                        }
                        val currentGroupForBtn = selectedGroup ?: setting.lastSelectedGroup.takeIf { it.isNotEmpty() }
                        if (currentGroupForBtn != null && setting.getGroupStocks(currentGroupForBtn).contains(suggestion.code)) {
                            actionButton.text = StockerStockOperation.STOCK_DELETE.operation
                        } else if (currentGroupForBtn == null && StockerSetting.instance.containsCode(suggestion.code)) {
                            actionButton.text = StockerStockOperation.STOCK_DELETE.operation
                        } else {
                            actionButton.text = StockerStockOperation.STOCK_ADD.operation
                        }
                        actionButton.addActionListener {
                            val myApplication = StockerAppManager.myApplication(project)
                            if (myApplication != null) {
                                myApplication.shutdownThenClear()
                                when (StockerStockOperation.mapOf(actionButton.text)) {
                                    StockerStockOperation.STOCK_ADD -> {
                                        val groupName = selectedGroup ?: setting.lastSelectedGroup.takeIf { it.isNotEmpty() }
                                        StockerActionUtil.addStock(suggestion.market, suggestion, project, groupName)
                                        updateButtonText(actionButton, suggestion)
                                    }

                                    StockerStockOperation.STOCK_DELETE -> {
                                        val currentGroupForBtn = selectedGroup ?: setting.lastSelectedGroup.takeIf { it.isNotEmpty() }
                                        if (currentGroupForBtn != null) {
                                            // Remove from specific group only
                                            setting.removeStockFromGroup(suggestion.code, currentGroupForBtn)
                                            if (!setting.isInAnyGroup(suggestion.code)) {
                                                StockerActionUtil.removeStock(suggestion.market, suggestion, project)
                                            }
                                        } else {
                                            // "All" view: remove from all groups first, then market list
                                            setting.removeStockFromGroup(suggestion.code)
                                            StockerActionUtil.removeStock(suggestion.market, suggestion, project)
                                        }
                                        updateButtonText(actionButton, suggestion)
                                    }

                                    else -> {
                                        myApplication.schedule()
                                        return@addActionListener
                                    }
                                }
                                myApplication.schedule()
                            }
                        }
                        cell(actionButton)
                            .applyToComponent {
                                minimumSize = java.awt.Dimension(100, 0)
                                preferredSize = java.awt.Dimension(100, preferredSize.height)
                            }
                    }.bottomGap(com.intellij.ui.dsl.builder.BottomGap.SMALL)
                }
            }.withBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8))
        }
        
        scrollPane.setViewportView(contentPanel)
        // Force UI refresh to prevent flickering
        scrollPane.revalidate()
        scrollPane.repaint()
    }

}
