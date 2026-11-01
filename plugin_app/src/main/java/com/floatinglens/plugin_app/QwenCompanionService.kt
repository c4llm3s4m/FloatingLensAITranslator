package com.floatinglens.plugin_app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.floatinglens.floatinglensaitranslator.plugin.ITranslationCallback
import com.floatinglens.floatinglensaitranslator.plugin.ITranslationPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QwenCompanionService : Service() {

    companion object {
        private const val TAG = "QwenCompanionService"
        const val PLUGIN_NAME = "Companion AI Engine (.task & .gguf)"
        const val PLUGIN_VERSION = 1
        const val PLUGIN_VERSION_STRING = "1.0.0"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var modelEngine: CompanionModelEngine

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "QwenCompanionService created")
        modelEngine = CompanionModelEngine(this)
        modelEngine.loadModel()
    }

    fun setModelEngineForTest(engine: CompanionModelEngine) {
        modelEngine = engine
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "QwenCompanionService destroyed")
    }

    private val binder = object : ITranslationPlugin.Stub() {
        override fun getPluginName(): String {
            return PLUGIN_NAME
        }

        override fun getPluginVersion(): Int {
            return PLUGIN_VERSION
        }

        override fun isAvailable(): Boolean {
            return true
        }

        override fun translate(text: String, sourceLang: String, targetLang: String): String {
            Log.d(TAG, "Sync translate called for text: '$text' ($sourceLang -> $targetLang)")
            return modelEngine.translateText(text, sourceLang, targetLang)
        }

        override fun translateAsync(
            text: String,
            sourceLang: String,
            targetLang: String,
            callback: ITranslationCallback?
        ) {
            Log.d(TAG, "Async translate called for text: '$text' ($sourceLang -> $targetLang)")
            serviceScope.launch {
                try {
                    if (text.isBlank()) {
                        callback?.onSuccess("")
                        return@launch
                    }

                    val translatedText = modelEngine.translateText(text, sourceLang, targetLang)
                    Log.d(TAG, "Async translation result for '$text': '$translatedText'")
                    callback?.onSuccess(translatedText)
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing async translation: ${e.message}", e)
                    try {
                        callback?.onError(500, e.message ?: "Translation error")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "QwenCompanionService bound with intent action: ${intent?.action}")
        return binder
    }
}
