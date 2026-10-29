package com.floatinglens.floatinglensaitranslator

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.floatinglens.floatinglensaitranslator.ocr.OcrScript
import com.floatinglens.floatinglensaitranslator.overlay.SpeechBubbleMaskOverlay
import com.floatinglens.floatinglensaitranslator.overlay.parseHexColor
import com.floatinglens.floatinglensaitranslator.plugin.TranslationPluginManager
import com.floatinglens.floatinglensaitranslator.processor.ProcessedTranslationResult
import com.floatinglens.floatinglensaitranslator.processor.ScreenTranslationPipeline
import com.floatinglens.floatinglensaitranslator.repository.ScreenCaptureRepository
import com.floatinglens.floatinglensaitranslator.service.FloatingOverlayService
import com.floatinglens.floatinglensaitranslator.settings.OverlaySettings
import com.floatinglens.floatinglensaitranslator.settings.OverlaySettingsRepository
import com.floatinglens.floatinglensaitranslator.translation.LanguageModelStatus
import com.floatinglens.floatinglensaitranslator.translation.ModelDownloadState
import com.floatinglens.floatinglensaitranslator.translation.OfflineTranslationEngine
import com.floatinglens.floatinglensaitranslator.ui.theme.FloatingLensAITranslatorTheme
import com.floatinglens.floatinglensaitranslator.utils.PermissionUtils
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isOverlayGrantedState by mutableStateOf(false)
    private var isNotificationGrantedState by mutableStateOf(false)

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, FloatingOverlayService::class.java).apply {
                    action = FloatingOverlayService.ACTION_START
                    putExtra(FloatingOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(FloatingOverlayService.EXTRA_RESULT_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, intent)
                Toast.makeText(this, "Floating Overlay Service Started!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Screen capture permission denied.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkPermissions()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            checkPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FloatingLensAITranslatorTheme {
                val context = LocalContext.current
                val isServiceRunning by FloatingOverlayService.isServiceRunning.collectAsState()
                val latestBitmap by ScreenCaptureRepository.latestBitmap.collectAsState()
                val captureCount by ScreenCaptureRepository.captureCount.collectAsState()

                val settingsRepo = remember { OverlaySettingsRepository.getInstance(context) }
                val settings by settingsRepo.settings.collectAsState()

                val offlineEngine = remember { OfflineTranslationEngine() }
                val modelStatuses by offlineEngine.modelStatuses.collectAsState()

                val pluginManager = remember { TranslationPluginManager(context) }
                val isPluginConnected by pluginManager.isConnected.collectAsState()
                val pluginName by pluginManager.pluginName.collectAsState()

                LaunchedEffect(Unit) {
                    pluginManager.bindPluginService()
                }

                DisposableEffect(Unit) {
                    checkPermissions()
                    onDispose {
                        pluginManager.unbindPluginService()
                        offlineEngine.close()
                    }
                }

                MainScreen(
                    isOverlayGranted = isOverlayGrantedState,
                    isNotificationGranted = isNotificationGrantedState,
                    isServiceRunning = isServiceRunning,
                    latestBitmap = latestBitmap,
                    captureCount = captureCount,
                    settings = settings,
                    modelStatuses = modelStatuses,
                    isPluginConnected = isPluginConnected,
                    pluginName = pluginName,
                    settingsRepo = settingsRepo,
                    offlineEngine = offlineEngine,
                    onRequestOverlayPermission = {
                        val intent = PermissionUtils.getOverlayPermissionIntent(this)
                        overlayPermissionLauncher.launch(intent)
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStartService = {
                        if (!PermissionUtils.hasOverlayPermission(this)) {
                            Toast.makeText(
                                this,
                                "Please grant System Overlay permission first",
                                Toast.LENGTH_SHORT
                            ).show()
                            val intent = PermissionUtils.getOverlayPermissionIntent(this)
                            overlayPermissionLauncher.launch(intent)
                            return@MainScreen
                        }

                        val mediaProjectionManager =
                            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    },
                    onStopService = {
                        val intent = Intent(this, FloatingOverlayService::class.java).apply {
                            action = FloatingOverlayService.ACTION_STOP
                        }
                        startService(intent)
                    },
                    onClearPreview = {
                        ScreenCaptureRepository.clear()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        isOverlayGrantedState = PermissionUtils.hasOverlayPermission(this)
        isNotificationGrantedState = PermissionUtils.hasNotificationPermission(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isOverlayGranted: Boolean,
    isNotificationGranted: Boolean,
    isServiceRunning: Boolean,
    latestBitmap: Bitmap?,
    captureCount: Int,
    settings: OverlaySettings,
    modelStatuses: List<LanguageModelStatus>,
    isPluginConnected: Boolean,
    pluginName: String?,
    settingsRepo: OverlaySettingsRepository,
    offlineEngine: OfflineTranslationEngine,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onClearPreview: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Floating Lens AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text("HyperOS Tablet Optimized", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        Text(
                            text = "In-Place Speech Bubble Translation & Overlay Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isTablet) {
                // Adaptive Navigation Rail layout for Xiaomi Pad 6 / Tablet
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        NavigationRailItem(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Overview & FAB") }
                        )
                        NavigationRailItem(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                            label = { Text("Engine & Plugin") }
                        )
                        NavigationRailItem(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            label = { Text("Overlay Tuning") }
                        )
                        NavigationRailItem(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            label = { Text("Offline Models") }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        when (selectedTabIndex) {
                            0 -> OverviewAndServiceTab(
                                isOverlayGranted = isOverlayGranted,
                                isNotificationGranted = isNotificationGranted,
                                isServiceRunning = isServiceRunning,
                                latestBitmap = latestBitmap,
                                captureCount = captureCount,
                                settings = settings,
                                onRequestOverlayPermission = onRequestOverlayPermission,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onStartService = onStartService,
                                onStopService = onStopService,
                                onClearPreview = onClearPreview
                            )

                            1 -> TranslationEngineAndPluginTab(
                                settings = settings,
                                isPluginConnected = isPluginConnected,
                                pluginName = pluginName,
                                settingsRepo = settingsRepo
                            )

                            2 -> SpeechBubbleOverlayTuningTab(
                                settings = settings,
                                settingsRepo = settingsRepo
                            )

                            3 -> OfflineModelManagerTab(
                                modelStatuses = modelStatuses,
                                offlineEngine = offlineEngine
                            )
                        }
                    }
                }
            } else {
                // Compact Mobile tabbed layout
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Service", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Engine", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text("Tuning", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            text = { Text("Models", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        when (selectedTabIndex) {
                            0 -> OverviewAndServiceTab(
                                isOverlayGranted = isOverlayGranted,
                                isNotificationGranted = isNotificationGranted,
                                isServiceRunning = isServiceRunning,
                                latestBitmap = latestBitmap,
                                captureCount = captureCount,
                                settings = settings,
                                onRequestOverlayPermission = onRequestOverlayPermission,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onStartService = onStartService,
                                onStopService = onStopService,
                                onClearPreview = onClearPreview
                            )

                            1 -> TranslationEngineAndPluginTab(
                                settings = settings,
                                isPluginConnected = isPluginConnected,
                                pluginName = pluginName,
                                settingsRepo = settingsRepo
                            )

                            2 -> SpeechBubbleOverlayTuningTab(
                                settings = settings,
                                settingsRepo = settingsRepo
                            )

                            3 -> OfflineModelManagerTab(
                                modelStatuses = modelStatuses,
                                offlineEngine = offlineEngine
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewAndServiceTab(
    isOverlayGranted: Boolean,
    isNotificationGranted: Boolean,
    isServiceRunning: Boolean,
    latestBitmap: Bitmap?,
    captureCount: Int,
    settings: OverlaySettings,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onClearPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PermissionStatusCard(
            isOverlayGranted = isOverlayGranted,
            isNotificationGranted = isNotificationGranted,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onRequestNotificationPermission = onRequestNotificationPermission
        )

        ServiceControlCard(
            isServiceRunning = isServiceRunning,
            isOverlayGranted = isOverlayGranted,
            onStartService = onStartService,
            onStopService = onStopService
        )

        CaptureAndInteractivePreviewCard(
            latestBitmap = latestBitmap,
            captureCount = captureCount,
            settings = settings,
            onClearPreview = onClearPreview
        )
    }
}

@Composable
fun PermissionStatusCard(
    isOverlayGranted: Boolean,
    isNotificationGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Required System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            PermissionRow(
                title = "Display over other apps (Overlay)",
                subtitle = "Allows floating FAB button and speech bubble translation masks",
                isGranted = isOverlayGranted,
                icon = Icons.Default.Layers,
                onRequest = onRequestOverlayPermission
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    title = "Post Notifications",
                    subtitle = "Required for MediaProjection foreground service",
                    isGranted = isNotificationGranted,
                    icon = Icons.Default.Notifications,
                    onRequest = onRequestNotificationPermission
                )
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Permission Granted",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(28.dp)
            )
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(text = "Grant", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ServiceControlCard(
    isServiceRunning: Boolean,
    isOverlayGranted: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (isServiceRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Floating Overlay Service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isServiceRunning -> "Floating FAB active & ready for screen translation"
                            !isOverlayGranted -> "Overlay permission required before starting"
                            else -> "Service is currently stopped"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "ACTIVE" else "STOPPED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = statusColor
                    )
                }
            }

            if (!isServiceRunning) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isOverlayGranted) "Start Floating Lens Service" else "Grant Permission & Start Service",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onStopService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Floating Lens Service", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CaptureAndInteractivePreviewCard(
    latestBitmap: Bitmap?,
    captureCount: Int,
    settings: OverlaySettings,
    onClearPreview: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var processedResult by remember { mutableStateOf<ProcessedTranslationResult?>(null) }
    var showInteractiveMaskOverlay by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Screen Frame & Mask Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (captureCount > 0) {
                    Text(
                        text = "Captures: $captureCount",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (latestBitmap != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Captured Frame (${latestBitmap.width} x ${latestBitmap.height}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(latestBitmap.width.toFloat() / latestBitmap.height.toFloat().coerceAtLeast(1f))
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Image(
                            bitmap = latestBitmap.asImageBitmap(),
                            contentDescription = "Captured Screen Frame",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Render overlay directly over image preview if test processing ran
                        if (showInteractiveMaskOverlay && processedResult != null) {
                            val result = processedResult!!
                            SpeechBubbleMaskOverlay(
                                bubbles = result.bubbles,
                                settings = settings,
                                bitmapWidth = result.bitmapWidth,
                                bitmapHeight = result.bitmapHeight,
                                screenWidthPx = latestBitmap.width,
                                screenHeightPx = latestBitmap.height,
                                onDismiss = { showInteractiveMaskOverlay = false }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    val pipeline = ScreenTranslationPipeline(context)
                                    pipeline.init()
                                    pipeline.translationManager.preferPlugin = settings.preferPlugin

                                    val script = when (settings.sourceLanguage) {
                                        TranslateLanguage.JAPANESE -> OcrScript.JAPANESE
                                        TranslateLanguage.KOREAN -> OcrScript.KOREAN
                                        TranslateLanguage.CHINESE -> OcrScript.CHINESE
                                        TranslateLanguage.SPANISH, TranslateLanguage.ENGLISH -> OcrScript.LATIN
                                        else -> OcrScript.AUTO
                                    }

                                    try {
                                        val result = pipeline.processFrame(
                                            bitmap = latestBitmap,
                                            script = script,
                                            sourceLang = settings.sourceLanguage,
                                            targetLang = settings.targetLanguage
                                        )
                                        processedResult = result
                                        showInteractiveMaskOverlay = true
                                        Toast.makeText(
                                            context,
                                            "Processed ${result.bubbles.size} bubbles in ${result.totalTimeMs}ms",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error processing frame: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        pipeline.close()
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Translating...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Mask Rendering")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showInteractiveMaskOverlay = false
                                processedResult = null
                                onClearPreview()
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No screen captured yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Start floating service and tap FAB over external apps to capture and translate speech bubbles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationEngineAndPluginTab(
    settings: OverlaySettings,
    isPluginConnected: Boolean,
    pluginName: String?,
    settingsRepo: OverlaySettingsRepository
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Language Preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Language Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Source Language Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Source Language",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LanguageDropdown(
                            selectedLang = settings.sourceLanguage,
                            languages = listOf(
                                TranslateLanguage.JAPANESE to "Japanese (日本語)",
                                TranslateLanguage.KOREAN to "Korean (한국어)",
                                TranslateLanguage.CHINESE to "Chinese (中文)",
                                TranslateLanguage.SPANISH to "Spanish (Español)",
                                TranslateLanguage.ENGLISH to "English"
                            ),
                            onSelect = { settingsRepo.updateSourceLanguage(it) }
                        )
                    }

                    // Target Language Selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Target Language",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LanguageDropdown(
                            selectedLang = settings.targetLanguage,
                            languages = listOf(
                                TranslateLanguage.ENGLISH to "English",
                                TranslateLanguage.SPANISH to "Spanish",
                                TranslateLanguage.JAPANESE to "Japanese",
                                TranslateLanguage.KOREAN to "Korean",
                                TranslateLanguage.CHINESE to "Chinese"
                            ),
                            onSelect = { settingsRepo.updateTargetLanguage(it) }
                        )
                    }
                }
            }
        }

        // 2. Translation Engine & Plugin Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Translation Provider Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Option A: Local ML Kit Engine
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { settingsRepo.updatePreferPlugin(false) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !settings.preferPlugin,
                        onClick = { settingsRepo.updatePreferPlugin(false) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Local Google ML Kit Offline",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.colorScheme.onSurface.let { MaterialTheme.typography.bodyMedium }
                        )
                        Text(
                            text = "Fast on-device neural translation without network calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Option B: Qwen Companion AIDL Plugin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { settingsRepo.updatePreferPlugin(true) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.preferPlugin,
                        onClick = { settingsRepo.updatePreferPlugin(true) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Qwen Companion AI Plugin (AIDL)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "LLM contextual speech bubble translation with automatic offline ML Kit fallback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Plugin Connection Status Card
                val statusBgColor = if (isPluginConnected) {
                    Color(0xFF2E7D32).copy(alpha = 0.1f)
                } else {
                    Color(0xFFE65100).copy(alpha = 0.08f)
                }
                val statusDotColor = if (isPluginConnected) {
                    Color(0xFF2E7D32)
                } else {
                    Color(0xFFE65100)
                }
                val statusTitleColor = if (isPluginConnected) {
                    Color(0xFF2E7D32)
                } else {
                    Color(0xFFE65100)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBgColor)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusDotColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isPluginConnected) "AIDL Plugin Connected" else "No External Companion Plugin Installed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = statusTitleColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (isPluginConnected) {
                                if (!pluginName.isNullOrBlank()) {
                                    Text(
                                        text = "Active Plugin: $pluginName",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Install a Qwen Companion APK or use Local ML Kit Offline Engine",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    selectedLang: String,
    languages: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = languages.find { it.first == selectedLang }?.second ?: selectedLang

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            shape = RoundedCornerShape(8.dp),
            textStyle = TextStyle(fontSize = 13.sp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechBubbleOverlayTuningTab(
    settings: OverlaySettings,
    settingsRepo: OverlaySettingsRepository
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "In-Place Masking & Overlay Styling",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Pill Background Color
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pill Mask Background Color",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val colorOptions = listOf(
                        "#FFFFFF" to "Pure White",
                        "#FFFDD0" to "Warm Cream",
                        "#F0F0F0" to "Light Gray",
                        "#E6FFFFFF" to "Translucent White",
                        "#222222" to "Dark Charcoal"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorOptions.forEach { (hex, name) ->
                            val isSelected = settings.pillColorHex.equals(hex, ignoreCase = true)
                            val colorVal = parseHexColor(hex, Color.White)

                            FilterChip(
                                selected = isSelected,
                                onClick = { settingsRepo.updatePillColor(hex) },
                                label = { Text(name, fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(colorVal)
                                            .border(1.dp, Color.Gray, CircleShape)
                                    )
                                }
                            )
                        }
                    }
                }

                // 2. Corner Radius Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Corner Radius",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${settings.cornerRadiusDp} dp (Recommended: 6–8dp)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = settings.cornerRadiusDp.toFloat(),
                        onValueChange = { settingsRepo.updateCornerRadius(it.toInt()) },
                        valueRange = 4f..20f,
                        steps = 15
                    )
                }

                // 3. Text Auto-Scale & Color
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto-Scale Text Size",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dynamically scales English text to fit inside bubble without clipping",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoScaleText,
                            onCheckedChange = { settingsRepo.updateAutoScaleText(it) }
                        )
                    }

                    Text(
                        text = "Text Color",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val textColorOptions = listOf(
                        "#111111" to "Dark Charcoal",
                        "#000000" to "Pure Black",
                        "#FFFFFF" to "Pure White"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        textColorOptions.forEach { (hex, name) ->
                            val isSelected = settings.textColorHex.equals(hex, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { settingsRepo.updateTextColor(hex) },
                                label = { Text(name, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // 4. Furigana Filtering Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Furigana Filtering",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Filters out small Japanese readings above kanji to prevent duplicate OCR text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.filterFurigana,
                        onCheckedChange = { settingsRepo.updateFilterFurigana(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineModelManagerTab(
    modelStatuses: List<LanguageModelStatus>,
    offlineEngine: OfflineTranslationEngine
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ML Kit Offline Language Models",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { offlineEngine.updateModelStatuses() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh model status")
                    }
                }

                Text(
                    text = "Download language models locally for fast offline translation without internet connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                modelStatuses.forEach { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.languageName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Code: ${status.languageCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (status.state == ModelDownloadState.DOWNLOADING) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
                                }
                            }

                            when (status.state) {
                                ModelDownloadState.DOWNLOADED -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("Downloaded", fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    offlineEngine.deleteModel(status.languageCode)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Model",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                ModelDownloadState.DOWNLOADING -> {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }

                                else -> {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                offlineEngine.downloadModel(status.languageCode)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun MainScreenTabletPreview() {
    FloatingLensAITranslatorTheme {
        MainScreen(
            isOverlayGranted = true,
            isNotificationGranted = true,
            isServiceRunning = true,
            latestBitmap = null,
            captureCount = 0,
            settings = OverlaySettings(),
            modelStatuses = listOf(
                LanguageModelStatus("ja", "Japanese", ModelDownloadState.DOWNLOADED),
                LanguageModelStatus("en", "English", ModelDownloadState.DOWNLOADED),
                LanguageModelStatus("ko", "Korean", ModelDownloadState.NOT_DOWNLOADED)
            ),
            isPluginConnected = true,
            pluginName = "Qwen Companion AI Service",
            settingsRepo = OverlaySettingsRepository(LocalContext.current),
            offlineEngine = OfflineTranslationEngine(),
            onRequestOverlayPermission = {},
            onRequestNotificationPermission = {},
            onStartService = {},
            onStopService = {},
            onClearPreview = {}
        )
    }
}
