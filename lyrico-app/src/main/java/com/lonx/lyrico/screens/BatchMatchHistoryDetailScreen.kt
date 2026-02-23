package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchMatchResult
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination

@OptIn(UnstableSaltUiApi::class)
@Destination<RootGraph>(route = "batch_match_history_detail")
@Composable
fun BatchMatchHistoryDetailScreen(
    historyId: Long,
    navigator: DestinationsNavigator
) {
    val viewModel: BatchMatchHistoryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(historyId) {
        viewModel.loadHistory(historyId)
    }

    BasicScreenBox(
        title = stringResource(R.string.batch_match_history_detail),
        onBack = { navigator.popBackStack() }
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BatchMatchResult.entries) { status ->

                    val isSelected = status == uiState.selectedTab

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onTabSelected(status) },
                        label = {
                            com.moriafly.salt.ui.Text(
                                text = stringResource(status.labelRes),
                                fontWeight = if (isSelected)
                                    FontWeight.Bold
                                else
                                    FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SaltTheme.colors.subBackground,
                            selectedContainerColor = SaltTheme.colors.highlight.copy(alpha = 0.1f),
                            labelColor = SaltTheme.colors.text,
                            selectedLabelColor = SaltTheme.colors.highlight
                        )
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {

                item {
                    RoundedColumn {
                        if (uiState.records.isEmpty()) {
                            ItemTip(text = stringResource(R.string.no_record))
                        }
                    }
                }

                items(
                    items = uiState.records,
                    key = { it.id }
                ) { record ->

                    RoundedColumn(
                        type = RoundedColumnType.InList
                    ) {
                        Item(
                            text = record.filePath.substringAfterLast("/"),
                            sub = record.filePath,
                            onClick = {
                                navigator.navigate(
                                    EditMetadataDestination(record.filePath)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

