package com.lonx.lyrico.ui.components.song

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.song.scan.LibraryScanProgress
import com.lonx.lyrico.data.song.scan.LibraryScanStage
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibraryScanProgressText(
    progress: LibraryScanProgress,
    modifier: Modifier = Modifier
) {
    val title = when (progress.stage) {
        LibraryScanStage.LISTING_FILES -> stringResource(R.string.scan_progress_listing)
        LibraryScanStage.READING_METADATA -> stringResource(
            R.string.scan_progress_reading,
            progress.current,
            progress.total
        )
        LibraryScanStage.WRITING_DATABASE -> stringResource(R.string.scan_progress_writing)
        LibraryScanStage.FINISHED -> stringResource(R.string.scan_progress_finished)
    }

    // 该组件被两套引擎共用的 pageContent 调用，Salt 下要用自己的语义字体和颜色，避免显示 Miuix 样式
    if (LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Text(
            text = title,
            modifier = modifier
                .padding(horizontal = 24.dp),
            style = SaltTheme.textStyles.main,
            color = SaltTheme.colors.text,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = title,
            modifier = modifier
                .padding(horizontal = 24.dp),
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
