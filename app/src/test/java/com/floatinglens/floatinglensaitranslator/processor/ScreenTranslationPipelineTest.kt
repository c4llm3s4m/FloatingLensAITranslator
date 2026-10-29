package com.floatinglens.floatinglensaitranslator.processor

import android.graphics.Rect
import com.floatinglens.floatinglensaitranslator.ocr.OcrResult
import com.floatinglens.floatinglensaitranslator.ocr.OcrScript
import com.floatinglens.floatinglensaitranslator.ocr.SpeechBubbleUnit
import com.floatinglens.floatinglensaitranslator.translation.TranslationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTranslationPipelineTest {

    @Test
    fun testDetermineOverallProvider_qwenPlugin() {
        val bubble = TranslatedBubble(
            bubbleUnit = SpeechBubbleUnit(
                lines = emptyList(),
                boundingBox = Rect(0, 0, 10, 10),
                mergedText = "Test"
            ),
            originalText = "Test",
            translatedText = "Test Trans",
            translationProvider = TranslationProvider.QWEN_AI_PLUGIN,
            isFallback = false
        )

        val provider = determineOverallProvider(listOf(bubble))
        assertEquals(TranslationProvider.QWEN_AI_PLUGIN, provider)
    }

    @Test
    fun testDetermineOverallProvider_mlKitFallback() {
        val bubble1 = TranslatedBubble(
            bubbleUnit = SpeechBubbleUnit(
                lines = emptyList(),
                boundingBox = Rect(0, 0, 10, 10),
                mergedText = "Test 1"
            ),
            originalText = "Test 1",
            translatedText = "Trans 1",
            translationProvider = TranslationProvider.QWEN_AI_PLUGIN,
            isFallback = false
        )
        val bubble2 = TranslatedBubble(
            bubbleUnit = SpeechBubbleUnit(
                lines = emptyList(),
                boundingBox = Rect(10, 10, 20, 20),
                mergedText = "Test 2"
            ),
            originalText = "Test 2",
            translatedText = "Trans 2",
            translationProvider = TranslationProvider.ML_KIT_FALLBACK,
            isFallback = true
        )

        val provider = determineOverallProvider(listOf(bubble1, bubble2))
        assertEquals(TranslationProvider.ML_KIT_FALLBACK, provider)
    }

    @Test
    fun testDetermineOverallProvider_mlKitOffline() {
        val bubble = TranslatedBubble(
            bubbleUnit = SpeechBubbleUnit(
                lines = emptyList(),
                boundingBox = Rect(0, 0, 10, 10),
                mergedText = "Test"
            ),
            originalText = "Test",
            translatedText = "Trans",
            translationProvider = TranslationProvider.ML_KIT_OFFLINE,
            isFallback = false
        )

        val provider = determineOverallProvider(
            bubbles = listOf(bubble),
            preferPlugin = false,
            pluginAvailable = false
        )
        assertEquals(TranslationProvider.ML_KIT_OFFLINE, provider)
    }

    @Test
    fun testProcessedTranslationResultMetadata() {
        val bubble = TranslatedBubble(
            bubbleUnit = SpeechBubbleUnit(
                lines = emptyList(),
                boundingBox = Rect(0, 0, 10, 10),
                mergedText = "Hello"
            ),
            originalText = "Hello",
            translatedText = "Bonjour",
            translationProvider = TranslationProvider.QWEN_AI_PLUGIN,
            isFallback = false
        )

        val result = ProcessedTranslationResult(
            bitmapWidth = 100,
            bitmapHeight = 100,
            ocrResult = OcrResult(
                fullText = "Hello",
                blocks = emptyList(),
                scriptUsed = OcrScript.AUTO
            ),
            bubbles = listOf(bubble),
            totalTimeMs = 150L,
            metadata = TranslationMetadata(
                providerUsed = TranslationProvider.QWEN_AI_PLUGIN,
                bubbleCount = 1
            )
        )

        assertEquals(TranslationProvider.QWEN_AI_PLUGIN, result.providerUsed)
        assertEquals(1, result.bubbleCount)
        assertEquals(TranslationProvider.QWEN_AI_PLUGIN, result.metadata.providerUsed)
        assertEquals(1, result.metadata.bubbleCount)
    }
}
