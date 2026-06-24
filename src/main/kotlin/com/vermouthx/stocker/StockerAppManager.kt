package com.vermouthx.stocker

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil

object StockerAppManager {
    private val myApplicationMap: MutableMap<Project, StockerApp> = mutableMapOf()

    fun myApplication(project: Project?): StockerApp? {
        return myApplicationMap[project]
    }

    fun getAllApplications(): Collection<StockerApp> {
        return myApplicationMap.values
    }

    fun register(project: Project, app: StockerApp) {
        myApplicationMap[project] = app
    }

    fun unregister(project: Project) {
        myApplicationMap.remove(project)?.let { app ->
            app.shutdownThenClear()
        }
        if (myApplicationMap.isEmpty()) {
            StockerQuoteHttpUtil.closeConnections()
        }
    }

    class StockerProjectManagerListener : ProjectManagerListener {
        override fun projectClosing(project: Project) {
            unregister(project)
        }
    }
}