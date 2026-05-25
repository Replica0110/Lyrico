package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.cover.CoverImage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


data class AlbumGridTextStyle(
    val title: TextStyle,
    val summary: TextStyle,
    val titleMaxLines: Int
)
@Composable
fun rememberAlbumGridTextStyle(columns: Int): AlbumGridTextStyle {
    val textStyles = MiuixTheme.textStyles

    return remember(columns, textStyles) {
        when (columns) {
            2 -> AlbumGridTextStyle(
                title = textStyles.body2,
                summary = textStyles.footnote1,
                titleMaxLines = 1
            )

            3 -> AlbumGridTextStyle(
                title = textStyles.body2,
                summary = textStyles.footnote1,
                titleMaxLines = 1
            )

            else -> AlbumGridTextStyle(
                title = textStyles.subtitle,
                summary = textStyles.footnote2,
                titleMaxLines = 1
            )
        }
    }
}
@Composable
fun AlbumGridItem(
    albumName: String,
    summary: String,
    coverUri: String?,
    coverLastModified: Long,
    titleStyle: TextStyle,
    summaryStyle: TextStyle,
    titleMaxLines: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(8.dp),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                CoverImage(
                    uri = coverUri,
                    lastModified = coverLastModified,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                )

                Text(
                    text = albumName,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Text(
                    text = summary,
                    style = summaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}