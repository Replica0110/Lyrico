package com.lonx.lyrico.data.db.dao

import androidx.room.*
import com.lonx.lyrico.data.db.entity.PluginSubscriptionEntity
import com.lonx.lyrico.data.db.entity.PluginCatalogCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginSubscriptionDao {
    @Query("SELECT * FROM plugin_subscriptions ORDER BY sortOrder, id")
    fun observeSubscriptions(): Flow<List<PluginSubscriptionEntity>>
    @Query("SELECT * FROM plugin_subscriptions ORDER BY sortOrder, id")
    suspend fun getSubscriptions(): List<PluginSubscriptionEntity>
    @Upsert suspend fun upsert(subscription: PluginSubscriptionEntity)
    @Query("DELETE FROM plugin_subscriptions WHERE id = :id")
    suspend fun delete(id: String)
    @Query("UPDATE plugin_subscriptions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
    @Query("SELECT * FROM plugin_catalog_cache")
    fun observeCache(): Flow<List<PluginCatalogCacheEntity>>
    @Query("SELECT * FROM plugin_catalog_cache WHERE url = :url")
    suspend fun getCache(url: String): PluginCatalogCacheEntity?
    @Upsert suspend fun upsertCache(cache: PluginCatalogCacheEntity)
}
