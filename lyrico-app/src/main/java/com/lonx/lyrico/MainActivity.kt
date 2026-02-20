package com.lonx.lyrico

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.ui.dialog.UpdateDialog
import com.lonx.lyrico.ui.theme.LyricoTheme
import com.lonx.lyrico.utils.PermissionUtil
import com.lonx.lyrico.utils.UpdateManager
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.ext.edgeToEdge
import com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory
import com.moriafly.salt.ui.util.WindowUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

open class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<Uri?>(null)

    @JvmField
    protected var hasPermission = false
    private val songListViewModel: SongListViewModel by inject()
    private val settingsRepository: SettingsRepository by inject()

    @OptIn(UnstableSaltUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        edgeToEdge()
        super.onCreate(savedInstanceState)
        songListViewModel.onStart()
        // 解析启动时的 Intent
        handleIntent(intent)
        hasPermission = PermissionUtil.hasNecessaryPermission(this)
        if (!hasPermission) {

            XXPermissions.with(this)
                // 申请多个权限
                .permission(PermissionLists.getWriteExternalStoragePermission())

                .request(object : OnPermissionCallback {

                    override fun onResult(
                        grantedList: MutableList<IPermission>,
                        deniedList: MutableList<IPermission>
                    ) {
                        val allGranted = deniedList.isEmpty()
                        if (!allGranted) {
                            // 判断请求失败的权限是否被用户勾选了不再询问的选项
                            Toast.makeText(this@MainActivity, "已拒绝权限", Toast.LENGTH_SHORT)
                                .show()
                            return
                        }

                        hasPermission = true
                        // Trigger a scan after permission is granted, with a small delay
                        lifecycleScope.launch {
                            delay(500) // Delay to allow MediaStore to update
                            songListViewModel.refreshSongs()
                        }
                    }

                })
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.AUTO
            )
            val updateManager: UpdateManager = koinInject()
            val updateState by updateManager.state.collectAsState()
            LyricoTheme(themeMode = themeMode) {
                val isDarkTheme = when (themeMode) {
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

                SideEffect {
                    WindowUtil.setStatusBarForegroundColor(
                        window,
                        if (isDarkTheme) WindowUtil.BarColor.White else WindowUtil.BarColor.Black
                    )
                }

                CompositionLocalProvider(
                    LocalOverscrollFactory provides CupertinoOverscrollEffectFactory()
                ) {
                    LyricoApp(externalUri = if (hasPermission) externalUri else null)
                    if (updateState.updateDTO != null) {
                        UpdateDialog(
                            versionName = updateState.updateDTO!!.versionName,
                            onConfirm =  {
                                openBrowser(this@MainActivity, updateState.updateDTO!!.url)
                                updateManager.resetUpdateState()
                            },
                            onDismissRequest = {
                                updateManager.resetUpdateState()
                            },
                            releaseNote = updateState.updateDTO!!.releaseNotes,
                        )
                    }
                    if (updateState.info != null) {
                        Toast.makeText(this, updateState.info, Toast.LENGTH_SHORT).show()
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
        if (intent?.action == Intent.ACTION_VIEW) {
            externalUri = intent.data
        }
    }

    private fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}