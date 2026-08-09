package com.henrylumis.rvhgrader.ocr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Compose-observable holder for one row on the "Review & Correct" screen shown after scanning
 * a class-list photo. Every row starts pre-filled from OCR but stays fully editable — a teacher
 * can fix a misread name or mark, or uncheck a row entirely, before anything is committed to
 * the class list. Handwriting OCR is never going to be perfect; this review step is the safety
 * net, not an afterthought.
 */
class PendingRow(
    val id: Int,
    initialName: String,
    initialScores: Map<String, Int?>,
    val flaggedFields: Set<String>,
    val lowConfidence: Boolean
) {
    var name by mutableStateOf(initialName)
    var included by mutableStateOf(true)
    val scores: SnapshotStateMap<String, String> = mutableStateMapOf<String, String>().apply {
        initialScores.forEach { (field, value) -> put(field, value?.toString().orEmpty()) }
    }
}
