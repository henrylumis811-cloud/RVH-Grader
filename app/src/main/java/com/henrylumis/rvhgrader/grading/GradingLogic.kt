package com.henrylumis.rvhgrader.grading

import com.henrylumis.rvhgrader.model.GradingScale
import com.henrylumis.rvhgrader.model.SchoolClass
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SubjectScore
import com.henrylumis.rvhgrader.model.SystemMode
import com.henrylumis.rvhgrader.model.Term

/**
 * Direct port of the scoring rules from the original R_V_H_Grader web app:
 * - Lower Primary (P1-P3): 7 subjects, not graded (no aggregate / division).
 * - Upper Primary (P4-P7): 4 subjects, graded with PLE-style aggregate + division.
 *
 * The actual aggregate/division thresholds now live in [GradingScale] (teacher-editable from
 * Settings) instead of being hardcoded here — this object just knows the subject lists and how
 * to assemble a [StudentRecord] from raw input using whichever scale is passed in.
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

    /**
     * Builds a StudentRecord from raw text-field input, mirroring commitDataArray() in the
     * original app: clamps each mark to 0..100, sums totals, and only grades Upper Primary.
     */
    fun buildRecord(
        name: String,
        schoolClass: SchoolClass,
        term: Term,
        rawValues: Map<String, String>,
        scale: GradingScale
    ): StudentRecord {
        val mode = schoolClass.mode
        val subjects = subjectsFor(mode)
        val gradingApplies = mode != SystemMode.LOWER

        var runningTotal = 0
        var runningAggSum = 0
        val marksheet = mutableMapOf<String, SubjectScore>()

        for (subject in subjects) {
            val raw = rawValues[subject].orEmpty()
            val absoluteScore = raw.toIntOrNull()?.coerceIn(0, 100) ?: 0
            val agg = if (gradingApplies) scale.aggregateFor(absoluteScore) else null
            marksheet[subject] = SubjectScore(absoluteScore, agg)
            runningTotal += absoluteScore
            if (gradingApplies && agg != null) runningAggSum += agg
        }

        val division = if (gradingApplies) scale.divisionFor(runningAggSum, runningTotal) else null

        return StudentRecord(
            name = name.trim().uppercase(),
            schoolClass = schoolClass,
            term = term,
            scores = marksheet,
            total = runningTotal,
            aggSum = if (gradingApplies) runningAggSum else null,
            division = division,
            graded = gradingApplies
        )
    }
}
