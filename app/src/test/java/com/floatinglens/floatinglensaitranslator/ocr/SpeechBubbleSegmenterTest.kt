package com.floatinglens.floatinglensaitranslator.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechBubbleSegmenterTest {

    private lateinit var segmenter: SpeechBubbleSegmenter

    @Before
    fun setUp() {
        segmenter = SpeechBubbleSegmenter()
    }

    @Test
    fun testStripInlineRubyText() {
        val input1 = "漢字(かんじ)"
        val expected1 = "漢字"
        assertEquals(expected1, segmenter.stripInlineRubyText(input1))

        val input2 = "日本語（にほんご）"
        val expected2 = "日本語"
        assertEquals(expected2, segmenter.stripInlineRubyText(input2))

        val input3 = "文字|もじ"
        val expected3 = "文字"
        assertEquals(expected3, segmenter.stripInlineRubyText(input3))
    }

    @Test
    fun testOrderLinesInBubbleVerticalRtl() {
        val rightColTop = OcrTextLine(
            text = "右1",
            boundingBox = Rect(80, 10, 100, 50),
            isVertical = true
        )
        val rightColBottom = OcrTextLine(
            text = "右2",
            boundingBox = Rect(80, 60, 100, 100),
            isVertical = true
        )
        val leftColTop = OcrTextLine(
            text = "左1",
            boundingBox = Rect(30, 10, 50, 50),
            isVertical = true
        )

        val unorganized = listOf(leftColTop, rightColBottom, rightColTop)
        val ordered = segmenter.orderLinesInBubble(unorganized, isVertical = true)

        assertEquals("Right column top should be first (Right-to-Left)", "右1", ordered[0].text)
        assertEquals("Right column bottom should be second", "右2", ordered[1].text)
        assertEquals("Left column top should be third", "左1", ordered[2].text)
    }

    @Test
    fun testOrderLinesInBubbleHorizontalLtr() {
        val line1Left = OcrTextLine(
            text = "Top Left",
            boundingBox = Rect(10, 10, 50, 30),
            isVertical = false
        )
        val line1Right = OcrTextLine(
            text = "Top Right",
            boundingBox = Rect(60, 10, 100, 30),
            isVertical = false
        )
        val line2 = OcrTextLine(
            text = "Bottom Line",
            boundingBox = Rect(10, 40, 100, 60),
            isVertical = false
        )

        val unorganized = listOf(line2, line1Right, line1Left)
        val ordered = segmenter.orderLinesInBubble(unorganized, isVertical = false)

        assertEquals("Top Left", ordered[0].text)
        assertEquals("Top Right", ordered[1].text)
        assertEquals("Bottom Line", ordered[2].text)
    }

    @Test
    fun testMergeLinesCjkNoSpaces() {
        val lines = listOf(
            OcrTextLine(text = "これ", boundingBox = null, isVertical = true),
            OcrTextLine(text = "は", boundingBox = null, isVertical = true),
            OcrTextLine(text = "テスト", boundingBox = null, isVertical = true)
        )

        val merged = segmenter.mergeLinesToCleanText(lines, OcrScript.JAPANESE, isVertical = true)
        assertEquals("Column lines in Japanese should merge with no spaces", "これはテスト", merged)
    }

    @Test
    fun testMergeLinesLatinWithSpacesAndHyphenation() {
        val lines = listOf(
            OcrTextLine(text = "This is a trans-", boundingBox = null, isVertical = false),
            OcrTextLine(text = "lation test.", boundingBox = null, isVertical = false)
        )

        val merged = segmenter.mergeLinesToCleanText(lines, OcrScript.LATIN, isVertical = false)
        assertEquals("Hyphenation should be removed and spaces preserved", "This is a translation test.", merged)
    }

    @Test
    fun testRemoveFuriganaSpatialFilter() {
        val mainLine = OcrTextLine(
            text = "漢字本",
            boundingBox = Rect(50, 10, 90, 200),
            isVertical = true
        )
        val furiganaLine = OcrTextLine(
            text = "かんじほん",
            boundingBox = Rect(92, 10, 104, 200),
            isVertical = true
        )

        val filtered = segmenter.removeFurigana(listOf(mainLine, furiganaLine))

        assertEquals("Furigana line should be removed by spatial filter", 1, filtered.size)
        assertEquals("漢字本", filtered[0].text)
    }

    @Test
    fun testCleanPunctuationAndCapitalizeLeadingDotsAndArtifacts() {
        val cleaned1 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize(". It is the kingdom...")
        assertEquals("It is the kingdom...", cleaned1)

        val cleaned2 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize(".. さてと")
        assertEquals("さてと", cleaned2)

        val cleaned3 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize("• 探してみるか")
        assertEquals("探してみるか", cleaned3)

        val cleaned4 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize(", beautiful hometown")
        assertEquals("Beautiful hometown", cleaned4)
    }

    @Test
    fun testCleanPunctuationAndCapitalizeTrailingAndCapitalization() {
        val cleaned1 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize("hello world|")
        assertEquals("Hello world", cleaned1)

        val cleaned2 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize("remember found hometown")
        assertEquals("Remember found hometown", cleaned2)

        val cleaned3 = SpeechBubbleSegmenter.cleanPunctuationAndCapitalize("フォーランド王国")
        assertEquals("フォーランド王国", cleaned3)
    }

    @Test
    fun testMergeLinesCleansLeadingPunctuation() {
        val lines = listOf(
            OcrTextLine(text = ". さてと", boundingBox = null, isVertical = true),
            OcrTextLine(text = "そろそろ", boundingBox = null, isVertical = true)
        )

        val merged = segmenter.mergeLinesToCleanText(lines, OcrScript.JAPANESE, isVertical = true)
        assertEquals("Leading period artifact should be cleaned during merging", "さてとそろそろ", merged)
    }
}
