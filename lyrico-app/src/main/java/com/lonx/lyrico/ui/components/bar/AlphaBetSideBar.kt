package com.lonx.lyrico.ui.components.bar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.viewmodel.SortOrder
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AlphabetSideBar(
    sections: List<String>,
    onSectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var componentHeight by remember { mutableIntStateOf(0) }
    var currentSection by remember { mutableStateOf<String?>(null) }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    fun getSectionIndex(offsetY: Float): Int {
        if (componentHeight == 0 || sections.isEmpty()) return -1
        val step = componentHeight.toFloat() / sections.size
        return (offsetY / step).toInt().coerceIn(0, sections.lastIndex)
    }

    fun updateSelection(index: Int) {
        if (index != -1) {
            val section = sections[index]
            currentSection = section
            if (index != lastSelectedIndex) {
                lastSelectedIndex = index
                onSectionSelected(section)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        AnimatedVisibility(
            visible = currentSection != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(50.dp)
                    .background(
                        color = MiuixTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentSection ?: "",
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .width(24.dp)
                .onGloballyPositioned { componentHeight = it.size.height }
                .pointerInput(sections) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val index = getSectionIndex(offset.y)
                            updateSelection(index)
                        },
                        onDragEnd = {
                            currentSection = null
                            lastSelectedIndex = -1
                        },
                        onDragCancel = {
                            currentSection = null
                            lastSelectedIndex = -1
                        }
                    ) { change, _ ->
                        change.consume()
                        val index = getSectionIndex(change.position.y)
                        updateSelection(index)
                    }
                }
                .pointerInput(sections) {
                    detectTapGestures(
                        onPress = { offset ->
                            val index = getSectionIndex(offset.y)
                            updateSelection(index)
                            tryAwaitRelease()
                            currentSection = null
                            lastSelectedIndex = -1
                        },
                        onTap = {}
                    )
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            sections.forEach { section ->
                Text(
                    text = section,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                    color = if (currentSection == section) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

fun findScrollIndex(
    section: String,
    sectionIndexMap: Map<String, Int>,
    order: SortOrder
): Int {
    if (sectionIndexMap.isEmpty()) return 0
    sectionIndexMap[section]?.let { return it }
    val keys = sectionIndexMap.keys.sorted()

    return if (order == SortOrder.ASC) {
        keys.firstOrNull { it >= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.last()]!!
    } else {
        keys.lastOrNull { it <= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.first()]!!
    }
}
