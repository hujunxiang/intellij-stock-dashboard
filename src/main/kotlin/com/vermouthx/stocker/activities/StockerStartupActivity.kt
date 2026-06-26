package com.vermouthx.stocker.activities

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.vermouthx.stocker.StockerMeta
import com.vermouthx.stocker.notifications.StockerNotification
import com.vermouthx.stocker.settings.StockerSetting

class StockerStartupActivity : ProjectActivity, DumbAware {

    private val log = Logger.getInstance(StockerStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        val settings = StockerSetting.instance
        val currentVersion = StockerMeta.currentVersion
        log.info("StockerStartup: version=${settings.version}, currentVersion=$currentVersion")

        if (settings.version.isEmpty()) {
            settings.version = currentVersion
            log.info("StockerStartup: first install, showing welcome notification")
            try {
                StockerNotification.notifyWelcome(project)
            } catch (e: Exception) {
                log.warn("StockerStartup: failed to show welcome notification", e)
            }
            return
        }
        if (currentVersion != settings.version) {
            log.info("StockerStartup: version changed ${settings.version} -> $currentVersion")
            settings.version = currentVersion
            StockerNotification.notifyReleaseNote(project)
        }
        prewarmSettingsFramework()
        checkForPluginUpdate(project)
    }

    private fun prewarmSettingsFramework() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Class.forName("com.intellij.openapi.options.ex.EpBasedConfigurableGroup")
                Class.forName("com.intellij.application.options.colors.ColorAndFontOptions")
                Class.forName("com.intellij.openapi.options.colors.pages.ColorSettingsPagesImpl")
            } catch (_: ClassNotFoundException) {
            }
        }
    }

    private fun checkForPluginUpdate(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val installedVersion = StockerMeta.currentVersion
                if (installedVersion.isEmpty()) return@executeOnPooledThread

                // Step 1: Get numeric plugin ID from Marketplace search API
                val searchUrl = java.net.URI("https://plugins.jetbrains.com/api/searchPlugins?search=StockerPlus&size=1").toURL()
                val searchConn = searchUrl.openConnection()
                searchConn.connectTimeout = 5000
                searchConn.readTimeout = 5000
                val searchJson = searchConn.getInputStream().bufferedReader().readText()

                val numericId = parseNumericPluginId(searchJson, "com.huly.stocker-plus")
                if (numericId == null) {
                    log.warn("StockerStartup: could not find numeric plugin ID on Marketplace")
                    return@executeOnPooledThread
                }

                // Step 2: Get latest version using numeric ID
                val updateUrl = java.net.URI("https://plugins.jetbrains.com/api/plugins/$numericId/updates?size=1").toURL()
                val updateConn = updateUrl.openConnection()
                updateConn.connectTimeout = 5000
                updateConn.readTimeout = 5000
                val updateJson = updateConn.getInputStream().bufferedReader().readText()

                val latestVersion = parseVersionFromResponse(updateJson)
                if (latestVersion != null && latestVersion != installedVersion) {
                    log.info("StockerStartup: update available $installedVersion -> $latestVersion")
                    ApplicationManager.getApplication().invokeLater {
                        StockerNotification.notifyUpdate(project, installedVersion, latestVersion)
                    }
                }
            } catch (e: Exception) {
                log.warn("StockerStartup: failed to check for plugin update", e)
            }
        }
    }

    private fun parseNumericPluginId(json: String, xmlId: String): Int? {
        return try {
            val xmlIdIndex = json.indexOf("\"xmlId\":\"$xmlId\"")
            if (xmlIdIndex == -1) return null
            // Search backwards for the "id" field before xmlId
            val beforeXmlId = json.substring(0, xmlIdIndex)
            val idIndex = beforeXmlId.lastIndexOf("\"id\":")
            if (idIndex == -1) return null
            val start = idIndex + 5
            val end = json.indexOfAny(charArrayOf(',', '}'), start)
            json.substring(start, end).trim().toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVersionFromResponse(json: String): String? {
        return try {
            val versionIndex = json.indexOf("\"version\":")
            if (versionIndex == -1) return null
            val start = json.indexOf("\"", versionIndex + 10) + 1
            val end = json.indexOf("\"", start)
            if (start > 0 && end > start) json.substring(start, end) else null
        } catch (_: Exception) {
            null
        }
    }
}
