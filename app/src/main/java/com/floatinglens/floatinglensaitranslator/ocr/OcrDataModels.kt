package com.floatinglens.floatinglensaitranslator.ocr

import android.graphics.Point
import android.graphics.Rect

enum class OcrScript(val code: String, val displayName: String) {
    AUTO("auto", "Auto Detect"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    CHINESE("zh", "Chinese"),
    LATIN("latin", "Latin / Spanish")
}

data class OcrTextLine(
    val text: String,
    val boundingBox: Rect?,
    val cornerPoints: Array<Point>? = null,
    val recognizedLanguage: String = "",
    val confidence: Float = 1.0f,
    val isVertical: Boolean = false
) {
    val height: Int get() = boundingBox?.height() ?: 0
    val width: Int get() = boundingBox?.width() ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OcrTextLine

        if (text != other.text) return false
        if (boundingBox != other.boundingBox) return false
        if (recognizedLanguage != other.recognizedLanguage) return false
        if (confidence != other.confidence) return false
        if (isVertical != other.isVertical) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (boundingBox?.hashCode() ?: 0)
        result = 31 * result + recognizedLanguage.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + isVertical.hashCode()
        return result
    }
}

data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrTextLine>,
    val recognizedLanguage: String = "",
    val isVertical: Boolean = false
)

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val scriptUsed: OcrScript,
    val processingTimeMs: Long = 0L
)
