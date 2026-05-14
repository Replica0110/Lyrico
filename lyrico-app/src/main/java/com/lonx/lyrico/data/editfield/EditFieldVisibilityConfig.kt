package com.lonx.lyrico.data.editfield

data class EditFieldVisibilityConfig(
    val overrides: Map<String, Boolean> = emptyMap(),
) {

    fun isGroupSwitchChecked(group: EditFieldGroupDefinition): Boolean {
        return overrides[group.code] ?: group.defaultVisible
    }

    fun isFieldSwitchChecked(field: EditFieldDefinition): Boolean {
        return overrides[field.code] ?: field.defaultVisible
    }

    fun isFieldActuallyVisible(field: EditFieldDefinition): Boolean {
        val group = EditFieldRegistry.groupMap[field.groupCode] ?: return false
        return isGroupSwitchChecked(group) && isFieldSwitchChecked(field)
    }

    fun isFieldVisibleInScene(
        field: EditFieldDefinition,
        scene: EditFieldScene,
    ): Boolean {
        return field.scope.supports(scene) && isFieldActuallyVisible(field)
    }

    fun visibleGroupsForScene(scene: EditFieldScene): List<VisibleEditFieldGroup> {
        return EditFieldRegistry.groups
            .sortedBy { it.order }
            .mapNotNull { group ->
                if (!isGroupSwitchChecked(group)) {
                    return@mapNotNull null
                }

                val sceneVisibleFields = EditFieldRegistry.fieldsOf(group.code)
                    .filter { isFieldVisibleInScene(it, scene) }
                    .sortedBy { it.order }

                if (sceneVisibleFields.isEmpty()) {
                    return@mapNotNull null
                }

                VisibleEditFieldGroup(
                    group = group,
                    fields = sceneVisibleFields,
                )
            }
    }

    fun withOverride(code: String, visible: Boolean): EditFieldVisibilityConfig {
        val next = overrides.toMutableMap()
        next[code] = visible
        return copy(overrides = next)
    }

    fun withoutOverride(code: String): EditFieldVisibilityConfig {
        val next = overrides.toMutableMap()
        next.remove(code)
        return copy(overrides = next)
    }
}
