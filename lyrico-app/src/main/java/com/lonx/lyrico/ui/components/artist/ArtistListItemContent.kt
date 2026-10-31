package com.lonx.lyrico.ui.components.artist


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.artwork.CoverCandidate
import com.lonx.lyrico.ui.components.cover.CoverImage
import com.lonx.lyrico.ui.components.library.LibraryItemText as Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ArtistListItemContent(
    artist: ArtistEntity,
    modifier: Modifier = Modifier,
    coverCandidates: List<CoverCandidate> = emptyList(),
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        CoverImage(
            uri = artist.coverSongUri,
            lastModified = artist.coverSongLastModified,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            pictureType = AudioPictureType.Artist,
            fallbackPictureTypes = listOf(
                AudioPictureType.LeadArtist,
                AudioPictureType.Band
            ),
            fallbackToAny = true,
            candidates = coverCandidates,
            artistName = artist.name
        )


        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = artist.name,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.album_song_count,
                        artist.albumCount,
                        artist.songCount
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        trailingContent?.let {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                trailingContent()
            }
        }
    }
}
