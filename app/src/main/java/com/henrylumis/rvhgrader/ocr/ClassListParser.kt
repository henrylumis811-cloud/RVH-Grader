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
 * [ExtractedRow] per learner, built on ML Kit's word-level bounding boxes.
 *
 * STRATEGY: names are the anchor, not the whole row. A handheld photo is almost never level, and
 * the further right a token sits, the more a small camera tilt shifts its y-position — so trying
 * to cluster a WHOLE row (name + every score, spanning the sheet's full width) by raw vertical
 * position is fragile: tilt alone can push one learner's scores closer to the NEXT row's name
 * than their own. Names, by contrast, sit in a narrow column near the left edge, where tilt has
 * far less room to do damage — so rows are built from name-like words first, then every number is
 * matched to whichever name-row it's nearest to, with vertical tolerance that grows the further
 * right the number sits (absorbing unknown tilt without needing a clean header to calibrate off).
 *
 * DESIGN PRINCIPLE: never silently drop a row just because OCR was imperfect — the Review &
 * Correct screen exists specifically so a teacher can fix a bad read.
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

    // Column-header labels that aren't a subject and aren't a learner — if a row is made up of
    // just one of these, it's the "Name" column header, not a phantom extra learner.
    private val nonDataLabels = setOf(
        "NAME", "NAMES", "LEARNER", "LEARNERS", "STUDENT", "STUDENTS", "PUPIL", "PUPILS", "SN"
    )

    // How much extra vertical slack (as a fraction of horizontal distance from the name) to
    // allow when matching a number to a name-row. 0.35 means: for every 100px a number sits to
    // the right of the name it belongs to, allow up to 35px of extra vertical drift before
    // ruling it out — generous enough for real camera tilt, without being unlimited.
    private const val TILT_SLACK_PER_PX = 0.35f

    fun extractRows(visionText: Text, mode: SystemMode): List<ExtractedRow> {
        val tokens = flattenToTokens(visionText)
        if (tokens.isEmpty()) return emptyList()

        val activeFieldIds = GradingLogic.subjectsFor(mode)
        val nameLikeTokens = tokens.filter { isNameLikeToken(it.text) }
        val otherTokens = tokens.filter { !isNameLikeToken(it.text) }

        if (nameLikeTokens.isEmpty()) {
            // Nothing looked name-like at all — fall back to the old whole-row clustering as a
            // last resort so a photo with unusual formatting still gets *some* attempt.
            return extractRowsFallback(tokens, activeFieldIds)
        }

        val nameRows = mergeFragmentedRows(clusterIntoRows(nameLikeTokens, toleranceMultiplier = 0.85f))

        // Two kinds of rows need excluding from "these are learners": the subject-header row
        // (ENG, MTC, ...) — made of letters too, so it naturally becomes one of the name-rows
        // above — and the NAME COLUMN's own header ("Name", "Learner", ...), which used to get
        // treated as a phantom extra learner with no numbers of its own, and would then steal
        // nearby numbers away from a real learner's row right next to it.
        val excludedRowIndices = mutableSetOf<Int>()
        var columnMap: List<Pair<String, Float>>? = null
        nameRows.forEachIndexed { idx, row ->
            val matches = row.items.mapNotNull { tok ->
                matchSubjectAlias(tok.text, activeFieldIds)?.let { it to tok.cx }
            }
            if (matches.size >= 2 && columnMap == null) {
                columnMap = matches
                excludedRowIndices.add(idx)
            }
            val combinedLetters = row.items.joinToString("") { it.text.uppercase().filter { c -> c.isLetter() } }
            if (row.items.size <= 2 && combinedLetters in nonDataLabels) {
                excludedRowIndices.add(idx)
            }
        }

        // For each number, find the nearest name-row and assign it there — with NO rejection
        // threshold. A hard cutoff here was silently DROPPING numbers that didn't look "close
        // enough," which is a much worse outcome than a number ending up in a slightly wrong
        // row: a wrong-looking value is obvious and easy to fix in the review screen, but an
        // empty field looks identical whether OCR missed it or the learner actually scored 0.
        // Every number ML Kit reads should end up visible SOMEWHERE.
        val numericByRow = HashMap<Int, MutableList<Triple<Int, Float, Boolean>>>()
        otherTokens.forEach { tok ->
            val numResult = extractNumericValue(tok.text.replace(stripCharsRegex, "")) ?: return@forEach
            val (value, corrected) = numResult
            var bestIdx = -1
            var bestScore = Float.MAX_VALUE
            nameRows.forEachIndexed { idx, row ->
                if (idx in excludedRowIndices) return@forEachIndexed
                val rowAvgX = row.items.map { it.cx }.average().toFloat()
                val dx = (tok.cx - rowAvgX).coerceAtLeast(0f)
                val expectedDrift = dx * TILT_SLACK_PER_PX // how much y-drift camera tilt alone could explain
                val dy = abs(tok.cy - row.cy)
                val score = (dy - expectedDrift).coerceAtLeast(0f) // "excess" drift beyond what tilt explains
                if (score < bestScore) {
                    bestScore = score
                    bestIdx = idx
                }
            }
            if (bestIdx >= 0) {
                numericByRow.getOrPut(bestIdx) { mutableListOf() }.add(Triple(value, tok.cx, corrected))
            }
        }

        val results = mutableListOf<ExtractedRow>()
        nameRows.forEachIndexed { idx, row ->
            if (idx in excludedRowIndices) return@forEachIndexed
            val name = row.items.joinToString(" ") { cleanNameWord(it.text) }.trim().uppercase()
            val numericTokens = numericByRow[idx].orEmpty().sortedBy { it.second }

            val scores = mutableMapOf<String, Int?>()
            val flagged = mutableSetOf<String>()
            val cMap = columnMap

            if (cMap != null && numericTokens.size == cMap.size) {
                // Counts line up with the header exactly — pair by left-to-right order rather
                // than nearest-x-distance. This is far more reliable than distance matching when
                // a column's header word isn't perfectly centered above its own data column
                // (extremely common with handwritten, hand-ruled tables), which used to make two
                // numbers both look "nearest" to the same column and silently lose one of them.
                val sortedColumns = cMap.sortedBy { it.second }
                numericTokens.forEachIndexed { i, (value, _, corrected) ->
                    val fieldId = sortedColumns[i].first
                    scores[fieldId] = value
                    if (corrected) flagged.add(fieldId)
                }
            } else if (cMap != null) {
                // Counts don't match (OCR likely missed or invented a digit token) — fall back
                // to nearest-column matching per number, still never dropping a number outright.
                for ((value, x, corrected) in numericTokens) {
                    val best = cMap.minByOrNull { abs(it.second - x) } ?: continue
                    if (!scores.containsKey(best.first)) {
                        scores[best.first] = value
                        if (corrected) flagged.add(best.first)
                    }
                }
            } else {
                numericTokens.forEachIndexed { i, (value, _, corrected) ->
                    activeFieldIds.getOrNull(i)?.let { fieldId ->
                        scores[fieldId] = value
                        if (corrected) flagged.add(fieldId)
                    }
                }
            }

            if (name.isEmpty() && scores.isEmpty()) return@forEachIndexed
            val lowConfidence = name.isEmpty() || flagged.isNotEmpty() || scores.size < activeFieldIds.size
            results.add(ExtractedRow(name, scores, flagged, lowConfidence))
        }
        return results
    }

    /** Letters clearly outnumber digits — used to decide whether a word belongs to the name. */
    private fun isNameLikeToken(text: String): Boolean {
        val cleaned = text.replace(stripCharsRegex, "")
        if (cleaned.length < 2) return false
        val letters = cleaned.count { it.isLetter() }
        val digits = cleaned.count { it.isDigit() }
        return letters > digits
    }

    private fun cleanNameWord(text: String): String =
        text.replace(stripCharsRegex, "").filter { it.isLetter() || it == '\'' || it == '-' }

    /** Whole-image whole-row clustering, used only if nothing looked name-like at all. */
    private fun extractRowsFallback(tokens: List<Token>, activeFieldIds: List<String>): List<ExtractedRow> {
        val rows = clusterIntoRows(tokens, toleranceMultiplier = 0.9f)
        val results = mutableListOf<ExtractedRow>()
        rows.forEach { row ->
            if (row.items.size < 2) return@forEach
            val rawText = row.items.joinToString(" ") { it.text }.trim()
            if (rawText.isNotEmpty()) {
                results.add(ExtractedRow(rawText.uppercase(), emptyMap(), emptySet(), lowConfidence = true))
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

    private fun clusterIntoRows(tokens: List<Token>, toleranceMultiplier: Float): List<RowCluster> {
        val medianHeight = tokens.map { it.height }.sorted()[tokens.size / 2].coerceAtLeast(10)
        val rowTolerance = medianHeight * toleranceMultiplier

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

    /**
     * Real table rows tend to be roughly evenly spaced. If two adjacent row-clusters sit much
     * closer together than that typical spacing, they're far more likely to be two fragments of
     * the same physical row (e.g. a column-header label like "Name" that landed a hair off from
     * the subject headers next to it) than genuinely separate learners — merge them.
     */
    private fun mergeFragmentedRows(rows: List<RowCluster>): List<RowCluster> {
        if (rows.size < 3) return rows
        val gaps = (1 until rows.size).map { rows[it].cy - rows[it - 1].cy }.sorted()
        val medianGap = gaps[gaps.size / 2]
        if (medianGap <= 0f) return rows

        val merged = mutableListOf<RowCluster>()
        var current = rows[0]
        for (i in 1 until rows.size) {
            val gap = rows[i].cy - current.cy
            if (gap < medianGap * 0.45f) {
                current.items.addAll(rows[i].items)
                current.items.sortBy { it.cx }
                current.cy = current.items.map { it.cy }.average().toFloat()
            } else {
                merged.add(current)
                current = rows[i]
            }
        }
        merged.add(current)
        return merged
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
