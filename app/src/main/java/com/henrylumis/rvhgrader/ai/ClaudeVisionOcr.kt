package com.henrylumis.rvhgrader.ai

import android.graphics.Bitmap
import android.util.Base64
import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.SystemMode
import com.henrylumis.rvhgrader.ocr.ExtractedRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class VisionOcrException(message: String) : Exception(message)

/**
 * Reads a class-list photo using Claude's vision API instead of the on-device ML Kit OCR. Opt-in
 * only (see [VisionSettingsRepository]) — needs the teacher's own Anthropic API key and an
 * internet connection, and costs a small amount per scan, in exchange for dramatically better
 * handwriting reading than any on-device OCR can currently manage: a vision model actually looks
 * at the whole table and understands its structure, rather than reconstructing it from word
 * bounding-box geometry.
 */
object ClaudeVisionOcr {

    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val MAX_IMAGE_DIMENSION = 1568 // matches Anthropic's recommended max for vision input

    suspend fun extractRows(apiKey: String, model: String, bitmap: Bitmap, mode: SystemMode): List<ExtractedRow> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw VisionOcrException("No API key set — add one in Settings.")

            val base64Image = encodeForUpload(bitmap)
            val subjects = GradingLogic.subjectsFor(mode)
            val prompt = buildPrompt(subjects)

            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 4096)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "image")
                                put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                        })
                    }
                ))
            }

            val responseJson = try {
                postRequest(apiKey, requestBody)
            } catch (e: VisionOcrException) {
                throw e
            } catch (e: Exception) {
                throw VisionOcrException("Network error: ${e.message ?: "could not reach the API"}")
            }

            parseRows(responseJson, subjects)
        }

    private fun encodeForUpload(bitmap: Bitmap): String {
        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 87, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildPrompt(subjects: List<String>): String {
        val fieldList = subjects.joinToString(", ") { "\"$it\"" }
        return """
            This photo shows a handwritten class list: one row per learner, with a name and a mark
            (0-100) for each subject. Read every row you can find, including ones with messy or
            unclear handwriting — give your best reading rather than skipping a row.

            Respond with ONLY a JSON array, no other text, no markdown code fences, in exactly
            this shape:
            [{"name": "LEARNER NAME", "scores": {$fieldList}}]

            Rules:
            - Use these exact subject keys in "scores": $fieldList
            - If a particular subject's mark is illegible or missing for a learner, use null for
              that key rather than guessing a number.
            - Write each name in uppercase, as best you can read it.
            - Include every row you can identify, even if some marks are unclear.
            - Do not include the header row itself as a learner.
            - Output nothing except the JSON array — no explanation before or after it.
        """.trimIndent()
    }

    private fun postRequest(apiKey: String, body: JSONObject): String {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val errorMessage = try {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                }
                throw VisionOcrException(errorMessage ?: "API error (HTTP $responseCode)")
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRows(responseJson: String, subjects: List<String>): List<ExtractedRow> {
        val root = JSONObject(responseJson)
        val content = root.optJSONArray("content") ?: throw VisionOcrException("Unexpected API response shape.")

        val textBuilder = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") {
                textBuilder.append(block.optString("text"))
            }
        }

        val rawText = textBuilder.toString().trim()
        val jsonText = extractJsonArray(rawText)
            ?: throw VisionOcrException("Couldn't find a JSON list in the model's reply.")

        val array = JSONArray(jsonText)
        val rows = mutableListOf<ExtractedRow>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name").trim().uppercase()
            val scoresObj = obj.optJSONObject("scores") ?: JSONObject()

            val scores = mutableMapOf<String, Int?>()
            subjects.forEach { subject ->
                if (scoresObj.has(subject) && !scoresObj.isNull(subject)) {
                    val value = scoresObj.optInt(subject, -1)
                    if (value in 0..100) scores[subject] = value
                }
            }

            if (name.isEmpty() && scores.isEmpty()) continue
            // AI vision reads are generally much more reliable than on-device OCR, so only flag
            // a row if it's genuinely incomplete — no blanket "always double-check" flagging.
            val lowConfidence = name.isEmpty() || scores.size < subjects.size
            rows.add(ExtractedRow(name, scores, emptySet(), lowConfidence))
        }
        return rows
    }

    /** Strips any stray markdown fencing/preamble the model might add despite instructions. */
    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }
}
