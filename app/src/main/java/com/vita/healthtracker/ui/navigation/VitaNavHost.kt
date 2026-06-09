package com.vita.healthtracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vita.healthtracker.ui.screens.account.AccountAuthScreen
import com.vita.healthtracker.ui.screens.exercise.ExerciseDetailScreen
import com.vita.healthtracker.ui.screens.garmin.GarminDataDetailScreen
import com.vita.healthtracker.ui.screens.life.LifeScreen
import com.vita.healthtracker.ui.screens.life.HabitBadgeDetailScreen
import com.vita.healthtracker.ui.screens.life.HabitDetailScreen
import com.vita.healthtracker.ui.screens.life.MoodJournalScreen
import com.vita.healthtracker.ui.screens.settings.SettingsScreen
import com.vita.healthtracker.ui.screens.stats.StatsScreen
import com.vita.healthtracker.ui.screens.today.BodyBatteryDetailScreen
import com.vita.healthtracker.ui.screens.today.TodayScreen
import com.vita.healthtracker.ui.screens.life.SleepDetailScreen

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun VitaNavHost(
    navController: NavHostController,
) {
    NavHost(navController = navController, startDestination = TopDestination.Today.route) {
        composable(TopDestination.Today.route) { TodayScreen(navController) }
        composable(TopDestination.Stats.route) { StatsScreen(navController) }
        composable(TopDestination.Life.route) { LifeScreen(navController) }
        composable(TopDestination.Settings.route) { SettingsScreen(navController) }

        composable("account_auth") {
            AccountAuthScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "exercise/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            ExerciseDetailScreen(exerciseId = id, onBack = { navController.popBackStack() })
        }
        
        composable("sleep_detail") {
            SleepDetailScreen(onBack = { navController.popBackStack() })
        }

        composable("body_battery") {
            BodyBatteryDetailScreen(onBack = { navController.popBackStack() })
        }

        composable("habit_detail") {
            HabitDetailScreen(onBack = { navController.popBackStack() })
        }

        composable("habit_badges") {
            HabitBadgeDetailScreen(onBack = { navController.popBackStack() })
        }

        composable("mood_journal") {
            MoodJournalScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "garmin_day/{date}",
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: return@composable
            GarminDataDetailScreen(dateText = date, onBack = { navController.popBackStack() })
        }
    }
}
