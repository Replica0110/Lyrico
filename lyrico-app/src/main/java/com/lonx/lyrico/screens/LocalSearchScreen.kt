package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
@Destination<RootGraph>(route = "local_search")
fun LocalSearchScreen(
    keyword: String? = null,
    navigator: DestinationsNavigator
) {
    val viewModel: LocalSearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current


    // 初始化关键词
    LaunchedEffect(keyword) {
        if (keyword != null && keyword != uiState.searchQuery) {
            viewModel.onSearchQueryChanged(keyword)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(vertical = 8.dp)
            ) {

                SearchBar(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = stringResource(R.string.local_search_hint),
                    actionText = stringResource(R.string.cancel),
                    onActionClick = {
                        keyboardController?.hide()
                        navigator.navigateUp()
                    },
                    onSearch = {
                        keyboardController?.hide()
                    }
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Results
            if (uiState.isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = uiState.songs, key = { it.filePath }) { song ->
                        SongListItem(song = song, navigator = navigator)
                    }
                }
            }
        }
    }
}