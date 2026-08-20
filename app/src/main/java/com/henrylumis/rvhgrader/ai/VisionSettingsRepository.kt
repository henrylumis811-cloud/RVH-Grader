package com.henrylumis.rvhgrader.ai

import android.content.Context

data class VisionSettings(
    val enabled: Boolean,
    val apiKey: String,
    val model: String
)

/**
 * AI Vision OCR is opt-in and off by default — the app still works fully offline with the
 * bundled ML Kit OCR either way. This just remembers whether a teacher has chosen to use their
 * own Anthropic API key for photos instead, and what to use for it.
 *
 * NOTE ON STORAGE: the API key is stored in this app's private SharedPreferences, which other
 * apps can't read without root access — but it isn't encrypted at rest. Don't share your device
 * or a screen recording with this key visible, and never commit it anywhere.
 */
object VisionSettingsRepository {
    private const val PREFS = "rvh_vision_settings"
    private const val KEY_ENABLED = "ai_vision_enabled"
    private const val KEY_API_KEY = "ai_vision_api_key"
    private const val KEY_MODEL = "ai_vision_model"

    const val DEFAULT_MODEL = "claude-sonnet-4-5-20250929"

    fun load(context: Context): VisionSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return VisionSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        )
    }

    fun save(context: Context, settings: VisionSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .apply()
    }
}
