package com.walktape.app.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph

/** Covers load from the per-profile bounded cache; empty state is honest, no placeholders. */
@Composable
fun CoverArt(
    viewModel: LibraryViewModel,
    coverArtId: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    Box(modifier = modifier.size(size)) {
        CoverArtById(profile?.id, coverArtId, Modifier.size(size))
    }
}

/** Same bounded cache, addressed directly by profile id (player screen, enlarged cover). */
@Composable
fun CoverArtById(
    profileId: Long?,
    coverArtId: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    BoxWithConstraints(modifier) {
        val sizePx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        var bitmap by remember(coverArtId, profileId, sizePx) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(coverArtId, profileId, sizePx) {
            val id = coverArtId ?: return@LaunchedEffect
            val profile = profileId ?: return@LaunchedEffect
            bitmap = Graph.coverArtStore(profile).bitmap(id, sizePx)
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
