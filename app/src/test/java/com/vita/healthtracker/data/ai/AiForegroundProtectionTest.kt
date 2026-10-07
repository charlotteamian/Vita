package com.vita.healthtracker.data.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiForegroundProtectionTest {

    @Test
    fun `ai analysis is protected by foreground service and wake lock`() {
        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        val manager = projectFile("src/main/java/com/vita/healthtracker/data/ai/AiInsightManager.kt").readText()
        val service = projectFile("src/main/java/com/vita/healthtracker/data/ai/AiInsightForegroundService.kt").readText()

        assertTrue(manifest.contains(".data.ai.AiInsightForegroundService"))
        assertTrue(manager.contains("AiInsightForegroundService.start"))
        assertTrue(manager.contains("AiInsightForegroundService.stop"))
        assertTrue(service.contains("startForeground"))
        assertTrue(service.contains("PowerManager.PARTIAL_WAKE_LOCK"))
    }

    private fun projectFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile
        return File("app/$path")
    }
}
