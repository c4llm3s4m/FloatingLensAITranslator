package com.floatinglens.floatinglensaitranslator.translation

import android.content.ContextWrapper
import com.floatinglens.floatinglensaitranslator.plugin.TranslationPluginManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnifiedTranslationManagerTest {

    private class TestPluginManager(
        private val available: Boolean,
        private val resultText: String? = null,
        private val shouldThrow: Boolean = false
    ) : TranslationPluginManager(ContextWrapper(null)) {

        override fun isAvailable(): Boolean = available

        override suspend fun translate(
            text: String,
            sourceLang: String,
            targetLang: String,
            timeoutMs: Long
        ): String? {
            if (shouldThrow) throw RuntimeException("Plugin RPC error")
            return resultText
        }
    }

    private class TestOfflineEngine(
        private val mockResult: String = "Offline Translated Text"
    ) : OfflineTranslationEngine() {
        override fun updateModelStatuses() {
            // No-op in test environment
        }

        override suspend fun translate(
            text: String,
            sourceLang: String,
            targetLang: String
        ): String {
            return mockResult
        }
    }

    @Test
    fun testPluginSuccessNoFallback() = runBlocking {
        val dummyContext = ContextWrapper(null)
        val fakePlugin = TestPluginManager(available = true, resultText = "[Qwen AI] Hello")
        val fakeOffline = TestOfflineEngine(mockResult = "Offline Hello")

        val manager = UnifiedTranslationManager(
            context = dummyContext,
            pluginManager = fakePlugin,
            offlineEngine = fakeOffline
        )

        val result = manager.translate("こんにちは", "ja", "en")

        assertEquals("[Qwen AI] Hello", result.translatedText)
        assertEquals(TranslationProvider.QWEN_AI_PLUGIN, result.providerUsed)
        assertFalse("Should not be fallback when plugin succeeds", result.isFallback)
    }

    @Test
    fun testPluginUnavailableFallbackToOffline() = runBlocking {
        val dummyContext = ContextWrapper(null)
        val fakePlugin = TestPluginManager(available = false)
        val fakeOffline = TestOfflineEngine(mockResult = "Offline Hello")

        val manager = UnifiedTranslationManager(
            context = dummyContext,
            pluginManager = fakePlugin,
            offlineEngine = fakeOffline
        )

        val result = manager.translate("こんにちは", "ja", "en")

        assertEquals("Offline Hello", result.translatedText)
        assertEquals(TranslationProvider.ML_KIT_OFFLINE, result.providerUsed)
        assertFalse("Should not be fallback flag if plugin was not available", result.isFallback)
    }

    @Test
    fun testPluginSuccessWithQwenPrefixNoFallback() = runBlocking {
        val dummyContext = ContextWrapper(null)
        val fakePlugin = TestPluginManager(available = true, resultText = "[Qwen2.5] Translated text")
        val fakeOffline = TestOfflineEngine(mockResult = "Offline Hello")

        val manager = UnifiedTranslationManager(
            context = dummyContext,
            pluginManager = fakePlugin,
            offlineEngine = fakeOffline
        )

        val result = manager.translate("探してみるか", "ja", "en")

        assertEquals("[Qwen2.5] Translated text", result.translatedText)
        assertEquals(TranslationProvider.QWEN_AI_PLUGIN, result.providerUsed)
        assertFalse("Should not fallback when plugin returns prefixed English response", result.isFallback)
    }

    @Test
    fun testPluginErrorFallbackToOffline() = runBlocking {
        val dummyContext = ContextWrapper(null)
        val fakePlugin = TestPluginManager(available = true, shouldThrow = true)
        val fakeOffline = TestOfflineEngine(mockResult = "Offline Fallback Hello")

        val manager = UnifiedTranslationManager(
            context = dummyContext,
            pluginManager = fakePlugin,
            offlineEngine = fakeOffline
        )

        val result = manager.translate("こんにちは", "ja", "en")

        assertEquals("Offline Fallback Hello", result.translatedText)
        assertEquals(TranslationProvider.ML_KIT_FALLBACK, result.providerUsed)
        assertTrue("Should be flagged as fallback because plugin threw an error", result.isFallback)
    }
}
