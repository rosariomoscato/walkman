package com.walktape.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningHistorySheet(profileId: Long, onDismiss: () -> Unit) {
    val flow = remember(profileId) { Graph.database.scrobbleDao().history(profileId) }
    val history by flow.collectAsStateWithLifecycle(emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.listening_history), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { Graph.scrobbleSender.trigger() }) { Text(stringResource(R.string.listening_retry)) }
                if (history.isEmpty()) Text(stringResource(R.string.home_section_empty))
            }
            items(history, key = { it.eventId }) { row ->
                Column {
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                    row.artist?.let { Text(it) }
                    Text(DateFormat.getDateTimeInstance().format(Date(row.playedAtMs)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (row.sentAt == null) R.string.listening_pending else R.string.listening_sent),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
