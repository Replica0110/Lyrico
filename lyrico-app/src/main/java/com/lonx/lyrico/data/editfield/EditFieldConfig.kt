package com.lonx.lyrico.data.editfield

/**
 * 编辑字段配置：字段顺序与显隐。
 * [order] 为内置字段与自定义标签的混排顺序；[overrides] 只记录用户显式改过的显隐，缺省回落到
 * `defaultVisible`；[customTags] 为已加入列表的自定义标签键。
 */
data class EditFieldConfig(
    val order: List<String> = emptyList(),
    val overrides: Map<String, Boolean> = emptyMap(),
    val customTags: List<String> = emptyList(),
    val componentOverrides: Map<String, Boolean> = emptyMap(),
) {

    /** 自定义标签对应的字段定义。 */
    val customFields: List<EditFieldDefinition> = customTags.map { key ->
        EditFieldDefinition(
            code = EditFieldRegistry.customTagCode(key),
            groupCode = EditFieldRegistry.GROUP_CUSTOM_TAGS,
            titleRes = 0,
            order = Int.MAX_VALUE,
            scope = EditFieldScope.Both,
            kind = EditFieldKind.Custom,
            target = null,
            custom = true,
        )
    }

    /** 内置字段 + 自定义字段，按用户顺序排列。 */
    val allFields: List<EditFieldDefinition> = run {
        val byCode = (EditFieldRegistry.fields + customFields).associateBy { it.code }
        val ordered = order.mapNotNull { byCode[it] }
        val missing = byCode.values.filter { it.code !in order }
            .sortedBy { if (it.custom) Int.MAX_VALUE else it.order }
        (ordered + missing).distinctBy { it.code }
    }

    /** 显式改过的顺序；没有条目时回落到注册表默认顺序。 */
    fun orderedCodes(): List<String> = allFields.map { it.code }

    fun isEnabled(code: String): Boolean {
        overrides[code]?.let { return it }
        EditFieldRegistry.fieldMap[code]?.let { return it.defaultVisible }
        // 自定义标签默认启用：用户是主动把它加进列表的。
        return customFields.any { it.code == code }
    }

    fun isEnabled(field: EditFieldDefinition): Boolean = isEnabled(field.code)

    fun isComponentEnabled(key: String): Boolean = componentOverrides[key] ?: true

    fun isEffectivelyEnabled(field: EditFieldDefinition): Boolean =
        (field.componentKey?.let(::isComponentEnabled) ?: true) && isEnabled(field)

    fun withComponentEnabled(key: String, enabled: Boolean): EditFieldConfig {
        if (allFields.none { it.componentKey == key }) return this
        return copy(componentOverrides = componentOverrides + (key to enabled))
    }

    fun isVisibleInScene(field: EditFieldDefinition, scene: EditFieldScene): Boolean =
        field.scope.supports(scene) && isEffectivelyEnabled(field)

    /** 指定页面下、按用户顺序排列的可见字段。 */
    fun visibleFieldsForScene(scene: EditFieldScene): List<EditFieldDefinition> =
        allFields.toEditFieldBlocks().flatMap { block ->
            block.fields.filter { isVisibleInScene(it, scene) }
        }

    /** 指定页面下可见字段的 code 集合，供编辑页按 code 判断单个字段。 */
    fun visibleFieldCodesForScene(scene: EditFieldScene): Set<String> =
        visibleFieldsForScene(scene).map { it.code }.toSet()

    /** 指定页面下可见的自定义标签键，供编辑页渲染自定义标签区。 */
    fun visibleCustomTagKeys(scene: EditFieldScene): List<String> =
        visibleFieldsForScene(scene)
            .filter { it.custom }
            .mapNotNull { EditFieldRegistry.customTagKeyOf(it.code) }

    fun matchTargets(): List<com.lonx.lyrico.data.metadata.MetadataFieldTarget> =
        allFields.toEditFieldBlocks().flatMap { it.fields }.filter { isEffectivelyEnabled(it) }.mapNotNull { it.target }

    fun fieldItems(): List<EditFieldListItem> =
        allFields.map { field -> EditFieldListItem(field, isEnabled(field)) }

    fun withEnabled(code: String, enabled: Boolean): EditFieldConfig =
        copy(overrides = overrides + (code to enabled))

    fun withOrder(nextOrder: List<String>): EditFieldConfig {
        val known = allFields.map { it.code }.toSet()
        val normalized = nextOrder.filter { it in known }.distinct() +
            allFields.map { it.code }.filterNot { it in nextOrder }
        return copy(order = normalized.distinct())
    }

    /** 主列表移动整个组件，保留组件内顺序和所有隐藏成员。 */
    fun withBlockOrder(keys: List<String>): EditFieldConfig {
        val blocks = allFields.toEditFieldBlocks().associateBy { it.key }
        val orderedKeys = (keys.filter { it in blocks } + blocks.keys).distinct()
        return withOrder(orderedKeys.flatMap { blocks.getValue(it).fields }.map { it.code })
    }

    /** 只修改指定组件内部顺序，不改其他组件的位置、顺序和显隐。 */
    fun withComponentOrder(key: String, codes: List<String>): EditFieldConfig {
        val blocks = allFields.toEditFieldBlocks()
        val component = blocks.firstOrNull { it.key == key && it.isComposite } ?: return this
        val members = component.fields.map { it.code }
        val orderedMembers = (codes.filter { it in members } + members).distinct()
        return withOrder(blocks.flatMap { block ->
            if (block.key == key) orderedMembers else block.fields.map { it.code }
        })
    }

    fun withAddedCustomTag(key: String): EditFieldConfig {
        if (key in customTags) return this
        val code = EditFieldRegistry.customTagCode(key)
        return copy(
            customTags = customTags + key,
            order = orderedCodes() + code,
            overrides = overrides + (code to true),
        )
    }

    fun withRemovedCustomTag(key: String): EditFieldConfig {
        val code = EditFieldRegistry.customTagCode(key)
        return copy(
            customTags = customTags - key,
            order = orderedCodes().filterNot { it == code },
            overrides = overrides - code,
        )
    }
}

/** 「编辑字段」页里的一行。 */
data class EditFieldListItem(
    val field: EditFieldDefinition,
    val enabled: Boolean,
)
