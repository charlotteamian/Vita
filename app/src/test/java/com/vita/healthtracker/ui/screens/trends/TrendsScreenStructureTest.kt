package com.vita.healthtracker.ui.screens.trends

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendsScreenStructureTest {

    @Test
    fun `trend page uses ai sections and removes old long-term card`() {
        val screen = projectFile("src/main/java/com/vita/healthtracker/ui/screens/trends/TrendsScreen.kt").readText()
        val viewModel = projectFile("src/main/java/com/vita/healthtracker/ui/screens/trends/TrendsViewModel.kt").readText()

        assertTrue(screen.contains("多年变化"))
        assertTrue(screen.contains("这段时间的规律"))
        assertTrue(screen.contains("longTermFindings"))
        assertTrue(screen.contains("recentPatterns"))
        assertFalse(screen.contains("LongTermTrendCard("))
        assertFalse(viewModel.contains("LongTermTrendAnalyzer"))
    }

    private fun projectFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile
        return File("app/$path")
    }
}
