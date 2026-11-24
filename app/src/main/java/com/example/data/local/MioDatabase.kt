package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LocalMessageEntity::class], version = 1, exportSchema = false)
abstract class MioDatabase : RoomDatabase() {
  abstract fun localMessageDao(): LocalMessageDao

  companion object {
    @Volatile
    private var instance: MioDatabase? = null

    fun getInstance(context: Context): MioDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          MioDatabase::class.java,
          "mio_local_history.db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
      }
    }
  }
}
