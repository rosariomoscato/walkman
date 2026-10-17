package com.walktape.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.walktape.app.ui.screens.backup.BackupScreen
import com.walktape.app.ui.screens.diagnostics.DiagnosticsScreen
import com.walktape.app.ui.screens.library.AlbumDetailScreen
import com.walktape.app.ui.screens.library.ArtistDetailScreen
import com.walktape.app.ui.screens.library.GenreDetailScreen
import com.walktape.app.ui.screens.library.LibraryScreen
import com.walktape.app.ui.screens.library.PlaylistDetailScreen
import com.walktape.app.ui.screens.player.PlayerScreen
import com.walktape.app.ui.screens.profiles.ProfilesScreen
import com.walktape.app.ui.screens.settings.SettingsScreen
import com.walktape.app.ui.screens.updates.UpdateScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val PROFILES = "profiles"
    const val PLAYER = "player"
    const val BACKUP = "backup"
    const val UPDATE = "update"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val GENRE = "genre/{name}"
    const val PLAYLIST = "playlist/{id}"
    fun album(id: String) = "album/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
    fun genre(name: String) = "genre/${Uri.encode(name)}"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
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
            LibraryScreen(
                onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenGenre = { navController.navigate(Routes.genre(it)) },
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenUpdates = { navController.navigate(Routes.UPDATE) }
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen()
        }
        composable(Routes.PROFILES) { ProfilesScreen() }
        composable(Routes.BACKUP) { BackupScreen() }
        composable(Routes.UPDATE) { UpdateScreen() }
        composable(Routes.PLAYER) { PlayerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ALBUM) { entry ->
            AlbumDetailScreen(
                albumId = entry.arguments?.getString("id") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ARTIST) { entry ->
            ArtistDetailScreen(
                artistId = entry.arguments?.getString("id") ?: "",
                onBack = { navController.popBackStack() },
                onOpenAlbum = { navController.navigate(Routes.album(it)) }
            )
        }
        composable(Routes.GENRE) { entry ->
            val name = entry.arguments?.getString("name") ?: ""
            GenreDetailScreen(genre = name, onBack = { navController.popBackStack() })
        }
        composable(Routes.PLAYLIST) { entry ->
            PlaylistDetailScreen(entry.arguments?.getString("id") ?: "", onBack = { navController.popBackStack() })
        }
    }
}
