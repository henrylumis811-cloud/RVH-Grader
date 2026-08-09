package com.henrylumis.rvhgrader.model

enum class SystemMode { LOWER, UPPER }

data class SubjectScore(
    val mark: Int,
    val agg: Int? = null
)

data class StudentRecord(
    val name: String,
    val scores: Map<String, SubjectScore>,
    val total: Int,
    val aggSum: Int?,
    val division: String?,
    val graded: Boolean
)
