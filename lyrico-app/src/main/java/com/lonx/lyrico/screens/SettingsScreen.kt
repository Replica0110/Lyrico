package com.lonx.lyrico.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "settings")
fun SettingsScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val lyricDisplayMode = uiState.lyricDisplayMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        navigator.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Lyric Display Mode Section
            SettingSectionTitle("歌词模式")
            LyricDisplayModeItem(
                mode = LyricDisplayMode.WORD_BY_WORD,
                selectedMode = lyricDisplayMode,
                onClick = { viewModel.setLyricDisplayMode(it) }
            )
            LyricDisplayModeItem(
                mode = LyricDisplayMode.LINE_BY_LINE,
                selectedMode = lyricDisplayMode,
                onClick = { viewModel.setLyricDisplayMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // You can add other settings below, e.g., Theme, Rescan Library, etc.
        }
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun LyricDisplayModeItem(
    mode: LyricDisplayMode,
    selectedMode: LyricDisplayMode,
    onClick: (LyricDisplayMode) -> Unit
) {
    val title = when (mode) {
        LyricDisplayMode.WORD_BY_WORD -> "逐字歌词"
        LyricDisplayMode.LINE_BY_LINE -> "逐行歌词"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(mode) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { onClick(mode) }
        )
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}