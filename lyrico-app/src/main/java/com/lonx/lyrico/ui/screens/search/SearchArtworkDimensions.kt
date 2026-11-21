package com.lonx.lyrico.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.lonx.lyrico.data.artwork.ArtworkDimensionsReader
import org.koin.compose.koinInject

/** Each URL and visibility state owns its result and cancels the previous network call. */
@Composable
internal fun rememberArtworkDimensions(url: String, enabled: Boolean = true): State<Pair<Int, Int>?> {
    val reader = koinInject<ArtworkDimensionsReader>()
    val dimensions = remember(url, enabled) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(reader, url, enabled) {
        if (enabled) dimensions.value = reader.read(url)
    }
    return dimensions
}
