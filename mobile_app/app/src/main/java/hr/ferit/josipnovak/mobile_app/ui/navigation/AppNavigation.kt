package hr.ferit.josipnovak.mobile_app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hr.ferit.josipnovak.mobile_app.ui.screens.MainScreen
import hr.ferit.josipnovak.mobile_app.ui.screens.RecordScreen
import hr.ferit.josipnovak.mobile_app.ui.screens.HistoryScreen
import hr.ferit.josipnovak.mobile_app.ui.screens.DetailsScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.ferit.josipnovak.mobile_app.viewmodel.DetectionViewModel

@Composable
fun AppNavigation(viewModel: DetectionViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToRecord = { navController.navigate("record") },
                onNavigateToHistory = { navController.navigate("history") }
            )
        }
        composable("record") {
            RecordScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = {
                    navController.popBackStack()
                    navController.navigate("history")
                }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onNavigateToDetails = { id -> navController.navigate("details/$id") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("details/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            DetailsScreen(
                id = id ?: "",
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
