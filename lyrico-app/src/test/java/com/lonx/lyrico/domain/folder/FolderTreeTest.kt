package com.lonx.lyrico.domain.folder

import com.lonx.lyrico.data.db.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun folder(id: Long, path: String, songCount: Int = 0) = FolderEntity(
        id = id,
        path = path,
        songCount = songCount,
    )

    @Test
    fun nestedPathsBecomeParentChildRelation() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "/music", songCount = 2),
                folder(2, "/music/rock", songCount = 3),
                folder(3, "/music/rock/90s", songCount = 4),
                folder(4, "/videos", songCount = 1),
            )
        )

        assertEquals(listOf(1L, 4L), tree.rootFolders.map { it.id })
        assertEquals(1L, tree.nodeOf(2)?.parentId)
        assertEquals(2L, tree.nodeOf(3)?.parentId)
        assertNull(tree.nodeOf(4)?.parentId)
        assertEquals(listOf(2L), tree.nodeOf(1)?.childFolders?.map { it.id })
    }

    @Test
    fun subtreeSongCountSumsDescendants() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "/music", songCount = 2),
                folder(2, "/music/rock", songCount = 3),
                folder(3, "/music/rock/90s", songCount = 4),
            )
        )

        assertEquals(2, tree.nodeOf(1)?.directSongCount)
        assertEquals(9, tree.nodeOf(1)?.subtreeSongCount)
        assertEquals(7, tree.nodeOf(2)?.subtreeSongCount)
        assertEquals(4, tree.nodeOf(3)?.subtreeSongCount)
    }

    @Test
    fun windowsSeparatorAndTrailingSlashStillNest() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "D:\\Music\\"),
                folder(2, "D:/Music/Rock/"),
                folder(3, "D:\\Music\\Rock\\Live"),
            )
        )

        assertEquals(listOf(1L), tree.rootFolders.map { it.id })
        assertEquals(1L, tree.nodeOf(2)?.parentId)
        assertEquals(2L, tree.nodeOf(3)?.parentId)
    }

    @Test
    fun sharedPrefixWithoutSeparatorIsNotNested() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "/music"),
                folder(2, "/music-videos"),
            )
        )

        assertEquals(listOf(1L, 2L), tree.rootFolders.map { it.id })
        assertTrue(tree.nodeOf(2)?.parentId == null)
    }

    @Test
    fun deepestAncestorWinsWhenSeveralPrefixesMatch() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "/music"),
                folder(2, "/music/rock"),
                folder(3, "/music/rock/live"),
            )
        )

        assertEquals(2L, tree.nodeOf(3)?.parentId)
    }

    @Test
    fun childFoldersOfRootAndUnknownFolderFallBackToTopLevel() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "/music"),
                folder(2, "/music/rock"),
            )
        )

        assertEquals(listOf(1L), tree.childFoldersOf(ROOT).map { it.id })
        assertEquals(listOf(1L), tree.childFoldersOf(999).map { it.id })
        assertEquals(listOf(2L), tree.childFoldersOf(1).map { it.id })
    }

    @Test
    fun breadcrumbsWalkFromRootToFolder() {
        val tree = buildFolderTree(
            listOf(
                folder(1, "/music"),
                folder(2, "/music/rock"),
                folder(3, "/music/rock/live"),
            )
        )

        val breadcrumbs = tree.breadcrumbsTo(folderId = 3, rootFolderId = ROOT, rootLabel = "Root")

        assertEquals(listOf(ROOT, 1L, 2L, 3L), breadcrumbs.map { it.folderId })
        assertEquals(listOf("Root", "music", "rock", "live"), breadcrumbs.map { it.label })
    }

    @Test
    fun breadcrumbsOfUnknownFolderOnlyContainRoot() {
        val tree = buildFolderTree(listOf(folder(1, "/music")))

        val breadcrumbs = tree.breadcrumbsTo(folderId = 42, rootFolderId = ROOT, rootLabel = "Root")

        assertEquals(listOf(ROOT), breadcrumbs.map { it.folderId })
    }

    @Test
    fun displayNameFallsBackToWholePath() {
        assertEquals("live", "/music/rock/live".folderDisplayName())
        assertEquals("live", "/music/rock/live/".trimEnd('/').folderDisplayName())
        assertEquals("/", "/".folderDisplayName())
        assertEquals("", "".folderDisplayName())
    }

    private companion object {
        const val ROOT = -1L
    }
}
