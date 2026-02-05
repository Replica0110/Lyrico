package com.lonx.lyrico.plugin

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PluginInfo(
    /** 插件唯一 ID（全局唯一，反向 DNS 强烈推荐） */
    val id: String,

    /** 插件显示名称 */
    val name: String,

    /** 作者 / 组织 */
    val author: String,

    /** 插件版本（人类可读） */
    val versionName: String,

    /** 插件版本号（机器可比较） */
    val versionCode: Long,

    /** 插件简介 */
    val description: String,

    /** 插件支持的能力 */
    val capabilities: List<String>,

    /** 插件 API 版本（用于协议兼容） */
    val apiVersion: Int
) : Parcelable