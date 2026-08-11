package com.henrylumis.rvhgrader.grading

import android.content.Context
import com.henrylumis.rvhgrader.model.AggregateBand
import com.henrylumis.rvhgrader.model.DivisionBand
import com.henrylumis.rvhgrader.model.GradingScale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the teacher's custom grading scale (if they've changed it from the default) to
 * SharedPreferences as JSON. Falls back to GradingScale.default() if nothing's been saved yet,
 * or if the saved data is somehow corrupt.
 */
object GradingScaleRepository {
    private const val PREFS = "rvh_grading_scale"
    private const val KEY = "scale_json"

    fun load(context: Context): GradingScale {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return GradingScale.default()
        return try {
            fromJson(json)
        } catch (e: Exception) {
            GradingScale.default()
        }
    }

    fun save(context: Context, scale: GradingScale) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, toJson(scale)).apply()
    }

    fun toJson(scale: GradingScale): String {
        val root = JSONObject()
        val aggArr = JSONArray()
        scale.aggregateBands.forEach {
            aggArr.put(JSONObject().put("minMark", it.minMark).put("aggregate", it.aggregate))
        }
        val divArr = JSONArray()
        scale.divisionBands.forEach {
            divArr.put(JSONObject().put("maxAggSum", it.maxAggSum).put("label", it.label))
        }
        root.put("aggregateBands", aggArr)
        root.put("divisionBands", divArr)
        return root.toString()
    }

    fun fromJson(json: String): GradingScale {
        val root = JSONObject(json)
        val aggArr = root.getJSONArray("aggregateBands")
        val aggBands = (0 until aggArr.length()).map {
            val o = aggArr.getJSONObject(it)
            AggregateBand(o.getInt("minMark"), o.getInt("aggregate"))
        }
        val divArr = root.getJSONArray("divisionBands")
        val divBands = (0 until divArr.length()).map {
            val o = divArr.getJSONObject(it)
            DivisionBand(o.getInt("maxAggSum"), o.getString("label"))
        }
        return GradingScale(aggBands, divBands)
    }
}
