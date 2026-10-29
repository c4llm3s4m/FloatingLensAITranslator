package com.floatinglens.plugin_app.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class OfflineTranslationEngine {

    companion object {
        private const val TAG = "OfflineTranslationEngine"
    }

    private val modelManager by lazy { RemoteModelManager.getInstance() }
    private val translatorCache = ConcurrentHashMap<String, Translator>()

    open suspend fun translate(
        text: String,
        sourceLang: String = TranslateLanguage.JAPANESE,
        targetLang: String = TranslateLanguage.ENGLISH
    ): String {
        if (text.isBlank()) return ""

        val translator = getOrCreateTranslator(sourceLang, targetLang)

        ensureModelDownloaded(sourceLang)
        ensureModelDownloaded(targetLang)

        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    if (continuation.isActive) {
                        continuation.resume(translatedText)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "ML Kit translation failed: ${exception.message}", exception)
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }
    }

    private suspend fun downloadModel(languageCode: String): Boolean {
        val model = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = DownloadConditions.Builder().build()

        return suspendCancellableCoroutine { continuation ->
            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully downloaded translation model: $languageCode")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to download translation model $languageCode: ${exception.message}", exception)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }
    }

    private suspend fun ensureModelDownloaded(langCode: String) {
        val model = TranslateRemoteModel.Builder(langCode).build()
        val isDownloaded = suspendCancellableCoroutine<Boolean> { cont ->
            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(false) }
        }

        if (!isDownloaded) {
            downloadModel(langCode)
        }
    }

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator {
        val key = "${sourceLang}_$targetLang"
        return translatorCache.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }

    fun close() {
        translatorCache.values.forEach {
            try {
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing translator: ${e.message}", e)
            }
        }
        translatorCache.clear()
    }
}
