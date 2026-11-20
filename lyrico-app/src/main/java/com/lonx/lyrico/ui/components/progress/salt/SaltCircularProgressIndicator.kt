package com.lonx.lyrico.ui.components.progress.salt

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme

@Composable
internal fun SaltCircularProgressIndicator(size: Dp = 32.dp) {
    CircularProgressIndicator(
        modifier = Modifier.width(size).height(size),
        color = SaltTheme.colors.highlight,
        trackColor = SaltTheme.colors.stroke,
    )
}
