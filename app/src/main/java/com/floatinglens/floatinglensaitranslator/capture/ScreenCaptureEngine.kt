package com.floatinglens.floatinglensaitranslator.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenCaptureEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    companion object {
        private const val TAG = "ScreenCaptureEngine"
        private const val VIRTUAL_DISPLAY_NAME = "FloatingLensScreenCapture"
    }

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var densityDpi: Int = 0

    init {
        updateScreenMetrics()
        setupVirtualDisplay()
    }

    private fun updateScreenMetrics() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            densityDpi = context.resources.configuration.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            densityDpi = displayMetrics.densityDpi
        }
    }

    private fun setupVirtualDisplay() {
        if (screenWidth <= 0 || screenHeight <= 0) {
            updateScreenMetrics()
        }

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        Log.d(TAG, "VirtualDisplay setup complete ($screenWidth x $screenHeight @ $densityDpi dpi)")
    }

    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val reader = imageReader ?: return@withContext null

        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) {
                delay(100)
                image = reader.acquireLatestImage() ?: reader.acquireNextImage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring image from ImageReader: ${e.message}", e)
        }

        if (image == null) {
            Log.w(TAG, "No image frame available from ImageReader")
            return@withContext null
        }

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = if (rowPadding > 0) {
                val result = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                if (result != bitmap) {
                    bitmap.recycle()
                }
                result
            } else {
                bitmap
            }

            Log.d(TAG, "Captured frame bitmap successfully: ${croppedBitmap.width}x${croppedBitmap.height}")
            croppedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Image to Bitmap: ${e.message}", e)
            null
        } finally {
            image.close()
        }
    }

    fun stop() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection.stop()
            Log.d(TAG, "ScreenCaptureEngine stopped and resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ScreenCaptureEngine: ${e.message}", e)
        }
    }
}
