package com.floatinglens.floatinglensaitranslator.ocr

import android.graphics.Rect
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SpeechBubbleUnit(
    val id: String = UUID.randomUUID().toString(),
    val boundingBox: Rect,
    val mergedText: String,
    val lines: List<OcrTextLine>,
    val isVertical: Boolean = false,
    val script: OcrScript = OcrScript.AUTO
)

private inline val Rect.boxWidth: Int get() = right - left
private inline val Rect.boxHeight: Int get() = bottom - top
private inline val Rect.boxCenterX: Int get() = (left + right) / 2
private inline val Rect.boxCenterY: Int get() = (top + bottom) / 2

class SpeechBubbleSegmenter {

    companion object {
        fun cleanPunctuationAndCapitalize(text: String): String {
            var clean = text.trim()

            // Clean leading punctuation artifacts (dots, commas, dashes, bullets, tildes, colons, quotes, etc.)
            clean = clean.replace(
                Regex("^[.\\-,_•·・~〜|/\\\\:;\\u3001\\u3002\\uFF0C\\uFF0E\\u2010-\\u2015\\u2018\\u2019\\u201C\\u201D\\s]+"),
                ""
            ).trim()

            // Clean trailing noise artifacts (pipes, slashes, backslashes, hashes, underscores)
            clean = clean.replace(Regex("[|_\\\\/#]+$"), "").trim()

            // Ensure proper sentence capitalization for Latin text
            if (clean.isNotEmpty() && clean[0].isLowerCase()) {
                clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            return clean
        }
    }

    fun segmentSpeechBubbles(
        ocrResult: OcrResult,
        proximityMarginPx: Int = 30
    ): List<SpeechBubbleUnit> {
        val allLines = ocrResult.blocks.flatMap { it.lines }
        if (allLines.isEmpty()) return emptyList()

        val cleanLines = removeFurigana(allLines)
        if (cleanLines.isEmpty()) return emptyList()

        val effectiveMargin = max(proximityMarginPx, 35)
        val clusters = clusterLinesByProximity(cleanLines, effectiveMargin)

        return clusters.map { lineCluster ->
            val isVerticalBubble = lineCluster.count { it.isVertical } > (lineCluster.size / 2)
            val orderedLines = orderLinesInBubble(lineCluster, isVerticalBubble)

            val mergedText = mergeLinesToCleanText(
                lines = orderedLines,
                script = ocrResult.scriptUsed,
                isVertical = isVerticalBubble
            )

            val bubbleBounds = computeBoundingBox(orderedLines)

            SpeechBubbleUnit(
                boundingBox = bubbleBounds,
                mergedText = mergedText,
                lines = orderedLines,
                isVertical = isVerticalBubble,
                script = ocrResult.scriptUsed
            )
        }
    }

    fun removeFurigana(lines: List<OcrTextLine>): List<OcrTextLine> {
        if (lines.size <= 1) return lines.map { stripInlineFuriganaFromLine(it) }

        val validLines = mutableListOf<OcrTextLine>()

        for (line in lines) {
            val box = line.boundingBox
            if (box == null) {
                validLines.add(stripInlineFuriganaFromLine(line))
                continue
            }

            val text = line.text.trim()
            // Lines containing Kanji, numbers, or English are main text, never furigana
            val containsKanji = text.any { ch ->
                val block = Character.UnicodeBlock.of(ch)
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            }

            if (containsKanji || text.length <= 1) {
                validLines.add(stripInlineFuriganaFromLine(line))
                continue
            }

            // Pure hiragana/katakana lines might be furigana ONLY if adjacent to a main Kanji line
            val isPurePhonetic = text.all { ch ->
                val block = Character.UnicodeBlock.of(ch)
                block == Character.UnicodeBlock.HIRAGANA ||
                        block == Character.UnicodeBlock.KATAKANA ||
                        block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            }

            if (isPurePhonetic) {
                val isAdjacentToMainKanji = lines.any { other ->
                    if (other === line) false
                    else {
                        val otherBox = other.boundingBox ?: return@any false
                        val otherText = other.text
                        val otherHasKanji = otherText.any { ch ->
                            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                        }

                        if (!otherHasKanji) return@any false

                        if (line.isVertical) {
                            val xDistance = abs(box.boxCenterX - otherBox.boxCenterX)
                            val yOverlap = max(0, minOf(box.bottom, otherBox.bottom) - maxOf(box.top, otherBox.top))
                            val mainCharWidth = if (otherText.isNotEmpty()) otherBox.boxWidth.toDouble() / otherText.length else otherBox.boxWidth.toDouble()
                            val furiganaCharWidth = if (text.isNotEmpty()) box.boxWidth.toDouble() / text.length else box.boxWidth.toDouble()

                            xDistance < (otherBox.boxWidth * 1.2) && yOverlap > (box.boxHeight * 0.3) && furiganaCharWidth < (mainCharWidth * 0.5)
                        } else {
                            val yDistance = abs(box.boxCenterY - otherBox.boxCenterY)
                            val xOverlap = max(0, minOf(box.right, otherBox.right) - maxOf(box.left, otherBox.left))
                            val mainCharHeight = if (otherText.isNotEmpty()) otherBox.boxHeight.toDouble() / otherText.length else otherBox.boxHeight.toDouble()
                            val furiganaCharHeight = if (text.isNotEmpty()) box.boxHeight.toDouble() / text.length else box.boxHeight.toDouble()

                            yDistance < (otherBox.boxHeight * 1.2) && xOverlap > (box.boxWidth * 0.3) && furiganaCharHeight < (mainCharHeight * 0.5)
                        }
                    }
                }

                if (isAdjacentToMainKanji) {
                    continue // Filter out furigana
                }
            }

            validLines.add(stripInlineFuriganaFromLine(line))
        }

        return validLines
    }

    fun stripInlineRubyText(text: String): String {
        var clean = text
        clean = clean.replace(Regex("([\\u4e00-\\u9fff]+)[(（\\[][\\u3040-\\u309f\\u30a0-\\u30ff]+\\s*[)）\\]]"), "$1")
        clean = clean.replace(Regex("([\\u4e00-\\u9fff]+)[|｜][\\u3040-\\u309f\\u30a0-\\u30ff]+"), "$1")
        return clean.trim()
    }

    private fun stripInlineFuriganaFromLine(line: OcrTextLine): OcrTextLine {
        val cleanText = stripInlineRubyText(line.text)
        return line.copy(text = cleanText)
    }

    fun orderLinesInBubble(lines: List<OcrTextLine>, isVertical: Boolean): List<OcrTextLine> {
        if (lines.size <= 1) return lines

        return if (isVertical) {
            lines.sortedWith { lineA, lineB ->
                val boxA = lineA.boundingBox
                val boxB = lineB.boundingBox
                if (boxA == null || boxB == null) return@sortedWith 0

                val colDiff = abs(boxA.boxCenterX - boxB.boxCenterX)
                val colThreshold = max(12, min(boxA.boxWidth, boxB.boxWidth) / 2)

                if (colDiff <= colThreshold) {
                    // Same vertical column: Top to Bottom
                    boxA.boxCenterY.compareTo(boxB.boxCenterY)
                } else {
                    // RTL vertical columns: Right to Left (larger X first)
                    boxB.boxCenterX.compareTo(boxA.boxCenterX)
                }
            }
        } else {
            lines.sortedWith { lineA, lineB ->
                val boxA = lineA.boundingBox
                val boxB = lineB.boundingBox
                if (boxA == null || boxB == null) return@sortedWith 0

                val rowDiff = abs(boxA.boxCenterY - boxB.boxCenterY)
                val rowThreshold = max(12, min(boxA.boxHeight, boxB.boxHeight) / 2)

                if (rowDiff <= rowThreshold) {
                    // Same row: Left to Right
                    boxA.boxCenterX.compareTo(boxB.boxCenterX)
                } else {
                    // Top to Bottom
                    boxA.boxCenterY.compareTo(boxB.boxCenterY)
                }
            }
        }
    }

    fun mergeLinesToCleanText(
        lines: List<OcrTextLine>,
        script: OcrScript,
        isVertical: Boolean
    ): String {
        if (lines.isEmpty()) return ""

        val rawLines = lines.map { stripInlineRubyText(it.text) }

        val isCjk = script == OcrScript.JAPANESE || script == OcrScript.CHINESE ||
                isVertical || rawLines.any { containsCjk(it) }

        val rawMerged = if (isCjk) {
            val sb = StringBuilder()
            for (lineText in rawLines) {
                sb.append(lineText)
            }
            sb.toString()
                .replace(Regex("\\s+"), "")
                .replace("・・", "…")
        } else {
            val sb = StringBuilder()
            for (i in rawLines.indices) {
                val current = rawLines[i].trim()
                if (current.isEmpty()) continue

                if (i > 0 && sb.isNotEmpty()) {
                    if (sb.endsWith("-")) {
                        sb.deleteCharAt(sb.length - 1)
                    } else {
                        sb.append(" ")
                    }
                }
                sb.append(current)
            }
            sb.toString().replace(Regex("\\s+"), " ").trim()
        }

        return cleanPunctuationAndCapitalize(rawMerged)
    }

    private fun containsCjk(text: String): Boolean {
        return text.any { ch ->
            val block = Character.UnicodeBlock.of(ch)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.HIRAGANA ||
                    block == Character.UnicodeBlock.KATAKANA ||
                    block == Character.UnicodeBlock.HANGUL_SYLLABLES
        }
    }

    private fun clusterLinesByProximity(
        lines: List<OcrTextLine>,
        proximityMargin: Int
    ): List<List<OcrTextLine>> {
        val visited = BooleanArray(lines.size) { false }
        val clusters = mutableListOf<List<OcrTextLine>>()

        for (i in lines.indices) {
            if (visited[i]) continue

            val currentCluster = mutableListOf<OcrTextLine>()
            val queue = ArrayDeque<Int>()

            queue.add(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst()
                val line = lines[idx]
                currentCluster.add(line)

                val box = line.boundingBox ?: continue

                val expandedBox = Rect(
                    box.left - proximityMargin,
                    box.top - proximityMargin,
                    box.right + proximityMargin,
                    box.bottom + proximityMargin
                )

                for (j in lines.indices) {
                    if (!visited[j]) {
                        val otherBox = lines[j].boundingBox
                        if (otherBox != null && Rect.intersects(expandedBox, otherBox)) {
                            visited[j] = true
                            queue.add(j)
                        }
                    }
                }
            }

            clusters.add(currentCluster)
        }

        return clusters
    }

    private fun computeBoundingBox(lines: List<OcrTextLine>): Rect {
        var minLeft = Int.MAX_VALUE
        var minTop = Int.MAX_VALUE
        var maxRight = Int.MIN_VALUE
        var maxBottom = Int.MIN_VALUE

        var valid = false
        for (line in lines) {
            val box = line.boundingBox ?: continue
            valid = true
            if (box.left < minLeft) minLeft = box.left
            if (box.top < minTop) minTop = box.top
            if (box.right > maxRight) maxRight = box.right
            if (box.bottom > maxBottom) maxBottom = box.bottom
        }

        if (!valid) return Rect(0, 0, 0, 0)

        // Add 6px horizontal, 4px vertical padding around text cluster
        val padX = 6
        val padY = 4
        return Rect(
            max(0, minLeft - padX),
            max(0, minTop - padY),
            maxRight + padX,
            maxBottom + padY
        )
    }
}
