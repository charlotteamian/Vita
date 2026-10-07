package com.vita.healthtracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.vita.healthtracker.R

/**
 * 底部四个 tab 按用户动作划分: 看今天 / 记一笔 / 查数据 / 读趋势。
 * 设置是低频操作, 不占 tab, 从今日页右上角齿轮进入 (路由仍是 "settings")。
 */
enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Today("today", R.string.nav_today, Icons.Outlined.Today, Icons.Filled.Today),
    Record("life", R.string.nav_record, Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    Data("stats", R.string.nav_data, Icons.Outlined.BarChart, Icons.Filled.BarChart),
    Trends("trends", R.string.nav_trends, Icons.Outlined.Insights, Icons.Filled.Insights),
}
