package com.lonx.lyrico.ui.components.pro

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.ui.components.dialog.YesNoDialog
import java.io.IOException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Both entry points share confirmation, persistence and failure handling. */
@Composable
internal fun ProDeactivationDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDisabled: () -> Unit = {},
) {
    val settings: SettingsRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    YesNoDialog(
        show = show,
        title = stringResource(R.string.pro_disable),
        summary = stringResource(R.string.pro_disable_confirmation),
        confirmText = stringResource(R.string.pro_disable),
        confirmEnabled = !saving,
        onDismissRequest = { if (!saving) onDismissRequest() },
        onConfirm = {
            if (!saving) {
                saving = true
                scope.launch {
                    try {
                        settings.deactivatePro()
                        onDismissRequest()
                        onDisabled()
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pro_deactivation_failed, Toast.LENGTH_SHORT).show()
                    } finally {
                        saving = false
                    }
                }
            }
        },
    )
}
