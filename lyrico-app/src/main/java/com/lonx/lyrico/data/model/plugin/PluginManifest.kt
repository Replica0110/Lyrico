package com.lonx.lyrico.data.model.plugin

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val author: String = "",
    val description: String = "",
    val apiVersion: Int,
    val entry: String = "source.js",
    val includeDirs: List<String> = emptyList(),
    val icon: String? = null,
    val capabilities: Set<PluginCapability> = emptySet(),
    val requiredHostApis: Set<String> = emptySet(),
    val configFields: List<PluginConfigField> = emptyList(),
    val metadataFields: List<PluginMetadataField> = emptyList()
)

@Serializable
enum class PluginCapability {
    @SerialName("searchSongs")
    SEARCH_SONGS,
    @SerialName("getLyrics")
    GET_LYRICS,
    @SerialName("searchCovers")
    SEARCH_COVERS
}

@Serializable
data class PluginConfigField(
    val key: String,
    val title: String,
    val summary: String? = null,
    val group: String = "",
    val type: PluginConfigFieldType,
    val required: Boolean = false,
    val defaultValue: String = "",
    val options: List<PluginConfigOption> = emptyList(),
    val dependency: PluginConfigDependency? = null
)

@Serializable
enum class PluginConfigFieldType {
    @SerialName("text")
    TEXT,
    @SerialName("password")
    PASSWORD,
    @SerialName("number")
    NUMBER,
    @SerialName("switch")
    SWITCH,
    @SerialName("dropdown")
    DROPDOWN,
    @SerialName("textarea")
    TEXTAREA,
    @SerialName("markdown")
    MARKDOWN
}

@Serializable
data class PluginConfigOption(
    val value: String,
    val label: String,
    val summary: String = ""
)

@Serializable(with = PluginConfigDependencySerializer::class)
sealed interface PluginConfigDependency {
    @Serializable
    @SerialName("match")
    data class Match(
        val key: String,
        val value: String
    ) : PluginConfigDependency

    @Serializable
    @SerialName("and")
    data class And(
        val conditions: List<PluginConfigDependency>
    ) : PluginConfigDependency

    @Serializable
    @SerialName("or")
    data class Or(
        val conditions: List<PluginConfigDependency>
    ) : PluginConfigDependency

    @Serializable
    @SerialName("not")
    data class Not(
        val condition: PluginConfigDependency
    ) : PluginConfigDependency
}

object PluginConfigDependencySerializer : KSerializer<PluginConfigDependency> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PluginConfigDependency")

    override fun serialize(encoder: Encoder, value: PluginConfigDependency) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        val (discriminator, inner) = when (value) {
            is PluginConfigDependency.Match -> "match" to json.encodeToJsonElement(
                PluginConfigDependency.Match.serializer(), value
            )
            is PluginConfigDependency.And -> "and" to json.encodeToJsonElement(
                PluginConfigDependency.And.serializer(), value
            )
            is PluginConfigDependency.Or -> "or" to json.encodeToJsonElement(
                PluginConfigDependency.Or.serializer(), value
            )
            is PluginConfigDependency.Not -> "not" to json.encodeToJsonElement(
                PluginConfigDependency.Not.serializer(), value
            )
        }
        encoder.encodeJsonElement(JsonObject(mapOf(discriminator to inner)))
    }

    override fun deserialize(decoder: Decoder): PluginConfigDependency {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        val obj = element.jsonObject
        val json = decoder.json
        return when {
            obj.containsKey("match") -> json.decodeFromJsonElement(
                PluginConfigDependency.Match.serializer(), obj.getValue("match")
            )
            obj.containsKey("and") -> json.decodeFromJsonElement(
                PluginConfigDependency.And.serializer(), obj.getValue("and")
            )
            obj.containsKey("or") -> json.decodeFromJsonElement(
                PluginConfigDependency.Or.serializer(), obj.getValue("or")
            )
            obj.containsKey("not") -> json.decodeFromJsonElement(
                PluginConfigDependency.Not.serializer(), obj.getValue("not")
            )
            else -> throw SerializationException(
                "Unknown config dependency discriminator, expected [match, and, or, not], got: ${obj.keys}"
            )
        }
    }
}

@Serializable
data class PluginMetadataField(
    val key: String,
    val title: String,
    val summary: String = "",
    val group: String = "extended",
    val type: PluginMetadataFieldType = PluginMetadataFieldType.TEXT,
    val writeable: Boolean = true,
    val internal: Boolean = false,
    val defaultTarget: PluginMetadataFieldTarget = PluginMetadataFieldTarget.COMMENT,
    val defaultMode: PluginMetadataWriteMode = PluginMetadataWriteMode.DISABLED,
    val defaultCustomTagKey: String = "",
    val targetOptions: List<PluginMetadataFieldTarget> = emptyList()
)

@Serializable
enum class PluginMetadataFieldType {
    @SerialName("text")
    TEXT,
    @SerialName("number")
    NUMBER,
    @SerialName("date")
    DATE,
    @SerialName("lyrics")
    LYRICS,
    @SerialName("cover")
    COVER,
    @SerialName("binary")
    BINARY,
    @SerialName("url")
    URL
}

@Serializable
enum class PluginMetadataWriteMode(
    @field:StringRes val labelRes: Int
) {
    DISABLED(R.string.extra_write_mode_disabled),
    SUPPLEMENT(R.string.extra_write_mode_supplement),
    OVERWRITE(R.string.extra_write_mode_overwrite)
}

@Serializable
enum class PluginMetadataFieldTarget(
    @field:StringRes val labelRes: Int
) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM(R.string.label_album),
    ALBUM_ARTIST(R.string.label_album_artist),
    GENRE(R.string.label_genre),
    DATE(R.string.label_date),
    TRACK_NUMBER(R.string.label_track_number),
    DISC_NUMBER(R.string.label_disc_number),
    COMPOSER(R.string.label_composer),
    LYRICIST(R.string.label_lyricist),
    COMMENT(R.string.label_comment),
    LYRICS(R.string.label_lyrics),
    COVER(R.string.label_cover),
    LANGUAGE(R.string.label_language),
    COPYRIGHT(R.string.label_copyright),
    RATING(R.string.label_rating),
    REPLAY_GAIN_TRACK_GAIN(R.string.label_replaygain_track_gain),
    REPLAY_GAIN_TRACK_PEAK(R.string.label_replaygain_track_peak),
    REPLAY_GAIN_ALBUM_GAIN(R.string.label_replaygain_album_gain),
    REPLAY_GAIN_ALBUM_PEAK(R.string.label_replaygain_album_peak),
    REPLAY_GAIN_REFERENCE_LOUDNESS(R.string.label_replaygain_reference_loudness),
    CUSTOM(R.string.label_custom)
}
