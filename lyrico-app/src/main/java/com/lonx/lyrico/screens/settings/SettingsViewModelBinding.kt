package com.lonx.lyrico.screens.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lonx.lyrico.viewmodel.SettingsEvent
import com.lonx.lyrico.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/** Only the visible settings destination consumes its operation messages. */
@Composable
internal fun settingsViewModelWithEvents(): SettingsViewModel {
    val viewModel: SettingsViewModel = koinViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, context, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SettingsEvent.ShowToast -> Toast.makeText(
                        context,
                        event.message.asString(context),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
    return viewModel
}
