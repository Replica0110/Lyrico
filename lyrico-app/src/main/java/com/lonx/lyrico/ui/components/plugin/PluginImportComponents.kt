@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.RoundedColumn
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import com.lonx.lyrico.plugin.source.PluginInstallFailed
import com.lonx.lyrico.plugin.source.PluginInstallResult
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.plugin.source.PluginVersionConflict
import com.lonx.lyrico.plugin.source.PluginInstallCandidate
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.displaySourceTypes
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.Uncheck
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared by local import preview, online selection and update summary. */
@Composable
internal fun PluginImportEntry(
    name: String,
    id: String,
    version: String,
    details: List<String>,
    conflict: PluginVersionConflict? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    compatible: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val status = if (!compatible) stringResource(R.string.plugin_online_incompatible) else conflict?.toImportConflictText()
    val statusColor = if (!compatible) {
        if (salt) SaltTheme.colors.error else MiuixTheme.colorScheme.error
    } else conflict?.toImportConflictColor()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (selected == null) Modifier else Modifier.toggleable(
                value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = if (salt) Modifier.fillMaxWidth().heightIn(min = SaltTheme.dimens.item).innerPadding(vertical = false)
                        else Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected != null) {
                        if (salt) com.moriafly.salt.ui.Icon(
                            painter = rememberVectorPainter(if (selected) SaltIcons.Check else SaltIcons.Uncheck),
                            contentDescription = null, modifier = Modifier.size(SaltTheme.dimens.itemIcon),
                            tint = if (enabled) SaltTheme.colors.highlight else SaltTheme.colors.subText)
                        else Checkbox(
                            state = if (selected) ToggleableState.On else ToggleableState.Off,
                            enabled = enabled, onClick = { onCheckedChange(!selected) })
                        Spacer(Modifier.width(if (salt) SaltTheme.dimens.subPadding else 12.dp))
                    }
                    Column(Modifier.weight(1f).then(if (salt) Modifier.innerPadding(horizontal = false) else Modifier)) {
                        if (salt) com.moriafly.salt.ui.Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        PluginEntrySubText(id)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (status != null && statusColor != null) ImportStatusBadge(status, statusColor)
                    PluginEntrySubText(stringResource(R.string.plugin_import_version, version))
                    details.forEach { PluginEntrySubText(it) }
                    supportingContent?.invoke()
                }
            }
            trailingContent?.let {
                Box(Modifier.padding(end = 16.dp), contentAlignment = Alignment.Center) { it() }
            }
        }
    }
    if (salt) com.moriafly.salt.ui.RoundedColumn { content() }
    else Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) { content() }
}

@Composable
private fun PluginEntrySubText(text: String) {
    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.Text(text,
        style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
    else Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
internal fun PluginImportSectionTitle(text: String) {
    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.ItemOuterTitle(text = text)
    else SmallTitle(text = text, insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
}

@Composable
internal fun ImportStatusBadge(text: String, color: Color) {
    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.Text(text,
        style = SaltTheme.textStyles.sub.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
        color = color, maxLines = 1, modifier = Modifier.clip(SaltTheme.shapes.small)
            .background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp))
    else Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
internal fun PluginVersionConflict.toImportConflictColor(): Color {
    if (LocalUiEngine.current == UiEngine.SALT) return when (this) {
        PluginVersionConflict.NONE, PluginVersionConflict.UPDATE -> SaltTheme.colors.highlight
        PluginVersionConflict.OVERWRITE -> SaltTheme.colors.subText
        PluginVersionConflict.DOWNGRADE -> SaltTheme.colors.error
    }
    return when (this) {
        PluginVersionConflict.NONE, PluginVersionConflict.UPDATE -> MiuixTheme.colorScheme.primary
        PluginVersionConflict.OVERWRITE -> MiuixTheme.colorScheme.onTertiaryContainer
        PluginVersionConflict.DOWNGRADE -> MiuixTheme.colorScheme.error
    }
}

@Composable
internal fun PluginVersionConflict.toImportConflictText(): String = stringResource(when (this) {
    PluginVersionConflict.NONE -> R.string.plugin_import_conflict_new
    PluginVersionConflict.UPDATE -> R.string.plugin_import_conflict_update
    PluginVersionConflict.OVERWRITE -> R.string.plugin_import_conflict_overwrite
    PluginVersionConflict.DOWNGRADE -> R.string.plugin_import_conflict_downgrade
})

@Composable
internal fun PluginImportCandidateItem(
    candidate: PluginInstallCandidate,
    selected: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val localeTags = androidx.compose.ui.platform.LocalConfiguration.current.locales.toLanguageTags()
    val manifest by androidx.compose.runtime.produceState(candidate.manifest, candidate, localeTags) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.lonx.lyrico.plugin.i18n.PluginStrings.load(candidate.pluginRoot, candidate.manifest)
                    .snapshot(localeTags.split(',')).localize(candidate.manifest)
            }.getOrDefault(candidate.manifest)
        }
    }
    val versionText = if (candidate.existingPlugin != null) {
        "${candidate.existingPlugin.versionName} → ${manifest.versionName}"
    } else {
        manifest.versionName
    }
    val locationText = candidate.relativeRootInArchive.ifBlank { "/" }
    val pluginTypeText = candidate.manifest.capabilities
        .displaySourceTypes()
        .map { stringResource(it.labelRes()) }
        .joinToString(" / ")

    PluginImportEntry(
        name = manifest.name,
        id = manifest.id,
        version = versionText,
        conflict = candidate.versionConflict,
        details = listOfNotNull(
            stringResource(R.string.plugin_type_with_value, pluginTypeText),
            stringResource(R.string.plugin_import_path, locationText),
            manifest.description.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.plugin_import_description, it)
            }
        ),
        selected = selected,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}



internal fun PluginSourceType.labelRes(): Int = when (this) {
    PluginSourceType.METADATA -> R.string.plugin_type_metadata
    PluginSourceType.LYRICS -> R.string.plugin_type_lyrics
    PluginSourceType.COVER -> R.string.plugin_type_cover
}


@Composable
internal fun PluginImportFailedItem(
    failed: PluginInstallFailed
) {
    var expanded by rememberSaveable(
        failed.rootPath,
        failed.reason,
        failed.pluginId,
        failed.versionName
    ) {
        mutableStateOf(false)
    }

    val title = failed.displayName

    val subtitle = buildString {
        failed.pluginId
            ?.takeIf { it.isNotBlank() && it != title }
            ?.let { append(it) }

        failed.versionName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (isNotEmpty()) append("\n")
                append(stringResource(R.string.plugin_import_version))
                append(" ")
                append(it)
            }

        if (failed.rootPath.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(failed.rootPath)
        }
    }

    val versionChange = buildString {
        val oldVersion = failed.existingVersionName
        val newVersion = failed.versionName
        if (!oldVersion.isNullOrBlank() && !newVersion.isNullOrBlank()) {
            append(oldVersion)
            append(" -> ")
            append(newVersion)
        }
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        RoundedColumn {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Item(
                    onClick = { expanded = !expanded },
                    text = title,
                    sub = subtitle.ifBlank { null },
                    textColor = SaltTheme.colors.error,
                    subColor = SaltTheme.colors.error.copy(alpha = 0.82f),
                    tag = if (expanded) stringResource(R.string.plugin_import_collapse)
                    else stringResource(R.string.plugin_import_expand),
                    arrowType = ItemArrowType.None,
                )
                if (versionChange.isNotBlank()) com.moriafly.salt.ui.Text(
                    text = versionChange,
                    style = SaltTheme.textStyles.sub,
                    color = SaltTheme.colors.error.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.innerPadding(vertical = false)
                )
                com.moriafly.salt.ui.Text(
                    text = failed.reason,
                    style = SaltTheme.textStyles.sub,
                    color = SaltTheme.colors.error,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.innerPadding(vertical = false)
                )
            }
        }
        return
    }

    Card(
        colors = CardDefaults.defaultColors(
            color = colorScheme.errorContainer
        )
    ) {
        BasicComponent(
            onClick = {
                expanded = !expanded
            },
            modifier = Modifier.animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                failed.conflict?.let { conflict ->
                    ImportStatusBadge(
                        text = conflict.toImportConflictText(),
                        color = colorScheme.error
                    )
                }

                Text(
                    text = if (expanded) {
                        stringResource(R.string.plugin_import_collapse)
                    } else {
                        stringResource(R.string.plugin_import_expand)
                    },
                    fontSize = 12.sp,
                    color = colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onErrorContainer.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (versionChange.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = versionChange,
                    fontSize = 12.sp,
                    color = colorScheme.onErrorContainer.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = failed.reason,
                fontSize = 12.sp,
                color = colorScheme.onErrorContainer,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}



@Composable
internal fun PluginSheetError(message: String) {
    if (LocalUiEngine.current == UiEngine.SALT) com.moriafly.salt.ui.RoundedColumn {
        com.moriafly.salt.ui.Text(message, modifier = Modifier.padding(12.dp),
            color = com.moriafly.salt.ui.SaltTheme.colors.error, style = com.moriafly.salt.ui.SaltTheme.textStyles.sub)
    } else Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer)) {
        Text(message, modifier = Modifier.padding(12.dp), color = MiuixTheme.colorScheme.onErrorContainer,
            fontSize = MiuixTheme.textStyles.body2.fontSize)
    }
}

@Composable
internal fun PluginImportResult(result: PluginInstallResult?, error: String?) {
    error?.let { PluginSheetError(stringResource(R.string.plugin_action_failed, it)) }
    if (result == null || result.failed.isEmpty()) return
    PluginImportSectionTitle(stringResource(R.string.plugin_install_result, result.installed.size, result.failed.size))
    result.failed.forEach { failure ->
        PluginImportFailedItem(failure)
        if (LocalUiEngine.current != UiEngine.SALT) Spacer(Modifier.height(8.dp))
    }
}
