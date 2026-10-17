@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.library

import com.lonx.lyrico.ui.theme.LocalUiEngine

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.R
import com.moriafly.salt.ui.*

@Composable
internal fun LibraryAlbumSurface(modifier: Modifier, onClick: () -> Unit,
    onLongClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val onLongPress = onLongClick?.let { action ->
        { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); action() }
    }
    if (LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        val shape = CardDefaults.shape
        Card(shape = shape, modifier = modifier.fillMaxWidth().clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) { content() }
        }
    } else top.yukonga.miuix.kmp.basic.Card(
        modifier = modifier.fillMaxWidth(), onClick = onClick, onLongPress = onLongPress,
    ) {
        top.yukonga.miuix.kmp.basic.BasicComponent(
            modifier = Modifier.fillMaxWidth().background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceVariant),
            insideMargin = PaddingValues(8.dp),
        ) { content() }
    }
}

@Composable
internal fun SaltLibraryItemActions(selecting: Boolean, selected: Boolean,
    onToggle: () -> Unit, onMenu: () -> Unit) {
    val interaction = if (selecting) Modifier.toggleable(value = selected, role = Role.Checkbox) { onToggle() }
        else Modifier.clickable(role = Role.Button, onClick = onMenu)
    Box(Modifier.size(40.dp).clip(CircleShape).then(interaction), contentAlignment = Alignment.Center) {
        AnimatedContent(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
            targetState = selecting to selected,
            contentKey = { if (it.first) it.second else null },
            label = "LibraryItemSelection",
            transitionSpec = {
                ((fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f)) togetherWith fadeOut(tween(150))).using(null)
            },
        ) { (selecting, selected) ->
        if (selecting && !selected) {
            Box(Modifier.size(20.dp).border(1.5.dp, SaltTheme.colors.subText, CircleShape))
        } else Icon(
            painter = rememberVectorPainter(if (selecting) LucideIcons.Check else LucideIcons.EllipsisVertical),
            modifier = Modifier.size(24.dp),
            contentDescription = if (selecting) null else stringResource(R.string.cd_more_actions),
            tint = if (selecting) SaltTheme.colors.highlight else SaltTheme.colors.subText,
        )
        }
    }
}
