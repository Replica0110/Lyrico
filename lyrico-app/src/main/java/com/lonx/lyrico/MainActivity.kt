package com.lonx.lyrico

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.lonx.lyrico.App.Companion.ACTION_EDIT_TAG
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.ui.dialog.UpdateDialog
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.ui.theme.LyricoTheme
import com.lonx.lyrico.utils.PermissionUtil
import com.lonx.lyrico.utils.UpdateManager
import com.lonx.lyrico.viewmodel.SongListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

open class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<Uri?>(null)

    @JvmField
    protected var hasPermission = false
    private val songListViewModel: SongListViewModel by inject()
    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        if (externalUri == null) {
            songListViewModel.checkForUpdate()
        }

        hasPermission = PermissionUtil.hasNecessaryPermission(this)
        if (!hasPermission) {
            XXPermissions.with(this)
                .permission(PermissionLists.getWriteExternalStoragePermission())
                .request(object : OnPermissionCallback {
                    override fun onResult(
                        grantedList: MutableList<IPermission>,
                        deniedList: MutableList<IPermission>
                    ) {
                        val allGranted = deniedList.isEmpty()
                        if (!allGranted) {
                            Toast.makeText(this@MainActivity, "已拒绝权限", Toast.LENGTH_SHORT)
                                .show()
                            return
                        }

                        hasPermission = true
                        lifecycleScope.launch {
                            delay(500)
                            songListViewModel.refreshSongs()
                        }
                    }
                })
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.AUTO
            )
            val monetEnable by settingsRepository.monetEnable.collectAsStateWithLifecycle(
                initialValue = false
            )
            val keyColor by settingsRepository.keyColor.collectAsStateWithLifecycle(
                initialValue = KeyColors.first()
            )
            val darkMode = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
            }

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkMode },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced =
                        false // Xiaomi moment, this code must be here
                }

                onDispose {}
            }
            val updateManager: UpdateManager = koinInject()
            val updateState by updateManager.state.collectAsState()
            val context = this

            LyricoTheme(
                colorMode = themeMode,
                monetEnabled = monetEnable,
                keyColor = keyColor.color
            ) {
                LaunchedEffect(Unit) {
                    updateManager.effect.collect { effect ->
                        val message = context.getString(
                            effect.messageRes,
                            *effect.formatArgs.toTypedArray()
                        )
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

                Scaffold(
                    popupHost = { MiuixPopupHost() },
                    containerColor = MiuixTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    LyricoApp(externalUri = if (hasPermission) externalUri else null)

                    updateState.releaseInfo?.let { releaseInfo ->
                        UpdateDialog(
                            versionName = releaseInfo.versionName,
                            onConfirm = {
                                openBrowser(this@MainActivity, releaseInfo.url)
                                updateManager.resetUpdateState()
                            },
                            onDismissRequest = {
                                updateManager.resetUpdateState()
                            },
                            releaseNote = releaseInfo.releaseNotes,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                externalUri = intent.data
            }

            Intent.ACTION_SEND -> {
                externalUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            ACTION_EDIT_TAG -> {
                externalUri = intent.data
            }
        }
    }

    private fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
