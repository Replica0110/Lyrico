package com.lonx.lyrico.domain.folder

/**
 * 将数据库里的目录路径统一成可比较的形式：分隔符统一为 `/`，去掉首尾空白与结尾分隔符。
 * 树结构和面包屑都按这一形式判断祖先关系，避免 Windows 目录与 SAF 目录写成两种形态。
 */
fun String.normalizeFolderPath(): String =
    replace('\\', '/')
        .trim()
        .trimEnd('/')

/** 目录在界面上的名称：取路径最后一段；路径本身已是根目录等没有分隔符的情况下原样返回。 */
fun String.folderDisplayName(): String = substringAfterLast('/').ifBlank { this }
