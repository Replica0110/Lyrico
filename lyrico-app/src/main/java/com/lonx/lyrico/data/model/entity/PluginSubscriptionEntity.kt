package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_subscriptions", indices = [Index(value = ["url"], unique = true)])
data class PluginSubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val sortOrder: Int
)

/** Also caches plugin-declared URLs, which do not become user subscriptions. */
@Entity(tableName = "plugin_catalog_cache")
data class PluginCatalogCacheEntity(
    @PrimaryKey val url: String,
    val catalogJson: String? = null,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val error: String? = null
)
