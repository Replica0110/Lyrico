package com.lonx.lyrico

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lonx.lyrico.App.Companion.ACTION_EDIT_TAG
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.ui.components.update.UpdateDialog
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.ui.theme.LyricoTheme
import com.lonx.lyrico.ui.theme.UiEngine
import com.moriafly.salt.ui.SaltTheme
import com.lonx.lyrico.utils.UpdateManager
import com.lonx.lyrico.viewmodel.SongListViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

open class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<Uri?>(null)
    private var pendingExternalUri: Uri? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "通知权限未授予，可能无法接收通知",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "音频访问权限未授予，将无法扫描和管理本地音频文件",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val externalAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val uri = pendingExternalUri
            pendingExternalUri = null

            if (uri != null) {
                externalUri = uri
            }

            if (!isGranted) {
                Toast.makeText(
                    this,
                    "未授予音频读取权限，将尝试使用外部应用提供的临时授权",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    private val songListViewModel: SongListViewModel by viewModel()
    private val settingsRepository: SettingsRepository by inject()
    private val libraryIndexRepository: LibraryIndexRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        if (externalUri == null) {
            songListViewModel.checkForUpdate()
        }
        requestNotificationPermissionIfNeeded()
        requestAudioPermissionIfNeeded()
        lifecycleScope.launch {
            libraryIndexRepository.ensureIndexesCurrent()
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
            val uiEngine by settingsRepository.uiEngine.collectAsStateWithLifecycle(
                initialValue = UiEngine.SaltUI
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
                uiEngine = uiEngine,
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

                val appContent: @Composable () -> Unit = {
                    LyricoApp(externalUri = externalUri)

                    updateState.releaseInfo?.let { releaseInfo ->
                        UpdateDialog(
                            show = true,
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

                if (uiEngine == UiEngine.Miuix) {
                    MiuixScaffold(
                        popupHost = { MiuixPopupHost() },
                        containerColor = MiuixTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        appContent()
                    }
                } else {
                    MaterialScaffold(
                        containerColor = SaltTheme.colors.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        appContent()
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
                handleExternalUri(intent.data, intent.flags)
            }

            Intent.ACTION_SEND -> {
                handleExternalUri(
                    intent.getParcelableExtra(Intent.EXTRA_STREAM),
                    intent.flags
                )
            }

            ACTION_EDIT_TAG -> {
                handleExternalUri(intent.data, intent.flags)
            }
        }
    }

    private fun handleExternalUri(uri: Uri?, intentFlags: Int) {
        if (uri == null) return

        if (needsExternalAudioReadPermission(uri, intentFlags)) {
            pendingExternalUri = uri
            requestExternalAudioReadPermission()
        } else {
            externalUri = uri
        }
    }

    private fun needsExternalAudioReadPermission(uri: Uri, intentFlags: Int): Boolean {
        val permission = externalAudioReadPermission() ?: return false
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val readGrant = intentFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        if (uri.scheme == "content" && uri.authority != "media") {
            return !readGrant
        }

        return !readGrant
    }

    private fun requestExternalAudioReadPermission() {
        val permission = externalAudioReadPermission()
        if (permission == null) {
            pendingExternalUri?.let { externalUri = it }
            pendingExternalUri = null
            return
        }

        externalAudioPermissionLauncher.launch(permission)
    }

    private fun externalAudioReadPermission(): String? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_AUDIO
            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_DENIED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = externalAudioReadPermission() ?: return
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(permission)
        }
    }

    private fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
