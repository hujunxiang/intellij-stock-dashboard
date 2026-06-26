package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.views.dialogs.StockerSuggestionDialog

class StockerStockSearchAction : AnAction(
    StockerBundle.msg("action.add.favorite.stocks"),
    StockerBundle.msg("action.add.favorite.stocks.description"),
    null
) {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        presentation.text = StockerBundle.msg("action.add.favorite.stocks")
        presentation.description = StockerBundle.msg("action.add.favorite.stocks.description")
        presentation.isEnabled = project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        StockerSuggestionDialog(e.project).show()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
