package com.walktape.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.walktape.app.ui.screens.diagnostics.DiagnosticsScreen
import com.walktape.app.ui.screens.home.HomeScreen
import com.walktape.app.ui.screens.settings.SettingsScreen
import com.walktape.app.ui.screens.profiles.ProfilesScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val PROFILES = "profiles"
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
            HomeScreen(onOpenProfiles = { navController.navigate(Routes.PROFILES) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onOpenProfiles = { navController.navigate(Routes.PROFILES) }
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen()
        }
        composable(Routes.PROFILES) { ProfilesScreen() }
    }
}
