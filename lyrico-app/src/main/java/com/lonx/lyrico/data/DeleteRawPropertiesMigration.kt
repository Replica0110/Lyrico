package com.lonx.lyrico.data

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

@DeleteColumn(tableName = "songs", columnName = "rawProperties")
class DeleteRawPropertiesMigration : AutoMigrationSpec
