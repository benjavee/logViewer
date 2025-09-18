package fr.javee.logviewer.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import fr.javee.logviewer.model.LogEntry
import fr.javee.logviewer.model.LogLevel
import fr.javee.logviewer.model.Score
import java.util.regex.Pattern
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.PsiCatchSection
import kotlin.compareTo


@Service
class LogFinderService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): LogFinderService = project.service<LogFinderService>()

        private val LOG_PATTERNS = mapOf(
            Pattern.compile("log\\.(trace|debug|info|warn|error|fatal)", Pattern.CASE_INSENSITIVE) to "log",
            Pattern.compile("logger\\.(trace|debug|info|warn|error)", Pattern.CASE_INSENSITIVE) to "logger",
            Pattern.compile("System\\.(out|err)\\.println") to "System.out"
        )
    }

    fun findAllLogs(): List<LogEntry> {
        val logs = mutableListOf<LogEntry>()
        val projectScope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)

        FilenameIndex.getAllFilesByExt(project, "java", projectScope).forEach { virtualFile ->
            psiManager.findFile(virtualFile)?.let { psiFile ->
                processFile(psiFile, logs)
            }
        }

        return logs
    }

    private fun processFile(file: PsiFile, logs: MutableList<LogEntry>) {
        // Classic log detection
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
        for (call in calls) {
            LOG_PATTERNS.keys.forEach { pattern ->
                val methodExpressionText = call.methodExpression.text
                val matcher = pattern.matcher(methodExpressionText)
                if (matcher.find()) {
                    val level = matcher.group(1)
                    val message = getJavaLogMessage(call)
                    val document = file.viewProvider.document
                    val lineNumber = document?.getLineNumber(call.textOffset)?.plus(1) ?: -1

                    if (message != null) {
                        logs.add(LogEntry(
                            message = message,
                            level = LogLevel.fromString(level),
                            fileName = file.name,
                            lineNumber = lineNumber,
                            psiElement = call,
                            virtualFile = file.virtualFile,
                            score = computeScore(call, message)
                        ))
                    }
                }
            }
        }

        // Detection of catch blocks without logs
        val tryStatements = PsiTreeUtil.findChildrenOfType(file, PsiTryStatement::class.java)
        for (tryStmt in tryStatements) {
            for (catchSection in tryStmt.catchSections) {
                val catchBlock = catchSection.catchBlock ?: continue
                val logCalls = PsiTreeUtil.findChildrenOfType(catchBlock, PsiMethodCallExpression::class.java)
                val hasLog = logCalls.any { call ->
                    LOG_PATTERNS.keys.any { pattern ->
                        pattern.matcher(call.methodExpression.text).find()
                    }
                }
                if (!hasLog) {
                    val document = file.viewProvider.document
                    val lineNumber = document?.getLineNumber(catchBlock.textOffset)?.plus(1) ?: -1
                    logs.add(LogEntry(
                        message = "Catch without log",
                        level = LogLevel.MISSING,
                        fileName = file.name,
                        lineNumber = lineNumber,
                        psiElement = catchBlock,
                        virtualFile = file.virtualFile,
                        score = Score.NONE
                    ))
                }
            }
        }
    }

    private fun computeScore(call: PsiMethodCallExpression, templateMessage: String): Score {
        val arguments = call.argumentList.expressions

        // Basic checks
        if (templateMessage.isBlank()) return Score.POOR

        // Number of substitution arguments (all except first which is the template)
        val substitutionArgsCount = arguments.size - 1

        // Count {} placeholders
        val placeholderCount = "\\{\\}".toRegex().findAll(templateMessage).count()

        // Check if arguments contain complex expressions
        val hasComplexExpressions = arguments.drop(1).any { arg ->
            val text = arg.text
            text.contains(".") || text.contains("?") || text.contains(":") ||
                    !PsiTreeUtil.findChildrenOfType(arg, PsiLiteralExpression::class.java).any()
        }

        // Check message structure
        val hasStructuredFormat = templateMessage.contains(":") || templateMessage.contains("=") ||
                templateMessage.contains("-") || templateMessage.contains(",")

        // Check multiline format
        val hasMultilineFormat = templateMessage.contains("\n") || templateMessage.contains("\\n")

        return when {
            // Excellent: well structured with several placeholders used
            (placeholderCount >= 3 && substitutionArgsCount >= placeholderCount &&
                    (hasStructuredFormat || hasMultilineFormat)) -> Score.EXCELLENT

            // Rich: several placeholders or elaborated structure
            (placeholderCount >= 2 && substitutionArgsCount >= placeholderCount) ||
                    (placeholderCount > 0 && hasComplexExpressions && (hasStructuredFormat || hasMultilineFormat)) -> Score.RICH

            // Average: at least one placeholder or long message
            placeholderCount > 0 || templateMessage.length > 30 -> Score.AVERAGE

            // Poor: simple message
            else -> Score.POOR
        }
    }

    private fun getJavaLogMessage(call: PsiMethodCallExpression): String? {
        val arguments = call.argumentList.expressions
        if (arguments.isEmpty()) return null
        return when (val firstArg = arguments[0]) {
            is PsiLiteralExpression -> firstArg.value as? String
            else -> null
        }
    }
}
