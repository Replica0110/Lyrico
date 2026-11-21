package com.lonx.lyrico.domain.folder

import com.lonx.lyrico.data.db.entity.FolderEntity

/**
 * 由扁平目录列表整理出的父子关系和歌曲统计。
 *
 * 数据库只存每行的路径，树按路径前缀在读取时推导：某个目录是另一目录的父级，当且仅当后者的路径以
 * `<父路径>/` 开头。目录数量在同一界面内有限，这里保留直接比较的写法，避免为扫描结果维护额外索引。
 */
data class FolderTree(
    val nodesById: Map<Long, FolderTreeNode>,
    val rootFolders: List<FolderEntity>,
) {
    fun nodeOf(folderId: Long): FolderTreeNode? = nodesById[folderId]

    /** 某目录下的直接子目录；根目录或未知目录都回落到顶层目录。 */
    fun childFoldersOf(folderId: Long): List<FolderEntity> =
        nodeOf(folderId)?.childFolders ?: rootFolders
}

data class FolderTreeNode(
    val folder: FolderEntity,
    val parentId: Long?,
    val childFolders: List<FolderEntity>,
    val directSongCount: Int,
    /** 自身与所有后代目录的歌曲数之和。 */
    val subtreeSongCount: Int,
)

/** 面包屑的一段；[label] 已按路径取过名称，可直接展示。 */
data class FolderBreadcrumb(
    val folderId: Long,
    val label: String,
)

fun buildFolderTree(folders: List<FolderEntity>): FolderTree {
    val normalizedPaths = folders.associate { folder ->
        folder.id to folder.path.normalizeFolderPath()
    }

    val parentIds = folders.associate { folder ->
        val path = normalizedPaths.getValue(folder.id)
        // 取最长匹配的祖先，处理「已添加父目录，又单独添加更深的子目录」的情况。
        val parent = folders
            .filter { candidate ->
                val candidatePath = normalizedPaths.getValue(candidate.id)
                candidate.id != folder.id &&
                    candidatePath.isNotBlank() &&
                    path.startsWith("$candidatePath/")
            }
            .maxByOrNull { candidate -> normalizedPaths.getValue(candidate.id).length }

        folder.id to parent?.id
    }

    val childrenByParentId = folders
        .groupBy { folder -> parentIds.getValue(folder.id) }
        .mapValues { (_, children) ->
            children.sortedBy { it.path.normalizeFolderPath() }
        }

    val directSongCounts = folders.associate { folder -> folder.id to folder.songCount }
    val subtreeSongCounts = mutableMapOf<Long, Int>()

    fun subtreeSongCount(folderId: Long): Int = subtreeSongCounts.getOrPut(folderId) {
        directSongCounts[folderId].orZero() +
            childrenByParentId[folderId].orEmpty().sumOf { child -> subtreeSongCount(child.id) }
    }

    val nodesById = folders.associate { folder ->
        folder.id to FolderTreeNode(
            folder = folder,
            parentId = parentIds.getValue(folder.id),
            childFolders = childrenByParentId[folder.id].orEmpty(),
            directSongCount = directSongCounts[folder.id].orZero(),
            subtreeSongCount = subtreeSongCount(folder.id),
        )
    }

    return FolderTree(
        nodesById = nodesById,
        rootFolders = childrenByParentId[null].orEmpty(),
    )
}

/**
 * 从顶层到 [folderId] 的面包屑。目录不在树里时只返回顶层一段，调用方无需再判空。
 */
fun FolderTree.breadcrumbsTo(
    folderId: Long,
    rootFolderId: Long,
    rootLabel: String,
): List<FolderBreadcrumb> {
    val node = nodeOf(folderId) ?: return listOf(FolderBreadcrumb(rootFolderId, rootLabel))

    val ancestors = buildList {
        var current: FolderTreeNode? = node
        while (current != null) {
            add(current)
            current = current.parentId?.let(::nodeOf)
        }
    }

    return buildList {
        add(FolderBreadcrumb(rootFolderId, rootLabel))
        ancestors.asReversed().forEach { ancestor ->
            add(
                FolderBreadcrumb(
                    folderId = ancestor.folder.id,
                    label = ancestor.folder.path.folderDisplayName(),
                )
            )
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
