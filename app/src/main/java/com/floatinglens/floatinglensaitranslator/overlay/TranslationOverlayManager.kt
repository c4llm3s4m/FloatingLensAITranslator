package com.floatinglens.floatinglensaitranslator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.floatinglens.floatinglensaitranslator.processor.ProcessedTranslationResult
import com.floatinglens.floatinglensaitranslator.settings.OverlaySettings
import com.floatinglens.floatinglensaitranslator.ui.theme.FloatingLensAITranslatorTheme

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun handleDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

class TranslationOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "TranslationOverlayManager"
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    fun showOverlay(
        processedResult: ProcessedTranslationResult,
        settings: OverlaySettings
    ) {
        clearOverlay()

        if (processedResult.bubbles.isEmpty()) {
            Log.d(TAG, "No translated speech bubbles found in frame. Skipping overlay display.")
            return
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        val screenHeightPx = displayMetrics.heightPixels

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val lifecycleOwner = OverlayLifecycleOwner()
        overlayLifecycleOwner = lifecycleOwner

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                FloatingLensAITranslatorTheme {
                    SpeechBubbleMaskOverlay(
                        bubbles = processedResult.bubbles,
                        settings = settings,
                        bitmapWidth = processedResult.bitmapWidth,
                        bitmapHeight = processedResult.bitmapHeight,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        onDismiss = {
                            clearOverlay()
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            Log.d(
                TAG,
                "Successfully added translation mask overlay for ${processedResult.bubbles.size} bubbles."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view to WindowManager: ${e.message}", e)
            clearOverlay()
        }
    }

    fun clearOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Cleared translation mask overlay view.")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        overlayLifecycleOwner?.handleDestroy()
        overlayLifecycleOwner = null
        overlayView = null
    }
}
