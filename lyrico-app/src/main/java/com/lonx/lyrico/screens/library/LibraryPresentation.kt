package com.lonx.lyrico.screens.library

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class LibraryMenuAction(
    val title: String,
    val selected: Boolean = false,
    /** 次要说明（如排序的升序/降序），由菜单以原生副文本显示，不拼进标题。 */
    val summary: String? = null,
    val onClick: () -> Unit,
)

internal data class LibrarySelectionState(
    val title: String,
    val allSelected: Boolean,
    val onClose: () -> Unit,
    val onToggleAll: () -> Unit,
)

@Composable
internal fun LibraryTopBarTransition(
    selection: LibrarySelectionState?,
    alignment: Alignment,
    content: @Composable (LibrarySelectionState?) -> Unit,
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
