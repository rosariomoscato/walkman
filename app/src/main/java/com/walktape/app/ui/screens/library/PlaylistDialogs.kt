package com.walktape.app.ui.screens.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.R

/** Creates an (initially empty) playlist for the active profile. */
@Composable
fun NewPlaylistDialog(busy: Boolean, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = !busy && name.isNotBlank()) {
                Text(stringResource(R.string.playlist_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Chooses a destination for the multi-selection: an existing own playlist, or a new one.
 * Shared/other owners' playlists are not offered: the server refuses their edits.
 */
@Composable
fun AddToPlaylistDialog(
    model: LibraryViewModel,
    busy: Boolean,
    onChoose: (playlistId: String?, newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playlists by model.editablePlaylists.collectAsStateWithLifecycle()
    var newNameMode by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.selection_add_to_playlist)) },
        text = {
            if (newNameMode) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlist_new))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        DropdownMenuItem(
                            text = { Text(playlist.name) },
                            onClick = { onChoose(playlist.id, "") },
                        )
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        },
        confirmButton = {
            if (newNameMode) {
                TextButton(onClick = { onChoose(null, newName) }, enabled = !busy && newName.isNotBlank()) {
                    Text(stringResource(R.string.playlist_create))
                }
            } else {
                TextButton(onClick = { newNameMode = true }, enabled = !busy) {
                    Text(stringResource(R.string.playlist_new))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Inline error line shown when a server mutation (playlist or favorite) fails. */
@Composable
fun MutationErrorLine(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    Text(
        stringResource(R.string.playlist_change_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
}
