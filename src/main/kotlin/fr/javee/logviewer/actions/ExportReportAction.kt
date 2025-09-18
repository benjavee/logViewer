package fr.javee.logviewer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import fr.javee.logviewer.service.LogFinderService
import fr.javee.logviewer.model.LogEntry

import com.lowagie.text.Document
import com.lowagie.text.DocumentException
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.Image
import com.lowagie.text.pdf.PdfPTable

import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.data.general.DefaultPieDataset

import java.io.File
import java.io.FileOutputStream
import javax.swing.JOptionPane
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.application.ApplicationManager
import java.awt.Desktop
import com.lowagie.text.Font
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.draw.LineSeparator
import org.jfree.chart.labels.StandardPieSectionLabelGenerator
import org.jfree.chart.plot.PiePlot
import java.text.NumberFormat

class ExportReportAction : AnAction("Export PDF report") {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val logs: List<LogEntry> = LogFinderService.getInstance(project).findAllLogs()
        val totalLogs = logs.size
        val exportDate = Date()

        // Ajout : calcul des logs existants et missing
        val missingLogs = logs.count { it.level.name == "MISSING" }
        val existingLogs = logs.count { it.level.name != "MISSING" }

        // Counts
        val levelCounts: Map<String, Int> = logs.groupingBy { it.level.name }.eachCount()
        val scoreCounts: Map<String, Int> = logs.groupingBy { it.score.name }.eachCount()
        val fileCounts: Map<String, Int> = logs.groupingBy { it.fileName }.eachCount()
        val topFiles = fileCounts.entries.sortedByDescending { it.value }.take(10)

        // Datasets (types explicites pour éviter problèmes d'inférence)
        val levelDataset: DefaultPieDataset<String> = DefaultPieDataset()
        levelCounts.entries.forEach { entry ->
            levelDataset.setValue(entry.key, entry.value)
        }
        val scoreDataset: DefaultPieDataset<String> = DefaultPieDataset()
        scoreCounts.entries.forEach { entry ->
            scoreDataset.setValue(entry.key, entry.value)
        }

        val levelChart: JFreeChart = ChartFactory.createPieChart("Volume by level", levelDataset, true, true, false)
        val scoreChart: JFreeChart = ChartFactory.createPieChart("Volume by score", scoreDataset, true, true, false)
        val numberFmt = NumberFormat.getIntegerInstance()
        val percentFmt = NumberFormat.getPercentInstance().apply { minimumFractionDigits = 1 }

        val levelPlot = levelChart.plot as PiePlot<*>
        levelPlot.setLabelGenerator(StandardPieSectionLabelGenerator("{0}: {1} ({2})", numberFmt, percentFmt))
        val scorePlot = scoreChart.plot as PiePlot<*>
        scorePlot.setLabelGenerator(StandardPieSectionLabelGenerator("{0}: {1} ({2})", numberFmt, percentFmt))

        val levelImg: BufferedImage = levelChart.createBufferedImage(480, 360)
        val scoreImg: BufferedImage = scoreChart.createBufferedImage(480, 360)

        val projectPath = project.basePath ?: System.getProperty("user.dir")
        val reportDir = File(projectPath, "logViewerReports").apply { if (!exists()) mkdirs() }
        val dateFormat = SimpleDateFormat("yyyyMMdd-HHmm")
        val dateStr = dateFormat.format(exportDate)
        val fileName = "${project.name}-logsReport-$dateStr.pdf"
        val pdfFile = File(reportDir, fileName)

        try {
            val document = Document(PageSize.A4, 36f, 36f, 48f, 48f)
            val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            document.addTitle("Logs report - ${project.name}")
            document.addAuthor("LogViewer Plugin")
            document.addCreationDate()
            document.open()

            createCover(document, project.name, totalLogs, existingLogs, missingLogs, exportDate, pdfFile)

            document.add(Paragraph("Top 10 classes/files with most logs", Font(Font.HELVETICA, 14f, Font.BOLD)))
            document.add(LineSeparator())
            document.add(Paragraph(" "))

            val table = PdfPTable(floatArrayOf(0.75f, 0.25f)).apply {
                setSpacingBefore(5f)
                setWidthPercentage(100f)
            }
            fun headerCell(txt: String) = PdfPCell(Phrase(txt, Font(Font.HELVETICA, 11f, Font.BOLD))).apply {
                horizontalAlignment = PdfPCell.ALIGN_CENTER
                backgroundColor = java.awt.Color(235, 235, 235)
            }
            fun bodyCell(txt: String, right: Boolean = false) =
                PdfPCell(Phrase(txt, Font(Font.HELVETICA, 10f, Font.NORMAL))).apply {
                    horizontalAlignment = if (right) PdfPCell.ALIGN_RIGHT else PdfPCell.ALIGN_LEFT
                }
            table.addCell(headerCell("Class / File"))
            table.addCell(headerCell("Log count"))
            topFiles.forEach { (file, count) ->
                table.addCell(bodyCell(file))
                table.addCell(bodyCell(count.toString(), right = true))
            }
            document.add(table)

            document.newPage()

            addPieChartSection(document, "Volume by level", levelImg)
            addPieChartSection(document, "Volume by score", scoreImg)

            document.close()
            writer.close()

            ApplicationManager.getApplication().invokeLater {
                val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(pdfFile)
                if (vFile != null) {
                    FileEditorManager.getInstance(project).openFile(vFile, true)
                } else if (Desktop.isDesktopSupported()) {
                    try { Desktop.getDesktop().open(pdfFile) } catch (_: Exception) {}
                }
                JOptionPane.showMessageDialog(null, "PDF report exported: ${pdfFile.absolutePath}")
            }
        } catch (ex: DocumentException) {
            JOptionPane.showMessageDialog(null, "PDF export error: ${ex.message}")
        } catch (ex: Exception) {
            JOptionPane.showMessageDialog(null, "PDF export error: ${ex.message}")
        }
    }


    private fun createCover(
        document: Document,
        projectName: String,
        total: Int,
        existing: Int,
        missing: Int,
        date: Date,
        pdfFile: File
    ) {
        val titleFont = Font(Font.HELVETICA, 20f, Font.BOLD)
        val labelFont = Font(Font.HELVETICA, 11f, Font.BOLD)
        val valueFont = Font(Font.HELVETICA, 11f, Font.NORMAL)
        document.add(Paragraph("Log analysis report", titleFont))
        document.add(Paragraph(" "))

        val infoTable = PdfPTable(floatArrayOf(0.40f, 0.60f)).apply {
            setWidthPercentage(100f)
            setSpacingBefore(5f)
            setSpacingAfter(10f)
        }
        fun cell(text: String, font: Font, gray: Boolean = false) = PdfPCell(Phrase(text, font)).apply {
            border = PdfPCell.NO_BORDER
            if (gray) backgroundColor = java.awt.Color(245, 245, 245)
            paddingBottom = 4f; paddingTop = 2f
        }
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        listOf(
            "Project" to projectName,
            "Export date" to sdf.format(date),
            "Existing logs" to existing.toString(),
            "Missing logs" to missing.toString()
        ).forEach { (k, v) ->
            infoTable.addCell(cell(k, labelFont, gray = true))
            infoTable.addCell(cell(v, valueFont))
        }
        document.add(infoTable)
        document.add(LineSeparator())
        document.add(Paragraph(" "))
    }

    private fun addPieChartSection(document: Document, title: String, img: java.awt.image.BufferedImage) {
        document.add(Paragraph(title, Font(Font.HELVETICA, 14f, Font.BOLD)))
        document.add(Paragraph(" "))
        document.add(Image.getInstance(img, null).apply {
            alignment = Image.MIDDLE
            scalePercent(85f)
        })
        document.add(Paragraph(" "))
    }

}
