package com.walktape.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.walktape.app.ui.screens.diagnostics.DiagnosticsScreen
import com.walktape.app.ui.screens.home.HomeScreen
import com.walktape.app.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun WalkTapeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen()
        }
    }
}
