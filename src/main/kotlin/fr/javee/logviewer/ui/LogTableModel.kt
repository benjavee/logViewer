package fr.javee.logviewer.ui

import fr.javee.logviewer.model.LogEntry
import fr.javee.logviewer.model.LogLevel
import fr.javee.logviewer.model.Score
import javax.swing.table.AbstractTableModel

class LogTableModel : AbstractTableModel() {
    private var logs: List<LogEntry> = emptyList()
    private var filteredLogs: List<LogEntry> = emptyList()

    // Added Score column
    private val columns = arrayOf("Level", "Message", "File", "Line", "Score")

    // Filters per column
    var levelFilter: Set<LogLevel>? = null
    var scoreFilter: Set<Score>? = null
    var messageFilter: String = ""
    var fileFilter: String = ""
    var lineFilter: String = ""

    fun setLogs(logs: List<LogEntry>) {
        this.logs = logs
        this.filteredLogs = logs
        fireTableDataChanged()
    }

    fun setFilters(
        levelFilter: Set<LogLevel>?,
        scoreFilter: Set<Score>?,
        messageFilter: String,
        fileFilter: String,
        lineFilter: String
    ) {
        this.levelFilter = levelFilter
        this.scoreFilter = scoreFilter
        this.messageFilter = messageFilter
        this.fileFilter = fileFilter
        this.lineFilter = lineFilter
        applyFilters()
    }

    private fun applyFilters() {
        filteredLogs = logs.filter { log ->
            (levelFilter == null || levelFilter!!.contains(log.level)) &&
            (scoreFilter == null || scoreFilter!!.contains(log.score)) &&
            (messageFilter.isBlank() || log.message.contains(messageFilter, ignoreCase = true)) &&
            (fileFilter.isBlank() || log.fileName.contains(fileFilter, ignoreCase = true)) &&
            (lineFilter.isBlank() || log.lineNumber.toString().contains(lineFilter))
        }
        fireTableDataChanged()
    }

    fun getLogEntryAt(row: Int): LogEntry? {
        return if (row >= 0 && row < filteredLogs.size) filteredLogs[row] else null
    }

    override fun getRowCount(): Int = filteredLogs.size

    override fun getColumnCount(): Int = columns.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val log = filteredLogs[rowIndex]
        return when (columnIndex) {
            0 -> log.level
            1 -> log.message
            2 -> log.fileName
            3 -> log.lineNumber
            4 -> log.score
            else -> ""
        }
    }

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0 -> LogLevel::class.java
            3 -> Int::class.java
            4 -> Score::class.java
            else -> String::class.java
        }
    }
}
