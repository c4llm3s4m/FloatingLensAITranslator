package com.floatinglens.floatinglensaitranslator.translation

import android.content.Context
import android.util.Log
import com.floatinglens.floatinglensaitranslator.plugin.TranslationPluginManager
import com.google.mlkit.nl.translate.TranslateLanguage

enum class TranslationProvider {
    QWEN_AI_PLUGIN,
    ML_KIT_OFFLINE,
    ML_KIT_FALLBACK
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val providerUsed: TranslationProvider,
    val sourceLang: String,
    val targetLang: String,
    val isFallback: Boolean = false,
    val errorMessage: String? = null,
    val executionTimeMs: Long = 0L
)

class UnifiedTranslationManager(
    private val context: Context,
    val pluginManager: TranslationPluginManager = TranslationPluginManager(context),
    val offlineEngine: OfflineTranslationEngine = OfflineTranslationEngine()
) {

    companion object {
        private const val TAG = "UnifiedTranslationManager"
    }

    var preferPlugin: Boolean = true

    fun initPluginBinding() {
        pluginManager.bindPluginService()
    }

    suspend fun translate(
        text: String,
        sourceLang: String = TranslateLanguage.JAPANESE,
        targetLang: String = TranslateLanguage.ENGLISH
    ): TranslationResult {
        if (text.isBlank()) {
            val defaultProvider = if (preferPlugin && pluginManager.isAvailable()) {
                TranslationProvider.QWEN_AI_PLUGIN
            } else {
                TranslationProvider.ML_KIT_OFFLINE
            }
            return TranslationResult(
                originalText = text,
                translatedText = "",
                providerUsed = defaultProvider,
                sourceLang = sourceLang,
                targetLang = targetLang
            )
        }

        val startTime = System.currentTimeMillis()
        var pluginAttempted = false

        if (preferPlugin && pluginManager.isAvailable()) {
            pluginAttempted = true
            Log.d(TAG, "Attempting translation via Companion AI Plugin...")
            try {
                val pluginResult = pluginManager.translate(text, sourceLang, targetLang)
                val isUntranslated = isUntranslatedCjk(pluginResult, targetLang)

                if (!pluginResult.isNullOrBlank() && !isUntranslated) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Companion AI Plugin translation succeeded in ${elapsedTime}ms")
                    return TranslationResult(
                        originalText = text,
                        translatedText = pluginResult,
                        providerUsed = TranslationProvider.QWEN_AI_PLUGIN,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        isFallback = false,
                        executionTimeMs = elapsedTime
                    )
                } else {
                    Log.w(TAG, "Companion AI Plugin returned null, empty, or untranslated Japanese. Triggering offline fallback.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Companion AI Plugin translation error: ${e.message}. Triggering offline fallback.", e)
            }
        } else {
            Log.d(TAG, "Companion AI Plugin unavailable or disabled. Using offline ML Kit translate directly.")
        }

        Log.d(TAG, "Executing ML Kit Offline Translate...")
        val providerForOffline = if (pluginAttempted) TranslationProvider.ML_KIT_FALLBACK else TranslationProvider.ML_KIT_OFFLINE
        return try {
            val offlineResult = offlineEngine.translate(text, sourceLang, targetLang)
            val finalResultText = if (offlineResult.isNotBlank()) offlineResult else text
            val elapsedTime = System.currentTimeMillis() - startTime
            TranslationResult(
                originalText = text,
                translatedText = finalResultText,
                providerUsed = providerForOffline,
                sourceLang = sourceLang,
                targetLang = targetLang,
                isFallback = pluginAttempted,
                executionTimeMs = elapsedTime
            )
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "Both companion plugin and offline ML Kit translate failed: ${e.message}", e)
            TranslationResult(
                originalText = text,
                translatedText = text,
                providerUsed = providerForOffline,
                sourceLang = sourceLang,
                targetLang = targetLang,
                isFallback = pluginAttempted,
                errorMessage = e.message ?: "Translation failed",
                executionTimeMs = elapsedTime
            )
        }
    }

    private fun isUntranslatedCjk(result: String?, targetLang: String): Boolean {
        if (result.isNullOrBlank()) return true
        if (targetLang != TranslateLanguage.ENGLISH) return false

        val clean = result
            .removePrefix("[Qwen LLM]")
            .removePrefix("[Qwen2.5 Neural AI]")
            .removePrefix("[Qwen2.5]")
            .removePrefix("[Qwen Companion]")
            .removePrefix("[Qwen]")
            .removePrefix("[MediaPipe GenAI .task]")
            .removePrefix("[MediaPipe]")
            .removePrefix("[AI Plugin]")
            .trim()

        val hasEnglishLetters = clean.any { (it in 'a'..'z') || (it in 'A'..'Z') }
        if (hasEnglishLetters) return false

        return containsCjk(clean)
    }

    private fun containsCjk(text: String): Boolean {
        return text.any { ch ->
            val block = Character.UnicodeBlock.of(ch)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.HIRAGANA ||
                    block == Character.UnicodeBlock.KATAKANA ||
                    block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                    block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
                    block == Character.UnicodeBlock.HANGUL_SYLLABLES
        }
    }

    fun close() {
        try {
            pluginManager.unbindPluginService()
            offlineEngine.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UnifiedTranslationManager: ${e.message}", e)
        }
    }
}
