package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "local_search")
fun LocalSearchScreen(
    keyword: String? = null,
    navigator: DestinationsNavigator
) {
    val viewModel: LocalSearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 初始化搜索关键词
    LaunchedEffect(keyword) {
        if (keyword != null && keyword != uiState.searchQuery) {
            viewModel.onSearchQueryChanged(keyword)
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        topBar = {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .background(SaltTheme.colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    value = uiState.searchQuery,
                    onValueChange = {
                        viewModel.onSearchQueryChanged(it)
                    },
                    placeholder = stringResource(R.string.local_search_hint),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaltTheme.colors.highlight,
                    modifier = Modifier.clickable {
                        // 退出前可以先收起键盘
                        keyboardController?.hide()
                        navigator.popBackStack()
                    }
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .padding(paddingValues)
        )
        {
            // Results
            if (uiState.isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = uiState.songs, key = { it.filePath }) { song ->
                        SongListItem(song = song, navigator = navigator)
                        ItemDivider()
                    }
                }
            }
        }
    }
}