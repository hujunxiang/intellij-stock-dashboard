package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerDataExporter
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * One row of the stock picker — drives both the table display and the export
 * entry rebuild.
 */
private data class StockRow(
    val code: String,
    val market: StockerMarketType,
    val name: String,
    val group: String
)

class StockerExportDialog(
    val project: Project?
) : DialogWrapper(project) {

    private val exporter = StockerDataExporter()

    private enum class Scope { ALL, GROUP, STOCK }

    private var scope: Scope = Scope.ALL
    private lateinit var groupList: CheckBoxList<String>
    private lateinit var stockTable: JBTable
    private lateinit var stockModel: DefaultTableModel
    private lateinit var allRadio: JBRadioButton
    private lateinit var groupRadio: JBRadioButton
    private lateinit var stockRadio: JBRadioButton
    private lateinit var cardLayout: CardLayout
    private lateinit var cardPanel: JPanel

    private val stockRows = mutableListOf<StockRow>()

    init {
        title = StockerBundle.msg("export.dialog.title")
        isModal = true
        init()
    }

    override fun createCenterPanel(): DialogPanel {
        val setting = StockerSetting.instance

        allRadio = JBRadioButton(StockerBundle.msg("export.scope.all"), true)
        groupRadio = JBRadioButton(StockerBundle.msg("export.scope.group"))
        stockRadio = JBRadioButton(StockerBundle.msg("export.scope.stock"))
        ButtonGroup().apply { add(allRadio); add(groupRadio); add(stockRadio) }

        // -- Stock picker: ✓ | code | name | group ------------------------- //
        stockRows.clear()
        fun append(list: List<String>, market: StockerMarketType) {
            for (code in list) {
                val customName = setting.getCustomName(code)
                val displayName = if (!customName.isNullOrBlank()) customName else code
                val group = setting.getStockGroup(code) ?: ""
                stockRows.add(StockRow(code, market, displayName, group))
            }
        }
        append(setting.aShareList, StockerMarketType.AShare)
        append(setting.hkStocksList, StockerMarketType.HKStocks)
        append(setting.usStocksList, StockerMarketType.USStocks)
        append(setting.cryptoList, StockerMarketType.Crypto)

        val colNames = arrayOf(
            "",
            StockerBundle.msg("export.stock.col.code"),
            StockerBundle.msg("export.stock.col.name"),
            StockerBundle.msg("export.stock.col.group")
        )
        stockModel = object : DefaultTableModel(colNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = column == 0
            override fun getColumnClass(column: Int): Class<*> =
                if (column == 0) java.lang.Boolean::class.java else String::class.java
        }
        for (r in stockRows) {
            stockModel.addRow(arrayOf(false, r.code, r.name, r.group))
        }
        stockTable = JBTable(stockModel).apply {
            setShowGrid(false)
            intercellSpacing = Dimension(0, 1)
            rowHeight = 24
            tableHeader.reorderingAllowed = false
            getColumnModel().getColumn(0).apply {
                minWidth = 28
                maxWidth = 28
                preferredWidth = 28
            }
            getColumnModel().getColumn(1).preferredWidth = 140
            getColumnModel().getColumn(2).preferredWidth = 140
            getColumnModel().getColumn(3).preferredWidth = 110
        }

        // -- Group picker: checkbox list of all groups --------------------- //
        groupList = CheckBoxList<String>()
        groupList.setItems(setting.stockGroupNames, null)

        // -- Card panel drives which picker the user sees ----------------- //
        cardLayout = CardLayout()
        cardPanel = JPanel(cardLayout).apply {
            add(JScrollPane(stockTable), "stock")
            add(JScrollPane(groupList), "group")
            add(Box.createRigidArea(Dimension(0, 0)), "none")
        }

        val panel = panel {
            buttonsGroup {
                row { cell(allRadio) }
                row { cell(groupRadio) }
                row { cell(stockRadio) }
            }
            row {
                cell(cardPanel).align(AlignX.FILL).align(AlignY.FILL)
                    .apply {
                        val d = Dimension(460, 220)
                        component.preferredSize = d
                        component.minimumSize = Dimension(240, 120)
                    }
            }
        }

        cardLayout.show(cardPanel, "none")
        allRadio.addActionListener {
            scope = Scope.ALL
            cardLayout.show(cardPanel, "none")
        }
        groupRadio.addActionListener {
            scope = Scope.GROUP
            cardLayout.show(cardPanel, "group")
        }
        stockRadio.addActionListener {
            scope = Scope.STOCK
            cardLayout.show(cardPanel, "stock")
        }

        return panel
    }

    fun buildData(): com.vermouthx.stocker.utils.StockerExportData? {
        val setting = StockerSetting.instance
        return when (scope) {
            Scope.ALL -> {
                val data = exporter.buildAllData()
                if (data.stocks.isEmpty()) null else data
            }
            Scope.GROUP -> {
                @Suppress("UNCHECKED_CAST")
                val chosen = LinkedHashSet(groupList.getCheckedItems() as Collection<String>)
                if (chosen.isEmpty()) {
                    warnNothing()
                    return null
                }
                exporter.buildCombinedData(chosen, emptyList())
            }
            Scope.STOCK -> {
                val chosen = (0 until stockModel.rowCount)
                    .filter { stockModel.getValueAt(it, 0) == true }
                    .map { stockRows[it].let { r -> r.code to r.market } }
                if (chosen.isEmpty()) {
                    warnNothing()
                    return null
                }
                exporter.buildCombinedData(
                    emptySet(),
                    chosen,
                    StockerBundle.msg("export.synthetic.group")
                )
            }
        }
    }

    private fun warnNothing() {
        Messages.showMessageDialog(
            project,
            StockerBundle.msg("export.nothing"),
            StockerBundle.msg("export.dialog.title"),
            Messages.getWarningIcon()
        )
    }
}
