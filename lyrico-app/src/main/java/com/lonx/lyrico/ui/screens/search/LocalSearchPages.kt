package com.lonx.lyrico.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.AlbumEntity
import com.lonx.lyrico.data.db.entity.ArtistEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.model.search.LocalLyricSearchResult
import com.lonx.lyrico.data.model.search.LocalSearchField
import com.lonx.lyrico.ui.components.album.AlbumListItem
import com.lonx.lyrico.ui.components.artist.ArtistListItemContent
import com.lonx.lyrico.ui.components.base.SectionHeader
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.ui.components.song.SongListItemContent
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 结果分页：两引擎共用同一套分页与分组逻辑。 */
@Composable
internal fun LocalSearchPages(
    pagerState: androidx.compose.foundation.pager.PagerState,
    searchTabs: List<LocalSearchTab>,
    uiState: LocalSearchUiState,
    searchQuery: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPaddingValues: PaddingValues,
    salt: Boolean,
    onArtistClick: (ArtistEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        LocalSearchResultsPage(
            tab = searchTabs.getOrElse(page) { LocalSearchTab.All },
            uiState = uiState,
            searchQuery = searchQuery,
            isSelectionMode = isSelectionMode,
            selectedSongUris = selectedSongUris,
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            contentPaddingValues = contentPaddingValues,
            salt = salt,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

@Composable
private fun LocalSearchResultsPage(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    searchQuery: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPaddingValues: PaddingValues,
    salt: Boolean,
    onArtistClick: (ArtistEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    val listState = rememberLazyListState()
    val pageSongs = visibleSongsForTab(tab, uiState, searchQuery)
    val hasResults = hasResultsForTab(tab, uiState, searchQuery)

    LazyColumn(
        modifier = (if (salt) Modifier else Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
            .fillMaxHeight(),
        state = listState,
        contentPadding = scaffoldContentPadding(
            paddingValues = contentPaddingValues,
            bottomExtra = 12.dp
        ),
        overscrollEffect = if (salt) rememberOverscrollEffect() else null
    ) {
        if (searchQuery.isNotBlank() && !hasResults) {
            item {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Search,
                    title = stringResource(R.string.search_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (tab == LocalSearchTab.All) {
            FieldSongsSection(
                field = LocalSearchField.TITLE,
                songs = songsMatching(LocalSearchField.TITLE, uiState, searchQuery),
                query = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                pageSongs = pageSongs,
                onSongClick = onSongClick,
                onToggleSelection = onToggleSelection,
                onSwipeSelection = onSwipeSelection,
                onShowSongMenu = onShowSongMenu
            )
            ArtistsSection(
                artists = uiState.artists,
                onArtistClick = onArtistClick
            )
            AlbumsSection(
                albums = uiState.albums,
                onAlbumClick = onAlbumClick
            )
            songResultFields
                .filterNot { it == LocalSearchField.TITLE }
                .forEach { field ->
                    FieldSongsSection(
                        field = field,
                        songs = songsMatching(field, uiState, searchQuery),
                        query = searchQuery,
                        isSelectionMode = isSelectionMode,
                        selectedSongUris = selectedSongUris,
                        swipeSelectionLabel = swipeSelectionLabel,
                        swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                        pageSongs = pageSongs,
                        onSongClick = onSongClick,
                        onToggleSelection = onToggleSelection,
                        onSwipeSelection = onSwipeSelection,
                        onShowSongMenu = onShowSongMenu
                    )
                }
        } else if (tab == LocalSearchTab.Artists) {
            ArtistsSection(artists = uiState.artists, onArtistClick = onArtistClick)
        } else if (tab == LocalSearchTab.Albums) {
            AlbumsSection(albums = uiState.albums, onAlbumClick = onAlbumClick)
        } else {
            tab.field?.let { field ->
                FieldSongsSection(
                    field = field,
                    songs = songsMatching(field, uiState, searchQuery),
                    query = searchQuery,
                    isSelectionMode = isSelectionMode,
                    selectedSongUris = selectedSongUris,
                    swipeSelectionLabel = swipeSelectionLabel,
                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                    pageSongs = pageSongs,
                    onSongClick = onSongClick,
                    onToggleSelection = onToggleSelection,
                    onSwipeSelection = onSwipeSelection,
                    onShowSongMenu = onShowSongMenu
                )
            }
        }

        if (uiState.lyricSearchEnabled && (tab == LocalSearchTab.All || tab == LocalSearchTab.Lyrics)) {
            LyricsSection(
                matches = uiState.lyricMatches,
                query = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                pageSongs = pageSongs,
                onSongClick = onSongClick,
                onToggleSelection = onToggleSelection,
                onSwipeSelection = onSwipeSelection,
                onShowSongMenu = onShowSongMenu
            )
        }
    }
}

internal fun visibleSongsForTab(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    query: String
): List<SongEntity> {
    return when {
        tab == LocalSearchTab.All -> (
            songResultFields.flatMap { songsMatching(it, uiState, query) } +
                uiState.lyricMatches.map { it.song }
            )
            .distinctBy { it.uri }
        tab == LocalSearchTab.Lyrics -> uiState.lyricMatches.map { it.song }
        tab == LocalSearchTab.Albums || tab == LocalSearchTab.Artists -> emptyList()
        tab.field != null -> songsMatching(tab.field, uiState, query)
        else -> emptyList()
    }
}

private fun hasResultsForTab(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    query: String
): Boolean = when {
    tab == LocalSearchTab.All ->
        uiState.songs.isNotEmpty() || uiState.albums.isNotEmpty() ||
            uiState.artists.isNotEmpty() || uiState.lyricMatches.isNotEmpty()
    tab == LocalSearchTab.Artists -> uiState.artists.isNotEmpty()
    tab == LocalSearchTab.Albums -> uiState.albums.isNotEmpty()
    tab == LocalSearchTab.Lyrics -> uiState.lyricMatches.isNotEmpty()
    tab.field != null -> songsMatching(tab.field, uiState, query).isNotEmpty()
    else -> false
}

private val songResultFields = LocalSearchField.entries.filterNot {
    it == LocalSearchField.ARTIST || it == LocalSearchField.ALBUM
}

private fun songsMatching(
    field: LocalSearchField,
    uiState: LocalSearchUiState,
    query: String
): List<SongEntity> = uiState.songs.filter { field.matches(it, query) }

private fun androidx.compose.foundation.lazy.LazyListScope.FieldSongsSection(
    field: LocalSearchField,
    songs: List<SongEntity>,
    query: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    if (songs.isEmpty()) return

    item {
        SectionHeader(
            title = stringResource(field.labelRes),
            subtitle = stringResource(R.string.song_count, songs.size)
        )
    }
    items(
        items = songs,
        key = { song -> "local-search-${field.name}-${localSearchSongKey(song)}" }
    ) { song ->
        LocalSearchSongItem(
            song = song,
            isSelectionMode = isSelectionMode,
            isSelected = selectedSongUris.contains(song.uri),
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            previewLabel = field.takeUnless { it == LocalSearchField.TITLE }
                ?.let { stringResource(it.labelRes) },
            previewText = field.takeUnless { it == LocalSearchField.TITLE }?.valueOf(song),
            previewMatchQuery = query,
            pageSongs = pageSongs,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.LyricsSection(
    matches: List<LocalLyricSearchResult>,
    query: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    if (matches.isEmpty()) return

    item {
        SectionHeader(
            title = stringResource(R.string.label_lyrics),
            subtitle = stringResource(R.string.song_count, matches.size)
        )
    }
    items(
        items = matches,
        key = { match -> "local-search-lyric-${localSearchSongKey(match.song)}" }
    ) { match ->
        LocalSearchSongItem(
            song = match.song,
            isSelectionMode = isSelectionMode,
            isSelected = selectedSongUris.contains(match.song.uri),
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            previewLabel = stringResource(R.string.label_lyrics),
            previewText = match.lyricLine,
            previewMatchQuery = query,
            pageSongs = pageSongs,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.AlbumsSection(
    albums: List<AlbumEntity>,
    onAlbumClick: (AlbumEntity) -> Unit
) {
    if (albums.isEmpty()) return

    item {
        SectionHeader(
            title = stringResource(R.string.search_section_albums),
            subtitle = stringResource(R.string.album_count, albums.size)
        )
    }
    items(
        items = albums,
        key = { album -> "${album.name}|${album.albumArtist.orEmpty()}" }
    ) { album ->
        AlbumListItem(
            album = album,
            onClick = { onAlbumClick(album) }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ArtistsSection(
    artists: List<ArtistEntity>,
    onArtistClick: (ArtistEntity) -> Unit
) {
    if (artists.isEmpty()) return

    item {
        SectionHeader(
            title = stringResource(R.string.search_section_artists),
            subtitle = stringResource(R.string.song_count, artists.size)
        )
    }
    items(
        items = artists,
        key = { artist -> artist.name }
    ) { artist ->
        SelectableLibraryItem(
            itemKey = artist.id,
            onClick = { onArtistClick(artist) }
        ) {
            ArtistListItemContent(
                artist = artist
            )
        }
    }
}

@Composable
private fun LocalSearchSongItem(
    song: SongEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    previewLabel: String?,
    previewText: String?,
    previewMatchQuery: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    SelectableLibraryItem(
        itemKey = song.uri,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        swipeSelectionLabel = swipeSelectionLabel,
        swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
        onClick = { onSongClick(song) },
        onToggleSelection = { onToggleSelection(song) },
        onSwipeSelection = { onSwipeSelection(song, pageSongs) }
    ) {
        SongListItemContent(
            song = song,
            previewLabel = previewLabel,
            previewText = previewText,
            previewMatchQuery = previewMatchQuery,
            trailingContent = {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    SongListItemActions(
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onToggleSelection = { onToggleSelection(song) },
                        onShowMenu = { onShowSongMenu(song) }
                    )
                }
            }
        )
    }
}

private fun localSearchSongKey(song: SongEntity): String {
    val stableId = song.uri.takeIf { it.isNotBlank() && it != "0" } ?: song.id.toString()
    return "local-search-song-$stableId"
}

internal enum class LocalSearchTab(
    val labelRes: Int,
    val field: LocalSearchField? = null
) {
    All(R.string.search_type_all),
    Title(LocalSearchField.TITLE.labelRes, LocalSearchField.TITLE),
    Artists(LocalSearchField.ARTIST.labelRes, LocalSearchField.ARTIST),
    Albums(LocalSearchField.ALBUM.labelRes, LocalSearchField.ALBUM),
    Lyrics(R.string.label_lyrics),
    AlbumArtist(LocalSearchField.ALBUM_ARTIST.labelRes, LocalSearchField.ALBUM_ARTIST),
    Date(LocalSearchField.DATE.labelRes, LocalSearchField.DATE),
    Language(LocalSearchField.LANGUAGE.labelRes, LocalSearchField.LANGUAGE),
    Genre(LocalSearchField.GENRE.labelRes, LocalSearchField.GENRE),
    Lyricist(LocalSearchField.LYRICIST.labelRes, LocalSearchField.LYRICIST),
    Composer(LocalSearchField.COMPOSER.labelRes, LocalSearchField.COMPOSER),
    Copyright(LocalSearchField.COPYRIGHT.labelRes, LocalSearchField.COPYRIGHT),
    Comment(LocalSearchField.COMMENT.labelRes, LocalSearchField.COMMENT)
}
