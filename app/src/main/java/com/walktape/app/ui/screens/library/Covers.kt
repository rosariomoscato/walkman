package com.walktape.app.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(coverArtId, profile?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(coverArtId, profile?.id, sizePx) {
        val id = coverArtId ?: return@LaunchedEffect
        val profileId = profile?.id ?: return@LaunchedEffect
        bitmap = Graph.coverArtStore(profileId).bitmap(id, sizePx)
    }
    Box(modifier = modifier.size(size)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    }
}
