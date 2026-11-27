package com.musab.miniplayer.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: MuzoraDatabase? = null

    fun getDatabase(context: Context): MuzoraDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                MuzoraDatabase::class.java,
                "muzora_database"
            ).build().also { INSTANCE = it }
        }
    }
}
