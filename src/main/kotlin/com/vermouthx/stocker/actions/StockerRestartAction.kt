package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.views.windows.StockerToolWindow

class StockerRestartAction : AnAction(
    StockerBundle.msg("action.restart"),
    StockerBundle.msg("action.restart.description"),
    null
) {

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        presentation.text = StockerBundle.msg("action.restart")
        presentation.description = StockerBundle.msg("action.restart.description")
        presentation.isEnabled = project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        StockerToolWindow.rebuildTabs()
        StockerAppManager.restart(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
