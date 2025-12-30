package com.lonx.lyrico.utils

import android.content.Context
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists

object PermissionUtil {

    /**
     * 普通必要权限判断（比如读取外部存储）
     */
    fun hasNecessaryPermission(context: Context): Boolean {
        val perms = PermissionLists.getReadExternalStoragePermission()

        return XXPermissions.isGrantedPermission(context, perms)
    }

}
