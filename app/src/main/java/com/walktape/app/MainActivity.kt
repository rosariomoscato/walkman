package com.walktape.app

import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.walktape.app.core.settings.AppSettings
import com.walktape.app.ui.nav.Routes
import com.walktape.app.ui.nav.WalkTapeDestination
import com.walktape.app.ui.nav.WalkTapeNavHost
import com.walktape.app.ui.screens.settings.SettingsViewModel
import com.walktape.app.ui.theme.WalkTapeTheme

class MainActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            WalkTapeTheme(themeMode = settings.themeMode) {
                if (!settings.loaded) {
                    // Gate must not run on default preferences: wait for the first DataStore emission.
                    Surface {}
                } else {
                    val lockAvailable = remember {
                        BiometricManager.from(this@MainActivity).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                                or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                    }
                    var unlocked by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(settings.appLockEnabled, lockAvailable) {
                        if (!settings.appLockEnabled || !lockAvailable) unlocked = true
                    }
                    if (unlocked) {
                        WalkTapeShell(windowSizeClass.widthSizeClass)
                    } else {
                        LockGate(onUnlocked = { unlocked = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun LockGate(onUnlocked: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity ?: return
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.lock_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onUnlocked()
                        }
                    }
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(R.string.lock_prompt_title))
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                                or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()
                )
            }) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}

@Composable
private fun WalkTapeShell(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = widthSizeClass == WindowWidthSizeClass.Compact

    Scaffold(
        bottomBar = {
            if (showBottomBar && currentRoute != Routes.DIAGNOSTICS) {
                NavigationBar {
                    WalkTapeDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (!showBottomBar && currentRoute != Routes.DIAGNOSTICS) {
                NavigationRail {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        WalkTapeDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.labelRes)
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) }
                            )
                        }
                    }
                }
            }
            WalkTapeNavHost(
                navController = navController,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
