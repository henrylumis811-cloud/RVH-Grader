package com.henrylumis.rvhgrader.grading

import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SubjectScore
import com.henrylumis.rvhgrader.model.SystemMode

/**
 * Direct port of the scoring rules from the original R_V_H_Grader web app:
 * - Lower Primary: 7 subjects, not graded (no aggregate / division).
 * - Upper Primary: 4 subjects, graded with PLE-style aggregate + division.
 */
object GradingLogic {

    val lowerSubjects = listOf("eng", "mtc", "liti", "read", "writ", "re", "lug")
    val upperSubjects = listOf("eng", "mtc", "sci", "sst")

    val subjectLabels = mapOf(
        "eng" to "ENG",
        "mtc" to "MTC",
        "liti" to "LIT I",
        "read" to "READ",
        "writ" to "WRIT",
        "re" to "R.E",
        "lug" to "LUG",
        "sci" to "SCI",
        "sst" to "S.S.T"
    )

    fun subjectsFor(mode: SystemMode): List<String> =
        if (mode == SystemMode.LOWER) lowerSubjects else upperSubjects

    fun aggregateFor(score: Int): Int = when {
        score >= 90 -> 1
        score >= 80 -> 2
        score >= 70 -> 3
        score >= 60 -> 4
        score >= 50 -> 5
        score >= 45 -> 6
        score >= 40 -> 7
        score >= 35 -> 8
        else -> 9
    }

    fun divisionFor(mode: SystemMode, aggSum: Int, grossSum: Int): String {
        if (grossSum == 0) return "U"
        return if (mode == SystemMode.LOWER) {
            when {
                aggSum <= 14 -> "I"
                aggSum <= 28 -> "II"
                aggSum <= 42 -> "III"
                aggSum <= 56 -> "IV"
                else -> "U"
            }
        } else {
            when {
                aggSum in 4..12 -> "I"
                aggSum in 13..24 -> "II"
                aggSum in 25..28 -> "III"
                aggSum in 29..32 -> "IV"
                else -> "U"
            }
        }
    }

    /**
     * Builds a StudentRecord from raw text-field input, mirroring commitDataArray() in the
     * original app: clamps each mark to 0..100, sums totals, and only grades Upper Primary.
     */
    fun buildRecord(
        name: String,
        mode: SystemMode,
        rawValues: Map<String, String>
    ): StudentRecord {
        val subjects = subjectsFor(mode)
        val gradingApplies = mode != SystemMode.LOWER

        var runningTotal = 0
        var runningAggSum = 0
        val marksheet = mutableMapOf<String, SubjectScore>()

        for (subject in subjects) {
            val raw = rawValues[subject].orEmpty()
            val absoluteScore = raw.toIntOrNull()?.coerceIn(0, 100) ?: 0
            val agg = if (gradingApplies) aggregateFor(absoluteScore) else null
            marksheet[subject] = SubjectScore(absoluteScore, agg)
            runningTotal += absoluteScore
            if (gradingApplies && agg != null) runningAggSum += agg
        }

        val division = if (gradingApplies) divisionFor(mode, runningAggSum, runningTotal) else null

        return StudentRecord(
            name = name.trim().uppercase(),
            scores = marksheet,
            total = runningTotal,
            aggSum = if (gradingApplies) runningAggSum else null,
            division = division,
            graded = gradingApplies
        )
    }
}
