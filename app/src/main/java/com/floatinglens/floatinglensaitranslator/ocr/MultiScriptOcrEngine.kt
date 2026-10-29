package com.floatinglens.floatinglensaitranslator.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

class MultiScriptOcrEngine {

    companion object {
        private const val TAG = "MultiScriptOcrEngine"
    }

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    suspend fun processImage(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.AUTO,
        rotationDegrees: Int = 0
    ): OcrResult {
        val startTime = System.currentTimeMillis()
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

        val targetScript = if (script == OcrScript.AUTO) OcrScript.JAPANESE else script
        val recognizer = getRecognizerForScript(targetScript)

        val mlKitText = executeRecognizer(recognizer, inputImage)
        val elapsedTime = System.currentTimeMillis() - startTime

        val ocrBlocks = mapMlKitTextToBlocks(mlKitText)
        val fullText = ocrBlocks.joinToString("\n") { it.text }

        Log.d(
            TAG,
            "OCR complete in ${elapsedTime}ms for script $targetScript. Extracted ${ocrBlocks.size} blocks."
        )

        return OcrResult(
            fullText = fullText,
            blocks = ocrBlocks,
            scriptUsed = targetScript,
            processingTimeMs = elapsedTime
        )
    }

    private fun getRecognizerForScript(script: OcrScript): TextRecognizer {
        return when (script) {
            OcrScript.JAPANESE -> japaneseRecognizer
            OcrScript.KOREAN -> koreanRecognizer
            OcrScript.CHINESE -> chineseRecognizer
            OcrScript.LATIN -> latinRecognizer
            OcrScript.AUTO -> japaneseRecognizer
        }
    }

    private suspend fun executeRecognizer(
        recognizer: TextRecognizer,
        inputImage: InputImage
    ): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                if (continuation.isActive) {
                    continuation.resume(text)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
    }

    private fun mapMlKitTextToBlocks(text: Text): List<OcrTextBlock> {
        return text.textBlocks.map { mlBlock ->
            val lines = mlBlock.lines.map { mlLine ->
                val lineBox = mlLine.boundingBox
                val isLineVertical = isVerticalBox(lineBox) || isVerticalByCornerPoints(mlLine.cornerPoints)

                OcrTextLine(
                    text = mlLine.text,
                    boundingBox = lineBox,
                    cornerPoints = mlLine.cornerPoints,
                    recognizedLanguage = mlLine.recognizedLanguage,
                    confidence = 1.0f,
                    isVertical = isLineVertical
                )
            }

            val blockBox = mlBlock.boundingBox
            val isBlockVertical = isVerticalBox(blockBox) || lines.any { it.isVertical }

            OcrTextBlock(
                text = mlBlock.text,
                boundingBox = blockBox,
                lines = lines,
                recognizedLanguage = mlBlock.recognizedLanguage,
                isVertical = isBlockVertical
            )
        }
    }

    private fun isVerticalBox(box: Rect?): Boolean {
        if (box == null) return false
        val height = box.height()
        val width = box.width()
        return height > 0 && width > 0 && (height > (width * 1.05))
    }

    private fun isVerticalByCornerPoints(cornerPoints: Array<Point>?): Boolean {
        if (cornerPoints == null || cornerPoints.size < 4) return false
        val p0 = cornerPoints[0]
        val p1 = cornerPoints[1]
        val p2 = cornerPoints[2]

        val horizontalDist = abs(p1.x - p0.x)
        val verticalDist = abs(p2.y - p0.y)

        return verticalDist > (horizontalDist * 1.05)
    }

    fun close() {
        try {
            latinRecognizer.close()
            chineseRecognizer.close()
            japaneseRecognizer.close()
            koreanRecognizer.close()
            Log.d(TAG, "MultiScriptOcrEngine closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing OCR recognizers: ${e.message}", e)
        }
    }
}
