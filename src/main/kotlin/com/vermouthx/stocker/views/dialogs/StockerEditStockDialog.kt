package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.panel
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.settings.StockerSetting
import javax.swing.JComponent
import javax.swing.JTextField

class StockerEditStockDialog(
    project: Project?,
    private val stockCode: String,
    private val stockName: String
) : DialogWrapper(project) {

    private val setting = StockerSetting.instance
    private lateinit var costPriceField: JTextField
    private lateinit var holdingsField: JTextField

    init {
        title = StockerBundle.msg("edit.stock.title")
        isModal = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val currentCost = setting.getCostPrice(stockCode)?.toString() ?: ""
        val currentHoldings = setting.getHoldings(stockCode)?.toString() ?: ""

        return panel {
            row(StockerBundle.msg("edit.stock.code")) {
                label(stockCode)
            }
            row(StockerBundle.msg("edit.stock.name")) {
                label(stockName)
            }
            row(StockerBundle.msg("edit.stock.cost.price")) {
                costPriceField = JTextField(currentCost, 15)
                cell(costPriceField)
            }
            row(StockerBundle.msg("edit.stock.holdings")) {
                holdingsField = JTextField(currentHoldings, 15)
                cell(holdingsField)
            }
        }
    }

    override fun doOKAction() {
        val costPriceText = costPriceField.text.trim()
        val holdingsText = holdingsField.text.trim()

        val costPrice = costPriceText.toDoubleOrNull()
        val holdings = holdingsText.toIntOrNull()

        if (costPriceText.isNotEmpty() && costPrice != null) {
            setting.setCostPrice(stockCode, costPrice)
        } else {
            setting.removeCostPrice(stockCode)
        }

        if (holdingsText.isNotEmpty() && holdings != null) {
            setting.setHoldings(stockCode, holdings)
        } else {
            setting.removeHoldings(stockCode)
        }

        super.doOKAction()
    }

    override fun doValidate(): ValidationInfo? {
        val costPriceText = costPriceField.text.trim()
        val holdingsText = holdingsField.text.trim()

        if (costPriceText.isNotEmpty() && costPriceText.toDoubleOrNull() == null) {
            return ValidationInfo("Invalid cost price", costPriceField)
        }
        if (holdingsText.isNotEmpty() && holdingsText.toIntOrNull() == null) {
            return ValidationInfo("Invalid holdings", holdingsField)
        }
        return null
    }
}
