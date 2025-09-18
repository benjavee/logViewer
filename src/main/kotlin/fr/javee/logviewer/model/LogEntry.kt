package fr.javee.logviewer.model

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/**
 * Represents a detected log entry in the project.
 */
data class LogEntry(
    val message: String,
    val level: LogLevel,
    val fileName: String,
    val lineNumber: Int,
    val psiElement: PsiElement? = null,
    val virtualFile: VirtualFile? = null,
    val score: Score = Score.NONE
)

/**
 * Log level detected.
 */
enum class LogLevel {
    INFO, DEBUG, WARN, ERROR, TRACE, FATAL, MISSING;

    companion object {
        fun fromString(levelStr: String): LogLevel =
            when (levelStr.uppercase()) {
                "INFO" -> INFO
                "DEBUG" -> DEBUG
                "WARN", "WARNING" -> WARN
                "ERROR" -> ERROR
                "TRACE" -> TRACE
                "FATAL" -> FATAL
                "MISSING" -> MISSING
                else -> INFO
            }
    }
}

/**
 * Log richness score (context and message analysis).
 */
enum class Score {
    NONE,      // No log / no info
    POOR,      // Little context, basic message
    AVERAGE,   // Medium context, informative message
    RICH,      // Good context, detailed message
    EXCELLENT  // Full context, very rich message
}
