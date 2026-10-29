package com.floatinglens.floatinglensaitranslator.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

open class TranslationPluginManager(private val context: Context) {

    companion object {
        private const val TAG = "TranslationPluginManager"
        const val DEFAULT_PLUGIN_ACTION = "com.floatinglens.plugin.TRANSLATE_SERVICE"
        const val NO_PLUGIN_STATUS = "No External Companion Plugin Installed (Using ML Kit Offline Engine)"
    }

    private var pluginService: ITranslationPlugin? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pluginName = MutableStateFlow<String?>(null)
    val pluginName: StateFlow<String?> = _pluginName.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            pluginService = ITranslationPlugin.Stub.asInterface(service)
            _isConnected.value = true
            try {
                val servicePluginName = pluginService?.pluginName
                val appLabel = name?.packageName?.let { pkg ->
                    try {
                        val appInfo = context.packageManager?.getApplicationInfo(pkg, 0)
                        if (appInfo != null) {
                            context.packageManager.getApplicationLabel(appInfo).toString()
                        } else {
                            pkg
                        }
                    } catch (_: Exception) {
                        pkg
                    }
                } ?: name?.packageName ?: "External Companion Plugin"

                val displayName = if (!servicePluginName.isNullOrBlank()) {
                    "$servicePluginName ($appLabel)"
                } else {
                    appLabel
                }

                _pluginName.value = displayName
                Log.d(TAG, "Connected to external translation plugin service: ${_pluginName.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching plugin name: ${e.message}", e)
                _pluginName.value = name?.packageName ?: "External Companion Plugin"
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            pluginService = null
            _isConnected.value = false
            _pluginName.value = NO_PLUGIN_STATUS
            Log.d(TAG, "Disconnected from translation plugin service")
        }
    }

    open fun bindPluginService(action: String = DEFAULT_PLUGIN_ACTION, packageName: String? = null): Boolean {
        val intent = Intent(action)

        val targetPackageName: String? = if (!packageName.isNullOrBlank()) {
            if (packageName == context.packageName) {
                Log.w(TAG, "Ignoring bind request to host app package ($packageName). External companion plugin required.")
                null
            } else {
                packageName
            }
        } else {
            try {
                val resolveInfos = context.packageManager?.queryIntentServices(intent, 0) ?: emptyList()
                val externalResolveInfo = resolveInfos.firstOrNull {
                    it.serviceInfo?.packageName != context.packageName
                }
                externalResolveInfo?.serviceInfo?.packageName
            } catch (e: Exception) {
                Log.e(TAG, "Error querying external plugin services: ${e.message}", e)
                null
            }
        }

        if (targetPackageName.isNullOrBlank()) {
            Log.d(TAG, "No external companion plugin installed on device.")
            _isConnected.value = false
            _pluginName.value = NO_PLUGIN_STATUS
            return false
        }

        intent.setPackage(targetPackageName)
        try {
            val resolveInfos = context.packageManager?.queryIntentServices(intent, 0) ?: emptyList()
            val serviceInfo = resolveInfos.firstOrNull { it.serviceInfo?.packageName == targetPackageName }?.serviceInfo
            if (serviceInfo != null) {
                intent.component = ComponentName(serviceInfo.packageName, serviceInfo.name)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set exact ComponentName for $targetPackageName: ${e.message}")
        }

        return try {
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                _isConnected.value = false
                _pluginName.value = NO_PLUGIN_STATUS
            }
            Log.d(TAG, "bindPluginService initiated for package=$targetPackageName, bound=$bound")
            bound
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind plugin service: ${e.message}", e)
            _isConnected.value = false
            _pluginName.value = NO_PLUGIN_STATUS
            false
        }
    }

    fun unbindPluginService() {
        if (_isConnected.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding plugin service: ${e.message}", e)
            } finally {
                pluginService = null
                _isConnected.value = false
                _pluginName.value = NO_PLUGIN_STATUS
            }
        }
    }

    open fun isAvailable(): Boolean {
        val service = pluginService ?: return false
        return try {
            _isConnected.value && service.isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking plugin availability: ${e.message}", e)
            false
        }
    }

    open suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        timeoutMs: Long = 15000L
    ): String? = withContext(Dispatchers.IO) {
        val service = pluginService ?: return@withContext null
        if (!isAvailable()) return@withContext null

        return@withContext withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { continuation ->
                try {
                    service.translateAsync(
                        text,
                        sourceLang,
                        targetLang,
                        object : ITranslationCallback.Stub() {
                            override fun onSuccess(translatedText: String?) {
                                if (continuation.isActive) {
                                    continuation.resume(translatedText)
                                }
                            }

                            override fun onError(errorCode: Int, errorMessage: String?) {
                                Log.e(TAG, "Plugin translate error $errorCode: $errorMessage")
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    )
                } catch (e: RemoteException) {
                    Log.e(TAG, "RemoteException during plugin translate: ${e.message}", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
