@file:OptIn(
    com.moriafly.salt.ui.UnstableSaltUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.lonx.lyrico.ui.screens.plugin

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SourcePluginEntity
import com.lonx.lyrico.data.db.entity.displayName
import com.lonx.lyrico.data.db.entity.displaySourceTypes
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.data.plugin.displaySourceTypes
import com.lonx.lyrico.plugin.distribution.PluginUpdateState
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.plugin.PluginIcon
import com.lonx.lyrico.ui.components.plugin.labelRes
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.innerPadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun PluginListItem(
    plugin: SourcePluginEntity,
    compact: Boolean,
    enabled: Boolean,
    update: PluginUpdateState?,
    onUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (compact) {
        CompactPluginListItem(
            update = update,
            onUpdate = onUpdate,
            onCancelUpdate = onCancelUpdate,
            plugin = plugin,
            enabled = enabled,
            onUninstall = onUninstall,
            onRename = onRename,
            onCheckChanged = onCheckChanged,
            onConfig = onConfig,
            modifier = modifier
        )
        return
    }

    val salt = LocalUiEngine.current == UiEngine.SALT
    val actionStyle = if (salt) PillButtonDefaults.style(PillButtonSize.Medium)
        else PillButtonDefaults.style(PillButtonSize.Medium).copy(
            height = 35.dp,
            contentPadding = PaddingValues(horizontal = 10.dp),
            shape = CircleShape,
            textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    val actionColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.text
            else colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f),
        containerColor = if (salt) SaltTheme.colors.text.copy(alpha = 0.08f)
            else colorScheme.secondaryContainer.copy(alpha = 0.8f)
    )
    val dangerColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.error else colorScheme.error,
        containerColor = if (salt) SaltTheme.colors.error.copy(alpha = 0.12f)
            else colorScheme.errorContainer.copy(alpha = 0.45f)
    )

    val pluginId = plugin.id
    val pluginName = plugin.displayName
    val pluginAuthor = plugin.author
    val pluginVersion = plugin.versionName
    val pluginDescription = plugin.description
    val pluginEnabled = enabled
    val pluginTypeText = plugin.displaySourceTypes
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")

    val hasDescription = pluginDescription.isNotBlank()

    var expanded by rememberSaveable(pluginId) {
        mutableStateOf(false)
    }

    if (salt) {
        com.moriafly.salt.ui.Card(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasDescription) Modifier.clickable { expanded = !expanded } else Modifier)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().innerPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PluginIcon(
                        iconPath = plugin.iconPath,
                        contentDescription = pluginName,
                        size = 40.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.moriafly.salt.ui.Text(
                                text = pluginName,
                                style = SaltTheme.textStyles.main,
                                fontWeight = FontWeight.Medium,
                                color = SaltTheme.colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                        }

                        SaltPluginSubText(pluginTypeText)
                        SaltPluginSubText(
                            text = stringResource(R.string.plugin_version_with_value, pluginVersion),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        SaltPluginSubText(
                            text = stringResource(
                                R.string.plugin_api_versions_with_value,
                                plugin.apiVersion,
                                plugin.minHostApiVersion
                            )
                        )
                        SaltPluginSubText(
                            text = stringResource(R.string.plugin_author_with_value, pluginAuthor)
                        )
                    }

                    Switcher(
                        state = pluginEnabled,
                        onChange = { checked ->
                            if (checked != pluginEnabled) onCheckChanged(checked)
                        }
                    )
                }

                if (hasDescription) {
                    Box(
                        modifier = Modifier
                            .innerPadding(vertical = false)
                            .animateContentSize(
                                animationSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutSlowInEasing
                                )
                            )
                    ) {
                        com.moriafly.salt.ui.Text(
                            text = pluginDescription,
                            style = SaltTheme.textStyles.sub,
                            color = SaltTheme.colors.subText,
                            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                            maxLines = if (expanded) Int.MAX_VALUE else 3
                        )
                    }
                }

                // 分割线和上面的描述拉开距离，否则贴着文字。
                ItemDivider(modifier = Modifier.padding(top = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().innerPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillButton(
                            text = stringResource(R.string.plugin_config),
                            onClick = onConfig,
                            style = actionStyle,
                            colors = actionColors,
                            leading = {
                                Icon(
                                    imageVector = LucideIcons.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = actionColors.contentColor(selected = false, enabled = true)
                                )
                            }
                        )
                        PillButton(
                            text = stringResource(R.string.plugin_custom_name),
                            onClick = onRename,
                            style = actionStyle,
                            colors = actionColors,
                            leading = {
                                Icon(
                                    imageVector = LucideIcons.Pencil,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = actionColors.contentColor(selected = false, enabled = true)
                                )
                            }
                        )
                        if (update != null) PillButton(
                            text = pluginUpdateActionText(update),
                            onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                            enabled = !update.isInstalling,
                            style = actionStyle,
                            colors = actionColors,
                            leading = {
                                Icon(
                                    imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                                )
                            }
                        )
                    }
                    PillButton(
                        text = stringResource(R.string.plugin_uninstall),
                        onClick = onUninstall,
                        style = actionStyle,
                        colors = dangerColors,
                        leading = {
                            Icon(
                                imageVector = LucideIcons.Trash,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = dangerColors.contentColor(selected = false, enabled = true)
                            )
                        }
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = {
            if (hasDescription) expanded = !expanded
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIcon(
                iconPath = plugin.iconPath,
                contentDescription = pluginName,
                size = 40.dp
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pluginName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                }

                Text(
                    text = pluginTypeText,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.plugin_version_with_value, pluginVersion),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(
                        R.string.plugin_api_versions_with_value,
                        plugin.apiVersion,
                        plugin.minHostApiVersion
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.plugin_author_with_value, pluginAuthor),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 1.dp),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = pluginEnabled,
                onCheckedChange = { checked ->
                    if (checked != pluginEnabled) {
                        onCheckChanged(checked)
                    }
                }
            )
        }

        if (hasDescription) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = FastOutSlowInEasing
                        )
                    )
            ) {
                Text(
                    text = pluginDescription,
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    maxLines = if (expanded) Int.MAX_VALUE else 3
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = colorScheme.outline.copy(alpha = 0.5f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton(
                    text = stringResource(R.string.plugin_config),
                    onClick = onConfig,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_custom_name),
                    onClick = onRename,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Pencil,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                if (update != null) PillButton(
                    text = pluginUpdateActionText(update),
                    onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                    enabled = !update.isInstalling,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                        )
                    }
                )
            }
            PillButton(
                text = stringResource(R.string.plugin_uninstall),
                onClick = onUninstall,
                style = actionStyle,
                colors = dangerColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Trash,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = dangerColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
        }
    }
}

@Composable
private fun CompactPluginListItem(
    update: PluginUpdateState?,
    onUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    plugin: SourcePluginEntity,
    enabled: Boolean,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pluginName = plugin.displayName
    val versionText = if (update?.isDownloading == true) {
        "${plugin.versionName} · ${update.percent}%"
    } else plugin.versionName
    val pluginTypeText = plugin.displaySourceTypes
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")
    val salt = LocalUiEngine.current == UiEngine.SALT
    val actionStyle = PillButtonDefaults.style(PillButtonSize.Small).copy(
        height = 30.dp, iconOnlyPadding = PaddingValues(6.dp), shape = CircleShape
    )
    val actionColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.text
            else colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.7f else 0.9f),
        containerColor = if (salt) SaltTheme.colors.text.copy(alpha = 0.08f)
            else colorScheme.secondaryContainer.copy(alpha = 0.8f)
    )
    val dangerColors = PillButtonDefaults.colors(
        contentColor = if (salt) SaltTheme.colors.error else colorScheme.error,
        containerColor = if (salt) SaltTheme.colors.error.copy(alpha = 0.12f)
            else colorScheme.errorContainer.copy(alpha = 0.45f)
    )

    if (salt) {
        com.moriafly.salt.ui.Card(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth().innerPadding(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PluginIcon(
                    iconPath = plugin.iconPath,
                    contentDescription = pluginName,
                    size = 36.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    com.moriafly.salt.ui.Text(
                        text = pluginName,
                        style = SaltTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = SaltTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SaltPluginSubText(pluginTypeText)
                    SaltPluginSubText(stringResource(R.string.plugin_version_with_value, versionText))
                    SaltPluginSubText(
                        text = stringResource(
                            R.string.plugin_api_versions_with_value,
                            plugin.apiVersion,
                            plugin.minHostApiVersion
                        )
                    )
                }

                Switcher(
                    state = enabled,
                    onChange = { checked ->
                        if (checked != enabled) onCheckChanged(checked)
                    }
                )
            }

            Row(
                // 底部留出与卡片边缘的距离，操作按钮保持紧凑。
                modifier = Modifier.fillMaxWidth().innerPadding(vertical = false)
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (update != null) PillButton(
                    text = pluginUpdateActionText(update),
                    onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                    enabled = !update.isInstalling,
                    showText = false,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                            contentDescription = pluginUpdateActionText(update),
                            modifier = Modifier.size(18.dp),
                            tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_config),
                    onClick = onConfig,
                    showText = false,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Settings,
                            contentDescription = stringResource(R.string.plugin_config),
                            modifier = Modifier.size(18.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_custom_name),
                    onClick = onRename,
                    showText = false,
                    style = actionStyle,
                    colors = actionColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Pencil,
                            contentDescription = stringResource(R.string.plugin_custom_name),
                            modifier = Modifier.size(18.dp),
                            tint = actionColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
                PillButton(
                    text = stringResource(R.string.plugin_uninstall),
                    onClick = onUninstall,
                    showText = false,
                    style = actionStyle,
                    colors = dangerColors,
                    leading = {
                        Icon(
                            imageVector = LucideIcons.Trash,
                            contentDescription = stringResource(R.string.plugin_uninstall),
                            modifier = Modifier.size(18.dp),
                            tint = dangerColors.contentColor(selected = false, enabled = true)
                        )
                    }
                )
            }
        }
        return
    }

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIcon(
                iconPath = plugin.iconPath,
                contentDescription = pluginName,
                size = 36.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pluginName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                }

                Text(
                    text = pluginTypeText,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.plugin_version_with_value, versionText),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(
                        R.string.plugin_api_versions_with_value,
                        plugin.apiVersion,
                        plugin.minHostApiVersion
                    ),
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked != enabled) onCheckChanged(checked)
                }
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            if (update != null) PillButton(
                text = pluginUpdateActionText(update),
                onClick = if (update.isDownloading) onCancelUpdate else onUpdate,
                enabled = !update.isInstalling,
                showText = false,
                style = actionStyle,
                colors = actionColors,
                leading = {
                    Icon(
                        imageVector = if (update.isDownloading) LucideIcons.Close else LucideIcons.Download,
                        contentDescription = pluginUpdateActionText(update),
                        modifier = Modifier.size(18.dp),
                        tint = actionColors.contentColor(selected = false, enabled = !update.isInstalling)
                    )
                }
            )
            PillButton(
                text = stringResource(R.string.plugin_config),
                onClick = onConfig,
                showText = false,
                style = actionStyle,
                colors = actionColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Settings,
                        contentDescription = stringResource(R.string.plugin_config),
                        modifier = Modifier.size(18.dp),
                        tint = actionColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
            PillButton(
                text = stringResource(R.string.plugin_custom_name),
                onClick = onRename,
                showText = false,
                style = actionStyle,
                colors = actionColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Pencil,
                        contentDescription = stringResource(R.string.plugin_custom_name),
                        modifier = Modifier.size(18.dp),
                        tint = actionColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
            PillButton(
                text = stringResource(R.string.plugin_uninstall),
                onClick = onUninstall,
                showText = false,
                style = actionStyle,
                colors = dangerColors,
                leading = {
                    Icon(
                        imageVector = LucideIcons.Trash,
                        contentDescription = stringResource(R.string.plugin_uninstall),
                        modifier = Modifier.size(18.dp),
                        tint = dangerColors.contentColor(selected = false, enabled = true)
                    )
                }
            )
        }
    }
}

/** Salt 的副文案行：类型、版本、API 与作者共用同一语义样式。 */
@Composable
private fun SaltPluginSubText(text: String, modifier: Modifier = Modifier) {
    com.moriafly.salt.ui.Text(
        text = text,
        style = SaltTheme.textStyles.sub,
        color = SaltTheme.colors.subText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun pluginUpdateActionText(update: PluginUpdateState?): String = when (update?.phase) {
    PluginUpdateState.Phase.DOWNLOADING -> "${update.percent}%"
    PluginUpdateState.Phase.INSTALLING -> stringResource(R.string.plugin_online_installing)
    PluginUpdateState.Phase.FAILED -> stringResource(R.string.plugin_online_retry)
    else -> stringResource(R.string.plugin_update)
}
