package com.lonx.lyrico.ui.components.bar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class SelectionTopBarState(
    val title: String,
    val allSelected: Boolean,
    val onClose: () -> Unit,
    val onToggleAll: () -> Unit,
)

@Composable
internal fun SelectionTopBarTransition(
    selection: SelectionTopBarState?,
    alignment: Alignment,
    content: @Composable (SelectionTopBarState?) -> Unit,
) {
    AnimatedContent(
        modifier = Modifier.height(40.dp),
        targetState = selection,
        contentAlignment = alignment,
        contentKey = { it != null },
        label = "TopBarAnimation",
        transitionSpec = {
            val enter =
                fadeIn(tween(300)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 3 }
            val exit =
                fadeOut(tween(300)) +
                    slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 3 }
            // Preserve the native slot anchor; only the explicit Y transition should move content.
            (enter togetherWith exit).using(null)
        },
    ) {
        content(it)
    }
}
