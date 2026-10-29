package com.floatinglens.floatinglensaitranslator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.floatinglens.floatinglensaitranslator.MainActivity
import com.floatinglens.floatinglensaitranslator.R
import com.floatinglens.floatinglensaitranslator.capture.ScreenCaptureEngine
import com.floatinglens.floatinglensaitranslator.ocr.OcrScript
import com.floatinglens.floatinglensaitranslator.overlay.TranslationOverlayManager
import com.floatinglens.floatinglensaitranslator.processor.ScreenTranslationPipeline
import com.floatinglens.floatinglensaitranslator.repository.ScreenCaptureRepository
import com.floatinglens.floatinglensaitranslator.settings.OverlaySettingsRepository
import com.floatinglens.floatinglensaitranslator.translation.TranslationProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlayService"

        const val ACTION_START = "com.floatinglens.floatinglensaitranslator.ACTION_START_OVERLAY"
        const val ACTION_STOP = "com.floatinglens.floatinglensaitranslator.ACTION_STOP_OVERLAY"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_CHANNEL_ID = "floating_lens_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var fabView: View? = null
    private var dismissTargetView: View? = null

    private var fabParams: WindowManager.LayoutParams? = null
    private var dismissParams: WindowManager.LayoutParams? = null

    private var screenCaptureEngine: ScreenCaptureEngine? = null
    private var mediaProjection: MediaProjection? = null

    private var isOverDismissTarget = false

    private lateinit var settingsRepository: OverlaySettingsRepository
    private lateinit var translationPipeline: ScreenTranslationPipeline
    private lateinit var overlayManager: TranslationOverlayManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingOverlayService onCreate")
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        settingsRepository = OverlaySettingsRepository.getInstance(this)
        translationPipeline = ScreenTranslationPipeline(this)
        overlayManager = TranslationOverlayManager(this)

        translationPipeline.init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == 0 || resultData == null) {
            Log.e(TAG, "Missing MediaProjection permission result code/data")
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Start Foreground Service FIRST (required on Android 14+)
        startServiceForeground()

        // 2. Obtain MediaProjection AFTER starting foreground service
        try {
            val mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection stopped by system")
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            mediaProjection?.let { proj ->
                screenCaptureEngine = ScreenCaptureEngine(this, proj)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaProjection: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // 3. Setup Floating Views
        setupOverlayViews()

        _isServiceRunning.value = true
        return START_STICKY
    }

    private fun startServiceForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for Floating Lens screen capture"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenAppIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingOpenAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                pendingStopIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupOverlayViews() {
        if (fabView != null) return // Already setup

        val inflater = LayoutInflater.from(this)

        // 1. Setup FAB View
        fabView = inflater.inflate(R.layout.overlay_floating_fab, null)
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        fabParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // 2. Setup Dismiss Target View
        dismissTargetView = inflater.inflate(R.layout.overlay_dismiss_target, null)
        dismissParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(48f)
        }
        dismissTargetView?.visibility = View.GONE

        // Add views to WindowManager
        windowManager?.addView(dismissTargetView, dismissParams)
        windowManager?.addView(fabView, fabParams)

        setupFabTouchListener()
    }

    private fun setupFabTouchListener() {
        val currentFab = fabView ?: return

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        currentFab.setOnTouchListener { _, event ->
            val params = fabParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isOverDismissTarget = false

                    dismissTargetView?.visibility = View.VISIBLE
                    updateDismissTargetState(highlighted = false)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true
                    }

                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(fabView, params)

                    // Collision check with dismiss target
                    checkDismissTargetCollision()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dismissTargetView?.visibility = View.GONE

                    if (isOverDismissTarget) {
                        Log.d(TAG, "FAB dropped over dismiss target -> stopping service")
                        vibrateDevice()
                        stopSelf()
                    } else if (!isDragging) {
                        Log.d(TAG, "FAB tapped -> capturing screen frame & running translation overlay")
                        performSilentScreenCapture()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun checkDismissTargetCollision() {
        val fab = fabView ?: return
        val target = dismissTargetView ?: return

        val fabLocation = IntArray(2)
        fab.getLocationOnScreen(fabLocation)
        val fabCenterX = fabLocation[0] + fab.width / 2
        val fabCenterY = fabLocation[1] + fab.height / 2

        val targetLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        val targetCenterX = targetLocation[0] + target.width / 2
        val targetCenterY = targetLocation[1] + target.height / 2

        val distance = hypot(
            (fabCenterX - targetCenterX).toDouble(),
            (fabCenterY - targetCenterY).toDouble()
        )

        val thresholdPx = dpToPx(120f)
        val newlyOver = distance < thresholdPx

        if (newlyOver != isOverDismissTarget) {
            isOverDismissTarget = newlyOver
            updateDismissTargetState(highlighted = isOverDismissTarget)
            if (isOverDismissTarget) {
                vibrateDevice()
            }
        }
    }

    private fun updateDismissTargetState(highlighted: Boolean) {
        val circle = dismissTargetView?.findViewById<FrameLayout>(R.id.dismissCircle) ?: return
        if (highlighted) {
            circle.setBackgroundResource(R.drawable.bg_dismiss_target_highlighted)
            circle.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150).start()
        } else {
            circle.setBackgroundResource(R.drawable.bg_dismiss_target)
            circle.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        }
    }

    private fun performSilentScreenCapture() {
        serviceScope.launch {
            // Hide FAB temporarily so it doesn't appear in screen frame buffer
            fabView?.visibility = View.INVISIBLE
            delay(60) // short delay for screen buffer refresh

            val bitmap = screenCaptureEngine?.captureFrame()

            fabView?.visibility = View.VISIBLE

            if (bitmap != null) {
                ScreenCaptureRepository.updateCapturedBitmap(bitmap)

                val currentSettings = settingsRepository.settings.value

                translationPipeline.translationManager.preferPlugin = currentSettings.preferPlugin

                val script = when (currentSettings.sourceLanguage) {
                    TranslateLanguage.JAPANESE -> OcrScript.JAPANESE
                    TranslateLanguage.KOREAN -> OcrScript.KOREAN
                    TranslateLanguage.CHINESE -> OcrScript.CHINESE
                    TranslateLanguage.SPANISH, TranslateLanguage.ENGLISH -> OcrScript.LATIN
                    else -> OcrScript.AUTO
                }

                val processedResult = translationPipeline.processFrame(
                    bitmap = bitmap,
                    script = script,
                    sourceLang = currentSettings.sourceLanguage,
                    targetLang = currentSettings.targetLanguage
                )

                overlayManager.showOverlay(processedResult, currentSettings)

                val statusMessage = getTranslationStatusToastMessage(
                    providerUsed = processedResult.metadata.providerUsed,
                    bubbleCount = processedResult.metadata.bubbleCount
                )

                Toast.makeText(
                    this@FloatingOverlayService,
                    statusMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@FloatingOverlayService,
                    "Failed to capture screen",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getTranslationStatusToastMessage(
        providerUsed: TranslationProvider,
        bubbleCount: Int
    ): String {
        if (bubbleCount == 0) {
            return "No speech bubbles detected on screen"
        }

        val bubbleText = if (bubbleCount == 1) "1 speech bubble" else "$bubbleCount speech bubbles"

        return when (providerUsed) {
            TranslationProvider.QWEN_AI_PLUGIN ->
                "Translated via Qwen AI Engine ($bubbleText)"
            TranslationProvider.ML_KIT_OFFLINE ->
                "Translated via ML Kit Offline ($bubbleText)"
            TranslationProvider.ML_KIT_FALLBACK ->
                "Translated via ML Kit Offline (Qwen Fallback) ($bubbleText)"
        }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(40)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingOverlayService onDestroy")
        _isServiceRunning.value = false

        try {
            fabView?.let { windowManager?.removeView(it) }
            dismissTargetView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing window views: ${e.message}")
        }

        fabView = null
        dismissTargetView = null

        overlayManager.clearOverlay()
        translationPipeline.close()

        screenCaptureEngine?.stop()
        screenCaptureEngine = null

        serviceScope.cancel()
    }
}
