package com.musab.miniplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val songKey: String,
    val mediaStoreId: Long,
    val contentUri: String,
    val dateAdded: Long = System.currentTimeMillis()
)
