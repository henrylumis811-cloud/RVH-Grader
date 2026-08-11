package com.henrylumis.rvhgrader.export

import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SystemMode
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-rolled minimal .xlsx writer. An .xlsx file is just a zip of a handful of small XML parts
 * — this writes exactly what Excel/Sheets need for a single plain data sheet, using inline
 * strings so no shared-strings table is required. Deliberately not using Apache POI: it's a
 * heavy dependency with a history of Android compatibility issues for something this simple.
 */
object XlsxExporter {

    fun writeClassList(records: List<StudentRecord>, mode: SystemMode, outFile: File) {
        val subjects = GradingLogic.subjectsFor(mode)
        val header = mutableListOf<Any>("NAME")
        subjects.forEach { header.add(GradingLogic.subjectLabels[it] ?: it.uppercase()) }
        header.add("TOTAL")
        if (mode != SystemMode.LOWER) {
            header.add("AGG")
            header.add("DIVISION")
        }

        val rows = mutableListOf<List<Any>>(header)
        records.forEach { r ->
            val row = mutableListOf<Any>(r.name)
            subjects.forEach { sub -> row.add(r.scores[sub]?.mark ?: 0) }
            row.add(r.total)
            if (mode != SystemMode.LOWER) {
                row.add(r.aggSum ?: 0)
                row.add(r.division ?: "-")
            }
            rows.add(row)
        }
        write(rows, outFile)
    }

    fun writeIndividual(record: StudentRecord, outFile: File) {
        val mode = record.schoolClass.mode
        val subjects = GradingLogic.subjectsFor(mode)
        val rows = mutableListOf<List<Any>>()
        rows.add(listOf("FIELD", "VALUE"))
        rows.add(listOf("Name", record.name))
        rows.add(listOf("Class", record.schoolClass.displayName))
        rows.add(listOf("Term", record.term.displayName))
        subjects.forEach { sub ->
            val label = GradingLogic.subjectLabels[sub] ?: sub.uppercase()
            rows.add(listOf(label, record.scores[sub]?.mark ?: 0))
        }
        rows.add(listOf("Total", record.total))
        if (record.graded) {
            rows.add(listOf("Aggregate", record.aggSum ?: 0))
            rows.add(listOf("Division", record.division ?: "-"))
        }
        write(rows, outFile)
    }

    private fun write(rows: List<List<Any>>, outFile: File) {
        FileOutputStream(outFile).use { fos ->
            ZipOutputStream(fos).use { zip ->
                writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES)
                writeEntry(zip, "_rels/.rels", RELS)
                writeEntry(zip, "xl/workbook.xml", WORKBOOK)
                writeEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
                writeEntry(zip, "xl/styles.xml", STYLES)
                writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(rows))
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Standard 1-indexed spreadsheet column naming: 0->A, 25->Z, 26->AA, ... */
    private fun colLetter(zeroBasedIndex: Int): String {
        var num = zeroBasedIndex + 1
        val sb = StringBuilder()
        while (num > 0) {
            val remainder = (num - 1) % 26
            sb.insert(0, ('A' + remainder))
            num = (num - 1) / 26
        }
        return sb.toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun sheetXml(rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIdx, row ->
            val r = rowIdx + 1
            sb.append("<row r=\"$r\">")
            row.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$r"
                if (cell is Int || cell is Double || cell is Long) {
                    sb.append("<c r=\"$ref\"><v>$cell</v></c>")
                } else {
                    sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${xmlEscape(cell.toString())}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
        "</workbook>"

    private const val WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>" +
        "<borders count=\"1\"><border/></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>" +
        "</styleSheet>"
}
