package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AiMessageDao
import com.example.data.local.dao.AppSettingDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.ProjectFileDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.entity.AiMessageEntity
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.local.entity.UserProfileEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectFileEntity::class,
        AiMessageEntity::class,
        UserProfileEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectFileDao(): ProjectFileDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vlive_ai_studio.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
