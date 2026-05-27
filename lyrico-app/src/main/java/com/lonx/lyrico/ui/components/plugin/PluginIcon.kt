package com.lonx.lyrico.ui.components.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun PluginIcon(
    iconPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val iconFile = iconPath?.takeIf { it.isNotBlank() }?.let(::File)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconFile != null) {
            AsyncImage(
                model = iconFile,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Settings,
                contentDescription = contentDescription,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}
