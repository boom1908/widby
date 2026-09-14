package com.widby.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StreakEntity::class, ActivityDayEntity::class, WidgetInstanceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WidbyDatabase : RoomDatabase() {
    abstract fun dao(): WidbyDao

    companion object {
        @Volatile
        private var instance: WidbyDatabase? = null

        fun get(context: Context): WidbyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WidbyDatabase::class.java,
                    "widby.db",
                ).build().also { instance = it }
            }
    }
}