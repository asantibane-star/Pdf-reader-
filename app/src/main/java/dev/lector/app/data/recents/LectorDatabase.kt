package dev.lector.app.data.recents

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentEntity::class], version = 1, exportSchema = true)
abstract class LectorDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentDao

    companion object {
        const val NAME = "lector.db"

        fun build(context: Context): LectorDatabase =
            Room.databaseBuilder(context.applicationContext, LectorDatabase::class.java, NAME).build()
    }
}
