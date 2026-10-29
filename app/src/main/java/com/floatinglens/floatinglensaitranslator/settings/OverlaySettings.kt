package com.floatinglens.floatinglensaitranslator.settings

import com.google.mlkit.nl.translate.TranslateLanguage

data class OverlaySettings(
    val sourceLanguage: String = TranslateLanguage.JAPANESE,
    val targetLanguage: String = TranslateLanguage.ENGLISH,
    val preferPlugin: Boolean = true,
    val pillColorHex: String = "#FFFFFF",
    val cornerRadiusDp: Int = 8,
    val textColorHex: String = "#111111",
    val autoScaleText: Boolean = true,
    val filterFurigana: Boolean = true
)
