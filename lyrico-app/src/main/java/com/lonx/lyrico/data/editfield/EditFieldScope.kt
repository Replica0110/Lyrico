package com.lonx.lyrico.data.editfield

import com.lonx.lyrico.R

enum class EditFieldScope {
    SingleEdit,
    BatchEdit,
    Both;

    fun supports(scene: EditFieldScene): Boolean {
        return when (this) {
            SingleEdit -> scene == EditFieldScene.SingleEdit
            BatchEdit -> scene == EditFieldScene.BatchEdit
            Both -> true
        }
    }
    fun toStringRes(): Int {
        return when (this) {
            SingleEdit -> R.string.edit_field_scope_single_edit
            BatchEdit -> R.string.edit_field_scope_batch_edit
            Both -> R.string.edit_field_scope_both
        }
    }
}
