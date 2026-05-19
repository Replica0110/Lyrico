package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibraryEmptyState(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

