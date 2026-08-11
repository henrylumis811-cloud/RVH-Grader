package com.henrylumis.rvhgrader.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SystemMode
import java.io.File

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    EXCEL("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("CSV", "csv", "text/csv")
}

object ExportManager {

    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Exports a whole class/term as a class list. Returns the created file. */
    fun exportClassList(
        context: Context,
        records: List<StudentRecord>,
        mode: SystemMode,
        className: String,
        termName: String,
        format: ExportFormat
    ): File {
        val fileName = "${safeFileName(className)}_${safeFileName(termName)}_ClassList.${format.extension}"
        val outFile = File(exportsDir(context), fileName)
        when (format) {
            ExportFormat.CSV -> CsvExporter.writeClassList(records, mode, outFile)
            ExportFormat.EXCEL -> XlsxExporter.writeClassList(records, mode, outFile)
            ExportFormat.PDF -> PdfExporter.writeClassList(records, mode, className, termName, outFile)
        }
        return outFile
    }

    /** Exports a single learner's report. Returns the created file. */
    fun exportIndividual(context: Context, record: StudentRecord, format: ExportFormat): File {
        val fileName = "${safeFileName(record.name)}_${safeFileName(record.term.displayName)}_Report.${format.extension}"
        val outFile = File(exportsDir(context), fileName)
        when (format) {
            ExportFormat.CSV -> CsvExporter.writeIndividual(record, outFile)
            ExportFormat.EXCEL -> XlsxExporter.writeIndividual(record, outFile)
            ExportFormat.PDF -> PdfExporter.writeIndividualReport(record, outFile)
        }
        return outFile
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
