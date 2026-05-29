package com.vita.healthtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vita.healthtracker.ui.navigation.TopDestination
import com.vita.healthtracker.ui.navigation.VitaNavHost
import com.vita.healthtracker.ui.theme.VitaBackground
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary
import com.vita.healthtracker.ui.theme.VitaSurface
import com.vita.healthtracker.ui.theme.VitaTheme

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@androidx.compose.material3.ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startStepSensor()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                startStepSensor()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        } else {
            // Android < Q: no runtime permission needed for step counter
            startStepSensor()
        }

        setContent {
            VitaTheme {
                VitaApp()
            }
        }
    }

    private fun startStepSensor() {
        val app = application as VitaApplication
        val sensor = app.container.stepSensorManager
        if (sensor.isAvailable) {
            sensor.startListening()
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun VitaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = VitaSurface.copy(alpha = 0.85f),
                contentColor = VitaPrimary,
            ) {
                TopDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VitaPrimary,
                            selectedTextColor = VitaPrimary,
                            indicatorColor = VitaPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = VitaOnSurfaceMuted,
                            unselectedTextColor = VitaOnSurfaceMuted,
                        ),
                    )
                }
            }
        }
    ) { innerPadding: PaddingValues ->
        Box(modifier = Modifier.padding(innerPadding)) {
            VitaNavHost(navController = navController)
        }
    }
}
