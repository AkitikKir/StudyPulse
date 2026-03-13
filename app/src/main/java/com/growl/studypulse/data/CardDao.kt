package com.growl.studypulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE nextReviewAt <= :now ORDER BY nextReviewAt ASC")
    suspend fun getDue(now: Long): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE nextReviewAt <= :now")
    suspend fun getDueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getTotalCount(): Int
}
