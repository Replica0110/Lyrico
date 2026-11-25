package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.audiotag.model.AudioPicture
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.dialog.salt.SaltBasicDialog
import com.lonx.lyrico.ui.components.dialog.salt.SaltDialogButton
import com.lonx.lyrico.ui.components.dialog.salt.SaltDialogTitle
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 等待用户决定「交换归属」还是「直接替换」的一次重新绑定。 */
internal data class PendingRebind(val target: AudioPicture, val artistName: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtistImageConflictDialog(
    pending: PendingRebind?,
    onDismissRequest: () -> Unit,
    onSwap: (target: AudioPicture, artistName: String) -> Unit,
    onReplace: (target: AudioPicture, artistName: String) -> Unit,
) {
    if (pending == null) return

    val salt = LocalUiEngine.current == UiEngine.SALT

    if (salt) {
        SaltBasicDialog(onDismissRequest = onDismissRequest) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    SaltDialogTitle(stringResource(R.string.label_artist_image_conflict_title, pending.artistName))
                    com.moriafly.salt.ui.Text(
                        stringResource(R.string.label_artist_image_conflict_message),
                        style = SaltTheme.textStyles.main,
                        color = SaltTheme.colors.text
                    )
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
                ) {
                    SaltDialogButton(
                        text = stringResource(R.string.label_artist_image_swap),
                        modifier = Modifier.weight(1f),
                        appearance = ButtonAppearance.Subtle,
                        onClick = {
                            onDismissRequest()
                            onSwap(pending.target, pending.artistName)
                        }
                    )
                    SaltDialogButton(
                        text = stringResource(R.string.label_artist_image_replace),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDismissRequest()
                            onReplace(pending.target, pending.artistName)
                        }
                    )
                }
            }
        }
    } else {
        WindowDialog(
            show = true,
            title = stringResource(R.string.label_artist_image_conflict_title, pending.artistName),
            onDismissRequest = onDismissRequest
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.label_artist_image_conflict_message),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(R.string.label_artist_image_swap),
                        onClick = {
                            onDismissRequest()
                            onSwap(pending.target, pending.artistName)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.label_artist_image_replace),
                        onClick = {
                            onDismissRequest()
                            onReplace(pending.target, pending.artistName)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
