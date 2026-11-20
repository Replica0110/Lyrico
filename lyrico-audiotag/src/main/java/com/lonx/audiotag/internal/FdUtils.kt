package com.lonx.audiotag.internal

import android.os.ParcelFileDescriptor

internal object FdUtils {
    /**
     * 复制并分离文件描述符，将副本的所有权交给 Native 层。
     * 原 [pfd] 继续由调用方管理。
     */
    fun duplicateFdForNative(pfd: ParcelFileDescriptor): Int {
        return pfd.dup().detachFd()
    }
}
fun Map<String, List<String>>.toArrayMap(): Map<String, Array<String>> {
    return mapValues { it.value.toTypedArray() }
}
