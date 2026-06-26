package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle

class StockerRefreshAction : AnAction(
    StockerBundle.msg("action.refresh"),
    StockerBundle.msg("action.refresh.description"),
    null
) {

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        presentation.text = StockerBundle.msg("action.refresh")
        presentation.description = StockerBundle.msg("action.refresh.description")
        presentation.isEnabled = project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        StockerAppManager.myApplication(e.project)?.shutdownThenClear()
        StockerAppManager.myApplication(e.project)?.schedule()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
