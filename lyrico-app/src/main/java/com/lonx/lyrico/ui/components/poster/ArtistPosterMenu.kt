package com.lonx.lyrico.ui.components.poster

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.audiotag.model.AudioPicture
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.domain.poster.ArtistPictureEntry
import com.lonx.lyrico.domain.poster.ArtistPosterGrouping
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 「艺术家海报」的两个面板（操作菜单、目标选择）以及它们的选中状态。
 *
 * 菜单里有两类操作，目标不一样：
 * - 「从本地选择艺术家海报」按**归属**生效：一个艺术家只保留一张，选图即替换；
 * - 「移除 / 保存 / 裁剪」按**图片**生效：标签里已经存在几张就显示几张，不做隐藏。
 */
@Stable
class ArtistPosterMenuState {
    var showOptions by mutableStateOf(false)
        private set

    /** 非 null 时显示选择面板，并记录这次选择是为了什么。 */
    internal var pickerRequest by mutableStateOf<PosterPickerRequest?>(null)
        private set

    /** 直接点某张海报打开菜单时记住它，省得再让用户选一次。 */
    private var activePicture by mutableStateOf<AudioPicture?>(null)

    /**
     * 打开操作菜单。
     *
     * [entry] 是用户点的那张海报；从悬浮菜单按钮进来时传 `null`，此时目标退回到当前
     * 正在看的那一页。注意关闭菜单**不能**清空它——菜单项是先关面板再执行动作的。
     */
    fun open(entry: ArtistPictureEntry? = null) {
        activePicture = entry?.picture
        showOptions = true
    }

    fun closeOptions() {
        showOptions = false
    }

    internal fun ask(request: PosterPickerRequest) {
        pickerRequest = request
    }

    internal fun dismissPicker() {
        pickerRequest = null
    }

    /**
     * 用户点开菜单时所在的那张海报；它已经不在列表里（或没点过）时返回 `null`。
     * 按实例比对：列表增删不会影响身份，也不需要引入下标。
     */
    internal fun activeEntry(entries: List<ArtistPictureEntry>): ArtistPictureEntry? {
        val picture = activePicture ?: return null
        return entries.firstOrNull { it.picture === picture }
    }
}

@Composable
fun rememberArtistPosterMenuState(): ArtistPosterMenuState = remember { ArtistPosterMenuState() }

/** 打开操作菜单后要做的事，都需要先确定作用目标。 */
internal sealed interface PosterPickerRequest {
    /** 「从本地选择艺术家海报」：先确定属于哪个艺术家，再选图片。 */
    data object SetPoster : PosterPickerRequest

    /** 在已有图片里挑一张。 */
    data object Remove : PosterPickerRequest
    data object Export : PosterPickerRequest
    data object Crop : PosterPickerRequest

    /** 描述对不上的海报重新指定归属。 */
    data class Reassign(val target: AudioPicture) : PosterPickerRequest

    /** 需要用户选艺术家（而不是选已有图片）。 */
    val picksArtist: Boolean get() = this is SetPoster || this is Reassign
}

/**
 * 渲染「艺术家海报」的操作菜单与选择面板。
 *
 * @param artists 艺术家字段拆分出的艺术家，用于确定海报归属
 * @param entries 标签里当前的艺术家图片，全部都会显示出来
 * @param currentEntry 当前正在看的那一页对应的内嵌图片，菜单默认就作用于它
 * @param currentPageIsArtistSlot 当前页是不是一个艺术家海报位（否则是封面页）
 * @param onSetPoster 用户选好艺术家之后，由调用方打开系统图片选择器
 */
@Composable
fun ArtistPosterMenu(
    state: ArtistPosterMenuState,
    artists: List<String>,
    entries: List<ArtistPictureEntry>,
    currentEntry: ArtistPictureEntry?,
    currentPageIsArtistSlot: Boolean,
    onSetPoster: (artistName: String) -> Unit,
    onRemove: (target: AudioPicture) -> Unit,
    onExport: (target: AudioPicture) -> Unit,
    onCrop: (target: AudioPicture) -> Unit,
    onReassign: (target: AudioPicture, artistName: String) -> Unit,
    onMissingArtist: () -> Unit,
) {
    val untaggedLabel = stringResource(R.string.artist_image_untagged)

    fun runEntryAction(request: PosterPickerRequest, entry: ArtistPictureEntry) {
        when (request) {
            PosterPickerRequest.Remove -> onRemove(entry.picture)
            PosterPickerRequest.Export -> onExport(entry.picture)
            PosterPickerRequest.Crop -> onCrop(entry.picture)
            PosterPickerRequest.SetPoster, is PosterPickerRequest.Reassign -> Unit
        }
    }

    /**
     * 目标依次取：用户点的那张 → 当前这一页 → 唯一的一张。
     * 只有「人在封面页、却从悬浮菜单进来」这种情况才需要用户选一次。
     */
    fun requestEntryAction(request: PosterPickerRequest) {
        val target = state.activeEntry(entries) ?: currentEntry ?: entries.singleOrNull() ?: run {
            if (entries.isEmpty()) return
            state.ask(request)
            return
        }
        runEntryAction(request, target)
    }

    /**
     * 移除/保存/裁剪只对**标签里的内嵌图片**有意义。
     * 站在一个只有外置海报文件夹兜底的艺术家位上时，这些项不显示——否则会去动别的艺术家的图。
     */
    val canActOnEmbedded = if (currentPageIsArtistSlot) currentEntry != null else entries.isNotEmpty()

    fun chooseLocalImage() {
        when {
            // 艺术家字段空着就没法归属，提示用户先填，而不是写入一张没有归属的海报
            artists.isEmpty() -> onMissingArtist()
            artists.size > 1 -> state.ask(PosterPickerRequest.SetPoster)
            else -> onSetPoster(artists.first())
        }
    }

    fun reassign(target: AudioPicture) {
        if (artists.isEmpty()) {
            onMissingArtist()
            return
        }
        state.ask(PosterPickerRequest.Reassign(target))
    }

    fun onArtistPicked(artistName: String) {
        when (val request = state.pickerRequest) {
            PosterPickerRequest.SetPoster -> onSetPoster(artistName)
            is PosterPickerRequest.Reassign -> onReassign(request.target, artistName)
            else -> Unit
        }
        state.dismissPicker()
    }

    fun onEntryPicked(entry: ArtistPictureEntry) {
        val request = state.pickerRequest
        state.dismissPicker()
        if (request != null) runEntryAction(request, entry)
    }

    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
            show = state.showOptions,
            label = stringResource(R.string.label_artist_image_options),
            onDismissRequest = state::closeOptions,
        ) {
            com.lonx.lyrico.ui.components.base.ItemMenuAction(
                title = stringResource(R.string.label_change_artist_image), icon = LucideIcons.ImageAdd,
                onClick = { state.closeOptions(); chooseLocalImage() })
            if (canActOnEmbedded) {
                com.lonx.lyrico.ui.components.base.ItemMenuAction(
                    title = stringResource(R.string.label_remove_artist_image), icon = LucideIcons.Trash,
                    tone = PreferenceIconTone.Danger,
                    onClick = { state.closeOptions(); requestEntryAction(PosterPickerRequest.Remove) })
                com.lonx.lyrico.ui.components.base.ItemMenuAction(
                    title = stringResource(R.string.label_save_artist_image), icon = LucideIcons.Download,
                    onClick = { state.closeOptions(); requestEntryAction(PosterPickerRequest.Export) })
                com.lonx.lyrico.ui.components.base.ItemMenuAction(
                    title = stringResource(R.string.label_crop_artist_image), icon = LucideIcons.Crop,
                    onClick = { state.closeOptions(); requestEntryAction(PosterPickerRequest.Crop) })
            }
            currentEntry?.let { entry ->
                com.lonx.lyrico.ui.components.base.ItemMenuAction(
                    title = stringResource(R.string.label_reassign_artist_image, entry.displayName(untaggedLabel)),
                    icon = LucideIcons.UserSwitch,
                    onClick = { state.closeOptions(); reassign(entry.picture) })
            }
        }
        val request = state.pickerRequest
        com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
            show = request != null,
            label = stringResource(if (request?.picksArtist == true) R.string.label_select_artist
                else R.string.label_select_artist_image),
            onDismissRequest = state::dismissPicker,
        ) {
            if (request?.picksArtist == true) artists.forEach { name ->
                val count = ArtistPosterGrouping.countFor(entries, name)
                com.lonx.lyrico.ui.components.base.ItemMenuAction(
                    title = name, icon = LucideIcons.UserSwitch,
                    summary = when {
                        count > 1 -> stringResource(R.string.label_artist_image_count, count)
                        count == 1 -> stringResource(R.string.label_artist_image_exists)
                        else -> null
                    }, onClick = { onArtistPicked(name) })
            } else ArtistPicturePickerItems(entries, untaggedLabel, ::onEntryPicked)
        }
        return
    }

    WindowBottomSheet(
        show = state.showOptions,
        enableNestedScroll = false,
        title = stringResource(R.string.label_artist_image_options),
        onDismissRequest = { state.closeOptions() }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) {
                // 一个艺术家只有一张海报，所以「从本地选择」同时就是新增和替换
                ArrowPreference(
                    startAction = { PreferenceIcon(LucideIcons.ImageAdd, PreferenceIconTone.Appearance) },
                    title = stringResource(R.string.label_change_artist_image),
                    onClick = {
                        state.closeOptions()
                        chooseLocalImage()
                    }
                )
                if (canActOnEmbedded) {
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Trash, PreferenceIconTone.Danger) },
                        title = stringResource(R.string.label_remove_artist_image),
                        onClick = {
                            state.closeOptions()
                            requestEntryAction(PosterPickerRequest.Remove)
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Download, PreferenceIconTone.Tools) },
                        title = stringResource(R.string.label_save_artist_image),
                        onClick = {
                            state.closeOptions()
                            requestEntryAction(PosterPickerRequest.Export)
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Crop, PreferenceIconTone.Library) },
                        title = stringResource(R.string.label_crop_artist_image),
                        onClick = {
                            state.closeOptions()
                            requestEntryAction(PosterPickerRequest.Crop)
                        }
                    )
                }
            }

            // 重新绑定：描述对不上（空的、或对不上任何艺术家）要能绑，已经对上的也要能改。
            // 作用于当前这一页的海报，所以在封面页打开菜单时不显示。
            if (currentEntry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.UserSwitch, PreferenceIconTone.Lyrics) },
                        title = stringResource(
                            R.string.label_reassign_artist_image,
                            currentEntry.displayName(untaggedLabel)
                        ),
                        onClick = {
                            state.closeOptions()
                            reassign(currentEntry.picture)
                        }
                    )
                }
            }
        }
    }

    // 选择目标：选艺术家（设置/重挂）或选具体某张图片（移除/保存/裁剪）
    val pickerRequest = state.pickerRequest
    WindowBottomSheet(
        show = pickerRequest != null,
        enableNestedScroll = false,
        title = stringResource(
            if (pickerRequest?.picksArtist == true) R.string.label_select_artist
            else R.string.label_select_artist_image
        ),
        onDismissRequest = { state.dismissPicker() }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) {
                if (pickerRequest?.picksArtist == true) {
                    artists.forEach { artistName ->
                        // 一个艺术家只有一张海报，所以这里要回答的是「选了它会替换还是新增」，
                        // 而不是报一个 0/1 的数字
                        val posterCount = ArtistPosterGrouping.countFor(entries, artistName)
                        ArrowPreference(
                            title = artistName,
                            summary = when {
                                posterCount > 1 ->
                                    stringResource(R.string.label_artist_image_count, posterCount)
                                posterCount == 1 ->
                                    stringResource(R.string.label_artist_image_exists)
                                else -> null
                            },
                            onClick = { onArtistPicked(artistName) }
                        )
                    }
                } else {
                    ArtistPicturePickerItems(
                        entries = entries,
                        untaggedLabel = untaggedLabel,
                        onPick = { onEntryPicked(it) }
                    )
                }
            }
        }
    }
}

/**
 * 选择具体图片的列表。
 *
 * 正常情况下一个艺术家只有一张，直接显示艺术家名即可；标签里若残留了同一艺术家的多张
 * （早期版本允许追加），用 `(2/3)` 这样的序号把它们区分开，而不是把多余的藏起来。
 */
@Composable
private fun ArtistPicturePickerItems(
    entries: List<ArtistPictureEntry>,
    untaggedLabel: String,
    onPick: (ArtistPictureEntry) -> Unit,
) {
    val totals = entries.groupingBy { it.ownerKey }.eachCount()
    val seen = mutableMapOf<String, Int>()

    entries.forEach { entry ->
        val total = totals[entry.ownerKey] ?: 1
        val position = (seen[entry.ownerKey] ?: 0) + 1
        seen[entry.ownerKey] = position
        val name = entry.displayName(untaggedLabel)

        val title = if (total > 1) {
                stringResource(R.string.label_artist_image_duplicate, name, position, total)
            } else {
                name
            }
        if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
            com.lonx.lyrico.ui.components.base.ItemMenuAction(
                title = title, icon = LucideIcons.Image, onClick = { onPick(entry) })
        } else ArrowPreference(
            title = title,
            onClick = { onPick(entry) }
        )
    }
}
