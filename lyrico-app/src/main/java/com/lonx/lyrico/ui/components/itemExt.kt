package com.lonx.lyrico.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.JustifiedRow
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.enabledAlpha
import com.moriafly.salt.ui.innerPadding

@OptIn(UnstableSaltUiApi::class)
@Composable
fun ItemExt(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconPainter: Painter? = null,
    iconContent: (@Composable () -> Unit)? = null,
    iconPaddingValues: PaddingValues = PaddingValues(0.dp),
    iconColor: Color? = SaltTheme.colors.text,
    textColor: Color = SaltTheme.colors.text,
    sub: String? = null,
    subColor: Color = SaltTheme.colors.subText,
    subContent: (@Composable () -> Unit)? = null,
    iconEnd: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(SaltTheme.dimens.item)
            .enabledAlpha(enabled)
            .clickable(enabled = enabled) { onClick() }
            .innerPadding(vertical = false),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            iconContent != null -> {
                Box(
                    modifier = Modifier
                        .size(SaltTheme.dimens.itemIcon)
                        .padding(iconPaddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }
                Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
            }

            iconPainter != null -> {
                Image(
                    modifier = Modifier
                        .size(SaltTheme.dimens.itemIcon)
                        .padding(iconPaddingValues),
                    painter = iconPainter,
                    contentDescription = null,
                    colorFilter = iconColor?.let { ColorFilter.tint(it) } // 可改成 null 保留彩色
                )
                Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
            }
        }

        JustifiedRow(
            startContent = {
                Column {
                    Text(
                        text = text,
                        color = textColor
                    )
                    sub?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sub,
                            color = subColor,
                            style = SaltTheme.textStyles.sub
                        )
                    }
                    subContent?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        subContent()
                    }
                }
            },
            endContent = { },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .innerPadding(horizontal = false),
            verticalAlignment = Alignment.CenterVertically,
            spaceBetween = 0.dp
        )

        iconEnd?.let {
            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))
            iconEnd()
        }
    }
}