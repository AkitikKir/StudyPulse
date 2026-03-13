package com.growl.studypulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val grade: Int,
    val reviewedAt: Long = System.currentTimeMillis(),
    val scheduledNextAt: Long
)
