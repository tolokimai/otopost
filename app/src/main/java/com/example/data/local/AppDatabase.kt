package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.dao.ContentPlanDao
import com.example.data.local.dao.PersonaDao
import com.example.data.local.dao.PostingLogDao
import com.example.data.local.dao.ScheduledPostDao
import com.example.data.local.entity.*

@Database(
    entities = [
        PersonaEntity::class,
        ContentPlanEntity::class,
        ContentPlanItemEntity::class,
        ScheduledPostEntity::class,
        PostingLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun contentPlanDao(): ContentPlanDao
    abstract fun scheduledPostDao(): ScheduledPostDao
    abstract fun postingLogDao(): PostingLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autopost_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
