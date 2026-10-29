package com.floatinglens.floatinglensaitranslator.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TranslationPluginManagerTest {

    private lateinit var context: Context
    private lateinit var pluginManager: TranslationPluginManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pluginManager = TranslationPluginManager(context)
    }

    @Test
    fun testBindPluginServiceWithNoPluginInstalled() {
        val bound = pluginManager.bindPluginService()

        assertFalse("Should return false when no external plugin is installed", bound)
        assertFalse("isConnected state should be false", pluginManager.isConnected.value)
        assertEquals(
            TranslationPluginManager.NO_PLUGIN_STATUS,
            pluginManager.pluginName.value
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun testBindPluginServiceIgnoresInternalHostAppService() {
        // Register an internal service under the host app package
        val intent = Intent(TranslationPluginManager.DEFAULT_PLUGIN_ACTION)
        val resolveInfo = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = context.packageName
                name = "com.floatinglens.floatinglensaitranslator.plugin.MockCompanionTranslationService"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)

        val bound = pluginManager.bindPluginService()

        assertFalse("Should ignore internal service and return false", bound)
        assertFalse("isConnected state should be false", pluginManager.isConnected.value)
        assertEquals(
            TranslationPluginManager.NO_PLUGIN_STATUS,
            pluginManager.pluginName.value
        )
    }

    @Test
    fun testBindPluginServiceIgnoresSelfPackageNameExplicitlyPassed() {
        val bound = pluginManager.bindPluginService(packageName = context.packageName)

        assertFalse("Should reject binding when packageName matches context.packageName", bound)
        assertFalse("isConnected state should be false", pluginManager.isConnected.value)
        assertEquals(
            TranslationPluginManager.NO_PLUGIN_STATUS,
            pluginManager.pluginName.value
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun testBindPluginServiceSelectsExternalCompanionPlugin() {
        // Register an external service under a third-party package name
        val intent = Intent(TranslationPluginManager.DEFAULT_PLUGIN_ACTION)
        val externalResolveInfo = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = "com.qwen.companion.plugin"
                name = "com.qwen.companion.plugin.TranslationService"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, externalResolveInfo)

        val bound = pluginManager.bindPluginService()

        assertTrue("Should return true when an external plugin service is found and bound", bound)
    }
}
