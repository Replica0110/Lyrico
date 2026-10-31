@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.settings

import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.platform.player.AudioPlayerApp
import com.lonx.lyrico.platform.player.AudioPlayerApps
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.Uncheck

@Composable
internal fun DefaultPlayerSetting(
    selected: String?,
    selectedLabel: String?,
    onSelect: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var players by remember { mutableStateOf<List<AudioPlayerApp>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val salt = LocalUiEngine.current == UiEngine.SALT

    fun report(error: Exception) {
        if (error is CancellationException) throw error
        Toast.makeText(context, context.getString(R.string.unknown_error, error.message), Toast.LENGTH_SHORT).show()
    }
    fun openPicker() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                players = withContext(Dispatchers.IO) { AudioPlayerApps.query(context) }
                showPicker = true
            } catch (error: Exception) {
                report(error)
            } finally {
                loading = false
            }
        }
    }
    fun save(component: String?) {
        onSelect(component)
        showPicker = false
    }

    val title = stringResource(R.string.default_player)
    val summary = if (selected == null) stringResource(R.string.default_player_not_selected)
        else selectedLabel
            ?: stringResource(R.string.default_player_unavailable,
                ComponentName.unflattenFromString(selected)?.packageName.orEmpty())
    val presentation: SettingsItemPresentation = if (salt) SaltSettingsItems else MiuixSettingsItems
    with(presentation) {
        Action(
            title = title,
            summary = summary,
            icon = LucideIcons.Play,
            tone = PreferenceIconTone.Tools,
            onClick = ::openPicker,
        )
    }

    val maxListHeight = minOf(420.dp, LocalWindowInfo.current.containerDpSize.height * 0.6f)
    ActionBottomSheet(
        show = showPicker,
        title = stringResource(R.string.choose_player),
        onDismissRequest = { showPicker = false },
        groupContent = false,
        scrollContent = false,
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxListHeight),
                verticalArrangement = Arrangement.spacedBy(if (salt) 0.dp else 8.dp),
                overscrollEffect = null,
            ) {
                item(key = "no-default-player") {
                    PlayerChoiceCard(salt) {
                        PlayerChoiceItem(
                            player = null,
                            selected = selected == null,
                            onSelect = { save(null) }
                        )
                    }
                }
                if (players.isEmpty()) item {
                    PlayerChoiceCard(salt) {
                        if (salt) com.moriafly.salt.ui.ItemTip(stringResource(R.string.no_player_found))
                        else top.yukonga.miuix.kmp.basic.BasicComponent(title = stringResource(R.string.no_player_found))
                    }
                }
                items(players, key = { it.component }) { player ->
                    PlayerChoiceCard(salt) {
                        PlayerChoiceItem(
                            player = player,
                            selected = selected == player.component,
                            onSelect = { save(player.component) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun PlayerChoiceCard(salt: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (salt) com.moriafly.salt.ui.RoundedColumn(
        type = com.moriafly.salt.ui.RoundedColumnType.InList,
        content = content,
    ) else top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.secondaryContainer
        ),
        content = content,
    )
}

@Composable
private fun PlayerChoiceItem(player: AudioPlayerApp?, selected: Boolean, onSelect: () -> Unit) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val label = player?.label ?: stringResource(R.string.default_player_none)
    val summary = if (player == null) stringResource(R.string.default_player_ask_each_time)
        else ComponentName.unflattenFromString(player.component)?.packageName.orEmpty()
    val fallbackIcon = if (player == null) LucideIcons.Ban else LucideIcons.Music
    val fallbackTint = PreferenceIconTone.Tools.color()
    val iconBackground = if (salt) com.moriafly.salt.ui.SaltTheme.colors.subBackground
        else top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceVariant
    val appIcon: @Composable () -> Unit = {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            if (player?.icon != null) AsyncImage(
                model = player.icon, contentDescription = null, modifier = Modifier.fillMaxSize()
            ) else if (salt) com.moriafly.salt.ui.Icon(
                painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(fallbackIcon),
                contentDescription = null, modifier = Modifier.size(24.dp),
                tint = fallbackTint
            ) else top.yukonga.miuix.kmp.basic.Icon(
                fallbackIcon, null, Modifier.size(24.dp), tint = fallbackTint
            )
        }
    }
    if (salt) {
        SaltPlayerItem(
            text = label, sub = summary, icon = appIcon,
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
            endContent = {
                com.moriafly.salt.ui.Icon(
                    painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                        if (selected) com.moriafly.salt.ui.icons.SaltIcons.Check
                        else com.moriafly.salt.ui.icons.SaltIcons.Uncheck),
                    contentDescription = null,
                    tint = if (selected) com.moriafly.salt.ui.SaltTheme.colors.highlight
                        else com.moriafly.salt.ui.SaltTheme.colors.subText,
                    modifier = Modifier.size(com.moriafly.salt.ui.SaltTheme.dimens.itemIcon)
                )
            }
        )
    } else {
        top.yukonga.miuix.kmp.basic.BasicComponent(
            title = label,
            summary = summary,
            startAction = appIcon,
            endActions = {
                top.yukonga.miuix.kmp.basic.Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = onSelect
                )
            },
            onClick = onSelect,
            role = Role.RadioButton,
            modifier = Modifier.semantics { this.selected = selected },
        )
    }
}

/** SaltUI 3.0.0-beta02 Item has no trailing-content slot; keep custom content in its base container. */
@Composable
private fun SaltPlayerItem(
    text: String,
    sub: String,
    icon: @Composable () -> Unit,
    endContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.moriafly.salt.ui.ItemContainer(
        modifier = modifier.fillMaxWidth().heightIn(min = com.moriafly.salt.ui.SaltTheme.dimens.item)
    ) {
        com.moriafly.salt.ui.JustifiedRow(
            verticalAlignment = Alignment.CenterVertically,
            startContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(com.moriafly.salt.ui.SaltTheme.dimens.subPadding)
                ) {
                    icon()
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        com.moriafly.salt.ui.Text(text, style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        com.moriafly.salt.ui.Text(sub, style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                            color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            endContent = endContent,
        )
    }
}
