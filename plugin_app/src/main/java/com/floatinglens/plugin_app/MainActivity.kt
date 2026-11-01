package com.floatinglens.plugin_app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var modelEngine: CompanionModelEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelEngine = CompanionModelEngine(this)
        modelEngine.loadModel()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PluginAppScreen(modelEngine = modelEngine)
                }
            }
        }
    }
}

@Composable
fun PluginAppScreen(modelEngine: CompanionModelEngine) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoadedState by remember { mutableStateOf(modelEngine.isLoaded()) }
    var loadedPathState by remember { mutableStateOf(modelEngine.getLoadedModelPath()) }
    var loadedTypeState by remember { mutableStateOf(modelEngine.getLoadedModelTypeString()) }
    var statusMessage by remember { mutableStateOf(modelEngine.getStatusMessage()) }
    var badgeState by remember { mutableStateOf(modelEngine.getActiveModelStatusBadgeText()) }

    var modelPathInput by remember {
        mutableStateOf(if (modelEngine.isLoaded()) modelEngine.getLoadedModelPath() else "")
    }

    var inputText by remember { mutableStateOf("探してみるか") }
    var resultText by remember { mutableStateOf("") }
    var lastPromptSent by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }

    fun refreshState() {
        isLoadedState = modelEngine.isLoaded()
        loadedPathState = modelEngine.getLoadedModelPath()
        loadedTypeState = modelEngine.getLoadedModelTypeString()
        statusMessage = modelEngine.getStatusMessage()
        badgeState = modelEngine.getActiveModelStatusBadgeText()
        if (modelEngine.isLoaded()) {
            modelPathInput = modelEngine.getLoadedModelPath()
        }
    }

    fun showToastForModelLoad() {
        if (modelEngine.isLoaded()) {
            val modelName = modelEngine.getLoadedModelFilename()
            Toast.makeText(context, "Model successfully loaded: $modelName", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to load model file. Please check file path", Toast.LENGTH_SHORT).show()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val resolvedPath = resolvePathFromUri(context, it)
            modelPathInput = resolvedPath
            modelEngine.loadModel(resolvedPath)
            refreshState()
            showToastForModelLoad()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Companion AI Plugin",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Supports On-Device (.task / .gguf) & PC LM Studio Local AI Server",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Text(
            text = "Target Device: Xiaomi Pad 6 / HyperOS Tablet",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Prominent Active Model Status Badge
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (isLoadedState) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = badgeState,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLoadedState) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLoadedState) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Service & Engine Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AIDL Service Action:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "com.floatinglens.plugin.TRANSLATE_SERVICE",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Supported Formats & Services:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = ".task (MediaPipe)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = ".gguf (llama.cpp)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "LM Studio PC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Active Target / Path:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = loadedPathState,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Active Engine:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = loadedTypeState,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PC LM Studio Local AI Server Card (Above On-Device Model Selection)
        var useLmStudio by remember { mutableStateOf(modelEngine.useLmStudioServer) }
        var lmStudioIp by remember { mutableStateOf(modelEngine.lmStudioServerIp) }
        var lmStudioPortStr by remember { mutableStateOf(modelEngine.lmStudioPort.toString()) }
        var lmStudioModel by remember { mutableStateOf(modelEngine.lmStudioModelName) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PC LM Studio Local AI Server",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Route translation requests to LM Studio on PC",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useLmStudio,
                        onCheckedChange = { checked ->
                            useLmStudio = checked
                            modelEngine.useLmStudioServer = checked
                            refreshState()
                        }
                    )
                }

                if (useLmStudio) {
                    OutlinedTextField(
                        value = lmStudioIp,
                        onValueChange = {
                            lmStudioIp = it
                            modelEngine.lmStudioServerIp = it
                            refreshState()
                        },
                        label = { Text("PC IP Address") },
                        placeholder = { Text("10.147.137.213") },
                        supportingText = { Text("Tap preset below if using Emulator vs Physical Phone:", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val emuIp = "10.0.2.2"
                                lmStudioIp = emuIp
                                modelEngine.lmStudioServerIp = emuIp
                                refreshState()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("10.0.2.2 (Emulator)", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val pcIp = "10.147.137.213"
                                lmStudioIp = pcIp
                                modelEngine.lmStudioServerIp = pcIp
                                refreshState()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("10.147.137.213 (Wi-Fi)", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = lmStudioPortStr,
                            onValueChange = {
                                lmStudioPortStr = it
                                it.toIntOrNull()?.let { port ->
                                    modelEngine.lmStudioPort = port
                                    refreshState()
                                }
                            },
                            label = { Text("Port") },
                            placeholder = { Text("1234") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = lmStudioModel,
                            onValueChange = {
                                lmStudioModel = it
                                modelEngine.lmStudioModelName = it
                                refreshState()
                            },
                            label = { Text("Model Name") },
                            placeholder = { Text("webbigdata-alma-7b-ja-v2") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                    }

                    var testStatus by remember { mutableStateOf<String?>(null) }
                    var isTesting by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isTesting = true
                                    testStatus = null
                                    val portInt = lmStudioPortStr.toIntOrNull() ?: 1234
                                    val (success, detailMsg) = modelEngine.testConnectionDetail(lmStudioIp, portInt)
                                    isTesting = false
                                    testStatus = if (success) "Connected!" else "Failed: $detailMsg"
                                }
                            },
                            enabled = !isTesting
                        ) {
                            Text(if (isTesting) "Testing..." else "Test Connection")
                        }

                        testStatus?.let { status ->
                            val color = if (status.contains("Connected")) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                            Text(
                                text = status,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Model Picker, Auto-Scan & Load Section (On-Device)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "On-Device Model Selection & Auto-Scan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-Scan Button
                Button(
                    onClick = {
                        modelEngine.autoScanDownloads()
                        refreshState()
                        showToastForModelLoad()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto-Scan Device Downloads")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = modelPathInput,
                    onValueChange = { modelPathInput = it },
                    label = { Text("Model File Path (.task or .gguf)") },
                    placeholder = { Text("e.g. /sdcard/Download/model.task") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Browse File")
                    }

                    Button(
                        onClick = {
                            modelEngine.loadModel(modelPathInput)
                            refreshState()
                            showToastForModelLoad()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Load Model")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Quick Load Presets:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val taskPath = "/sdcard/Download/model.task"
                            modelPathInput = taskPath
                            modelEngine.loadModel(taskPath)
                            refreshState()
                            showToastForModelLoad()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(".task Preset", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val ggufPath = "/sdcard/Download/qwen2.5-0.5b-instruct-q4_k_m.gguf"
                            modelPathInput = ggufPath
                            modelEngine.loadModel(ggufPath)
                            refreshState()
                            showToastForModelLoad()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(".gguf Preset", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Translation Test Section
        Text(
            text = "Test Prompt Translation Engine",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Input Japanese Text") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    isTranslating = true
                    val cleanText = inputText.replace("\r", "").replace("\n", "").trim()
                    val promptFormatted = if (modelEngine.useLmStudioServer) {
                        "Translate this from Japanese into English:\nJapanese: $cleanText\nEnglish:"
                    } else {
                        "<|im_start|>system\nTranslate this Japanese manga text into natural English. Output ONLY the English translation.<|im_end|>\n<|im_start|>user\n$cleanText<|im_end|>\n<|im_start|>assistant\n"
                    }
                    lastPromptSent = promptFormatted
                    resultText = modelEngine.generateTranslation(inputText, "ja", "en")
                    isTranslating = false
                }
            },
            enabled = !isTranslating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTranslating) "Translating..." else "Translate via Active AI Engine")
        }

        if (resultText.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Formatted Prompt Sent:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = lastPromptSent,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Translated English Output:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = resultText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun resolvePathFromUri(context: Context, uri: Uri): String {
    if (uri.scheme == "file") {
        return uri.path ?: uri.toString()
    }
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        return "/sdcard/Download/$name"
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return uri.path ?: uri.toString()
}
