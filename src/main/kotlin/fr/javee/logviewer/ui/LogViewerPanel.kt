package fr.javee.logviewer.ui

import fr.javee.logviewer.service.LogFinderService
import fr.javee.logviewer.model.LogEntry
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.table.JBTable
import com.intellij.ui.components.JBScrollPane
import javax.swing.*
import java.awt.BorderLayout

class LogViewerPanel(
    private val project: Project,
    private val tableModel: LogTableModel
) : JPanel(BorderLayout()) {
    private val logger = Logger.getInstance(LogViewerPanel::class.java)
    private val logTable = JBTable(tableModel)
    private val scrollPane = JBScrollPane(logTable)

    init {
        add(scrollPane, BorderLayout.CENTER)
        setupTable()
    }

    private fun setupTable() {
        logTable.rowHeight = 30
    }

    fun refreshLogs() {
        logger.info("Rafraîchissement des logs")
        try {
            val logs: List<LogEntry> = LogFinderService.getInstance(project).findAllLogs()
            tableModel.setLogs(logs)
            logger.info("${logs.size} logs chargés")
            logTable.revalidate()
            logTable.repaint()
        } catch (e: Exception) {
                    val logs: List<LogEntry> = ApplicationManager.getApplication().runReadAction<List<LogEntry>> {
                        LogFinderService.getInstance(project).findAllLogs()
                    }
        }
    }

    private fun generateErrorLog(entry: LogEntry) {
        try {
            val psiElement = entry.psiElement ?: return
            ApplicationManager.getApplication().runWriteAction {
                JOptionPane.showMessageDialog(
                    this,
                    "Génération de log error pour: ${entry.message}",
                    "Log généré",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("Erreur génération log", e)
            JOptionPane.showMessageDialog(
                this,
                "Erreur: ${e.message}",
                "Erreur",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
}
