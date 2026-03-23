package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.FolderSongsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Destination<RootGraph>(route = "folder_songs")
fun FolderSongsScreen(
    navigator: DestinationsNavigator,
    folderId: Long,
    folderPath: String
) {
    val viewModel: FolderSongsViewModel = koinViewModel(
        parameters = { parametersOf(folderId) }
    )
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    BasicScreenBox(
        title = folderPath.substringAfterLast("/"),
        onBack = { navigator.popBackStack() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FolderSongsOverviewCard(
                    folderPath = folderPath,
                    songCount = songs.size
                )
            }

            if (songs.isEmpty()) {
                item {
                    FolderSongsEmptyCard()
                }
            } else {
                items(
                    items = songs,
                    key = { song -> song.mediaId }
                ) { song ->
                    SongListItem(
                        song = song,
                        navigator = navigator
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderSongsOverviewCard(
    folderPath: String,
    songCount: Int
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onBackground
        )
    ) {
        BasicComponent(
            title = stringResource(R.string.folder_song_count_format, songCount),
            summary = folderPath
        )
    }
}

@Composable
private fun FolderSongsEmptyCard() {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onBackground
        )
    ) {
        BasicComponent(
            title = stringResource(R.string.no_songs_in_folder)
        )
    }
}
