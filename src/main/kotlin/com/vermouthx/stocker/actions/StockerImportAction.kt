package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerDataExporter
import com.vermouthx.stocker.views.windows.StockerToolWindow
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton

class StockerImportAction : AnAction(
    StockerBundle.msg("action.import.stocks"),
    StockerBundle.msg("action.import.stocks.description"),
    com.intellij.icons.AllIcons.Actions.Download
) {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val component = e.inputEvent?.component as? Component

        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
            .withTitle(StockerBundle.msg("import.file.title"))
        val vf = FileChooser.chooseFile(descriptor, project, null) ?: return

        val text = String(vf.contentsToByteArray(), Charsets.UTF_8)
        val data = StockerDataExporter().deserialize(text)
        if (data == null) {
            Messages.showErrorDialog(
                component,
                StockerBundle.msg("import.invalid.file"),
                StockerBundle.msg("import.dialog.title")
            )
            return
        }

        if (data.stocks.isEmpty()) {
            Messages.showInfoMessage(
                component,
                StockerBundle.msg("import.empty.file"),
                StockerBundle.msg("import.dialog.title")
            )
            return
        }

        // Ask user: merge or replace
        val choice = askMergeOrReplace(component)
        if (choice == -1) return // cancelled

        if (choice == 1) {
            setting.clearAllStocks()
        }

        // Stats
        var added = 0
        var skipped = 0

        // Pre-create all incoming groups so merge works even when the imported
        // file declares groups the user didn't have before.
        for (incomingGroup in data.groups) {
            if (incomingGroup.isNotBlank() && !setting.stockGroupNames.contains(incomingGroup)) {
                setting.addGroup(incomingGroup)
            }
        }

        // Import stocks
        for (entry in data.stocks) {
            // Normalize the code so unprefixed 6-digit A-share codes
            // (002008, 688630, 300776…) gain their SH/SZ/BJ prefix before we
            // look them up or persist them — otherwise they would be stored as
            // raw digits and silently detuplicate / mis-route later.
            val (market, normalizedCode) = normalizeImportedCode(entry.code)

            // Add to the inferred market list only if not already there. This
            // keeps "add" stats accurate, but a code that already exists in
            // the user's favourites still proceeds through group-assignment
            // below so the imported group membership is preserved even on
            // merge (a same-named group absorbs the stock).
            val alreadyInMarkets = setting.containsCode(normalizedCode)
            if (!alreadyInMarkets) {
                val list = when (market) {
                    StockerMarketType.AShare -> setting.aShareList
                    StockerMarketType.HKStocks -> setting.hkStocksList
                    StockerMarketType.USStocks -> setting.usStocksList
                    StockerMarketType.Crypto -> setting.cryptoList
                }
                list.add(normalizedCode)
                added++
            } else {
                skipped++
            }

            // Apply group info — this preserves the original grouping from
            // the export. When a group with the same name already exists the
            // stock is merged into it (assignStockToGroup de-dupes within the
            // target group and removes the stock from any other group first).
            val incomingGroup = entry.group
            if (incomingGroup != null && incomingGroup.isNotEmpty()) {
                setting.assignStockToGroup(normalizedCode, incomingGroup)
            } else {
                // No group in import → default to "导入"
                val defaultGroup = StockerBundle.msg("import.default.group")
                if (!setting.stockGroupNames.contains(defaultGroup)) {
                    setting.addGroup(defaultGroup)
                }
                setting.assignStockToGroup(normalizedCode, defaultGroup)
            }

            // Restore custom name
            entry.customName?.let { setting.setCustomName(normalizedCode, it) }
        }

        // Refresh UI
        StockerToolWindow.refreshGroupButtons()
        StockerToolWindow.rebuildTabs()
        val app = StockerAppManager.myApplication(project)
        app?.shutdownThenClear()
        app?.schedule()

        // Belt-and-squares: in case any group still carries duplicate codes
        // (legacy config or older export files), collapse them now so the UI
        // never renders the same stock twice inside one group.
        for ((name, codes) in setting.stockGroupMap) {
            val deduped = codes.distinct()
            if (deduped.size != codes.size) {
                setting.stockGroupMap[name] = deduped.toMutableList()
            }
        }

        Messages.showInfoMessage(
            component,
            StockerBundle.msg("import.success.message", added, skipped),
            StockerBundle.msg("import.success.title")
        )
    }

    /**
     * Infer market type AND normalized (prefixed) code from a raw stock code,
     * mirroring the logic in StockerSuggestionDialog.normalizeCode.
     *
     * Unprefixed 6-digit A-share codes (002008 → SZ, 688630 → SH, 300776 → SZ)
     * are upgraded to their market-prefixed form so they are stored, grouped
     * and de-duplicated consistently with codes entered anywhere else.
     */
    private fun normalizeImportedCode(code: String): Pair<StockerMarketType, String> {
        val upper = code.uppercase()
        if (upper.startsWith("SH") || upper.startsWith("SZ") || upper.startsWith("BJ")) {
            return StockerMarketType.AShare to upper
        }
        if (upper.matches(Regex("^\\d{6}$"))) {
            val prefix = when {
                upper.startsWith("6") -> "SH"
                upper.startsWith("0") || upper.startsWith("3") -> "SZ"
                upper.startsWith("4") || upper.startsWith("8") || upper.startsWith("9") -> "BJ"
                else -> "SZ"
            }
            return StockerMarketType.AShare to "$prefix$upper"
        }
        if (upper.matches(Regex("^\\d{5}$")))  return StockerMarketType.HKStocks to upper
        if (upper.matches(Regex("^[A-Z]{2,6}(USD|USDT)$"))) {
            return StockerMarketType.Crypto to "BTC$upper"
        }
        if (upper.matches(Regex("^[A-Z]{1,5}$"))) return StockerMarketType.USStocks to upper
        return StockerMarketType.AShare to upper
    }

    private val setting get() = StockerSetting.instance

    private fun askMergeOrReplace(parent: Component?): Int {
        val mergeBtn = JRadioButton(StockerBundle.msg("import.option.merge"))
        val replaceBtn = JRadioButton(StockerBundle.msg("import.option.replace"))
        mergeBtn.isSelected = true
        val group = ButtonGroup()
        group.add(mergeBtn)
        group.add(replaceBtn)

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(mergeBtn)
            add(replaceBtn)
        }

        return JOptionPane.showConfirmDialog(
            parent,
            panel,
            StockerBundle.msg("import.dialog.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        ).let { result ->
            if (result != JOptionPane.OK_OPTION) -1
            else if (replaceBtn.isSelected) 1
            else 0
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
