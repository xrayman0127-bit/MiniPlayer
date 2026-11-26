package com.musab.miniplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musab.miniplayer.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songKey = :songKey")
    suspend fun removeFavorite(songKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songKey = :songKey)")
    fun isFavorite(songKey: String): Flow<Boolean>

    @Query("SELECT * FROM favorites ORDER BY dateAdded DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
}
