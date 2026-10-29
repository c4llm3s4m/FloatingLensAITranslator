package com.floatinglens.floatinglensaitranslator.overlay

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floatinglens.floatinglensaitranslator.ocr.SpeechBubbleSegmenter
import com.floatinglens.floatinglensaitranslator.processor.TranslatedBubble
import com.floatinglens.floatinglensaitranslator.settings.OverlaySettings

@Composable
fun SpeechBubbleMaskOverlay(
    bubbles: List<TranslatedBubble>,
    settings: OverlaySettings,
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaleX = if (bitmapWidth > 0) screenWidthPx.toFloat() / bitmapWidth.toFloat() else 1.0f
    val scaleY = if (bitmapHeight > 0) screenHeightPx.toFloat() / bitmapHeight.toFloat() else 1.0f

    val pillColor = remember(settings.pillColorHex) { parseHexColor(settings.pillColorHex, Color.White) }
    val textColor = remember(settings.textColorHex) { parseHexColor(settings.textColorHex, Color(0xFF111111)) }
    val cornerRadius = settings.cornerRadiusDp.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
    ) {
        // Render each speech bubble mask item
        bubbles.forEach { bubble ->
            val rect = bubble.bubbleUnit.boundingBox
            if (rect.width() > 0 && rect.height() > 0) {
                val scaledLeft = (rect.left * scaleX).toInt()
                val scaledTop = (rect.top * scaleY).toInt()
                val scaledWidth = (rect.width() * scaleX).toInt()
                val scaledHeight = (rect.height() * scaleY).toInt()

                // Expand Horizontal Pill Mask Bounds for Narrow Vertical Bubbles (height > width * 1.2)
                val isVerticalBubble = scaledHeight > (scaledWidth * 1.2f)
                val finalWidth = if (isVerticalBubble) {
                    val expanded = (scaledWidth * 1.30f).toInt()
                    val minAspectWidth = (scaledHeight / 2.2f).toInt()
                    maxOf(expanded, minAspectWidth)
                } else {
                    scaledWidth
                }
                val widthDiff = finalWidth - scaledWidth
                val adjustedLeft = maxOf(0, scaledLeft - widthDiff / 2)

                val rawText = bubble.translatedText
                    .removePrefix("[Qwen LLM]")
                    .trim()
                val cleanText = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize(rawText)

                BubbleMaskItem(
                    rect = Rect(adjustedLeft, scaledTop, adjustedLeft + finalWidth, scaledTop + scaledHeight),
                    translatedText = cleanText,
                    pillColor = pillColor,
                    textColor = textColor,
                    cornerRadius = cornerRadius,
                    autoScale = settings.autoScaleText
                )
            }
        }

        // Floating Close button in top corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shadowElevation = 6.dp
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Translation Overlay",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BubbleMaskItem(
    rect: Rect,
    translatedText: String,
    pillColor: Color,
    textColor: Color,
    cornerRadius: Dp,
    autoScale: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val widthDp = with(density) { rect.width().toDp() }
    val heightDp = with(density) { rect.height().toDp() }

    val displayText = translatedText.ifBlank { "..." }

    Box(
        modifier = modifier
            .offset { IntOffset(rect.left, rect.top) }
            .width(widthDp)
            .height(heightDp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(pillColor)
            .border(
                width = 1.dp,
                color = Color.LightGray.copy(alpha = 0.4f),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (autoScale) {
            AutoResizedText(
                text = displayText,
                color = textColor,
                style = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    lineBreak = LineBreak.Paragraph,
                    textAlign = TextAlign.Center
                )
            )
        } else {
            Text(
                text = displayText,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    lineBreak = LineBreak.Paragraph,
                    textAlign = TextAlign.Center
                ),
                textAlign = TextAlign.Center,
                softWrap = true
            )
        }
    }
}

@Composable
fun AutoResizedText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 8.sp,
    maxFontSize: TextUnit = 16.sp
) {
    val defaultStyle = remember(style) {
        style.copy(
            lineBreak = LineBreak.Paragraph,
            textAlign = TextAlign.Center
        )
    }

    var resizedTextStyle by remember(text, defaultStyle) {
        mutableStateOf(defaultStyle.copy(fontSize = maxFontSize, lineHeight = (maxFontSize.value * 1.2f).sp))
    }
    var shouldDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        modifier = modifier.drawWithContent {
            if (shouldDraw) drawContent()
        },
        style = resizedTextStyle,
        softWrap = true,
        maxLines = 12,
        textAlign = TextAlign.Center,
        onTextLayout = { result ->
            val isMidWordSplit = hasMidWordSplit(text, result)
            val hasOverflow = result.hasVisualOverflow

            if (hasOverflow || isMidWordSplit) {
                if (resizedTextStyle.fontSize.value > minFontSize.value) {
                    val nextSize = (resizedTextStyle.fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                    resizedTextStyle = resizedTextStyle.copy(
                        fontSize = nextSize,
                        lineHeight = (nextSize.value * 1.2f).sp
                    )
                } else {
                    shouldDraw = true
                }
            } else {
                shouldDraw = true
            }
        }
    )
}

private fun hasMidWordSplit(text: String, result: TextLayoutResult): Boolean {
    val lineCount = result.lineCount
    if (lineCount <= 1) return false

    for (i in 0 until lineCount - 1) {
        val lineEnd = result.getLineEnd(i)
        if (lineEnd <= 0 || lineEnd >= text.length) continue

        val charBefore = text[lineEnd - 1]
        val charAfter = text[lineEnd]

        if (charBefore.isLetterOrDigit() && charAfter.isLetterOrDigit()) {
            return true
        }
    }
    return false
}

fun parseHexColor(hex: String, defaultColor: Color): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> return defaultColor
        }
        Color(colorInt)
    } catch (_: Exception) {
        defaultColor
    }
}
