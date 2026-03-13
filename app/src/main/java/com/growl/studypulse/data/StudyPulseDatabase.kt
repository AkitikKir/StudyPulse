package com.growl.studypulse.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CardEntity::class, ReviewLogEntity::class, AiSuggestionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StudyPulseDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun aiSuggestionDao(): AiSuggestionDao

    companion object {
        @Volatile
        private var INSTANCE: StudyPulseDatabase? = null

        fun get(context: Context): StudyPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudyPulseDatabase::class.java,
                    "studypulse.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
