package com.lonx.lyrico.data.editfield

import androidx.annotation.StringRes
import com.lonx.lyrico.data.metadata.MetadataFieldTarget

/** 分组仅作元数据；所有页面使用配置中的字段顺序。 */
data class EditFieldDefinition(
    val code: String,
    val groupCode: String,
    @field:StringRes val titleRes: Int,
    val defaultVisible: Boolean = true,
    val order: Int,
    val scope: EditFieldScope = EditFieldScope.Both,
    val kind: EditFieldKind = EditFieldKind.Text,
    /** 可使用文本查表渲染；增益字段仍必须收在同一个 ReplayGain 组件内。 */
    val simpleTextInput: Boolean = kind == EditFieldKind.Text || kind == EditFieldKind.Date ||
        kind == EditFieldKind.PersonList || kind == EditFieldKind.ReplayGain,
    /** 对应的元数据字段类型；自定义标签为 null。 */
    val target: MetadataFieldTarget? = null,
    /** 是否为用户添加的自定义标签。 */
    val custom: Boolean = false,
)
