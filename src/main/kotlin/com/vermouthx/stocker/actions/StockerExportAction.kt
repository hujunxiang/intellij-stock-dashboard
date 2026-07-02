package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.ThrowableComputable

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.utils.StockerDataExporter
import com.vermouthx.stocker.views.dialogs.StockerExportDialog
import com.vermouthx.stocker.views.windows.StockerToolWindow

class StockerExportAction : AnAction(
    StockerBundle.msg("action.export.stocks"),
    StockerBundle.msg("action.export.stocks.description"),
    com.intellij.icons.AllIcons.Actions.Upload
) {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = StockerExportDialog(project)
        if (!dialog.showAndGet()) return

        val data = dialog.buildData() ?: return
        val json = StockerDataExporter().serialize(data)

        // Directory chooser — the filename is auto-generated.
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(StockerBundle.msg("export.save.title"))
        fun baseDir(path: String?) = LocalFileSystem.getInstance().findFileByPath(path ?: "")
        val base = baseDir(project.basePath)
        val dir = FileChooser.chooseFile(descriptor, project, base) ?: return
        val targetDir = if (dir.isDirectory) dir else dir.parent ?: baseDir(project.basePath) ?: dir
        val prefix = StockerBundle.msg("export.save.filename.prefix")

        // Filename carries a timestamp: stocker-export-yyyyMMdd-HHmmss.json.
        // Falls back to appending (1), (2)… on the rare collision.
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

        val target = WriteAction.compute(object : ThrowableComputable<VirtualFile, Throwable> {
            override fun compute(): VirtualFile {
                val baseName = "$prefix-$timestamp"
                var idx = 0
                while (true) {
                    val name = if (idx == 0) "$baseName.json" else "$baseName($idx).json"
                    idx++
                    val existing = targetDir.findChild(name)
                    if (existing != null) return existing
                    return targetDir.createChildData(this@StockerExportAction, name)
                }
            }
        })

        WriteAction.run<Throwable> {
            target.getOutputStream(this).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }

        Messages.showInfoMessage(
            project,
            StockerBundle.msg("export.success.message", data.stocks.size, target.presentableUrl),
            StockerBundle.msg("export.success.title")
        )
    }
}
