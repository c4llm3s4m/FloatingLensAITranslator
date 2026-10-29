package com.floatinglens.floatinglensaitranslator.settings

import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlaySettingsRepositoryTest {

    private lateinit var repository: OverlaySettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = OverlaySettingsRepository(context)
    }

    @Test
    fun testDefaultSettings() {
        val settings = repository.settings.value
        assertEquals(TranslateLanguage.JAPANESE, settings.sourceLanguage)
        assertEquals(TranslateLanguage.ENGLISH, settings.targetLanguage)
        assertTrue(settings.preferPlugin)
        assertEquals("#FFFFFF", settings.pillColorHex)
        assertEquals(8, settings.cornerRadiusDp)
        assertEquals("#111111", settings.textColorHex)
        assertTrue(settings.autoScaleText)
        assertTrue(settings.filterFurigana)
    }

    @Test
    fun testUpdateSettings() {
        repository.updateSourceLanguage(TranslateLanguage.KOREAN)
        repository.updateTargetLanguage(TranslateLanguage.SPANISH)
        repository.updatePreferPlugin(false)
        repository.updatePillColor("#FFFDD0")
        repository.updateCornerRadius(12)
        repository.updateTextColor("#000000")
        repository.updateAutoScaleText(false)
        repository.updateFilterFurigana(false)

        val updated = repository.settings.value
        assertEquals(TranslateLanguage.KOREAN, updated.sourceLanguage)
        assertEquals(TranslateLanguage.SPANISH, updated.targetLanguage)
        assertFalse(updated.preferPlugin)
        assertEquals("#FFFDD0", updated.pillColorHex)
        assertEquals(12, updated.cornerRadiusDp)
        assertEquals("#000000", updated.textColorHex)
        assertFalse(updated.autoScaleText)
        assertFalse(updated.filterFurigana)
    }
}
