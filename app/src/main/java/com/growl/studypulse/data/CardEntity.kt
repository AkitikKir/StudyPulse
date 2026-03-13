package com.growl.studypulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val front: String,
    val back: String,
    val imageUri: String? = null,
    val easiness: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
