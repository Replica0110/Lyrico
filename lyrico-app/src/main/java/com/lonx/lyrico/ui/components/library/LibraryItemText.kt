package com.lonx.lyrico.ui.components.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.data.model.UiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared text semantics, rendered with the active library's typography and palette. */
@Composable
internal fun LibraryItemText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = MiuixTheme.textStyles.main,
) = LibraryItemText(AnnotatedString(text), modifier, color, fontSize, fontWeight, textAlign, maxLines, overflow, style)

@Composable
internal fun LibraryItemText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = MiuixTheme.textStyles.main,
) {
    if (LocalLibraryUiEngine.current == UiEngine.SALT) {
        val miuixColors = MiuixTheme.colorScheme
        val saltColors = SaltTheme.colors
        // Tiny cover badges are graphic labels, rather than regular list typography.
        val fixedLabel = fontSize != TextUnit.Unspecified && fontSize <= 10.sp
        val sourceSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize
        val secondary = sourceSize != TextUnit.Unspecified && sourceSize <= 13.sp
        val saltStyle = when {
            fixedLabel -> SaltTheme.textStyles.sub.copy(fontSize = fontSize)
            secondary -> SaltTheme.textStyles.sub
            else -> SaltTheme.textStyles.main
        }
        val saltColor = when {
            color == Color.Unspecified -> if (secondary) saltColors.subText else saltColors.text
            color == miuixColors.primary -> saltColors.highlight
            color == miuixColors.onSurfaceContainerVariant -> saltColors.subText
            color == miuixColors.onSurface -> saltColors.text
            color == miuixColors.error -> saltColors.error
            else -> color
        }
        val saltText = if (text.spanStyles.any { it.item.color == miuixColors.primary }) {
            buildAnnotatedString {
                append(text)
                text.spanStyles.filter { it.item.color == miuixColors.primary }.forEach {
                    addStyle(it.item.copy(color = saltColors.highlight), it.start, it.end)
                }
            }
        } else text
        com.moriafly.salt.ui.Text(
            saltText, modifier, color = saltColor,
            fontWeight = if (secondary && !fixedLabel) null else fontWeight,
            textAlign = textAlign,
            maxLines = maxLines, overflow = overflow, style = saltStyle,
        )
    } else top.yukonga.miuix.kmp.basic.Text(
        text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        textAlign = textAlign, maxLines = maxLines, overflow = overflow, style = style,
    )
}
