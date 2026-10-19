@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.scaffoldPageWindowInsets
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.ScreenCard

@Composable
internal fun SettingsPageSalt(
    initialized: Boolean,
    onBack: () -> Unit,
    content: SettingsItemsScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val title = stringResource(R.string.settings_title)
    CompositionLocalProvider(
        LocalIndication provides SaltTheme.configs.indication,
        LocalOverscrollFactory provides CupertinoOverscrollEffectFactory(),
    ) {
        BasicScreen(
            contentPadding = scaffoldPageWindowInsets().asPaddingValues(),
            modifier = Modifier.background(SaltTheme.colors.background),
            title = title,
            actionButton = {
                SaltIconButton(LucideIcons.ArrowLeft, stringResource(R.string.action_back), onBack)
            },
        ) { padding ->
            if (initialized) {
                LazyColumn(
                    state = listState,
                    contentPadding = padding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SettingsItemsScope(this, SaltSettingsItems).content()
                }
            }
        }
    }
}

internal object SaltSettingsItems : SettingsItemPresentation {
    @Composable
    override fun SectionTitle(text: String) {
        ItemOuterTitle(text)
    }

    @Composable
    override fun Group(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
        ScreenCard(modifier = modifier, content = content)
    }

    @Composable
    override fun Action(
        title: String,
        summary: String?,
        icon: ImageVector?,
        tone: PreferenceIconTone,
        value: String?,
        holdDownState: Boolean,
        onClick: () -> Unit,
    ) {
        Item(
            text = title,
            sub = summary,
            onClick = onClick,
            tag = value,
            iconPainter = icon?.let { rememberVectorPainter(it) },
            iconColor = tone.color(),
        )
    }

    @Composable
    override fun Toggle(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        summary: String?,
        enabled: Boolean,
        icon: ImageVector?,
        tone: PreferenceIconTone,
    ) {
        ItemSwitcher(
            text = title,
            sub = summary,
            state = checked,
            enabled = enabled,
            onChange = onCheckedChange,
            iconPainter = icon?.let { rememberVectorPainter(it) },
            iconColor = tone.color(),
        )
    }

    @Composable
    override fun Choice(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit,
        summary: String?,
        icon: ImageVector?,
        tone: PreferenceIconTone,
    ) {
        ItemDropdown(
            text = title,
            sub = summary,
            value = items.getOrNull(selectedIndex).orEmpty(),
            iconPainter = icon?.let { rememberVectorPainter(it) },
            iconColor = tone.color(),
        ) {
            items.forEachIndexed { index, label ->
                PopupMenuItem(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = {
                        state.dismiss()
                        onSelectedIndexChange(index)
                    },
                )
            }
        }
    }

    @Composable
    override fun Spinner(
        title: String,
        items: List<PaletteOption>,
        selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit,
        icon: ImageVector?,
        tone: PreferenceIconTone,
    ) {
        ItemDropdown(
            text = title,
            value = items.getOrNull(selectedIndex)?.title.orEmpty(),
            iconPainter = icon?.let { rememberVectorPainter(it) },
            iconColor = tone.color(),
        ) {
            items.forEachIndexed { index, option ->
                PopupMenuItem(
                    text = option.title,
                    selected = index == selectedIndex,
                    iconPainter = androidx.compose.ui.graphics.painter.ColorPainter(option.color),
                    iconColor = null,
                    onClick = {
                        state.dismiss()
                        onSelectedIndexChange(index)
                    },
                )
            }
        }
    }

    @Composable
    override fun Range(
        title: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: () -> Unit,
        valueText: String,
        summary: String?,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        icon: ImageVector?,
        tone: PreferenceIconTone,
    ) {
        Column {
            ItemSlider(
                value = value,
                onValueChange = onValueChange,
                text = title,
                sub = valueText,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                iconPainter = icon?.let { rememberVectorPainter(it) },
                iconColor = tone.color(),
            )
            summary?.let {
                Text(
                    text = it,
                    style = SaltTheme.textStyles.sub,
                    color = SaltTheme.colors.subText,
                    modifier = Modifier.innerPadding(vertical = false).padding(bottom = 12.dp),
                )
            }
        }
    }
}
