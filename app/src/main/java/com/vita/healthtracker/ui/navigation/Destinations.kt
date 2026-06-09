package com.vita.healthtracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.vita.healthtracker.R

enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Today("today", R.string.nav_today, Icons.Outlined.Today, Icons.Filled.Today),
    Stats("stats", R.string.nav_stats, Icons.Outlined.BarChart, Icons.Filled.BarChart),
    Life("life", R.string.nav_life, Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}
