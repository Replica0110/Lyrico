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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchMatchResult
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.viewmodel.BatchMatchHistoryViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
                    .padding(top = 8.dp, bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BatchMatchResult.entries) { status ->
                    val isSelected = status == uiState.selectedTab

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onTabSelected(status) },
                        label = {
                            Text(
                                text = stringResource(status.labelRes),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onBackground
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MiuixTheme.colorScheme.surfaceContainer,
                            selectedContainerColor = MiuixTheme.colorScheme.secondaryContainerVariant,
                            labelColor = MiuixTheme.colorScheme.onBackground,
                            selectedLabelColor = MiuixTheme.colorScheme.primary
                        ),
                        border = null
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.records.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            BasicComponent(
                                title = stringResource(R.string.no_record)
                            )
                        }
                    }
                } else {
                    items(
                        items = uiState.records,
                        key = { it.id }
                    ) { record ->
                        BatchMatchRecordCard(
                            record = record,
                            onClick = {
                                record.uri?.let {
                                    navigator.navigate(EditMetadataDestination(it))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchMatchRecordCard(
    record: BatchMatchRecordEntity,
    onClick: () -> Unit
) {
    val fileName = record.filePath.substringAfterLast("/")
    val isNavigable = record.uri != null

    Card(
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        BasicComponent(
            title = fileName,
            summary = record.filePath,
            onClick = if (isNavigable) onClick else null
        )
    }
}
