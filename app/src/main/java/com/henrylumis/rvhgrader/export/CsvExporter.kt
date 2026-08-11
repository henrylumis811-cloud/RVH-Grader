package com.henrylumis.rvhgrader.export

import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SystemMode
import java.io.File

object CsvExporter {

    fun writeClassList(records: List<StudentRecord>, mode: SystemMode, outFile: File) {
        val subjects = GradingLogic.subjectsFor(mode)
        val header = mutableListOf("NAME")
        subjects.forEach { header.add(GradingLogic.subjectLabels[it] ?: it.uppercase()) }
        header.add("TOTAL")
        if (mode != SystemMode.LOWER) {
            header.add("AGG")
            header.add("DIVISION")
        }

        val sb = StringBuilder()
        sb.append(header.joinToString(",") { csvEscape(it) }).append("\n")
        records.forEach { r ->
            val row = mutableListOf(r.name)
            subjects.forEach { sub -> row.add((r.scores[sub]?.mark ?: 0).toString()) }
            row.add(r.total.toString())
            if (mode != SystemMode.LOWER) {
                row.add((r.aggSum ?: 0).toString())
                row.add(r.division ?: "-")
            }
            sb.append(row.joinToString(",") { csvEscape(it) }).append("\n")
        }
        outFile.writeText(sb.toString())
    }

    fun writeIndividual(record: StudentRecord, outFile: File) {
        val mode = record.schoolClass.mode
        val subjects = GradingLogic.subjectsFor(mode)
        val sb = StringBuilder()
        sb.append("FIELD,VALUE\n")
        sb.append("Name,${csvEscape(record.name)}\n")
        sb.append("Class,${csvEscape(record.schoolClass.displayName)}\n")
        sb.append("Term,${csvEscape(record.term.displayName)}\n")
        subjects.forEach { sub ->
            val label = GradingLogic.subjectLabels[sub] ?: sub.uppercase()
            sb.append("${csvEscape(label)},${record.scores[sub]?.mark ?: 0}\n")
        }
        sb.append("Total,${record.total}\n")
        if (record.graded) {
            sb.append("Aggregate,${record.aggSum ?: 0}\n")
            sb.append("Division,${record.division ?: "-"}\n")
        }
        outFile.writeText(sb.toString())
    }

    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
