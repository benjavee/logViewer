package fr.javee.logviewer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import fr.javee.logviewer.ui.LogViewerPanel

class RefreshLogsAction(
    private val project: Project? = null,
    private val logViewerPanel: LogViewerPanel? = null
) : AnAction("Refresh logs", "Search logs in the project again", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val currentProject = project ?: e.project ?: return

        // If we have a direct reference to the panel, use it
        if (logViewerPanel != null) {
            logViewerPanel.refreshLogs()
            return
        }

        // Otherwise, locate the tool window
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(currentProject)
            .getToolWindow("Log Viewer") ?: return

        val content = toolWindow.contentManager.selectedContent ?: return
        val component = content.component

        if (component is LogViewerPanel) {
            component.refreshLogs()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
