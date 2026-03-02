package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.FolderSongsViewModel
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.SaltDimens
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.ext.safeMainCompat
import com.moriafly.salt.ui.lazy.LazyColumn
import com.moriafly.salt.ui.lazy.items
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, UnstableSaltUiApi::class)
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
        onBack = {
            navigator.popBackStack()
        },
        toolbar = {
            // TODO
        }
    ) {
        LazyColumn() {
            item {
                Spacer(Modifier.height(SaltDimens.RoundedColumnInListEdgePadding))
            }
            if (songs.isEmpty()) {
                item {
                    RoundedColumn(
                        type = RoundedColumnType.InList
                    ) {
                        ItemTip(stringResource(R.string.no_songs_in_folder))
                    }
                }
            } else {
                items(
                    items = songs,
                    key = { song -> song.mediaId }
                ) {
                    SongListItem(
                        song = it,
                        navigator = navigator,
                    )
                }
            }
            item {
                Spacer(Modifier.height(SaltDimens.RoundedColumnInListEdgePadding))
            }

            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeMainCompat))
            }
        }
    }
}