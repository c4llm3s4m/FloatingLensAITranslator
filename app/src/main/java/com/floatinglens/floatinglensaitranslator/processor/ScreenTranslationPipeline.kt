package com.floatinglens.floatinglensaitranslator.processor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.floatinglens.floatinglensaitranslator.ocr.MultiScriptOcrEngine
import com.floatinglens.floatinglensaitranslator.ocr.OcrResult
import com.floatinglens.floatinglensaitranslator.ocr.OcrScript
import com.floatinglens.floatinglensaitranslator.ocr.SpeechBubbleSegmenter
import com.floatinglens.floatinglensaitranslator.ocr.SpeechBubbleUnit
import com.floatinglens.floatinglensaitranslator.translation.TranslationProvider
import com.floatinglens.floatinglensaitranslator.translation.UnifiedTranslationManager
import com.google.mlkit.nl.translate.TranslateLanguage

data class TranslatedBubble(
    val bubbleUnit: SpeechBubbleUnit,
    val originalText: String,
    val translatedText: String,
    val translationProvider: TranslationProvider,
    val isFallback: Boolean
)

data class TranslationMetadata(
    val providerUsed: TranslationProvider,
    val bubbleCount: Int
)

fun determineOverallProvider(
    bubbles: List<TranslatedBubble>,
    preferPlugin: Boolean = true,
    pluginAvailable: Boolean = false
): TranslationProvider {
    if (bubbles.any { it.translationProvider == TranslationProvider.ML_KIT_FALLBACK }) {
        return TranslationProvider.ML_KIT_FALLBACK
    }
    if (bubbles.isNotEmpty() && bubbles.all { it.translationProvider == TranslationProvider.QWEN_AI_PLUGIN }) {
        return TranslationProvider.QWEN_AI_PLUGIN
    }
    if (bubbles.any { it.translationProvider == TranslationProvider.QWEN_AI_PLUGIN }) {
        return TranslationProvider.QWEN_AI_PLUGIN
    }
    if (preferPlugin && pluginAvailable) {
        return TranslationProvider.QWEN_AI_PLUGIN
    }
    return TranslationProvider.ML_KIT_OFFLINE
}

data class ProcessedTranslationResult(
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val ocrResult: OcrResult,
    val bubbles: List<TranslatedBubble>,
    val totalTimeMs: Long,
    val metadata: TranslationMetadata = TranslationMetadata(
        providerUsed = determineOverallProvider(bubbles),
        bubbleCount = bubbles.size
    )
) {
    val providerUsed: TranslationProvider get() = metadata.providerUsed
    val bubbleCount: Int get() = metadata.bubbleCount
}

class ScreenTranslationPipeline(
    private val context: Context,
    val ocrEngine: MultiScriptOcrEngine = MultiScriptOcrEngine(),
    val segmenter: SpeechBubbleSegmenter = SpeechBubbleSegmenter(),
    val translationManager: UnifiedTranslationManager = UnifiedTranslationManager(context)
) {

    companion object {
        private const val TAG = "ScreenTranslationPipeline"
    }

    fun init() {
        translationManager.initPluginBinding()
    }

    suspend fun processFrame(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.AUTO,
        sourceLang: String = TranslateLanguage.JAPANESE,
        targetLang: String = TranslateLanguage.ENGLISH
    ): ProcessedTranslationResult {
        val startTime = System.currentTimeMillis()

        val ocrResult = ocrEngine.processImage(bitmap, script)

        val bubbles = segmenter.segmentSpeechBubbles(ocrResult)

        val translatedBubbles = bubbles.map { bubble ->
            val transResult = translationManager.translate(
                text = bubble.mergedText,
                sourceLang = sourceLang,
                targetLang = targetLang
            )

            TranslatedBubble(
                bubbleUnit = bubble,
                originalText = bubble.mergedText,
                translatedText = transResult.translatedText,
                translationProvider = transResult.providerUsed,
                isFallback = transResult.isFallback
            )
        }

        val totalTime = System.currentTimeMillis() - startTime
        val bubbleCount = translatedBubbles.size
        val overallProvider = determineOverallProvider(
            bubbles = translatedBubbles,
            preferPlugin = translationManager.preferPlugin,
            pluginAvailable = translationManager.pluginManager.isAvailable()
        )
        val metadata = TranslationMetadata(
            providerUsed = overallProvider,
            bubbleCount = bubbleCount
        )

        Log.d(
            TAG,
            "Screen translation pipeline finished in ${totalTime}ms. Processed ${translatedBubbles.size} bubbles. Provider used: $overallProvider."
        )

        return ProcessedTranslationResult(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            ocrResult = ocrResult,
            bubbles = translatedBubbles,
            totalTimeMs = totalTime,
            metadata = metadata
        )
    }

    fun close() {
        ocrEngine.close()
        translationManager.close()
    }
}
