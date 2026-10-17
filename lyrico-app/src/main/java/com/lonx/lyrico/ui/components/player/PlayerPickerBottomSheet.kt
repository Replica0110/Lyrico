package com.lonx.lyrico.ui.components.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.data.player.FriendlyPlayer
import com.lonx.lyrico.data.player.FriendlyPlayerRegistry
import com.lonx.lyrico.data.player.FriendlyPlayerUiState
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.ui.components.salt.SaltBottomSheet
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.platform.player.InstalledAppChecker
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.innerPadding
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import androidx.core.net.toUri
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun PlayerPickerBottomSheet(
    show: Boolean,
    uri: Uri?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    playbackRepository: PlaybackRepository = koinInject()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerStates = remember(show, context) {
        val checker = InstalledAppChecker(context)
        FriendlyPlayerRegistry.players.map { player ->
            val installedApp = checker.findInstalledApp(player.packageNames)
            FriendlyPlayerUiState(
                player = player,
                installedPackageName = installedApp?.packageName,
                installedDisplayName = installedApp?.displayName?.takeIf { it.isNotBlank() }
            )
        }
    }

    val salt = LocalUiEngine.current == UiEngine.SALT
    val openSystemPicker = {
        val playUri = uri
        if (playUri != null) {
            onDismissRequest()
            playbackRepository.openSystemChooser(context, playUri)
        }
    }
    if (salt) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = {},
            header = { ItemOuterTitle(text = stringResource(R.string.play_with)) },
            scrollContent = true,
        ) {
            RoundedColumn {
                playerStates.forEach { item ->
                    FriendlyPlayerItem(
                        item = item,
                        onOpenInfo = { openPlayerInfo(context, item.player) },
                        onClick = {
                            val playUri = uri ?: return@FriendlyPlayerItem
                            if (item.installed) {
                                onDismissRequest()
                                val launched = playbackRepository.openWithPackage(
                                    context = context,
                                    uri = playUri,
                                    packageName = item.installedPackageName.orEmpty()
                                )
                                if (!launched) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.player_open_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    playbackRepository.openSystemChooser(context, playUri)
                                }
                            } else {
                                onDismissRequest()
                                openPlayerInfo(context, item.player)
                            }
                        }
                    )
                }
            }

            ItemOuterTitle(text = stringResource(R.string.other_playback_methods))
            RoundedColumn {
                Item(
                    onClick = openSystemPicker,
                    text = stringResource(R.string.open_with_system_picker),
                    iconPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.OpenInNew),
                    iconColor = SaltTheme.colors.highlight,
                    arrowType = ItemArrowType.None,
                )
            }
        }
        return
    }

    WindowBottomSheet(
        show = show,
        enableNestedScroll = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.play_with)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                playerStates.forEach { item ->
                    FriendlyPlayerItem(
                        item = item,
                        onOpenInfo = {
                            openPlayerInfo(context, item.player)
                        },
                        onClick = {
                            val playUri = uri ?: return@FriendlyPlayerItem
                            if (item.installed) {
                                onDismissRequest()
                                val launched = playbackRepository.openWithPackage(
                                    context = context,
                                    uri = playUri,
                                    packageName = item.installedPackageName.orEmpty()
                                )
                                if (!launched) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.player_open_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    playbackRepository.openSystemChooser(context, playUri)
                                }
                            } else {
                                onDismissRequest()
                                openPlayerInfo(context, item.player)
                            }
                        }
                    )
                }
            }

            SmallTitle(
                text = stringResource(R.string.other_playback_methods),
                insideMargin = PaddingValues(4.dp)
            )

            Card(
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                ArrowPreference(
                    startAction = { PreferenceIcon(LucideIcons.OpenInNew, PreferenceIconTone.Appearance) },
                    title = stringResource(R.string.open_with_system_picker),
                    onClick = {
                        val playUri = uri ?: return@ArrowPreference
                        onDismissRequest()
                        playbackRepository.openSystemChooser(context, playUri)
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendlyPlayerItem(
    item: FriendlyPlayerUiState,
    onOpenInfo: () -> Unit,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fallbackDisplayName = stringResource(item.player.displayNameRes)
    val displayName = item.installedDisplayName ?: fallbackDisplayName
    val icon = remember(item.installedPackageName) {
        item.installedPackageName?.let { packageName ->
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    }

    val nameText = if (item.installed) {
        displayName
    } else {
        stringResource(R.string.player_name_not_installed_format, displayName)
    }
    if (LocalUiEngine.current == UiEngine.SALT) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SaltTheme.dimens.item)
                .clickable(onClick = onClick)
                .innerPadding(vertical = false)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SaltTheme.colors.subBackground),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    AsyncImage(
                        model = icon,
                        contentDescription = displayName,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    com.moriafly.salt.ui.Icon(
                        painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.Music),
                        contentDescription = displayName,
                        modifier = Modifier.size(20.dp),
                        tint = SaltTheme.colors.subText
                    )
                }
            }
            com.moriafly.salt.ui.Text(
                text = nameText,
                style = SaltTheme.textStyles.main,
                color = SaltTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            SaltIconButton(
                icon = LucideIcons.Link,
                label = stringResource(R.string.learn_more),
                onClick = onOpenInfo,
                size = 32.dp,
                iconSize = 16.dp
            )
        }
        return
    }

    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        startAction = {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    AsyncImage(
                        model = icon,
                        contentDescription = displayName,
                        modifier = Modifier.size(42.dp)
                    )
                } else {
                    Icon(
                        imageVector = LucideIcons.Music,
                        contentDescription = displayName,
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        endActions = {
            IconButton(
                onClick = onOpenInfo
            ) {
                Icon(
                    imageVector = LucideIcons.Link,
                    contentDescription = stringResource(R.string.learn_more),
                    tint = MiuixTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = nameText,
                style = MiuixTheme.textStyles.main,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun openPlayerInfo(context: Context, player: FriendlyPlayer) {
    val target = player.marketUri ?: player.websiteUrl
    val intent = Intent(Intent.ACTION_VIEW, target.toUri())

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.player_install_source_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }
}
