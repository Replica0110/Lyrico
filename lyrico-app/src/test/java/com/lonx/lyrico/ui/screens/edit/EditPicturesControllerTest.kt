package com.lonx.lyrico.ui.screens.edit

import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.frontCoverOrFallback
import com.lonx.audiotag.model.pictureOfType
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.log.AppLogRepository
import com.lonx.lyrico.data.settings.ArtistSplitConfig
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagMutation
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagWriteResult
import com.lonx.lyrico.domain.song.usecase.ReadAudioTagsUseCase
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditPicturesControllerTest {
    @Test
    fun `latest cover wins while unrelated edits survive a delayed import`() = runBlocking {
        Fixture().use { fixture ->
            val old = CompletableDeferred<AudioPicture?>()
            val latest = picture(3)
            fixture.editor.importCover("old") { old.await() }
            fixture.editor.importCover("new") { latest }
            fixture.state.update { it.copy(editingTagData = it.editingTagData!!.copy(title = "Edited title")) }
            old.complete(picture(2))
            assertSame(latest, fixture.state.value.picture)
            assertEquals("new", fixture.state.value.coverUri)
            assertEquals("Edited title", fixture.state.value.editingTagData!!.title)
            assertSame(fixture.artist, fixture.state.value.editingTagData!!.pictures.last())
        }
    }

    @Test
    fun `reverting or removing a cover prevents a pending import from reviving it`() = runBlocking {
        Fixture().use { fixture ->
            val pending = CompletableDeferred<AudioPicture?>()
            fixture.editor.importCover("pending") { pending.await() }
            fixture.editor.revertCover()
            pending.complete(picture(2))
            assertSame(fixture.cover, fixture.state.value.picture)
            val removed = CompletableDeferred<AudioPicture?>()
            fixture.editor.importCover("pending again") { removed.await() }
            fixture.editor.removeFrontCover()
            removed.complete(picture(3))
            assertNull(fixture.state.value.editingTagData!!.pictures.pictureOfType(AudioPictureType.FrontCover))
            assertSame(fixture.artist, fixture.state.value.picture)
        }
    }

    @Test
    fun `song switches invalidate delayed imports even after returning to the same URI`() = runBlocking {
        Fixture().use { fixture ->
            val pending = CompletableDeferred<AudioPicture?>()
            fixture.editor.importCover("old song") { pending.await() }
            val ticket = fixture.editor.songTicket()
            fixture.switchSong("song-b")
            fixture.switchSong("song-a")
            val current = fixture.state.value
            pending.complete(picture(8))
            assertEquals(current, fixture.state.value)
            assertFalse(fixture.editor.isCurrent(ticket))
        }
    }

    @Test
    fun `cancelled image loading neither applies nor reports a read failure`() = runBlocking {
        Fixture().use { fixture ->
            fixture.editor.importCover("cancelled") { throw CancellationException("Cancelled picker") }
            assertSame(fixture.cover, fixture.state.value.picture)
            assertFalse(fixture.state.value.isEditing)
            assertTrue(fixture.errors.isEmpty())
            val pending = CompletableDeferred<AudioPicture?>()
            fixture.editor.importCover("pending") { pending.await() }
            fixture.scope.coroutineContext[Job]!!.children.single().cancel()
            pending.complete(picture(9))
            assertSame(fixture.cover, fixture.state.value.picture)
            assertTrue(fixture.errors.isEmpty())
        }
    }

    @Test
    fun `different artists import independently while the same owner uses the latest selection`() = runBlocking {
        Fixture().use { fixture ->
            val oldA = CompletableDeferred<AudioPicture?>()
            val b = CompletableDeferred<AudioPicture?>()
            fixture.editor.importArtistImage("A") { oldA.await() }
            fixture.editor.importArtistImage("B") { b.await() }
            val latestA = picture(5, AudioPictureType.Artist, "A")
            fixture.editor.importArtistImage("a") { latestA }
            b.complete(picture(6, AudioPictureType.Artist, "B"))
            oldA.complete(picture(4, AudioPictureType.Artist, "A"))
            val pictures = fixture.state.value.editingTagData!!.pictures
            assertEquals(listOf(5), pictures.single { it.description.equals("a", true) }.data.map(Byte::toInt))
            assertEquals(listOf(6), pictures.single { it.description == "B" }.data.map(Byte::toInt))
            assertSame(fixture.cover, pictures.pictureOfType(AudioPictureType.FrontCover))
        }
    }

    @Test
    fun `artist group revert invalidates imports without reverting the cover`() = runBlocking {
        Fixture().use { fixture ->
            val pending = CompletableDeferred<AudioPicture?>()
            fixture.editor.importArtistImage("A") { pending.await() }
            val newCover = picture(9)
            fixture.editor.importCover(null) { newCover }
            fixture.editor.revertArtistImages()
            pending.complete(picture(7, AudioPictureType.Artist, "A"))
            assertSame(fixture.artist, fixture.state.value.editingTagData!!.pictures.last())
            assertSame(newCover, fixture.state.value.picture)
        }
    }

    @Test
    fun `cover undo restores only the cover and retains newer artist images and tag edits`() = runBlocking {
        Fixture().use { fixture ->
            val before = fixture.state.value
            fixture.editor.revertCover()
            fixture.editor.importArtistImage("A") { picture(6, AudioPictureType.Artist, "A") }
            fixture.state.update { it.copy(editingTagData = it.editingTagData!!.copy(album = "New album")) }
            fixture.editor.restoreCoverSnapshot(before.coverUri, before.picture,
                before.editingTagData!!.pictures, before.editingTagData.picUrl)
            assertEquals(listOf(6), fixture.state.value.editingTagData!!.pictures.last().data.map(Byte::toInt))
            assertEquals("New album", fixture.state.value.editingTagData!!.album)
            assertSame(fixture.cover, fixture.state.value.picture)
        }
    }

    @Test
    fun `artist undo retains a newer cover and unrelated picture types`() = runBlocking {
        Fixture().use { fixture ->
            val before = fixture.state.value.editingTagData!!.pictures
            val newCover = picture(8)
            val back = picture(7, AudioPictureType.BackCover)
            fixture.editor.importCover(null) { newCover }
            val expectedCover = fixture.state.value.editingTagData!!.pictures.pictureOfType(AudioPictureType.FrontCover)
            fixture.state.update { it.copy(editingTagData = it.editingTagData!!.copy(
                pictures = it.editingTagData.pictures + back)) }
            fixture.editor.importArtistImage("A") { picture(6, AudioPictureType.Artist, "A") }
            fixture.editor.restoreArtistImageSnapshot(before)
            val pictures = fixture.state.value.editingTagData!!.pictures
            assertSame(expectedCover, pictures.pictureOfType(AudioPictureType.FrontCover))
            assertSame(back, pictures.pictureOfType(AudioPictureType.BackCover))
            assertTrue(pictures.any { it === fixture.artist })
        }
    }

    @Test
    fun `removing one artist invalidates only that owner pending import`() = runBlocking {
        Fixture().use { fixture ->
            val a = CompletableDeferred<AudioPicture?>()
            val b = CompletableDeferred<AudioPicture?>()
            fixture.editor.importArtistImage("A") { a.await() }
            fixture.editor.importArtistImage("B") { b.await() }
            fixture.editor.removeArtistImage(fixture.artist)
            a.complete(picture(5, AudioPictureType.Artist, "A"))
            b.complete(picture(6, AudioPictureType.Artist, "B"))
            val pictures = fixture.state.value.editingTagData!!.pictures
            assertFalse(pictures.any { it.description == "A" })
            assertTrue(pictures.any { it.description == "B" })
        }
    }

    @Test
    fun `same album cover copies original bytes and mime without reencoding or changing other pictures`() = runBlocking {
        Fixture().use { fixture ->
            fixture.albumSongs = listOf(song("song-a"), song("other"))
            val source = picture(11, AudioPictureType.BackCover).copy(mimeType = "image/png")
            fixture.readTags = { uri ->
                assertEquals("other", uri)
                AudioTagData(pictures = listOf(source))
            }
            fixture.editor.loadSameAlbumCovers()
            val selected = fixture.state.value.editingTagData!!.pictures.pictureOfType(AudioPictureType.FrontCover)!!
            assertSame(source.data, selected.data)
            assertEquals("image/png", selected.mimeType)
            assertEquals("", selected.description)
            assertTrue(fixture.state.value.editingTagData!!.pictures.any { it === fixture.artist })
        }
    }

    @Test
    fun `same album result cannot replace a cover selected while tags were being read`() = runBlocking {
        Fixture().use { fixture ->
            val tags = CompletableDeferred<AudioTagData>()
            fixture.albumSongs = listOf(song("other"))
            fixture.readTags = { tags.await() }
            fixture.editor.loadSameAlbumCovers()
            val latest = picture(12)
            fixture.editor.importCover(null) { latest }
            tags.complete(AudioTagData(pictures = listOf(picture(13))))
            assertSame(latest, fixture.state.value.picture)
            assertNull(fixture.state.value.sameAlbumCoverMessage)
        }
    }

    @Test
    fun `same album selection skips unreadable songs and stops after the first usable picture`() = runBlocking {
        Fixture().use { fixture ->
            fixture.albumSongs = listOf(song("broken"), song("usable"), song("not needed"))
            val source = picture(14)
            val visited = mutableListOf<String>()
            fixture.readTags = { uri ->
                visited += uri
                when (uri) {
                    "broken" -> throw IOException("Cannot read tags")
                    "usable" -> AudioTagData(pictures = listOf(source))
                    else -> error("Already found a cover")
                }
            }
            fixture.editor.loadSameAlbumCovers()
            assertEquals(listOf("broken", "usable"), visited)
            assertSame(source.data, fixture.state.value.picture!!.data)
            assertEquals(1, fixture.errors.size)
        }
    }

    @Test
    fun `cancelled same album read does not become a failed or empty cover result`() = runBlocking {
        Fixture().use { fixture ->
            fixture.albumSongs = listOf(song("cancelled"))
            fixture.readTags = { throw CancellationException("Cancelled tag read") }
            fixture.editor.loadSameAlbumCovers()
            assertSame(fixture.cover, fixture.state.value.picture)
            assertNull(fixture.state.value.sameAlbumCoverMessage)
            assertTrue(fixture.errors.isEmpty())
        }
    }

    @Test
    fun `export snapshot keeps initiating source audio URI and filename`() {
        Fixture().use { fixture ->
            val export = requireNotNull(fixture.editor.captureExport())
            fixture.editor.updateCover("https://example.com/new.jpg")
            fixture.state.update { it.copy(fileName = "new name") }
            fixture.switchSong("song-b")
            assertSame(fixture.cover.data, export.source)
            assertEquals("song-a", export.audioUri)
            assertEquals("a.live.jpg", export.fileName)
            assertFalse(fixture.editor.isCurrent(export.ticket))
        }
    }

    private class Fixture : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cover = picture(1)
        val artist = picture(2, AudioPictureType.Artist, "A")
        private val initial = AudioTagData(title = "Title", album = "Album", artist = "A/B", pictures = listOf(cover, artist))
        val state = MutableStateFlow(EditMetadataUiState(songInfo = SongInfo("song-a", initial),
            editingTagData = initial, originalTagData = initial, coverUri = cover.data, originalCover = cover.data,
            picture = cover, fileName = "a.live"))
        var uri: String? = "song-a"
        val errors = mutableListOf<Throwable>()
        var albumSongs = emptyList<SongEntity>()
        var readTags: suspend (String) -> AudioTagData = { error("Unexpected tag read: $it") }
        private val settings = proxy<SettingsRepository> { name, _ ->
            when (name) { "getSeparator" -> flowOf("/"); else -> error("Unexpected settings call: $name") }
        }
        private val library = proxy<SongLibraryRepository> { name, _ ->
            when (name) { "getSongsByAlbum" -> albumSongs; else -> error("Unexpected library call: $name") }
        }
        private val tags = object : AudioTagRepository {
            override suspend fun read(uri: String, options: AudioTagReadOptions): AudioTagData = readTags(uri)
            override suspend fun overwrite(uri: String, mutation: AudioTagMutation): AudioTagWriteResult = error("No tag writes")
            override suspend fun patch(uri: String, mutation: AudioTagMutation): AudioTagWriteResult = error("No tag writes")
        }
        val editor = EditPicturesController(scope, state, { uri }, { ArtistSplitConfig() }, settings, library,
            ReadAudioTagsUseCase(tags, settings), proxy<AppLogRepository> { name, _ -> error("Unexpected log call: $name") },
            recordFailure = { _, _, _ -> error("Unexpected read failure") },
            recordException = { _, _, error -> errors += error }, ioDispatcher = Dispatchers.Unconfined)

        init { editor.onSongChanged("song-a") }

        fun switchSong(next: String) {
            uri = next
            editor.onSongChanged(next)
            state.value = EditMetadataUiState(songInfo = SongInfo(next, initial),
                originalTagData = initial, editingTagData = initial, coverUri = cover.data,
                originalCover = cover.data, picture = cover, fileName = "new file")
        }

        override fun close() = scope.cancel()
    }

    private companion object {
        fun picture(value: Int, type: AudioPictureType = AudioPictureType.FrontCover, description: String = "") =
            AudioPicture(byteArrayOf(value.toByte()), "image/jpeg", description, type.tagLibName)

        fun song(uri: String) = SongEntity(uri = uri, folderId = 1, mediaId = 0,
            filePath = "/$uri", fileName = "$uri.flac", title = uri, artist = "A/B", album = "Album")

        inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
                when (method.name) {
                    "toString" -> "Fixture ${T::class.java.simpleName}"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> invoke(method.name, arguments.orEmpty())
                }
            } as T
    }
}
