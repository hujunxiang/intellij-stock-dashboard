package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
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
        try {
            val project = e.project ?: return
            val app = StockerAppManager.myApplication(project)
            app?.shutdownThenClear()
            // Always reschedule, even if shutdownThenClear threw — otherwise the
            // refresh loop dies permanently and the tool window is unresponsive.
            StockerAppManager.myApplication(project)?.schedule()
        } catch (ex: Exception) {
            log.warn("StockerRefreshAction failed", ex)
            try { StockerAppManager.myApplication(e.project)?.schedule() } catch (_: Exception) {}
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private val log = Logger.getInstance(javaClass)
}
