package com.henrylumis.rvhgrader.ocr

import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.SystemMode

data class OcrParseResult(
    val name: String?,
    val scores: Map<String, Int>,
    val fieldsPopulated: Int
)

/**
 * Direct port of fillFieldsFromOcrText() from the original web app. Scans OCR'd text line by
 * line, matches known subject aliases (longest/most specific first), and pulls the first
 * plausible 0-100 mark following the alias. Also attempts to find the learner's name.
 */
object OcrFieldParser {

    private val subjectAliases = mapOf(
        "liti" to listOf("LITERACY 1", "LITERACY I", "LIT 1", "LIT I", "LITI"),
        "writ" to listOf("WRITING", "WRIT"),
        "read" to listOf("READING", "READ"),
        "sst" to listOf("S.S.T", "SST", "SOCIAL STUDIES"),
        "sci" to listOf("SCIENCE", "SCI"),
        "mtc" to listOf("MATHEMATICS", "MATH", "MTC"),
        "eng" to listOf("ENGLISH", "ENG"),
        "re" to listOf("R.E", "RELIGIOUS EDUCATION", "R E", "RE"),
        "lug" to listOf("LUGANDA", "LUG")
    )

    private val nameLineRegex = Regex("NAME[:\\s]+([A-Z .'-]{3,40})")
    private val plainNameRegex = Regex("^[A-Z .'-]{4,40}$")
    private val numberRegex = Regex("(\\d{1,3})")
    private val keywordRegex = Regex("NAME|SCHOOL|REPORT|TERM|CLASS|DIVISION|AGGREGATE")

    fun parse(rawText: String, mode: SystemMode): OcrParseResult {
        val text = rawText.uppercase()
        val lines = text.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }

        val activeFieldIds = GradingLogic.subjectsFor(mode)
        val scores = mutableMapOf<String, Int>()

        for (line in lines) {
            for (fieldId in activeFieldIds) {
                if (scores.containsKey(fieldId)) continue
                val aliases = subjectAliases[fieldId] ?: continue
                for (alias in aliases) {
                    val idx = line.indexOf(alias)
                    if (idx == -1) continue
                    val remainder = line.substring(idx + alias.length)
                    val match = numberRegex.find(remainder)
                    if (match != null) {
                        val value = match.groupValues[1].toIntOrNull()
                        if (value != null && value in 0..100) {
                            scores[fieldId] = value
                            break
                        }
                    }
                }
            }
        }

        val allSubjectWords = subjectAliases.values.flatten()
        val candidateName: String? = nameLineRegex.find(text)?.groupValues?.get(1)?.trim()
            ?: lines.firstOrNull { l ->
                plainNameRegex.matches(l) &&
                    allSubjectWords.none { l.contains(it) } &&
                    !keywordRegex.containsMatchIn(l)
            }

        val cleanName = candidateName?.replace(Regex("\\s+"), " ")?.trim()
        val fieldsPopulated = scores.size + if (cleanName != null) 1 else 0

        return OcrParseResult(cleanName, scores, fieldsPopulated)
    }
}
