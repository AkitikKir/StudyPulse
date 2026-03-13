package com.growl.studypulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiSuggestionDao {
    @Query("SELECT * FROM ai_suggestions WHERE term = :term LIMIT 1")
    suspend fun getByTerm(term: String): AiSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiSuggestionEntity)
}
