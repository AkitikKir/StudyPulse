package com.growl.studypulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Query("SELECT reviewedAt FROM review_logs ORDER BY reviewedAt DESC")
    suspend fun getReviewTimestampsDesc(): List<Long>

    @Query("SELECT COUNT(*) FROM review_logs")
    suspend fun getTotalReviews(): Int

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :startOfDay")
    suspend fun getReviewsSince(startOfDay: Long): Int
}
