package com.floatinglens.plugin_app

import android.content.Context
import android.os.Environment
import android.util.Log
import com.floatinglens.plugin_app.translation.OfflineTranslationEngine
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Companion Model Engine supporting both Google MediaPipe GenAI (`.task`)
 * and llama.cpp (`.gguf`) LLM model files.
 */
class CompanionModelEngine(private val context: Context) {

    enum class ModelType {
        TASK,
        GGUF,
        UNKNOWN
    }

    companion object {
        private const val TAG = "CompanionModelEngine"
        private const val PREFS_NAME = "companion_model_prefs"
        private const val KEY_SAVED_MODEL_PATH = "saved_model_path"

        const val DEFAULT_GGUF_MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val DEFAULT_TASK_MODEL_NAME = "model.task"
        const val DEFAULT_MODEL_NAME = DEFAULT_GGUF_MODEL_NAME
        const val NO_MODEL_LOADED_NOTICE = "No .task or .gguf model file loaded yet. Please select your model file in Qwen Companion App"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val offlineEngine by lazy { OfflineTranslationEngine() }

    private var loadedModelPath: String? = null
    private var currentModelType: ModelType = ModelType.UNKNOWN
    private var isInitialized = false
    private var llmInference: LlmInference? = null

    /**
     * Detect model type from file path extension.
     */
    fun detectModelType(path: String): ModelType {
        val cleanPath = path.lowercase().trim()
        return when {
            cleanPath.endsWith(".task") -> ModelType.TASK
            cleanPath.endsWith(".gguf") -> ModelType.GGUF
            else -> ModelType.UNKNOWN
        }
    }

    /**
     * Auto-scan common device download directories for .task or .gguf model files.
     * Searches /sdcard/Download/, /sdcard/Downloads/, context external storage, app dirs.
     * Automatically selects and loads the detected model file if found.
     */
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
            // Sort by last modified date descending to pick the newest model file
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

    /**
     * Initialize and load a .task or .gguf model file.
     * Can load from specified path, saved preferences, or auto-scan downloads.
     */
    fun loadModel(modelPath: String? = null): Boolean {
        closeLlmInference()

        var targetPath = modelPath?.trim()?.ifEmpty { null }

        if (targetPath == null) {
            // Check saved preference first
            val savedPath = prefs.getString(KEY_SAVED_MODEL_PATH, null)
            if (!savedPath.isNullOrBlank() && File(savedPath).exists()) {
                targetPath = savedPath
                Log.i(TAG, "Found valid saved model path in preferences: $targetPath")
            }
        }

        if (targetPath == null) {
            // Trigger auto-scan downloads if no path provided or saved
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
        val path = loadedModelPath
        return isInitialized && path != null && File(path).exists()
    }

    fun getLoadedModelPath(): String = loadedModelPath ?: "None"

    fun getLoadedModelFilename(): String {
        return loadedModelPath?.let { File(it).name } ?: "None"
    }

    fun getLoadedModelType(): ModelType = currentModelType

    fun getLoadedModelTypeString(): String {
        if (!isLoaded()) return "None / Unloaded"
        return when (currentModelType) {
            ModelType.TASK -> "Google MediaPipe GenAI (.task)"
            ModelType.GGUF -> "llama.cpp GGUF (.gguf)"
            ModelType.UNKNOWN -> "Auto-detect / Fallback"
        }
    }

    fun getStatusMessage(): String {
        return if (isLoaded()) {
            "Loaded model file: ${getLoadedModelFilename()} ($loadedModelPath)"
        } else {
            NO_MODEL_LOADED_NOTICE
        }
    }

    fun getActiveModelStatusBadgeText(): String {
        return if (isLoaded()) {
            "Active Model Status: Loaded [${getLoadedModelFilename()}]"
        } else {
            "Active Model Status: Waiting for Model Selection"
        }
    }

    /**
     * Delegate method for translating text (alias to generateTranslation).
     */
    fun translateText(sourceText: String, sourceLang: String = "ja", targetLang: String = "en"): String {
        return generateTranslation(sourceText, sourceLang, targetLang)
    }

    /**
     * Execute translation prompt using loaded MediaPipe .task or GGUF model,
     * or fall back to OfflineTranslationEngine (ML Kit Translate).
     */
    fun generateTranslation(sourceText: String, sourceLang: String = "ja", targetLang: String = "en"): String {
        if (sourceText.isBlank()) return ""

        val prompt = "Translate the following Japanese text to English: $sourceText"
        Log.d(TAG, "Inference request ($sourceLang -> $targetLang) [Type: $currentModelType]: $prompt")

        // 1. If MediaPipe LlmInference or AI model is loaded, run inference on the specific sourceText
        llmInference?.let { engine ->
            try {
                val translation = engine.generateResponse(prompt)
                if (!translation.isNullOrBlank()) {
                    Log.i(TAG, "MediaPipe LlmInference response for '$sourceText': $translation")
                    return translation.trim()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error executing MediaPipe LlmInference generateResponse for '$sourceText': ${e.message}", e)
            }
        }

        // 2. If no AI model is loaded or if LlmInference failed, fall back to OfflineTranslationEngine (ML Kit Translate)
        Log.d(TAG, "Falling back to OfflineTranslationEngine (ML Kit) for sourceText: '$sourceText'")
        return try {
            runBlocking {
                val offlineResult = offlineEngine.translate(sourceText, sourceLang, targetLang)
                if (offlineResult.isNotBlank()) {
                    offlineResult
                } else {
                    sourceText
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OfflineTranslationEngine translation error for '$sourceText': ${e.message}")
            sourceText
        }
    }
}

/**
 * Backward compatibility type alias for GgufModelEngine.
 */
typealias GgufModelEngine = CompanionModelEngine
