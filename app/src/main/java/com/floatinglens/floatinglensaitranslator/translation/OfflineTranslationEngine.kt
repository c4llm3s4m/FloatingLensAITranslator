package com.floatinglens.floatinglensaitranslator.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ModelDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

data class LanguageModelStatus(
    val languageCode: String,
    val languageName: String,
    val state: ModelDownloadState,
    val errorMessage: String? = null
)

open class OfflineTranslationEngine {

    companion object {
        private const val TAG = "OfflineTranslationEngine"

        val SUPPORTED_LANGUAGES = listOf(
            TranslateLanguage.JAPANESE to "Japanese",
            TranslateLanguage.KOREAN to "Korean",
            TranslateLanguage.CHINESE to "Chinese",
            TranslateLanguage.SPANISH to "Spanish",
            TranslateLanguage.ENGLISH to "English"
        )
    }

    private val modelManager by lazy { RemoteModelManager.getInstance() }
    private val translatorCache = ConcurrentHashMap<String, Translator>()

    private val _modelStatuses = MutableStateFlow<List<LanguageModelStatus>>(emptyList())
    val modelStatuses: StateFlow<List<LanguageModelStatus>> = _modelStatuses.asStateFlow()

    init {
        updateModelStatuses()
    }

    open fun updateModelStatuses() {
        val initialList = SUPPORTED_LANGUAGES.map { (code, name) ->
            LanguageModelStatus(
                languageCode = code,
                languageName = name,
                state = ModelDownloadState.NOT_DOWNLOADED
            )
        }
        _modelStatuses.value = initialList

        SUPPORTED_LANGUAGES.forEach { (code, name) ->
            val model = TranslateRemoteModel.Builder(code).build()
            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    val currentState = if (isDownloaded) ModelDownloadState.DOWNLOADED else ModelDownloadState.NOT_DOWNLOADED
                    updateSingleStatus(code, name, currentState)
                }
                .addOnFailureListener { error ->
                    updateSingleStatus(code, name, ModelDownloadState.ERROR, error.message)
                }
        }
    }

    private fun updateSingleStatus(
        code: String,
        name: String,
        state: ModelDownloadState,
        error: String? = null
    ) {
        val current = _modelStatuses.value.toMutableList()
        val index = current.indexOfFirst { it.languageCode == code }
        val newStatus = LanguageModelStatus(code, name, state, error)
        if (index >= 0) {
            current[index] = newStatus
        } else {
            current.add(newStatus)
        }
        _modelStatuses.value = current
    }

    suspend fun downloadModel(languageCode: String): Boolean {
        val langName = SUPPORTED_LANGUAGES.find { it.first == languageCode }?.second ?: languageCode
        updateSingleStatus(languageCode, langName, ModelDownloadState.DOWNLOADING)

        val model = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = DownloadConditions.Builder().build()

        return suspendCancellableCoroutine { continuation ->
            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    updateSingleStatus(languageCode, langName, ModelDownloadState.DOWNLOADED)
                    Log.d(TAG, "Successfully downloaded translation model: $languageCode")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                .addOnFailureListener { exception ->
                    updateSingleStatus(languageCode, langName, ModelDownloadState.ERROR, exception.message)
                    Log.e(TAG, "Failed to download translation model $languageCode: ${exception.message}", exception)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }
    }

    suspend fun deleteModel(languageCode: String): Boolean {
        val langName = SUPPORTED_LANGUAGES.find { it.first == languageCode }?.second ?: languageCode
        val model = TranslateRemoteModel.Builder(languageCode).build()

        return suspendCancellableCoroutine { continuation ->
            modelManager.deleteDownloadedModel(model)
                .addOnSuccessListener {
                    updateSingleStatus(languageCode, langName, ModelDownloadState.NOT_DOWNLOADED)
                    translatorCache.keys.filter { it.contains(languageCode) }.forEach { key ->
                        translatorCache.remove(key)?.close()
                    }
                    Log.d(TAG, "Successfully deleted translation model: $languageCode")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to delete translation model $languageCode: ${exception.message}", exception)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }
    }

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
