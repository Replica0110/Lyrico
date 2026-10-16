@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.LocalOverscrollFactory
import com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.base.SaltIconButton
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.blur.*
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.scaffoldPageWindowInsets
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.screen.BasicScreen
import com.moriafly.salt.ui.screen.ScreenCard
import com.moriafly.salt.ui.popup.PopupMenuItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Settings definitions and callbacks are shared; each design system owns its presentation. */
@Composable
internal fun SettingsPage(
    engine: UiEngine,
    initialized: Boolean,
    onBack: () -> Unit,
    content: SettingsItemsScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val title = stringResource(R.string.settings_title)
    if (engine == UiEngine.SALT) {
        CompositionLocalProvider(LocalIndication provides SaltTheme.configs.indication,
            LocalOverscrollFactory provides CupertinoOverscrollEffectFactory()) {
        BasicScreen(
            contentPadding = scaffoldPageWindowInsets().asPaddingValues(),
            modifier = Modifier.background(SaltTheme.colors.background),
            title = title,
            actionButton = {
                SaltIconButton(LucideIcons.ArrowLeft, stringResource(R.string.action_back), onBack)
            },
        ) { padding ->
            if (initialized) {
                LazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                    SettingsItemsScope(engine, this).content()
                }
            }
        }
        }
    } else {
        val behavior = MiuixScrollBehavior()
        val backdrop = rememberBarBlurBackdrop()
        top.yukonga.miuix.kmp.basic.Scaffold(topBar = {
            BlurredTopBar(backdrop) {
                top.yukonga.miuix.kmp.basic.SmallTopAppBar(
                    title = title, color = Color.Transparent, defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                            top.yukonga.miuix.kmp.basic.Icon(LucideIcons.ArrowLeft, stringResource(R.string.action_back))
                        }
                    }, scrollBehavior = behavior,
                )
            }
        }) { padding ->
            if (initialized) {
                Box(Modifier.fillMaxSize().blurSource(backdrop)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                            .nestedScroll(behavior.nestedScrollConnection),
                        contentPadding = scaffoldContentPadding(padding, bottomExtra = 12.dp),
                        overscrollEffect = null,
                    ) {
                        SettingsItemsScope(engine, this).content()
                    }
                }
            } else Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                top.yukonga.miuix.kmp.basic.CircularProgressIndicator(size = 32.dp)
            }
        }
    }
}

/** A page-local settings vocabulary, deliberately limited to the controls used on this page. */
internal data class PaletteOption(val title: String, val color: Color)

internal class SettingsItemsScope(private val engine: UiEngine, private val listScope: LazyListScope) {
    fun item(key: String, content: @Composable () -> Unit) = listScope.item(key = key) { content() }

    @Composable fun SectionTitle(text: String) {
        if (engine == UiEngine.SALT) ItemOuterTitle(text) else top.yukonga.miuix.kmp.basic.SmallTitle(text)
    }

    @Composable fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        if (engine == UiEngine.SALT) ScreenCard(modifier = modifier, content = content)
        else top.yukonga.miuix.kmp.basic.Card(modifier = modifier.padding(horizontal = 12.dp), content = content)
    }

    @Composable fun Action(
        title: String, summary: String? = null, icon: ImageVector? = null, tone: PreferenceIconTone = PreferenceIconTone.Tools,
        endActions: @Composable (() -> Unit)? = null, holdDownState: Boolean = false,
        onClick: () -> Unit,
    ) {
        if (engine == UiEngine.SALT) {
            Item(text = title, sub = summary, onClick = onClick, subContent = endActions, iconPainter = icon?.let { rememberVectorPainter(it) })
        } else top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = title, summary = summary, startAction = { icon?.let { PreferenceIcon(it, tone) } }, endActions = { endActions?.invoke() },
            holdDownState = holdDownState, onClick = onClick,
        )
    }

    @Composable fun Toggle(
        title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
        summary: String? = null, enabled: Boolean = true, icon: ImageVector? = null, tone: PreferenceIconTone = PreferenceIconTone.Tools,
    ) {
        if (engine == UiEngine.SALT) {
            ItemSwitcher(text = title, sub = summary, state = checked, enabled = enabled, onChange = onCheckedChange, iconPainter = icon?.let { rememberVectorPainter(it) })
        } else top.yukonga.miuix.kmp.preference.SwitchPreference(
            title = title, summary = summary, checked = checked, enabled = enabled,
            startAction = { icon?.let { PreferenceIcon(it, tone) } }, onCheckedChange = onCheckedChange,
        )
    }

    @Composable fun Choice(
        title: String, items: List<String>, selectedIndex: Int, onSelectedIndexChange: (Int) -> Unit,
        summary: String? = null, icon: ImageVector? = null, tone: PreferenceIconTone = PreferenceIconTone.Tools,
    ) {
        if (engine == UiEngine.SALT) {
            ItemDropdown(text = title, sub = summary, value = items.getOrNull(selectedIndex).orEmpty(), iconPainter = icon?.let { rememberVectorPainter(it) }) {
                items.forEachIndexed { index, label ->
                    PopupMenuItem(text = label, selected = index == selectedIndex, onClick = {
                        state.dismiss()
                        onSelectedIndexChange(index)
                    })
                }
            }
        } else top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
            title = title, summary = summary, items = items, selectedIndex = selectedIndex,
            startAction = { icon?.let { PreferenceIcon(it, tone) } }, onSelectedIndexChange = onSelectedIndexChange,
        )
    }

    @Composable fun Spinner(
        title: String, items: List<PaletteOption>, selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit, icon: ImageVector? = null, tone: PreferenceIconTone = PreferenceIconTone.Tools,
    ) {
        if (engine == UiEngine.SALT) {
            ItemDropdown(text = title, value = items.getOrNull(selectedIndex)?.title.orEmpty(),
                iconPainter = icon?.let { rememberVectorPainter(it) }) {
                items.forEachIndexed { index, option ->
                    PopupMenuItem(text = option.title, selected = index == selectedIndex,
                        iconPainter = androidx.compose.ui.graphics.painter.ColorPainter(option.color), iconColor = null,
                        onClick = { state.dismiss(); onSelectedIndexChange(index) })
                }
            }
        } else top.yukonga.miuix.kmp.preference.WindowSpinnerPreference(
            title = title, items = items.map { option ->
                top.yukonga.miuix.kmp.basic.DropdownItem(title = option.title, icon = {
                    Box(Modifier.padding(end = 12.dp).size(20.dp).background(option.color))
                })
            }, selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange, startAction = { icon?.let { PreferenceIcon(it, tone) } },
        )
    }

    @Composable fun Range(
        title: String, value: Float, onValueChange: (Float) -> Unit,
        onValueChangeFinished: () -> Unit, valueText: String, summary: String? = null,
        valueRange: ClosedFloatingPointRange<Float>, steps: Int,
        icon: ImageVector? = null, tone: PreferenceIconTone = PreferenceIconTone.Tools,
    ) {
        if (engine == UiEngine.SALT) {
            Column {
                summary?.let { ItemTip(it) }
                ItemSlider(value = value, onValueChange = onValueChange, text = title, sub = valueText,
                    valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished, iconPainter = icon?.let { rememberVectorPainter(it) })
            }
        } else top.yukonga.miuix.kmp.preference.SliderPreference(
            title = title, value = value, onValueChange = onValueChange, valueText = valueText,
            summary = summary, valueRange = valueRange, steps = steps, showKeyPoints = true,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step, onValueChangeFinished = onValueChangeFinished, startAction = { icon?.let { PreferenceIcon(it, tone) } },
        )
    }
}

@Composable
internal fun SettingsCacheDialog(show: Boolean, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        if (show) com.moriafly.salt.ui.dialog.YesNoDialog(
            title = stringResource(R.string.clear_cache), content = "",
            drawContent = {
                Text(text, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
            },
            onDismissRequest = onDismiss, onConfirm = onConfirm,
            cancelText = stringResource(R.string.cancel), confirmText = stringResource(R.string.confirm),
        )
    } else com.lonx.lyrico.ui.components.base.YesNoDialog(
        show = show, title = stringResource(R.string.clear_cache),
        content = {
            top.yukonga.miuix.kmp.basic.Text(text,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
        },
        onDismissRequest = onDismiss, onConfirm = onConfirm,
    )
}
