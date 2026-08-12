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
 * [ExtractedRow] per learner. Rebuilt on top of ML Kit's word-level bounding boxes.
 *
 * DESIGN PRINCIPLE: never silently drop a row just because OCR was imperfect. Handwriting OCR
 * is never going to be clean, and the Review & Correct screen exists specifically so a teacher
 * can fix a bad read — so the parser's job is to surface its best guess for every plausible row
 * (flagged low-confidence when unsure), not to only surface rows it's fully confident about.
 * A row only gets skipped if it's almost certainly not a learner row at all (e.g. a single lone
 * token, most likely a page number).
 */
object ClassListParser {

    private data class Token(val text: String, val cx: Float, val cy: Float, val height: Int)
    private class RowCluster(var cy: Float, val items: MutableList<Token>)

    private val subjectAliases = mapOf(
        "liti" to listOf("LITERACY 1", "LITERACY I", "LIT 1", "LIT I", "LITI", "LIT"),
        "writ" to listOf("WRITING", "WRIT"),
        "read" to listOf("READING", "READ"),
        "sst" to listOf("S.S.T", "SST", "SOCIAL STUDIES", "SOCIAL"),
        "sci" to listOf("SCIENCE", "SCI"),
        "mtc" to listOf("MATHEMATICS", "MATH", "MTC", "MATHS"),
        "eng" to listOf("ENGLISH", "ENG"),
        "re" to listOf("R.E", "RELIGIOUS EDUCATION", "R E", "RE", "REL"),
        "lug" to listOf("LUGANDA", "LUG")
    )

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

        // Last resort: OCR clearly read *something* (tokens isn't empty) but every row got
        // filtered out above. Rather than surfacing nothing at all, show one row per detected
        // line with its raw text as the name — fully editable, heavily flagged, but visible.
        if (results.isEmpty()) {
            rows.forEachIndexed { idx, row ->
                if (idx == headerRowIndex || row.items.isEmpty()) return@forEachIndexed
                val rawText = row.items.joinToString(" ") { it.text }.trim()
                if (rawText.isNotEmpty()) {
                    results.add(ExtractedRow(rawText.uppercase(), emptyMap(), emptySet(), lowConfidence = true))
                }
            }
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
        // Generous tolerance — handheld photos are rarely perfectly level, and being too strict
        // here just fragments one learner's row into several unusable half-rows.
        val rowTolerance = medianHeight * 1.1f

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
        // A single lone token on its own row is most likely a page number, index number, or
        // stray mark — not enough signal to be a learner row. Anything with 2+ tokens gets a
        // real attempt, even if the parse below ends up mostly empty.
        if (row.items.size < 2) return null

        val nameTokens = mutableListOf<String>()
        // value, x-position, was a letter->digit correction applied
        val numericTokens = mutableListOf<Triple<Int, Float, Boolean>>()

        for (tok in row.items) {
            val cleaned = tok.text.replace(stripCharsRegex, "")
            if (cleaned.isEmpty()) continue

            val letterCount = cleaned.count { it.isLetter() }
            val digitCount = cleaned.count { it.isDigit() }

            // Classify by which character type dominates the token, rather than demanding a
            // perfect full match — a name word with one OCR-noise character should still read
            // as a name, and a score with one stray letter should still read as a number.
            if (letterCount >= digitCount && letterCount >= 1 && cleaned.length >= 2) {
                nameTokens.add(cleaned.filter { it.isLetter() || it == '\'' || it == '-' })
            } else {
                extractNumericValue(cleaned)?.let { (value, corrected) ->
                    numericTokens.add(Triple(value, tok.cx, corrected))
                }
            }
        }

        val name = nameTokens.joinToString(" ") { it.uppercase() }.trim()
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

        val lowConfidence = name.isEmpty() || flagged.isNotEmpty() || scores.size < activeFieldIds.size
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
