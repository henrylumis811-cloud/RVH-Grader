package com.henrylumis.rvhgrader.data

import android.content.Context
import com.henrylumis.rvhgrader.model.SchoolClass
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SubjectScore
import com.henrylumis.rvhgrader.model.Term
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Auto-saves the full gradebook (every learner, across every class and term) to a JSON file in
 * the app's private storage so records survive app restarts — without this, Student History
 * and the whole point of Backup/Restore wouldn't mean anything. The same [recordsToJson] /
 * [recordsFromJson] pair is reused by the manual Backup & Restore screen for exporting/importing
 * to a user-chosen file.
 */
object GradebookRepository {
    private const val FILE_NAME = "gradebook.json"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<StudentRecord> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            recordsFromJson(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, records: List<StudentRecord>) {
        try {
            file(context).writeText(recordsToJson(records))
        } catch (e: Exception) {
            // Best-effort autosave; a failure here shouldn't crash the app mid-grading.
        }
    }

    fun recordsToJson(records: List<StudentRecord>): String {
        val arr = JSONArray()
        records.forEach { r -> arr.put(recordToJson(r)) }
        return arr.toString()
    }

    fun recordsFromJson(json: String): List<StudentRecord> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { recordFromJson(arr.getJSONObject(it)) }
    }

    private fun recordToJson(r: StudentRecord): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("name", r.name)
        o.put("schoolClass", r.schoolClass.name)
        o.put("term", r.term.name)
        o.put("total", r.total)
        o.put("aggSum", r.aggSum ?: JSONObject.NULL)
        o.put("division", r.division ?: JSONObject.NULL)
        o.put("graded", r.graded)
        o.put("recordedAtMillis", r.recordedAtMillis)
        val scoresObj = JSONObject()
        r.scores.forEach { (subject, score) ->
            val s = JSONObject()
            s.put("mark", score.mark)
            s.put("agg", score.agg ?: JSONObject.NULL)
            scoresObj.put(subject, s)
        }
        o.put("scores", scoresObj)
        return o
    }

    private fun recordFromJson(o: JSONObject): StudentRecord {
        val scoresObj = o.getJSONObject("scores")
        val scores = mutableMapOf<String, SubjectScore>()
        scoresObj.keys().forEach { key ->
            val s = scoresObj.getJSONObject(key)
            val agg = if (s.isNull("agg")) null else s.getInt("agg")
            scores[key] = SubjectScore(s.getInt("mark"), agg)
        }
        return StudentRecord(
            id = o.getString("id"),
            name = o.getString("name"),
            schoolClass = SchoolClass.valueOf(o.getString("schoolClass")),
            term = Term.valueOf(o.getString("term")),
            scores = scores,
            total = o.getInt("total"),
            aggSum = if (o.isNull("aggSum")) null else o.getInt("aggSum"),
            division = if (o.isNull("division")) null else o.getString("division"),
            graded = o.getBoolean("graded"),
            recordedAtMillis = o.optLong("recordedAtMillis", System.currentTimeMillis())
        )
    }
}
