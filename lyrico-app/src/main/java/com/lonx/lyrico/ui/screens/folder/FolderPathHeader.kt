@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.folder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.domain.folder.FolderBreadcrumb
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 目录页顶部：从顶层到当前目录的路径，以及当前目录的子目录数与歌曲数。 */
@Composable
internal fun FolderPathHeader(
    breadcrumbs: List<FolderBreadcrumb>,
    folderCount: Int,
    songCount: Int,
    onBreadcrumbClick: (Long) -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltFolderPathHeader(breadcrumbs, folderCount, songCount, onBreadcrumbClick)
    } else {
        MiuixFolderPathHeader(breadcrumbs, folderCount, songCount, onBreadcrumbClick)
    }
}

@Composable
private fun SaltFolderPathHeader(
    breadcrumbs: List<FolderBreadcrumb>,
    folderCount: Int,
    songCount: Int,
    onBreadcrumbClick: (Long) -> Unit,
) {
    RoundedColumn {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            breadcrumbs.forEachIndexed { index, breadcrumb ->
                val isCurrent = index == breadcrumbs.lastIndex
                // 面包屑是紧凑的行内链接：Salt 原生 Plain 按钮最小 48dp，会撑高整条面包屑。
                PillButton(
                    text = breadcrumb.label,
                    style = PillButtonDefaults.style(PillButtonSize.Medium),
                    colors = PillButtonDefaults.plainColors(),
                    onClick = { if (!isCurrent) onBreadcrumbClick(breadcrumb.folderId) },
                )
                if (!isCurrent) SaltText(
                    "›",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = SaltTheme.colors.subText,
                )
            }
        }
        SaltText(
            stringResource(R.string.folder_browser_count_format, folderCount, songCount),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText,
        )
    }
}

@Composable
private fun MiuixFolderPathHeader(
    breadcrumbs: List<FolderBreadcrumb>,
    folderCount: Int,
    songCount: Int,
    onBreadcrumbClick: (Long) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BreadcrumbBar(
                items = breadcrumbs.map { breadcrumb ->
                    BreadcrumbItem(path = breadcrumb.folderId.toString(), text = breadcrumb.label)
                },
                onItemClick = { index ->
                    if (index != breadcrumbs.lastIndex) onBreadcrumbClick(breadcrumbs[index].folderId)
                },
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.folder_browser_count_format, folderCount, songCount),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
        }
    }
}
