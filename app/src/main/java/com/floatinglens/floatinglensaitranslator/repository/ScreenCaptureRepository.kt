package com.floatinglens.floatinglensaitranslator.repository

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureRepository {
    private val _latestBitmap = MutableStateFlow<Bitmap?>(null)
    val latestBitmap: StateFlow<Bitmap?> = _latestBitmap.asStateFlow()

    private val _captureCount = MutableStateFlow(0)
    val captureCount: StateFlow<Int> = _captureCount.asStateFlow()

    private val _captureEvents = MutableSharedFlow<Bitmap>(extraBufferCapacity = 5)
    val captureEvents: SharedFlow<Bitmap> = _captureEvents.asSharedFlow()

    fun updateCapturedBitmap(bitmap: Bitmap) {
        _latestBitmap.value = bitmap
        _captureCount.value += 1
        _captureEvents.tryEmit(bitmap)
    }

    fun clear() {
        _latestBitmap.value = null
    }
}
