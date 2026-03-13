package com.growl.studypulse.data

data class GamificationStats(
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val reviewsToday: Int,
    val totalReviews: Int,
    val unlockedAchievements: List<String>
)
