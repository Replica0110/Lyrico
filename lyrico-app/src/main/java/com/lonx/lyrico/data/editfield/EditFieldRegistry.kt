package com.lonx.lyrico.data.editfield

import com.lonx.lyrico.R

object EditFieldRegistry {

    const val GROUP_BASIC_INFO = "basic_info"
    const val GROUP_TRACK_DETAILS = "track_details"
    const val GROUP_CREDITS_OTHER = "credits_other"
    const val GROUP_REPLAY_GAIN = "replay_gain"
    const val GROUP_CUSTOM_TAGS = "custom_tags"
    const val GROUP_LYRICS = "lyrics"
    const val GROUP_COVER = "cover"

    val groups: List<EditFieldGroupDefinition> = listOf(
        EditFieldGroupDefinition(
            code = GROUP_BASIC_INFO,
            titleRes = R.string.group_basic_info,
            defaultVisible = true,
            order = 10,
        ),
        EditFieldGroupDefinition(
            code = GROUP_TRACK_DETAILS,
            titleRes = R.string.group_track_details,
            defaultVisible = true,
            order = 20,
        ),
        EditFieldGroupDefinition(
            code = GROUP_CREDITS_OTHER,
            titleRes = R.string.group_credits_other,
            defaultVisible = true,
            order = 30,
        ),
        EditFieldGroupDefinition(
            code = GROUP_REPLAY_GAIN,
            titleRes = R.string.group_replay_gain,
            defaultVisible = true,
            order = 40,
        ),
        EditFieldGroupDefinition(
            code = GROUP_CUSTOM_TAGS,
            titleRes = R.string.group_custom_tags,
            defaultVisible = true,
            order = 45,
        ),
        EditFieldGroupDefinition(
            code = GROUP_LYRICS,
            titleRes = R.string.edit_field_group_lyrics,
            defaultVisible = true,
            order = 50,
        ),
        EditFieldGroupDefinition(
            code = GROUP_COVER,
            titleRes = R.string.edit_field_group_cover,
            defaultVisible = true,
            order = 60,
        ),
    )

    val fields: List<EditFieldDefinition> = listOf(
        EditFieldDefinition(
            code = "basic_info.title",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_title,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "basic_info.artist",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_artists,
            defaultVisible = true,
            order = 20,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "basic_info.album_artist",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_album_artist,
            defaultVisible = true,
            order = 30,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "basic_info.album",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_album,
            defaultVisible = true,
            order = 40,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "basic_info.date",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_date,
            defaultVisible = true,
            order = 50,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "basic_info.language",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_language,
            defaultVisible = true,
            order = 60,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "basic_info.genre",
            groupCode = GROUP_BASIC_INFO,
            titleRes = R.string.label_genre,
            defaultVisible = true,
            order = 70,
            scope = EditFieldScope.Both,
        ),

        EditFieldDefinition(
            code = "track_details.track_number",
            groupCode = GROUP_TRACK_DETAILS,
            titleRes = R.string.label_track_number,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "track_details.disc_number",
            groupCode = GROUP_TRACK_DETAILS,
            titleRes = R.string.label_disc_number,
            defaultVisible = true,
            order = 20,
            scope = EditFieldScope.Both,
        ),

        EditFieldDefinition(
            code = "credits_other.composer",
            groupCode = GROUP_CREDITS_OTHER,
            titleRes = R.string.label_composer,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "credits_other.lyricist",
            groupCode = GROUP_CREDITS_OTHER,
            titleRes = R.string.label_lyricist,
            defaultVisible = true,
            order = 20,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "credits_other.copyright",
            groupCode = GROUP_CREDITS_OTHER,
            titleRes = R.string.label_copyright,
            defaultVisible = false,
            order = 30,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "credits_other.comment",
            groupCode = GROUP_CREDITS_OTHER,
            titleRes = R.string.label_comment,
            defaultVisible = false,
            order = 40,
            scope = EditFieldScope.Both,
        ),

        EditFieldDefinition(
            code = "replay_gain.track_gain",
            groupCode = GROUP_REPLAY_GAIN,
            titleRes = R.string.label_replaygain_track_gain,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "replay_gain.track_peak",
            groupCode = GROUP_REPLAY_GAIN,
            titleRes = R.string.label_replaygain_track_peak,
            defaultVisible = true,
            order = 20,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "replay_gain.album_gain",
            groupCode = GROUP_REPLAY_GAIN,
            titleRes = R.string.label_replaygain_album_gain,
            defaultVisible = true,
            order = 30,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "replay_gain.album_peak",
            groupCode = GROUP_REPLAY_GAIN,
            titleRes = R.string.label_replaygain_album_peak,
            defaultVisible = true,
            order = 40,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "replay_gain.reference_loudness",
            groupCode = GROUP_REPLAY_GAIN,
            titleRes = R.string.label_replaygain_reference_loudness,
            defaultVisible = true,
            order = 50,
            scope = EditFieldScope.Both,
        ),

        EditFieldDefinition(
            code = "custom_tags.custom_tags",
            groupCode = GROUP_CUSTOM_TAGS,
            titleRes = R.string.label_custom_tag,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),

        EditFieldDefinition(
            code = "lyrics.lyrics",
            groupCode = GROUP_LYRICS,
            titleRes = R.string.label_lyrics,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "lyrics.lyrics_offset",
            groupCode = GROUP_LYRICS,
            titleRes = R.string.label_lyrics_offset,
            defaultVisible = true,
            order = 20,
            scope = EditFieldScope.BatchEdit,
        ),

        EditFieldDefinition(
            code = "cover.picture",
            groupCode = GROUP_COVER,
            titleRes = R.string.label_cover,
            defaultVisible = true,
            order = 10,
            scope = EditFieldScope.Both,
        ),
        EditFieldDefinition(
            code = "cover.rating",
            groupCode = GROUP_COVER,
            titleRes = R.string.label_rating,
            defaultVisible = true,
            order = 20,
            scope = EditFieldScope.Both,
        ),
    )

    val groupMap: Map<String, EditFieldGroupDefinition> =
        groups.associateBy { it.code }

    val fieldMap: Map<String, EditFieldDefinition> =
        fields.associateBy { it.code }

    val knownCodes: Set<String> =
        groupMap.keys + fieldMap.keys

    fun fieldsOf(groupCode: String): List<EditFieldDefinition> {
        return fields
            .filter { it.groupCode == groupCode }
            .sortedBy { it.order }
    }

    fun validate() {
        val groupCodes = groups.map { it.code }
        require(groupCodes.size == groupCodes.toSet().size) {
            "Duplicate edit field group code found"
        }

        val fieldCodes = fields.map { it.code }
        require(fieldCodes.size == fieldCodes.toSet().size) {
            "Duplicate edit field code found"
        }

        fields.forEach { field ->
            require(field.groupCode in groupMap) {
                "Unknown groupCode=${field.groupCode} for field=${field.code}"
            }
        }
    }
}
