package com.lonx.lyrico.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.ItemPopupArrow
import com.moriafly.salt.ui.JustifiedRow
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.enabledAlpha
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupState
import com.moriafly.salt.ui.popup.rememberPopupState

@OptIn(UnstableSaltUiApi::class)
@Composable
fun SaltDropdownItem(
    text: String,
    value: String,
    modifier: Modifier = Modifier,
    state: PopupState = rememberPopupState(),
    enabled: Boolean = true,
    iconPainter: Painter? = null,
    iconPaddingValues: PaddingValues = PaddingValues(0.dp),
    iconColor: Color? = SaltTheme.colors.text,
    sub: String? = null,
    content: @Composable ColumnScope.(PopupState) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(SaltTheme.dimens.item)
            .enabledAlpha(enabled)
            .clickable(enabled = enabled) {
                state.expend()
            }
            .innerPadding(vertical = false),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconPainter?.let {
            Image(
                modifier = Modifier
                    .size(SaltTheme.dimens.itemIcon)
                    .padding(iconPaddingValues),
                painter = iconPainter,
                contentDescription = null,
                colorFilter = iconColor?.let { ColorFilter.tint(iconColor) }
            )
            Spacer(Modifier.width(SaltTheme.dimens.subPadding))
        }

        JustifiedRow(
            startContent = {
                Column {
                    Text(text = text)
                    sub?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sub,
                            color = SaltTheme.colors.subText,
                            style = SaltTheme.textStyles.sub
                        )
                    }
                }
            },
            endContent = {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = value,
                            color = SaltTheme.colors.subText,
                            textAlign = TextAlign.End,
                            style = SaltTheme.textStyles.sub
                        )
                        Spacer(Modifier.width(SaltTheme.dimens.subPadding))
                        ItemPopupArrow()
                    }

                    PopupMenu(
                        expanded = state.expend,
                        onDismissRequest = state::dismiss
                    ) {
                        content(state)
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .innerPadding(horizontal = false),
            verticalAlignment = Alignment.CenterVertically
        )
    }
}
