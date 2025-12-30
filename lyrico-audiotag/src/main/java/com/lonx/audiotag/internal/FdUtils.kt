package com.lonx.audiotag.internal

import android.os.ParcelFileDescriptor

internal object FdUtils {
    /**
     * 获取用于传递给 Native 层的 FD。
     * 使用 dup() 复制 FD，然后 detachFd() 断开 Java 层的所有权，
     * 防止 Java 层关闭 PFD 时影响 Native 层，或者 Native 层关闭时影响 Java 层。
     */
    fun getNativeFd(pfd: ParcelFileDescriptor): Int {
        return pfd.dup().detachFd()
    }
}