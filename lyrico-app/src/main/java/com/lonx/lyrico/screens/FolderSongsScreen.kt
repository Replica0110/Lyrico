package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.viewmodel.FolderSongsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = folderPath.substringAfterLast("/"),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            overscrollEffect = null,
        ) {

            if (songs.isEmpty()) {
                item {
                    FolderSongsEmptyCard()
                }
            } else {
                items(
                    items = songs,
                    key = { song -> song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}" }
                ) { song ->
                    SongListItem(
                        song = song,
                        onClick = {
                            navigator.navigate(
                                EditMetadataDestination(song.uri)
                            )
                        }
                    )
                }
            }
        }
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
