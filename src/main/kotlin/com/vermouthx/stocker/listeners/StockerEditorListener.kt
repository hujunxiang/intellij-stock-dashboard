package com.vermouthx.stocker.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Double-clicking an empty area of the editor toggles the StockerPlus tool window.
 * Double-clicking on text preserves the default word-selection behavior.
 */
class StockerEditorListener : EditorFactoryListener {

    // Track listeners per editor component so they can be removed on release.
    private val listeners = mutableMapOf<javax.swing.JComponent, java.awt.event.MouseListener>()

    override fun editorCreated(event: EditorFactoryEvent) {
        attachToEditor(event.editor)
    }

    fun attachToEditor(editor: Editor) {
        val component = editor.contentComponent
        // Avoid attaching the same listener twice for the same editor.
        if (listeners.containsKey(component)) return

        val listener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                if (!isOnEmptyArea(editor, e)) return
                toggleStockerToolWindow(editor.project)
            }
        }

        component.addMouseListener(listener)
        listeners[component] = listener
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val component = event.editor.contentComponent
        listeners.remove(component)?.let { component.removeMouseListener(it) }
    }

    /**
     * Returns true when the click landed on an empty region of the editor
     * (beyond the end of the line text, or on a blank line). In that case the
     * default word-selection does not apply and we toggle the tool window.
     */
    private fun isOnEmptyArea(editor: Editor, e: MouseEvent): Boolean {
        val document = editor.document
        val logicalPos = editor.xyToLogicalPosition(Point(e.x, e.y))
        val line = logicalPos.line

        // Line out of range → treat as empty
        if (line < 0 || line >= document.lineCount) return true

        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val lineLength = lineEnd - lineStart

        // Column beyond the line text → empty area (trailing whitespace / gap)
        return logicalPos.column >= lineLength
    }

    private fun toggleStockerToolWindow(project: Project?) {
        if (project == null) return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("StockerPlus") ?: return
        if (toolWindow.isVisible) {
            toolWindow.hide(null)
        } else {
            toolWindow.show(null)
        }
    }
}
