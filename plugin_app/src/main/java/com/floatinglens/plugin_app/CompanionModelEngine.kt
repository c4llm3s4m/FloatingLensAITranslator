package com.floatinglens.plugin_app

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Companion Model Engine supporting Google MediaPipe GenAI (`.task`),
 * llama.cpp (`.gguf`) LLM model files, and PC LM Studio Server (/v1/completions).
 */
class CompanionModelEngine(private val context: Context) {

    enum class ModelType {
        TASK,
        GGUF,
        LM_STUDIO,
        UNKNOWN
    }

    companion object {
        private const val TAG = "CompanionModelEngine"
        private const val PREFS_NAME = "companion_model_prefs"
        private const val KEY_SAVED_MODEL_PATH = "saved_model_path"

        private const val KEY_USE_LM_STUDIO = "use_lm_studio"
        private const val KEY_LM_STUDIO_IP = "lm_studio_ip"
        private const val KEY_LM_STUDIO_PORT = "lm_studio_port"
        private const val KEY_LM_STUDIO_MODEL = "lm_studio_model"

        const val DEFAULT_LM_STUDIO_IP = "10.147.137.213"
        const val DEFAULT_LM_STUDIO_PORT = 1234
        const val DEFAULT_LM_STUDIO_MODEL = "webbigdata-alma-7b-ja-v2"

        const val DEFAULT_GGUF_MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val DEFAULT_TASK_MODEL_NAME = "model.task"
        const val DEFAULT_MODEL_NAME = DEFAULT_GGUF_MODEL_NAME
        const val NO_MODEL_LOADED_NOTICE = "No .task or .gguf model file loaded yet. Please select your model file in Qwen Companion App"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private var loadedModelPath: String? = null
    private var currentModelType: ModelType = ModelType.UNKNOWN
    private var isInitialized = false
    private var llmInference: LlmInference? = null

    var useLmStudioServer: Boolean
        get() = prefs.getBoolean(KEY_USE_LM_STUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_LM_STUDIO, value).apply()

    var lmStudioServerIp: String
        get() = prefs.getString(KEY_LM_STUDIO_IP, DEFAULT_LM_STUDIO_IP) ?: DEFAULT_LM_STUDIO_IP
        set(value) = prefs.edit().putString(KEY_LM_STUDIO_IP, value).apply()

    var lmStudioPort: Int
        get() = prefs.getInt(KEY_LM_STUDIO_PORT, DEFAULT_LM_STUDIO_PORT)
        set(value) = prefs.edit().putInt(KEY_LM_STUDIO_PORT, value).apply()

    var lmStudioModelName: String
        get() = prefs.getString(KEY_LM_STUDIO_MODEL, DEFAULT_LM_STUDIO_MODEL) ?: DEFAULT_LM_STUDIO_MODEL
        set(value) = prefs.edit().putString(KEY_LM_STUDIO_MODEL, value).apply()

    suspend fun testConnectionDetail(ip: String = lmStudioServerIp, port: Int = lmStudioPort): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val baseUrl = buildBaseUrl(ip, port)
        val url = "$baseUrl/v1/models"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Bypass-Tunnel-Reminder", "true")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, "Connected!")
                } else {
                    Pair(false, "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LM Studio connection test failed at $url: ${e.message}")
            Pair(false, e.localizedMessage ?: e.message ?: "Connection failed")
        }
    }

    suspend fun testConnection(ip: String = lmStudioServerIp, port: Int = lmStudioPort): Boolean {
        return testConnectionDetail(ip, port).first
    }

    private fun buildBaseUrl(rawInput: String, defaultPort: Int): String {
        var input = rawInput.trim()
        if (input.isEmpty()) {
            return "http://$DEFAULT_LM_STUDIO_IP:$defaultPort"
        }

        input = input.trimEnd('/')

        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input
        }

        if (input.contains(".loca.lt") || input.contains(".ngrok") || input.contains(".app") || input.contains(".page") || input.contains(".pub") || input.contains(".dev") || input.contains(".io")) {
            return "https://$input"
        }

        val cleanHost = input.removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (cleanHost.isEmpty()) {
            return "http://$DEFAULT_LM_STUDIO_IP:$defaultPort"
        }

        if (cleanHost.contains(":") && !cleanHost.contains("[")) {
            return "http://$cleanHost"
        }

        return "http://$cleanHost:$defaultPort"
    }

    private fun cleanIpAddress(rawIp: String): String {
        var clean = rawIp.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (clean.contains("/")) {
            clean = clean.substringBefore('/')
        }
        if (clean.contains(":") && !clean.contains("[")) {
            clean = clean.substringBefore(':')
        }
        return clean.ifBlank { DEFAULT_LM_STUDIO_IP }
    }

    fun detectModelType(path: String): ModelType {
        val cleanPath = path.lowercase().trim()
        return when {
            cleanPath.endsWith(".task") -> ModelType.TASK
            cleanPath.endsWith(".gguf") -> ModelType.GGUF
            else -> ModelType.UNKNOWN
        }
    }

    fun autoScanDownloads(): String? {
        Log.i(TAG, "Starting Auto-Scan for device downloads (.task or .gguf model files)...")

        @Suppress("SdCardPath")
        val searchDirs = LinkedHashSet<File>().apply {
            add(File("/sdcard/Download"))
            add(File("/sdcard/Downloads"))
            try {
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            } catch (_: Exception) {}
            try {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { add(it) }
            } catch (_: Exception) {}
            try {
                context.getExternalFilesDir(null)?.let { add(it) }
            } catch (_: Exception) {}
            try {
                context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { add(it) }
            } catch (_: Exception) {}
            try {
                add(context.filesDir)
                add(File(context.filesDir, "models"))
            } catch (_: Exception) {}
        }

        val foundFiles = mutableListOf<File>()

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                try {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.canRead()) {
                            val name = file.name.lowercase()
                            if (name.endsWith(".task") || name.endsWith(".gguf")) {
                                foundFiles.add(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error listing files in ${dir.absolutePath}: ${e.message}")
                }
            }
        }

        if (foundFiles.isNotEmpty()) {
            foundFiles.sortByDescending { it.lastModified() }
            val bestFile = foundFiles.first()
            Log.i(TAG, "Auto-Scan detected model file: ${bestFile.absolutePath}")
            val loaded = loadModel(bestFile.absolutePath)
            if (loaded) {
                return bestFile.absolutePath
            }
        }

        Log.w(TAG, NO_MODEL_LOADED_NOTICE)
        loadedModelPath = null
        isInitialized = false
        return null
    }

    fun loadModel(modelPath: String? = null): Boolean {
        closeLlmInference()

        var targetPath = modelPath?.trim()?.ifEmpty { null }

        if (targetPath == null) {
            val savedPath = prefs.getString(KEY_SAVED_MODEL_PATH, null)
            if (!savedPath.isNullOrBlank() && File(savedPath).exists()) {
                targetPath = savedPath
                Log.i(TAG, "Found valid saved model path in preferences: $targetPath")
            }
        }

        if (targetPath == null) {
            val scannedPath = autoScanDownloads()
            return if (scannedPath != null) {
                true
            } else {
                Log.w(TAG, NO_MODEL_LOADED_NOTICE)
                loadedModelPath = null
                isInitialized = false
                false
            }
        }

        Log.d(TAG, "Attempting to load model from path: $targetPath")

        val file = File(targetPath)
        if (!file.exists()) {
            Log.w(TAG, "Model file does not exist at $targetPath. $NO_MODEL_LOADED_NOTICE")
            loadedModelPath = null
            isInitialized = false
            return false
        }

        currentModelType = detectModelType(targetPath)
        when (currentModelType) {
            ModelType.TASK -> {
                Log.i(TAG, "Detected MediaPipe GenAI .task model file format.")
                loadedModelPath = targetPath
                isInitialized = true
                saveModelPath(targetPath)
                val success = initMediaPipeLlmInference(targetPath)
                Log.i(TAG, "Loaded .task model file at $targetPath (MediaPipe native init: $success)")
                return true
            }
            ModelType.GGUF -> {
                Log.i(TAG, "Detected llama.cpp GGUF .gguf model file format.")
                loadedModelPath = targetPath
                isInitialized = true
                saveModelPath(targetPath)
                Log.i(TAG, "GGUF model file found and loaded: $targetPath")
                return true
            }
            ModelType.UNKNOWN -> {
                Log.w(TAG, "Unknown model extension for $targetPath. Defaulting based on filename.")
                loadedModelPath = targetPath
                isInitialized = true
                saveModelPath(targetPath)
                if (targetPath.lowercase().endsWith(".task")) {
                    currentModelType = ModelType.TASK
                    initMediaPipeLlmInference(targetPath)
                } else {
                    currentModelType = ModelType.GGUF
                }
                return true
            }
            ModelType.LM_STUDIO -> {
                return true
            }
        }
    }

    private fun saveModelPath(path: String) {
        try {
            prefs.edit().putString(KEY_SAVED_MODEL_PATH, path).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save model path to SharedPreferences: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun initMediaPipeLlmInference(modelPath: String): Boolean {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "LlmInference created successfully for path: $modelPath")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "LlmInference initialization skipped or failed for $modelPath: ${e.message}")
            llmInference = null
            false
        }
    }

    private fun closeLlmInference() {
        try {
            llmInference?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing LlmInference: ${e.message}")
        } finally {
            llmInference = null
        }
    }

    fun isLoaded(): Boolean {
        if (useLmStudioServer) return true
        val path = loadedModelPath
        return isInitialized && path != null && File(path).exists()
    }

    fun getLoadedModelPath(): String {
        if (useLmStudioServer) {
            return "http://${cleanIpAddress(lmStudioServerIp)}:$lmStudioPort ($lmStudioModelName)"
        }
        return loadedModelPath ?: "None"
    }

    fun getLoadedModelFilename(): String {
        if (useLmStudioServer) return lmStudioModelName
        return loadedModelPath?.let { File(it).name } ?: "None"
    }

    fun getLoadedModelType(): ModelType = if (useLmStudioServer) ModelType.LM_STUDIO else currentModelType

    fun getLoadedModelTypeString(): String {
        if (useLmStudioServer) return "PC LM Studio Local AI Server"
        if (!isLoaded()) return "None / Unloaded"
        return when (currentModelType) {
            ModelType.TASK -> "Google MediaPipe GenAI (.task)"
            ModelType.GGUF -> "llama.cpp GGUF (.gguf)"
            ModelType.LM_STUDIO -> "PC LM Studio Local AI Server"
            ModelType.UNKNOWN -> "Auto-detect / Fallback"
        }
    }

    fun getStatusMessage(): String {
        return if (useLmStudioServer) {
            "Active Engine: PC LM Studio Server at http://${cleanIpAddress(lmStudioServerIp)}:$lmStudioPort [$lmStudioModelName]"
        } else if (isLoaded()) {
            "Loaded model file: ${getLoadedModelFilename()} ($loadedModelPath)"
        } else {
            NO_MODEL_LOADED_NOTICE
        }
    }

    fun getActiveModelStatusBadgeText(): String {
        return if (useLmStudioServer) {
            "Active Engine: PC LM Studio Server [$lmStudioModelName]"
        } else if (isLoaded()) {
            "Active Model Status: Loaded [${getLoadedModelFilename()}]"
        } else {
            "Active Model Status: Waiting for Model Selection"
        }
    }

    fun translateText(sourceText: String, sourceLang: String = "ja", targetLang: String = "en"): String {
        return generateTranslation(sourceText, sourceLang, targetLang)
    }

    fun generateTranslation(sourceText: String, sourceLang: String = "ja", targetLang: String = "en"): String {
        if (sourceText.isBlank()) return ""

        // Strip all newline characters (\n) and extra spaces so vertical Japanese columns form a single continuous string
        val cleanText = sourceText.replace("\r", "").replace("\n", "").trim()
        if (cleanText.isBlank()) return ""

        // 1. If LM Studio Local AI Server is toggled ON, send HTTP POST request to /v1/completions
        if (useLmStudioServer) {
            return runBlocking {
                try {
                    translateViaLmStudio(cleanText)
                } catch (e: Exception) {
                    val errorText = "[LM Studio Error: ${e.message ?: "Failed to connect to LM Studio"}]"
                    Log.e(TAG, errorText, e)
                    errorText
                }
            }
        }

        // 2. On-Device Qwen Model (.task / .gguf) with ChatML Prompt Formatting
        llmInference?.let { engine ->
            try {
                val chatMlPrompt = "<|im_start|>system\nTranslate this Japanese manga text into natural English. Output ONLY the English translation.<|im_end|>\n<|im_start|>user\n$cleanText<|im_end|>\n<|im_start|>assistant\n"
                val translation = engine.generateResponse(chatMlPrompt)
                if (!translation.isNullOrBlank()) {
                    return translation.trim()
                }
            } catch (e: Throwable) {
                val errorText = "[Plugin Error: ${e.message ?: "Inference error"}]"
                Log.e(TAG, errorText, e)
                return errorText
            }
        }

        val noModelError = "[Plugin Error: No .task or .gguf model file loaded]"
        Log.w(TAG, noModelError)
        return noModelError
    }

    private suspend fun translateViaLmStudio(cleanText: String): String = withContext(Dispatchers.IO) {
        val baseUrl = buildBaseUrl(lmStudioServerIp, lmStudioPort)
        val url = "$baseUrl/v1/completions"

        val promptString = "Translate this from Japanese into English:\nJapanese: $cleanText\nEnglish:"

        val payload = JSONObject().apply {
            put("model", lmStudioModelName)
            put("prompt", promptString)
            put("temperature", 0.1)
            put("max_tokens", 128)
            put("stop", JSONArray().apply {
                put("\n")
                put("Japanese:")
            })
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Bypass-Tunnel-Reminder", "true")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = httpClient.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "LM Studio /v1/completions error ${response.code}: $errorBody")
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val responseBodyStr = response.body?.string()
                ?: throw Exception("Empty response body from LM Studio server")

            val jsonResponse = JSONObject(responseBodyStr)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choiceObj = choices.getJSONObject(0)
                val text = choiceObj.optString("text")?.trim()
                if (!text.isNullOrBlank()) {
                    return@withContext text
                }
            }
            throw Exception("No choice text returned in completion response")
        } finally {
            response.close()
        }
    }
}

/**
 * Backward compatibility type alias for GgufModelEngine.
 */
typealias GgufModelEngine = CompanionModelEngine
