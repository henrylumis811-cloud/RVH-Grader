package com.henrylumis.rvhgrader.ocr

import com.google.mlkit.vision.text.Text
import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.SystemMode
import kotlin.math.abs

data class ExtractedRow(
    val name: String,
    val scores: Map<String, Int?>,
    val flaggedFields: Set<String>,
    val lowConfidence: Boolean
)

/**
 * Splits a class-list photo (many learners, one row each, subject columns across) into one
 * [ExtractedRow] per learner. This is the multi-student equivalent of the old single-form
 * autofill — it ports the row-clustering / column-matching approach used in the web app's
 * Tesseract pipeline, rebuilt on top of ML Kit's word-level bounding boxes.
 *
 * Nothing here is auto-committed: the caller shows these as editable rows in a review screen
 * so a teacher can fix any misread name or mark (handwriting OCR is never going to be perfect —
 * that's what the review step is for).
 */
object ClassListParser {

    private data class Token(val text: String, val cx: Float, val cy: Float, val height: Int)
    private class RowCluster(var cy: Float, val items: MutableList<Token>)

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

    private val nameTokenRegex = Regex("^[A-Za-z.'-]{2,}$")
    private val stripCharsRegex = Regex("[|_~`]")
    private val aliasCleanupRegex = Regex("[^A-Z. ]")
    private val digitRunRegex = Regex("\\d{1,3}")

    fun extractRows(visionText: Text, mode: SystemMode): List<ExtractedRow> {
        val tokens = flattenToTokens(visionText)
        if (tokens.isEmpty()) return emptyList()

        val rows = clusterIntoRows(tokens)
        val activeFieldIds = GradingLogic.subjectsFor(mode)

        val (columnMap, headerRowIndex) = detectHeaderColumns(rows, activeFieldIds)

        val results = mutableListOf<ExtractedRow>()
        rows.forEachIndexed { idx, row ->
            if (idx == headerRowIndex) return@forEachIndexed
            val extracted = parseRow(row, activeFieldIds, columnMap) ?: return@forEachIndexed
            results.add(extracted)
        }
        return results
    }

    private fun flattenToTokens(visionText: Text): List<Token> {
        val tokens = mutableListOf<Token>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val text = element.text.trim()
                    if (text.isEmpty()) continue
                    tokens.add(Token(text, box.exactCenterX(), box.exactCenterY(), box.height()))
                }
            }
        }
        return tokens
    }

    private fun clusterIntoRows(tokens: List<Token>): List<RowCluster> {
        val medianHeight = tokens.map { it.height }.sorted()[tokens.size / 2].coerceAtLeast(10)
        val rowTolerance = medianHeight * 0.7f

        val rows = mutableListOf<RowCluster>()
        for (tok in tokens.sortedBy { it.cy }) {
            val cluster = rows.firstOrNull { abs(it.cy - tok.cy) <= rowTolerance }
            if (cluster != null) {
                cluster.items.add(tok)
                cluster.cy = cluster.items.map { it.cy }.average().toFloat()
            } else {
                rows.add(RowCluster(tok.cy, mutableListOf(tok)))
            }
        }
        rows.forEach { row -> row.items.sortBy { it.cx } }
        return rows.sortedBy { it.cy }
    }

    /** Looks for a header row (2+ words matching subject aliases) to anchor column x-positions. */
    private fun detectHeaderColumns(
        rows: List<RowCluster>,
        activeFieldIds: List<String>
    ): Pair<List<Pair<String, Float>>?, Int> {
        rows.forEachIndexed { idx, row ->
            val matches = row.items.mapNotNull { tok ->
                matchSubjectAlias(tok.text, activeFieldIds)?.let { it to tok.cx }
            }
            if (matches.size >= 2) return matches to idx
        }
        return null to -1
    }

    private fun parseRow(
        row: RowCluster,
        activeFieldIds: List<String>,
        columnMap: List<Pair<String, Float>>?
    ): ExtractedRow? {
        val nameTokens = mutableListOf<String>()
        // value, x-position, was a letter->digit correction applied
        val numericTokens = mutableListOf<Triple<Int, Float, Boolean>>()

        for (tok in row.items) {
            val cleaned = tok.text.replace(stripCharsRegex, "")
            if (nameTokenRegex.matches(cleaned)) {
                nameTokens.add(cleaned)
            } else {
                extractNumericValue(cleaned)?.let { (value, corrected) ->
                    numericTokens.add(Triple(value, tok.cx, corrected))
                }
            }
        }
        if (nameTokens.isEmpty() && numericTokens.size < 2) return null // stray page number etc.

        val name = nameTokens.joinToString(" ").uppercase()
        val scores = mutableMapOf<String, Int?>()
        val flagged = mutableSetOf<String>()

        if (columnMap != null) {
            for ((value, x, corrected) in numericTokens) {
                val best = columnMap.minByOrNull { abs(it.second - x) } ?: continue
                if (!scores.containsKey(best.first)) {
                    scores[best.first] = value
                    if (corrected) flagged.add(best.first)
                }
            }
        } else {
            val sortedNums = numericTokens.sortedBy { it.second }
            activeFieldIds.forEachIndexed { i, fieldId ->
                sortedNums.getOrNull(i)?.let { (value, _, corrected) ->
                    scores[fieldId] = value
                    if (corrected) flagged.add(fieldId)
                }
            }
        }

        val lowConfidence = nameTokens.isEmpty() || flagged.isNotEmpty() || scores.size < activeFieldIds.size
        return ExtractedRow(name, scores, flagged, lowConfidence)
    }

    private fun matchSubjectAlias(token: String, activeFieldIds: List<String>): String? {
        val upper = token.uppercase().replace(aliasCleanupRegex, "")
        if (upper.isEmpty()) return null
        for (fieldId in activeFieldIds) {
            val aliases = subjectAliases[fieldId] ?: continue
            for (alias in aliases) {
                if (upper == alias || upper.startsWith(alias)) return fieldId
            }
        }
        return null
    }

    /**
     * Corrects common handwriting/OCR letter-for-digit mix-ups (O->0, I/L->1, S->5, B->8, Z->2)
     * and returns the resulting 0-100 score, plus whether a correction was actually applied
     * (used to flag the cell as low-confidence for the review screen).
     */
    private fun extractNumericValue(token: String): Pair<Int, Boolean>? {
        val relevant = token.uppercase().filter { it.isDigit() || it in "OILSBZ" }
        if (relevant.isEmpty()) return null

        var corrected = false
        val fixed = buildString {
            for (ch in relevant) {
                val replacement = when (ch) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    'Z' -> '2'
                    else -> ch
                }
                if (replacement != ch) corrected = true
                append(replacement)
            }
        }

        val match = digitRunRegex.find(fixed) ?: return null
        val value = match.value.toIntOrNull() ?: return null
        if (value !in 0..100) return null
        return value to corrected
    }
}
