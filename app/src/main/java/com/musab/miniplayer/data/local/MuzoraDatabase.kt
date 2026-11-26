package com.musab.miniplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.musab.miniplayer.data.local.dao.FavoriteDao
import com.musab.miniplayer.data.local.entity.FavoriteEntity

@Database(
    entities = [FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MuzoraDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
}
