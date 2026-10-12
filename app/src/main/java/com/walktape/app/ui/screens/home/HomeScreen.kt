package com.walktape.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.ui.screens.profiles.ProfilesViewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.walktape.app.R
import com.walktape.app.ui.components.EmptyState
import com.walktape.app.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.Factory)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val active = profiles.firstOrNull { it.isActive }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_home),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp)
        )
        SectionHeader(text = stringResource(R.string.home_section_recently_added))
        EmptyState(
            icon = Icons.Filled.LibraryMusic,
            title = active?.name ?: stringResource(R.string.home_empty_title),
            body = stringResource(if (active == null) R.string.home_empty_body else R.string.home_connected_body),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Button(onClick = onOpenProfiles, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.profiles_title))
        }
    }
}
