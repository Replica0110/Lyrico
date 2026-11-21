package com.lonx.lyrico.data.batch

import android.os.Parcelable
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.metadata.MetadataWriteMode
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class BatchMatchConfig(
    val targetModes: Map<MetadataFieldTarget, MetadataWriteMode>,
    val concurrency: Int = 3,
    val preferFileName: Boolean = false
) : Parcelable {
    fun restrictedTo(targets: Set<MetadataFieldTarget>): BatchMatchConfig = copy(
        targetModes = targetModes.mapValues { (target, mode) ->
            if (target in targets) mode else MetadataWriteMode.DISABLED
        }
    )
}

object BatchMatchConfigDefaults {
    val BATCH_MATCH_TARGETS: List<MetadataFieldTarget> =
        EditFieldRegistry.fields.mapNotNull { it.target }

    val DEFAULT_ENABLED_TARGETS = setOf(
        MetadataFieldTarget.TITLE,
        MetadataFieldTarget.ARTIST,
        MetadataFieldTarget.ALBUM,
        MetadataFieldTarget.GENRE,
        MetadataFieldTarget.DATE,
        MetadataFieldTarget.TRACK_NUMBER,
        MetadataFieldTarget.LYRICS,
        MetadataFieldTarget.COVER
    )

    val DEFAULT_CONFIG = BatchMatchConfig(
        targetModes = BATCH_MATCH_TARGETS.associateWith { target ->
            if (target in DEFAULT_ENABLED_TARGETS) {
                MetadataWriteMode.SUPPLEMENT
            } else {
                MetadataWriteMode.DISABLED
            }
        },
        concurrency = 3,
        preferFileName = false
    )

}
