package com.vermouthx.stocker.views.windows

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.actions.StockerRefreshAction
import com.vermouthx.stocker.actions.StockerRestartAction
import com.vermouthx.stocker.actions.StockerSettingAction
import com.vermouthx.stocker.actions.StockerStockManageAction
import com.vermouthx.stocker.actions.StockerStockSearchAction
import com.vermouthx.stocker.actions.StockerStopAction
import com.vermouthx.stocker.views.StockerTableView

class StockerSimpleToolWindow : SimpleToolWindowPanel(true) {
    var tableView: StockerTableView = StockerTableView()

    init {
        val actionManager = ActionManager.getInstance()

        val leftEntries: List<Pair<AnAction, String>> = listOfNotNull(
            actionManager.getAction(StockerStockSearchAction::class.qualifiedName!!)?.let { it to "action.add.favorite.stocks.description" },
            actionManager.getAction(StockerRefreshAction::class.qualifiedName!!)?.let { it to "action.refresh.description" },
            actionManager.getAction(StockerStopAction::class.qualifiedName!!)?.let { it to "action.stop.refresh.description" },
            actionManager.getAction(StockerRestartAction::class.qualifiedName!!)?.let { it to "action.restart.description" },
            actionManager.getAction(StockerStockManageAction::class.qualifiedName!!)?.let { it to "action.manage.favorite.stocks.description" }
        )

        val actionGroup = DefaultActionGroup(leftEntries.map { it.first })
        val actionToolbar = actionManager.createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, actionGroup, true)
        actionToolbar.targetComponent = tableView.component

        val tooltipKeys = leftEntries.map { StockerBundle.msg(it.second) }
        setTooltips(actionToolbar.component, tooltipKeys)

        val settingAction = actionManager.getAction(StockerSettingAction::class.qualifiedName!!)
        val rightActionGroup = DefaultActionGroup().apply { settingAction?.let { add(it) } }
        val rightActionToolbar = actionManager.createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, rightActionGroup, true)
        rightActionToolbar.targetComponent = tableView.component

        setTooltips(rightActionToolbar.component, listOf(StockerBundle.msg("action.settings.description")))

        val toolbarPanel = com.intellij.ui.components.panels.HorizontalLayout(0).let { layout ->
            javax.swing.JPanel(java.awt.BorderLayout()).apply {
                add(actionToolbar.component, java.awt.BorderLayout.WEST)
                add(rightActionToolbar.component, java.awt.BorderLayout.EAST)
            }
        }

        this.toolbar = toolbarPanel
        setContent(tableView.component)
    }

    private fun collectButtons(container: java.awt.Component): List<javax.swing.AbstractButton> {
        val result = mutableListOf<javax.swing.AbstractButton>()
        if (container is javax.swing.AbstractButton) {
            result.add(container)
        }
        if (container is java.awt.Container) {
            for (child in container.components) {
                result.addAll(collectButtons(child))
            }
        }
        return result
    }

    private fun setTooltips(container: java.awt.Component, tooltips: List<String>) {
        val buttons = collectButtons(container)
        for ((i, btn) in buttons.withIndex()) {
            if (i < tooltips.size) btn.toolTipText = tooltips[i]
        }
    }
}
