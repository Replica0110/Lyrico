package com.lonx.lyrico.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.blur.*
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun SettingsPageMiuix(
    initialized: Boolean,
    onBack: () -> Unit,
    title: String,
    content: SettingsItemsScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val behavior = MiuixScrollBehavior()
    val backdrop = rememberBarBlurBackdrop()
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = {
            BlurredTopBar(backdrop) {
                top.yukonga.miuix.kmp.basic.SmallTopAppBar(
                    title = title,
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                            top.yukonga.miuix.kmp.basic.Icon(
                                LucideIcons.ArrowLeft,
                                stringResource(R.string.action_back),
                            )
                        }
                    },
                    scrollBehavior = behavior,
                )
            }
        }
    ) { padding ->
        if (initialized) {
            Box(Modifier.fillMaxSize().blurSource(backdrop)) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier.fillMaxSize()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(behavior.nestedScrollConnection),
                    contentPadding = scaffoldContentPadding(padding, bottomExtra = 12.dp),
                    overscrollEffect = null,
                ) {
                    SettingsItemsScope(this, MiuixSettingsItems).content()
                }
            }
        } else
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                top.yukonga.miuix.kmp.basic.CircularProgressIndicator(size = 32.dp)
            }
    }
}

internal object MiuixSettingsItems : SettingsItemPresentation {
    @Composable
    override fun SectionTitle(text: String) {
        top.yukonga.miuix.kmp.basic.SmallTitle(text)
    }

    @Composable
    override fun Group(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = modifier.padding(horizontal = 12.dp),
            content = content,
        )
    }

    @Composable
    override fun Action(
        title: String,
        summary: String?,
        icon: ImageVector?,
        tone: PreferenceIconTone,
        value: String?,
        holdDownState: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = title,
            summary = summary,
            startAction = { icon?.let { PreferenceIcon(it, tone) } },
            endActions = {
                value?.let {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = it,
                        fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2.fontSize,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            },
            holdDownState = holdDownState,
            enabled = enabled,
            onClick = onClick,
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
        top.yukonga.miuix.kmp.preference.SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            startAction = { icon?.let { PreferenceIcon(it, tone) } },
            onCheckedChange = onCheckedChange,
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
        top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
            title = title,
            summary = summary,
            items = items,
            selectedIndex = selectedIndex,
            startAction = { icon?.let { PreferenceIcon(it, tone) } },
            onSelectedIndexChange = onSelectedIndexChange,
        )
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
        top.yukonga.miuix.kmp.preference.WindowSpinnerPreference(
            title = title,
            items =
                items.map { option ->
                    top.yukonga.miuix.kmp.basic.DropdownItem(
                        title = option.title,
                        icon = {
                            Box(Modifier.padding(end = 12.dp).size(20.dp).background(option.color))
                        },
                    )
                },
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            startAction = { icon?.let { PreferenceIcon(it, tone) } },
        )
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
        top.yukonga.miuix.kmp.preference.SliderPreference(
            title = title,
            value = value,
            onValueChange = onValueChange,
            valueText = valueText,
            summary = summary,
            valueRange = valueRange,
            steps = steps,
            showKeyPoints = true,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            onValueChangeFinished = onValueChangeFinished,
            startAction = { icon?.let { PreferenceIcon(it, tone) } },
        )
    }
}
