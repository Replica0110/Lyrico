package com.lonx.lyrico.data.editfield

import com.lonx.lyrico.R
import com.lonx.lyrico.data.metadata.MetadataFieldTarget

/** 内置字段的稳定代码、文案、默认顺序与显隐定义。 */
object EditFieldRegistry {

    const val GROUP_BASIC_INFO = "basic_info"
    const val GROUP_TRACK_DETAILS = "track_details"
    const val GROUP_CREDITS_OTHER = "credits_other"
    const val GROUP_REPLAY_GAIN = "replay_gain"
    const val GROUP_LYRICS = "lyrics"
    const val GROUP_COVER = "cover"

    /** 自定义标签在列表里的归属标记。 */
    const val GROUP_CUSTOM_TAGS = "custom_tags"

    val fields: List<EditFieldDefinition> = listOf(
        EditFieldDefinition(
            code = "picture", groupCode = GROUP_COVER, titleRes = MetadataFieldTarget.COVER.labelRes,
            order = 10, kind = EditFieldKind.Cover, target = MetadataFieldTarget.COVER,
        ),
        EditFieldDefinition(
            code = "rating", groupCode = GROUP_COVER, titleRes = MetadataFieldTarget.RATING.labelRes,
            order = 20, kind = EditFieldKind.Number, target = MetadataFieldTarget.RATING,
        ),
        EditFieldDefinition(
            code = "title", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.TITLE.labelRes,
            order = 30, kind = EditFieldKind.Text, target = MetadataFieldTarget.TITLE,
        ),
        EditFieldDefinition(
            code = "artist", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.ARTIST.labelRes,
            order = 40, kind = EditFieldKind.PersonList, target = MetadataFieldTarget.ARTIST,
        ),
        EditFieldDefinition(
            code = "album_artist", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.ALBUM_ARTIST.labelRes,
            order = 50, kind = EditFieldKind.PersonList, target = MetadataFieldTarget.ALBUM_ARTIST,
        ),
        EditFieldDefinition(
            code = "album", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.ALBUM.labelRes,
            order = 60, kind = EditFieldKind.Text, target = MetadataFieldTarget.ALBUM,
        ),
        EditFieldDefinition(
            code = "date", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.DATE.labelRes,
            order = 70, kind = EditFieldKind.Date, target = MetadataFieldTarget.DATE,
        ),
        EditFieldDefinition(
            code = "language", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.LANGUAGE.labelRes,
            order = 80, kind = EditFieldKind.Text, target = MetadataFieldTarget.LANGUAGE,
        ),
        EditFieldDefinition(
            code = "genre", groupCode = GROUP_BASIC_INFO, titleRes = MetadataFieldTarget.GENRE.labelRes,
            order = 90, kind = EditFieldKind.Text, target = MetadataFieldTarget.GENRE,
        ),
        EditFieldDefinition(
            code = "track_number", groupCode = GROUP_TRACK_DETAILS, titleRes = MetadataFieldTarget.TRACK_NUMBER.labelRes,
            order = 100, kind = EditFieldKind.Number, target = MetadataFieldTarget.TRACK_NUMBER,
        ),
        EditFieldDefinition(
            code = "disc_number", groupCode = GROUP_TRACK_DETAILS, titleRes = MetadataFieldTarget.DISC_NUMBER.labelRes,
            order = 110, kind = EditFieldKind.Number, target = MetadataFieldTarget.DISC_NUMBER,
        ),
        EditFieldDefinition(
            code = "lyricist", groupCode = GROUP_CREDITS_OTHER, titleRes = MetadataFieldTarget.LYRICIST.labelRes,
            order = 120, kind = EditFieldKind.PersonList, target = MetadataFieldTarget.LYRICIST,
        ),
        EditFieldDefinition(
            code = "composer", groupCode = GROUP_CREDITS_OTHER, titleRes = MetadataFieldTarget.COMPOSER.labelRes,
            order = 130, kind = EditFieldKind.PersonList, target = MetadataFieldTarget.COMPOSER,
        ),
        EditFieldDefinition(
            code = "copyright", groupCode = GROUP_CREDITS_OTHER, titleRes = MetadataFieldTarget.COPYRIGHT.labelRes,
            order = 140, kind = EditFieldKind.Text, target = MetadataFieldTarget.COPYRIGHT,
        ),
        EditFieldDefinition(
            code = "comment", groupCode = GROUP_CREDITS_OTHER, titleRes = MetadataFieldTarget.COMMENT.labelRes,
            order = 150, kind = EditFieldKind.Text, target = MetadataFieldTarget.COMMENT,
        ),
        EditFieldDefinition(
            code = "track_gain", groupCode = GROUP_REPLAY_GAIN, titleRes = MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN.labelRes,
            order = 160, kind = EditFieldKind.ReplayGain, target = MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN,
        ),
        EditFieldDefinition(
            code = "track_peak", groupCode = GROUP_REPLAY_GAIN, titleRes = MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK.labelRes,
            order = 170, kind = EditFieldKind.ReplayGain, target = MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK,
        ),
        EditFieldDefinition(
            code = "album_gain", groupCode = GROUP_REPLAY_GAIN, titleRes = MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN.labelRes,
            order = 180, kind = EditFieldKind.ReplayGain, target = MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN,
        ),
        EditFieldDefinition(
            code = "album_peak", groupCode = GROUP_REPLAY_GAIN, titleRes = MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK.labelRes,
            order = 190, kind = EditFieldKind.ReplayGain, target = MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK,
        ),
        EditFieldDefinition(
            code = "reference_loudness", groupCode = GROUP_REPLAY_GAIN, titleRes = MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS.labelRes,
            order = 200, kind = EditFieldKind.ReplayGain, target = MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS,
        ),
        EditFieldDefinition(
            code = "lyrics", groupCode = GROUP_LYRICS, titleRes = MetadataFieldTarget.LYRICS.labelRes,
            order = 210, kind = EditFieldKind.Lyrics, target = MetadataFieldTarget.LYRICS,
        ),
        EditFieldDefinition(
            code = "lyrics_offset", groupCode = GROUP_LYRICS, titleRes = R.string.label_lyrics_offset,
            order = 220, scope = EditFieldScope.BatchEdit, kind = EditFieldKind.Number,
        ),
    )

    val fieldMap: Map<String, EditFieldDefinition> =
        fields.associateBy { it.code }

    /** 内置字段的 code 集合；不包含运行时追加的自定义标签。 */
    val knownCodes: Set<String> = fieldMap.keys

    /** 独立命名空间，避免用户标签名与内置字段代码冲突。 */
    const val CUSTOM_TAG_PREFIX = "tag:"

    fun customTagCode(key: String): String = CUSTOM_TAG_PREFIX + key

    fun isCustomTagCode(code: String): Boolean = code.startsWith(CUSTOM_TAG_PREFIX)

    /** 从自定义标签 code 取回原始标签键名；不是自定义标签时返回 null。 */
    fun customTagKeyOf(code: String): String? =
        code.takeIf { isCustomTagCode(it) }?.removePrefix(CUSTOM_TAG_PREFIX)

    val defaultOrder: List<String> = fields.sortedBy { it.order }.map { it.code }

    fun isStandardField(code: String): Boolean = code in fieldMap

    fun validate() {
        val codes = fields.map { it.code }
        require(codes.size == codes.toSet().size) {
            "Duplicate edit field code found"
        }

        val orders = fields.map { it.order }
        require(orders.size == orders.toSet().size) {
            "Duplicate edit field order found"
        }

        fields.forEach { field ->
            require(!isCustomTagCode(field.code)) {
                "Built-in field code collides with the custom tag prefix: ${field.code}"
            }
        }
    }
}
