package com.henrylumis.rvhgrader.data

import android.content.Context
import android.net.Uri
import com.henrylumis.rvhgrader.grading.GradingScaleRepository
import com.henrylumis.rvhgrader.model.GradingScale
import com.henrylumis.rvhgrader.model.StudentRecord
import org.json.JSONObject

data class BackupPayload(
    val records: List<StudentRecord>,
    val gradingScale: GradingScale
)

/**
 * Writes/reads a single portable backup file containing the whole gradebook plus the teacher's
 * custom grading scale — separate from the silent autosave in [GradebookRepository], this is the
 * one meant to be shared, moved to a new phone, or kept somewhere safe.
 */
object BackupManager {

    private const val BACKUP_VERSION = 1

    fun buildBackupJson(records: List<StudentRecord>, gradingScale: GradingScale): String {
        val root = JSONObject()
        root.put("backupVersion", BACKUP_VERSION)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("records", org.json.JSONArray(GradebookRepository.recordsToJson(records)))
        root.put("gradingScale", JSONObject(GradingScaleRepository.toJson(gradingScale)))
        return root.toString(2)
    }

    fun parseBackupJson(json: String): BackupPayload {
        val root = JSONObject(json)
        val records = GradebookRepository.recordsFromJson(root.getJSONArray("records").toString())
        val gradingScale = GradingScaleRepository.fromJson(root.getJSONObject("gradingScale").toString())
        return BackupPayload(records, gradingScale)
    }

    fun writeTo(context: Context, uri: Uri, records: List<StudentRecord>, gradingScale: GradingScale) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(buildBackupJson(records, gradingScale).toByteArray())
        }
    }

    fun readFrom(context: Context, uri: Uri): BackupPayload {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("Could not read the selected file.")
        return parseBackupJson(text)
    }
}
