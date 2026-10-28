package com.lonx.lyrico.screens.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone

/** Shared settings definitions; native controls live in SettingsSalt/SettingsMiuix. */
@Composable
internal fun SettingsPage(
    engine: UiEngine,
    initialized: Boolean,
    onBack: () -> Unit,
    title: String = stringResource(R.string.settings_title),
    content: SettingsItemsScope.() -> Unit,
) {
    when (engine) {
        UiEngine.SALT -> SettingsPageSalt(initialized, onBack, title, content)
        UiEngine.MIUIX -> SettingsPageMiuix(initialized, onBack, title, content)
    }
}

internal data class PaletteOption(val title: String, val color: Color)

internal class SettingsItemsScope(
    private val listScope: LazyListScope,
    presentation: SettingsItemPresentation,
) : SettingsItemPresentation by presentation {
    fun item(key: String, content: @Composable () -> Unit) = listScope.item(key = key) { content() }
}

internal interface SettingsItemPresentation {
    @Composable fun SectionTitle(text: String)

    @Composable
    fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)

    @Composable
    fun Action(
        title: String,
        summary: String? = null,
        icon: ImageVector? = null,
        tone: PreferenceIconTone = PreferenceIconTone.Tools,
        value: String? = null,
        holdDownState: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit,
    )

    @Composable
    fun Toggle(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        summary: String? = null,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        tone: PreferenceIconTone = PreferenceIconTone.Tools,
    )

    @Composable
    fun Choice(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit,
        summary: String? = null,
        icon: ImageVector? = null,
        tone: PreferenceIconTone = PreferenceIconTone.Tools,
    )

    @Composable
    fun Spinner(
        title: String,
        items: List<PaletteOption>,
        selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit,
        icon: ImageVector? = null,
        tone: PreferenceIconTone = PreferenceIconTone.Tools,
    )

    @Composable
    fun Range(
        title: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: () -> Unit,
        valueText: String,
        summary: String? = null,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        icon: ImageVector? = null,
        tone: PreferenceIconTone = PreferenceIconTone.Tools,
    )
}
