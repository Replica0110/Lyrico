package com.lonx.lyrico

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.lonx.lyrico.ui.theme.LyricoTheme
import com.lonx.lyrico.utils.PermissionUtil
import androidx.lifecycle.lifecycleScope
import com.lonx.lyrico.viewmodel.SongListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

open class MainActivity : ComponentActivity() {

    @JvmField
    protected var hasPermission = false
    private val songListViewModel: SongListViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermission = PermissionUtil.hasNecessaryPermission(this)
        if (!hasPermission) {

            XXPermissions.with(this)
                // 申请多个权限
                .permission(PermissionLists.getWriteExternalStoragePermission())

                .request(object : OnPermissionCallback {

                    override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                        val allGranted = deniedList.isEmpty()
                        if (!allGranted) {
                            // 判断请求失败的权限是否被用户勾选了不再询问的选项
                            Toast.makeText(this@MainActivity, "已拒绝权限", Toast.LENGTH_SHORT).show()
                            return
                        }
                        
                        hasPermission = true
                        // Trigger a scan after permission is granted, with a small delay
                        lifecycleScope.launch {
                            delay(500) // Delay to allow MediaStore to update
                            songListViewModel.refreshSongs(forceFullScan = true)
                        }
                    }

                })
        }

        enableEdgeToEdge()
        setContent {
            LyricoTheme {
                LyricoApp()
            }
        }
    }
}