package com.lonx.lyrico.plugin


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class FieldType : Parcelable {
    TEXT,       // 普通文本
    PASSWORD,   // 密码/密文（宿主应隐藏输入）
    NUMBER,     // 数字输入
    SWITCH,     // 开关（Boolean）
    SELECT      // 下拉单选
}

@Parcelize
data class ConfigField(
    val group: String,               // 分组，如 "搜索"
    val key: String,                // 唯一标识符，如 "api_key"
    val label: String,              // UI 显示的名称，如 "搜索 API 密钥"
    val type: FieldType,            // 字段类型
    val hint: String = "",          // 输入框提示文字
    val description: String = "",   // 详细说明文字
    val isRequired: Boolean = false,// 是否必填
    val defaultValue: String = "",  // 默认值
    val options: List<String>? = null // 仅当类型为 SELECT 时有效
) : Parcelable