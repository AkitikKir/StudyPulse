package com.growl.studypulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_suggestions")
data class AiSuggestionEntity(
    @PrimaryKey val term: String,
    val definition: String,
    val translation: String,
    val example: String,
    val updatedAt: Long = System.currentTimeMillis()
)
