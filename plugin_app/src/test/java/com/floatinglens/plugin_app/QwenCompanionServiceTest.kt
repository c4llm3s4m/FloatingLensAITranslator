package com.floatinglens.plugin_app

import android.content.Intent
import com.floatinglens.floatinglensaitranslator.plugin.ITranslationCallback
import com.floatinglens.floatinglensaitranslator.plugin.ITranslationPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class QwenCompanionServiceTest {

    private lateinit var controller: ServiceController<QwenCompanionService>
    private lateinit var service: QwenCompanionService
    private lateinit var plugin: ITranslationPlugin

    @Before
    fun setUp() {
        controller = Robolectric.buildService(QwenCompanionService::class.java)
        service = controller.create().get()
        val binder = service.onBind(Intent("com.floatinglens.plugin.TRANSLATE_SERVICE"))
        assertNotNull(binder)
        plugin = ITranslationPlugin.Stub.asInterface(binder)
    }

    @Test
    fun testGetPluginMetadata() {
        assertEquals(QwenCompanionService.PLUGIN_NAME, plugin.pluginName)
        assertEquals(QwenCompanionService.PLUGIN_VERSION, plugin.pluginVersion)
    }

    @Test
    fun testSyncTranslate() {
        val result = plugin.translate("こんにちは", "ja", "en")
        assertNotNull(result)
        assertTrue("Result should be non-blank", result.isNotBlank())
    }

    @Test
    fun testAsyncTranslateSuccess() {
        val latch = CountDownLatch(1)
        var callbackResult: String? = null

        plugin.translateAsync("こんにちは", "ja", "en", object : ITranslationCallback.Stub() {
            override fun onSuccess(translatedText: String?) {
                callbackResult = translatedText
                latch.countDown()
            }

            override fun onError(errorCode: Int, errorMessage: String?) {
                latch.countDown()
            }
        })

        assertTrue("Callback should complete within 3 seconds", latch.await(3, TimeUnit.SECONDS))
        assertNotNull(callbackResult)
        assertTrue("Callback result should be non-blank", callbackResult!!.isNotBlank())
    }

    @Test
    fun testAsyncTranslateEmptyInput() {
        val latch = CountDownLatch(1)
        var callbackResult: String? = null

        plugin.translateAsync("   ", "ja", "en", object : ITranslationCallback.Stub() {
            override fun onSuccess(translatedText: String?) {
                callbackResult = translatedText
                latch.countDown()
            }

            override fun onError(errorCode: Int, errorMessage: String?) {
                latch.countDown()
            }
        })

        assertTrue("Callback should complete within 3 seconds", latch.await(3, TimeUnit.SECONDS))
        assertEquals("", callbackResult)
    }
}
