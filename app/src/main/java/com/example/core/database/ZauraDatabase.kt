package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TabEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class,
        ResearchSessionEntity::class,
        SitePermissionEntity::class,
        BlockedTrackerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZauraDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: ZauraDatabase? = null

        fun getDatabase(context: Context): ZauraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZauraDatabase::class.java,
                    "zaura_browser.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
