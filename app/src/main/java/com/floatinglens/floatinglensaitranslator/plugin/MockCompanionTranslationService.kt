package com.floatinglens.floatinglensaitranslator.plugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.floatinglens.floatinglensaitranslator.translation.OfflineTranslationEngine
import kotlinx.coroutines.runBlocking

class MockCompanionTranslationService : Service() {

    companion object {
        private const val TAG = "MockCompanionTranslationService"
    }

    private val offlineEngine by lazy { OfflineTranslationEngine() }

    private val binder = object : ITranslationPlugin.Stub() {
        override fun getPluginName(): String {
            return "Qwen2.5-VL Companion AI Engine"
        }

        override fun getPluginVersion(): Int {
            return 1
        }

        override fun isAvailable(): Boolean {
            return true
        }

        override fun translate(text: String, sourceLang: String, targetLang: String): String {
            Log.d(TAG, "Sync translate called for text: $text ($sourceLang -> $targetLang)")
            return performTranslation(text, sourceLang, targetLang)
        }

        override fun translateAsync(
            text: String,
            sourceLang: String,
            targetLang: String,
            callback: ITranslationCallback?
        ) {
            Log.d(TAG, "Async translate called for text: $text ($sourceLang -> $targetLang)")
            try {
                if (text.isBlank()) {
                    callback?.onError(400, "Empty text provided")
                } else {
                    val result = performTranslation(text, sourceLang, targetLang)
                    callback?.onSuccess(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in async translate: ${e.message}", e)
                try {
                    callback?.onError(500, e.message ?: "Unknown service error")
                } catch (_: Exception) {}
            }
        }
    }

    private fun performTranslation(text: String, sourceLang: String, targetLang: String): String {
        if (text.isBlank()) return ""

        // Attempt offline ML Kit translation engine
        try {
            val mlKitResult = runBlocking {
                offlineEngine.translate(text, sourceLang, targetLang)
            }
            if (mlKitResult.isNotBlank()) {
                return mlKitResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit offline translation in MockCompanion Service failed for '$text': ${e.message}")
        }

        return text
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound with intent: $intent")
        return binder
    }
}
