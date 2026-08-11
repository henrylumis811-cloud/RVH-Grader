package com.henrylumis.rvhgrader.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SystemMode
import java.io.File
import java.io.FileOutputStream

/**
 * Generates print-ready PDFs using Android's built-in [PdfDocument] (Canvas-based) — no external
 * PDF library needed. Page size is A4 at 72dpi (595x842pt).
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun writeClassList(
        records: List<StudentRecord>,
        mode: SystemMode,
        className: String,
        termName: String,
        outFile: File
    ) {
        val subjects = GradingLogic.subjectsFor(mode)
        val document = PdfDocument()

        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        val subPaint = Paint().apply { textSize = 10f; color = Color.parseColor("#555555") }
        val headerPaint = Paint().apply { textSize = 9f; isFakeBoldText = true }
        val cellPaint = Paint().apply { textSize = 9f }
        val linePaint = Paint().apply { strokeWidth = 0.75f; color = Color.parseColor("#999999") }

        val nameColWidth = 170f
        val extraCols = subjects.size + 1 + if (mode != SystemMode.LOWER) 2 else 0
        val otherColWidth = (PAGE_WIDTH - MARGIN * 2 - nameColWidth) / extraCols
        fun colX(index: Int) = MARGIN + nameColWidth + otherColWidth * index

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawPageHeader() {
            canvas.drawText("HENRY LUMIS MAINFRAME", MARGIN, y, titlePaint)
            y += 18f
            canvas.drawText("$className — $termName — Class List", MARGIN, y, subPaint)
            y += 22f
        }

        fun drawTableHeader() {
            canvas.drawText("NAME", MARGIN, y, headerPaint)
            subjects.forEachIndexed { i, sub ->
                canvas.drawText(GradingLogic.subjectLabels[sub] ?: sub.uppercase(), colX(i), y, headerPaint)
            }
            var idx = subjects.size
            canvas.drawText("TOTAL", colX(idx), y, headerPaint)
            idx++
            if (mode != SystemMode.LOWER) {
                canvas.drawText("AGG", colX(idx), y, headerPaint)
                idx++
                canvas.drawText("DIV", colX(idx), y, headerPaint)
            }
            y += 6f
            canvas.drawLine(MARGIN, y, (PAGE_WIDTH - MARGIN), y, linePaint)
            y += 14f
        }

        drawPageHeader()
        drawTableHeader()

        records.forEachIndexed { rowIndex, r ->
            if (y > PAGE_HEIGHT - MARGIN - 20) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                drawTableHeader()
            }
            canvas.drawText("${rowIndex + 1}. ${r.name}", MARGIN, y, cellPaint)
            subjects.forEachIndexed { i, sub ->
                canvas.drawText((r.scores[sub]?.mark ?: 0).toString(), colX(i), y, cellPaint)
            }
            var idx = subjects.size
            canvas.drawText(r.total.toString(), colX(idx), y, cellPaint)
            idx++
            if (mode != SystemMode.LOWER) {
                canvas.drawText((r.aggSum ?: 0).toString(), colX(idx), y, cellPaint)
                idx++
                canvas.drawText(r.division ?: "-", colX(idx), y, cellPaint)
            }
            y += 16f
        }

        document.finishPage(page)
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
    }

    fun writeIndividualReport(record: StudentRecord, outFile: File) {
        val mode = record.schoolClass.mode
        val subjects = GradingLogic.subjectsFor(mode)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val subPaint = Paint().apply { textSize = 12f; color = Color.parseColor("#555555") }
        val labelPaint = Paint().apply { textSize = 11f; isFakeBoldText = true }
        val valuePaint = Paint().apply { textSize = 11f }
        val sectionPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val linePaint = Paint().apply { strokeWidth = 0.75f; color = Color.parseColor("#999999") }

        var y = MARGIN
        canvas.drawText("HENRY LUMIS MAINFRAME", MARGIN, y, titlePaint)
        y += 22f
        canvas.drawText("Learner Progress Report", MARGIN, y, subPaint)
        y += 34f

        canvas.drawText("Name:", MARGIN, y, labelPaint)
        canvas.drawText(record.name, MARGIN + 90, y, valuePaint)
        y += 18f
        canvas.drawText("Class:", MARGIN, y, labelPaint)
        canvas.drawText(record.schoolClass.displayName, MARGIN + 90, y, valuePaint)
        y += 18f
        canvas.drawText("Term:", MARGIN, y, labelPaint)
        canvas.drawText(record.term.displayName, MARGIN + 90, y, valuePaint)
        y += 32f

        canvas.drawText("SUBJECT BREAKDOWN", MARGIN, y, sectionPaint)
        y += 10f
        canvas.drawLine(MARGIN, y, (PAGE_WIDTH - MARGIN), y, linePaint)
        y += 20f

        subjects.forEach { sub ->
            val score = record.scores[sub]
            canvas.drawText(GradingLogic.subjectLabels[sub] ?: sub.uppercase(), MARGIN, y, labelPaint)
            canvas.drawText("${score?.mark ?: 0} / 100", MARGIN + 150, y, valuePaint)
            if (record.graded && score?.agg != null) {
                canvas.drawText("Aggregate: ${score.agg}", MARGIN + 260, y, valuePaint)
            }
            y += 20f
        }

        y += 10f
        canvas.drawLine(MARGIN, y, (PAGE_WIDTH - MARGIN), y, linePaint)
        y += 24f

        canvas.drawText("TOTAL MARKS: ${record.total}", MARGIN, y, sectionPaint)
        y += 22f
        if (record.graded) {
            canvas.drawText("AGGREGATE: ${record.aggSum ?: 0}", MARGIN, y, sectionPaint)
            y += 22f
            canvas.drawText("DIVISION: ${record.division ?: "-"}", MARGIN, y, sectionPaint)
            y += 22f
        }

        val signatureY = PAGE_HEIGHT - MARGIN - 40
        canvas.drawLine(MARGIN, signatureY, MARGIN + 170f, signatureY, linePaint)
        canvas.drawText("Teacher's Signature", MARGIN, signatureY + 14, subPaint)

        document.finishPage(page)
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
    }
}
