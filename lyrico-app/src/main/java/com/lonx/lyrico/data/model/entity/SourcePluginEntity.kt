package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "source_plugins")
data class SourcePluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val author: String,
    val description: String,
    val apiVersion: Int,
    val pluginDir: String,
    val entryFile: String,
    val includeDirsJson: String,
    val iconPath: String?,
    val enabled: Boolean,
    val sortOrder: Int,
    val installedAt: Long,
    val updatedAt: Long
)
