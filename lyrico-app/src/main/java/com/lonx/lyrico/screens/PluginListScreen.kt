package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.PluginListViewModel
import com.lonx.lyrico.viewmodel.PluginUiModel
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Surface
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.lazy.LazyColumn
import com.moriafly.salt.ui.lazy.items
import com.moriafly.salt.ui.rememberScrollState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "plugin_list")
fun PluginListScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: PluginListViewModel = koinViewModel()
    val plugins by viewModel.pluginList.collectAsState()
    BasicScreenBox(
        title = "插件列表",
        onBack = {
            navigator.popBackStack()
        },
        toolbar = {
            IconButton(
                onClick = {
                    viewModel.refresh()
                },
                modifier = Modifier
                    .size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search_24dp),
                    contentDescription = null,
                    modifier = Modifier
                        .size(SaltTheme.dimens.itemIcon)
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            item {
                ItemOuterTitle("可用插件")
            }
            items(items =  plugins){
                PluginItem(plugin = it)
            }
        }
    }
}
@Composable
fun PluginItem(plugin: PluginUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = plugin.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "v${plugin.version}", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "作者: ${plugin.author}", style = MaterialTheme.typography.bodySmall)
            Text(text = plugin.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapabilityBadge("搜索源", MaterialTheme.colorScheme.primaryContainer)
                }
            }
        }
}

@Composable
fun CapabilityBadge(text: String, containerColor: Color) {
    Surface(
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = SaltTheme.textStyles.paragraph
        )
    }
}