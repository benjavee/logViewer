package fr.javee.logviewer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import fr.javee.logviewer.service.LogFinderService
import com.intellij.pom.Navigatable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.icons.AllIcons

import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.table.TableCellRenderer
import javax.swing.table.JTableHeader
import fr.javee.logviewer.model.LogLevel
import fr.javee.logviewer.model.Score
import javax.swing.table.TableRowSorter
import java.awt.event.ActionListener
import java.awt.event.ActionEvent

class FilterHeaderRenderer(
    private val column: Int,
    private val tableModel: LogTableModel,
    private val onFilterChanged: () -> Unit
) : JPanel(BorderLayout()), TableCellRenderer {
    private var filterComponent: JComponent? = null

    init {
        isOpaque = true
        when (column) {
            0 -> { // Niveau (enum)
                val combo = JComboBox(LogLevel.values())
                combo.isEditable = false
                combo.selectedItem = null
                combo.maximumRowCount = LogLevel.values().size
                combo.isFocusable = true
                filterComponent = combo
                combo.addActionListener {
                    val selected = combo.selectedItem as? LogLevel
                    tableModel.setFilters(
                        if (selected != null) setOf(selected) else null,
                        tableModel.scoreFilter,
                        tableModel.messageFilter,
                        tableModel.fileFilter,
                        tableModel.lineFilter
                    )
                    onFilterChanged()
                }
                add(combo, BorderLayout.CENTER)
            }
            1 -> { // Message (string)
                val field = JTextField()
                field.toolTipText = "Filter message"
                field.isFocusable = true
                filterComponent = field
                field.addActionListener {
                    tableModel.setFilters(
                        tableModel.levelFilter,
                        tableModel.scoreFilter,
                        field.text,
                        tableModel.fileFilter,
                        tableModel.lineFilter
                    )
                    onFilterChanged()
                }
                field.addFocusListener(object : java.awt.event.FocusAdapter() {})
                add(field, BorderLayout.CENTER)
            }
            2 -> { // Fichier (string)
                val field = JTextField()
                field.toolTipText = "Filter file"
                field.isFocusable = true
                filterComponent = field
                field.addActionListener {
                    tableModel.setFilters(
                        tableModel.levelFilter,
                        tableModel.scoreFilter,
                        tableModel.messageFilter,
                        field.text,
                        tableModel.lineFilter
                    )
                    onFilterChanged()
                }
                field.addFocusListener(object : java.awt.event.FocusAdapter() {})
                add(field, BorderLayout.CENTER)
            }
            3 -> { // Ligne (int)
                val field = JTextField()
                field.toolTipText = "Filter line"
                field.isFocusable = true
                filterComponent = field
                field.addActionListener {
                    tableModel.setFilters(
                        tableModel.levelFilter,
                        tableModel.scoreFilter,
                        tableModel.messageFilter,
                        tableModel.fileFilter,
                        field.text
                    )
                    onFilterChanged()
                }
                field.addFocusListener(object : java.awt.event.FocusAdapter() {})
                add(field, BorderLayout.CENTER)
            }
            4 -> { // Score (enum)
                val combo = JComboBox(Score.values())
                combo.isEditable = false
                combo.selectedItem = null
                combo.maximumRowCount = Score.values().size
                combo.isFocusable = true
                filterComponent = combo
                combo.addActionListener {
                    val selected = combo.selectedItem as? Score
                    tableModel.setFilters(
                        tableModel.levelFilter,
                        if (selected != null) setOf(selected) else null,
                        tableModel.messageFilter,
                        tableModel.fileFilter,
                        tableModel.lineFilter
                    )
                    onFilterChanged()
                }
                add(combo, BorderLayout.CENTER)
            }
        }
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        // Permet au composant de filtre de recevoir le focus
        filterComponent?.isFocusable = true
        return this
    }
}

class MultiEnumFilterButton<T : Enum<T>>(
    private val enumClass: Class<T>,
    private val values: Array<T>,
    label: String,
    private val onChange: (Set<T>) -> Unit
) : JButton(label) {
    private val menu = JPopupMenu()
    private val items = values.map { value ->
        object : JCheckBoxMenuItem(value.name, true) {
            override fun processMouseEvent(e: java.awt.event.MouseEvent) {
                if (e.id == java.awt.event.MouseEvent.MOUSE_RELEASED) {
                    // Toggle sans fermer le menu
                    isSelected = !isSelected
                    updateLabel()
                    onChange(getSelectedValues())
                }
            }
        }
    }

    init {
        items.forEach { menu.add(it) }
        addActionListener { menu.show(this, 0, height) }
        updateLabel()
    }

    fun getSelectedValues(): Set<T> {
        return items.filter { it.isSelected }.map { java.lang.Enum.valueOf(enumClass, it.text) }.toSet()
    }

    fun reset() {
        items.forEach { it.isSelected = true }
        updateLabel()
        onChange(getSelectedValues())
    }

    private fun updateLabel() {
        val selected = getSelectedValues()
        if (selected.size == values.size) {
            text = "ALL"
        } else if (selected.isEmpty()) {
            text = ""
        } else {
            val names = selected.map { it.name }
            val joined = names.joinToString(", ")
            // Raccourci si trop long (>18 caractères)
            text = if (joined.length > 18) {
                joined.substring(0, 15) + "..."
            } else {
                joined
            }
        }
    }
}

class LogViewerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val tableModel = LogTableModel()
        val table = JBTable(tableModel)
        val rowSorter = TableRowSorter<LogTableModel>(tableModel)
        table.rowSorter = rowSorter

        // --- Panneau de filtres visuellement comme une ligne du tableau ---
        val filterPanel = JPanel(null)
        filterPanel.border = BorderFactory.createEmptyBorder()
        filterPanel.background = table.tableHeader.background

        // Filtres enums : boutons avec menu de cases à cocher
        val levelFilterBtn = MultiEnumFilterButton(LogLevel::class.java, LogLevel.values(), "Level") { selected ->
            tableModel.setFilters(
                if (selected.size == LogLevel.values().size) null else selected,
                tableModel.scoreFilter,
                (filterPanel.getComponent(1) as JTextField).text,
                (filterPanel.getComponent(2) as JTextField).text,
                (filterPanel.getComponent(3) as JTextField).text
            )
        }
        val scoreFilterBtn = MultiEnumFilterButton(Score::class.java, Score.values(), "Score") { selected ->
            tableModel.setFilters(
                tableModel.levelFilter,
                if (selected.size == Score.values().size) null else selected,
                (filterPanel.getComponent(1) as JTextField).text,
                (filterPanel.getComponent(2) as JTextField).text,
                (filterPanel.getComponent(3) as JTextField).text
            )
        }

        val messageFilter = JTextField()
        val fileFilter = JTextField()
        val lineFilter = JTextField()

        val filters = listOf(levelFilterBtn, messageFilter, fileFilter, lineFilter, scoreFilterBtn)
        filterPanel.layout = null

        // Fonction pour ajuster la taille des filtres selon la largeur des colonnes
        fun updateFilterBounds() {
            var x = 0
            for (i in filters.indices) {
                val colWidth = table.columnModel.getColumn(i).width
                filters[i].setBounds(x, 0, colWidth, table.tableHeader.height)
                x += colWidth
            }
            filterPanel.preferredSize = java.awt.Dimension(x, table.tableHeader.height)
            filterPanel.revalidate()
            filterPanel.repaint()
        }

        // Initialisation des filtres
        filters.forEach { filterPanel.add(it) }
        table.columnModel.addColumnModelListener(object : javax.swing.event.TableColumnModelListener {
            override fun columnMarginChanged(e: javax.swing.event.ChangeEvent?) = updateFilterBounds()
            override fun columnMoved(e: javax.swing.event.TableColumnModelEvent?) = updateFilterBounds()
            override fun columnAdded(e: javax.swing.event.TableColumnModelEvent?) = updateFilterBounds()
            override fun columnRemoved(e: javax.swing.event.TableColumnModelEvent?) = updateFilterBounds()
            override fun columnSelectionChanged(e: javax.swing.event.ListSelectionEvent?) {}
        })
        table.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) = updateFilterBounds()
        })
        SwingUtilities.invokeLater { updateFilterBounds() }

        // Listeners pour les champs texte
        val textFilterAction = {
            tableModel.setFilters(
                levelFilterBtn.getSelectedValues().let { if (it.size == LogLevel.values().size) null else it },
                scoreFilterBtn.getSelectedValues().let { if (it.size == Score.values().size) null else it },
                messageFilter.text ?: "",
                fileFilter.text ?: "",
                lineFilter.text ?: ""
            )
        }
        messageFilter.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
        })
        fileFilter.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
        })
        lineFilter.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = textFilterAction()
        })

        // --- Fin panneau filtres ---

        // Bouton refresh et reset au-dessus des filtres
        val actionPanel = JPanel()
        actionPanel.layout = BoxLayout(actionPanel, BoxLayout.X_AXIS)

        actionPanel.add(Box.createHorizontalGlue()) // pousse les boutons à droite

        // Bouton export (icône corrigée + DataContext custom)
        val exportButton = JButton(AllIcons.ToolbarDecorator.Export)
        exportButton.toolTipText = "Export PDF report"
        exportButton.isFocusPainted = false
        exportButton.isBorderPainted = false
        exportButton.isContentAreaFilled = false
        exportButton.addActionListener {
            val dataContext = object : DataContext {
                override fun getData(dataId: String): Any? =
                    if (dataId == CommonDataKeys.PROJECT.name) project else null
            }
            @Suppress("DEPRECATION")
            val event = AnActionEvent.createFromDataContext("exportReport", null, dataContext)
            fr.javee.logviewer.actions.ExportReportAction().actionPerformed(event)
        }

        val resetButton = JButton(AllIcons.Actions.Rollback)
        resetButton.toolTipText = "Reset all filters"
        resetButton.isFocusPainted = false
        resetButton.isBorderPainted = false
        resetButton.setContentAreaFilled(false)
        resetButton.addActionListener {
            levelFilterBtn.reset()
            scoreFilterBtn.reset()
            messageFilter.text = ""
            fileFilter.text = ""
            lineFilter.text = ""
            tableModel.setFilters(null, null, "", "", "")
        }

        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = "Refresh logs"
        refreshButton.isFocusPainted = false
        refreshButton.isBorderPainted = false
        refreshButton.setContentAreaFilled(false)
        refreshButton.addActionListener {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Refreshing logs", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Searching logs in project..."
                    val logs = ApplicationManager.getApplication().runReadAction<List<fr.javee.logviewer.model.LogEntry>> {
                        LogFinderService.getInstance(project).findAllLogs()
                    }
                    SwingUtilities.invokeLater {
                        tableModel.setLogs(logs)
                    }
                }
            })
        }

        actionPanel.add(exportButton)
        actionPanel.add(resetButton)
        actionPanel.add(refreshButton)

        // Chargement initial des logs
        val logs = LogFinderService.getInstance(project).findAllLogs()
        tableModel.setLogs(logs)

        // Listener pour la sélection et navigation
        table.selectionModel.addListSelectionListener {
            if (!table.selectionModel.isSelectionEmpty) {
                val row = table.selectedRow
                val logEntry = tableModel.getLogEntryAt(table.convertRowIndexToModel(row))
                if (logEntry?.psiElement is Navigatable) {
                    (logEntry.psiElement as Navigatable).navigate(true)
                }
            }
        }

        // Ajout du panneau d'action au-dessus du panneau de filtres
        val northPanel = JPanel()
        northPanel.layout = BoxLayout(northPanel, BoxLayout.Y_AXIS)
        northPanel.add(actionPanel)
        northPanel.add(filterPanel)
        northPanel.add(Box.createVerticalStrut(20)) // espace augmenté entre filtres et tableau

        panel.add(northPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
