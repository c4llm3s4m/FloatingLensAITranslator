package com.floatinglens.plugin_app

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CompanionModelEngineTest {

    private lateinit var context: Context
    private lateinit var engine: CompanionModelEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = CompanionModelEngine(context)
        engine.useLmStudioServer = false
    }

    @Test
    fun testDetectModelType() {
        assertEquals(CompanionModelEngine.ModelType.TASK, engine.detectModelType("/sdcard/Download/model.task"))
        assertEquals(CompanionModelEngine.ModelType.TASK, engine.detectModelType("GEMMA-2B.TASK"))
        assertEquals(CompanionModelEngine.ModelType.GGUF, engine.detectModelType("/sdcard/Download/qwen.gguf"))
        assertEquals(CompanionModelEngine.ModelType.GGUF, engine.detectModelType("MODEL.GGUF"))
        assertEquals(CompanionModelEngine.ModelType.UNKNOWN, engine.detectModelType("model.bin"))
    }

    @Test
    fun testLoadTaskModelWithExistingFile() {
        val testTaskFile = File(context.filesDir, "test_model.task").apply {
            createNewFile()
        }

        val success = engine.loadModel(testTaskFile.absolutePath)

        assertTrue(success)
        assertTrue(engine.isLoaded())
        assertEquals(testTaskFile.absolutePath, engine.getLoadedModelPath())
        assertEquals("test_model.task", engine.getLoadedModelFilename())
        assertEquals(CompanionModelEngine.ModelType.TASK, engine.getLoadedModelType())
        assertEquals("Google MediaPipe GenAI (.task)", engine.getLoadedModelTypeString())
        assertEquals("Active Model Status: Loaded [test_model.task]", engine.getActiveModelStatusBadgeText())
        assertTrue(engine.getStatusMessage().contains("test_model.task"))
    }

    @Test
    fun testLoadGgufModelWithExistingFile() {
        val testGgufFile = File(context.filesDir, "qwen2.5-0.5b.gguf").apply {
            createNewFile()
        }

        val success = engine.loadModel(testGgufFile.absolutePath)

        assertTrue(success)
        assertTrue(engine.isLoaded())
        assertEquals(testGgufFile.absolutePath, engine.getLoadedModelPath())
        assertEquals("qwen2.5-0.5b.gguf", engine.getLoadedModelFilename())
        assertEquals(CompanionModelEngine.ModelType.GGUF, engine.getLoadedModelType())
        assertEquals("llama.cpp GGUF (.gguf)", engine.getLoadedModelTypeString())
        assertEquals("Active Model Status: Loaded [qwen2.5-0.5b.gguf]", engine.getActiveModelStatusBadgeText())
    }

    @Test
    fun testLoadNonExistentModelFile() {
        val nonExistentPath = "/sdcard/Download/non_existent_model.task"
        val success = engine.loadModel(nonExistentPath)

        assertFalse(success)
        assertFalse(engine.isLoaded())
        assertEquals("Active Model Status: Waiting for Model Selection", engine.getActiveModelStatusBadgeText())
        assertEquals("No .task or .gguf model file loaded yet. Please select your model file in Qwen Companion App", engine.getStatusMessage())
    }

    @Test
    fun testAutoScanDownloadsWhenFileExists() {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val modelFile = File(downloadDir, "auto_scanned_model.gguf").apply {
            createNewFile()
        }

        val scannedPath = engine.autoScanDownloads()

        assertNotNull(scannedPath)
        assertTrue(engine.isLoaded())
        assertEquals(modelFile.absolutePath, scannedPath)
        assertEquals("Active Model Status: Loaded [auto_scanned_model.gguf]", engine.getActiveModelStatusBadgeText())
    }

    @Test
    fun testAutoScanDownloadsWhenNoFileExists() {
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { it.delete() }

        val freshEngine = CompanionModelEngine(context)
        freshEngine.useLmStudioServer = false
        val scannedPath = freshEngine.autoScanDownloads()

        assertNull(scannedPath)
        assertFalse(freshEngine.isLoaded())
        assertEquals("Active Model Status: Waiting for Model Selection", freshEngine.getActiveModelStatusBadgeText())
        assertEquals("No .task or .gguf model file loaded yet. Please select your model file in Qwen Companion App", freshEngine.getStatusMessage())
    }

    @Test
    fun testNoModelLoadedTranslationExecutionReturnsErrorMessage() {
        engine.useLmStudioServer = false
        val translation = engine.generateTranslation("探してみるか")
        assertNotNull(translation)
        assertTrue("Should return error message when no model is loaded", translation.contains("Plugin Error"))
    }

    @Test
    fun testEmptyInputTranslation() {
        val translation = engine.generateTranslation("   ")
        assertEquals("", translation)
    }

    @Test
    fun testLmStudioPreferences() {
        engine.useLmStudioServer = true
        engine.lmStudioServerIp = "192.168.1.100"
        engine.lmStudioPort = 5000
        engine.lmStudioModelName = "custom-model"

        assertTrue(engine.useLmStudioServer)
        assertEquals("192.168.1.100", engine.lmStudioServerIp)
        assertEquals(5000, engine.lmStudioPort)
        assertEquals("custom-model", engine.lmStudioModelName)
        assertEquals("PC LM Studio Local AI Server", engine.getLoadedModelTypeString())
        assertEquals("Active Engine: PC LM Studio Server [custom-model]", engine.getActiveModelStatusBadgeText())
    }
}
