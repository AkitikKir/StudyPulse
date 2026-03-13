package com.growl.studypulse.data

import com.growl.studypulse.ai.AiSuggestion
import com.growl.studypulse.ai.GroqClient
import com.growl.studypulse.domain.ReviewGrade
import com.growl.studypulse.domain.SpacedRepetitionEngine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class StudyRepository(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val aiSuggestionDao: AiSuggestionDao,
    private val groqClient: GroqClient,
    private val engine: SpacedRepetitionEngine = SpacedRepetitionEngine()
) {
    fun observeCards(): Flow<List<CardEntity>> = cardDao.observeAll()

    suspend fun addCard(front: String, back: String, imageUri: String? = null) {
        cardDao.insert(
            CardEntity(
                front = front.trim(),
                back = back.trim(),
                imageUri = imageUri?.trim()?.takeIf { it.isNotEmpty() },
                nextReviewAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun importFromCsv(csv: String): Int {
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) return 0

        var inserted = 0
        for ((index, line) in lines.withIndex()) {
            val cells = line.split(',').map { it.trim() }
            if (cells.size < 2) continue

            val front = cells[0]
            val back = cells[1]
            if (index == 0 && front.lowercase() in setOf("term", "front", "question")) continue
            val imageUri = cells.getOrNull(2)
            if (front.isBlank() || back.isBlank()) continue

            addCard(front, back, imageUri)
            inserted += 1
        }
        return inserted
    }

    suspend fun getDueCards(now: Long = System.currentTimeMillis()): List<CardEntity> = cardDao.getDue(now)

    suspend fun getDueCount(now: Long = System.currentTimeMillis()): Int = cardDao.getDueCount(now)

    suspend fun reviewCard(card: CardEntity, grade: ReviewGrade, now: Long = System.currentTimeMillis()): CardEntity {
        val updated = engine.review(card, grade, now)
        cardDao.update(updated)
        reviewLogDao.insert(
            ReviewLogEntity(
                cardId = card.id,
                grade = grade.quality,
                reviewedAt = now,
                scheduledNextAt = updated.nextReviewAt
            )
        )
        return updated
    }

    suspend fun completeCardWithAi(term: String): Result<AiSuggestion> {
        val normalized = term.trim().lowercase()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Термин пустой"))
        }

        val cached = aiSuggestionDao.getByTerm(normalized)
        if (cached != null) {
            return Result.success(
                AiSuggestion(
                    definition = cached.definition,
                    translation = cached.translation,
                    example = cached.example,
                    fromCache = true
                )
            )
        }

        if (!groqClient.isConfigured()) {
            return Result.failure(IllegalStateException("Укажите GROQ_API_KEY в gradle.properties"))
        }

        return runCatching {
            val suggestion = groqClient.completeCard(normalized)
            aiSuggestionDao.upsert(
                AiSuggestionEntity(
                    term = normalized,
                    definition = suggestion.definition,
                    translation = suggestion.translation,
                    example = suggestion.example
                )
            )
            suggestion
        }
    }

    suspend fun getGamificationStats(now: Long = System.currentTimeMillis()): GamificationStats {
        val timestamps = reviewLogDao.getReviewTimestampsDesc()
        val reviewDays = timestamps
            .map { millis ->
                LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(millis),
                    ZoneId.systemDefault()
                )
            }
            .distinct()

        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
        val streak = calculateCurrentStreak(reviewDays, today)
        val bestStreak = calculateBestStreak(reviewDays)
        val totalReviews = reviewLogDao.getTotalReviews()
        val reviewsToday = reviewLogDao.getReviewsSince(today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val totalCards = cardDao.getTotalCount()

        val achievements = buildList {
            if (totalReviews >= 1) add("Первое повторение")
            if (totalReviews >= 25) add("25 повторений")
            if (totalReviews >= 100) add("100 повторений")
            if (streak >= 3) add("Серия 3 дня")
            if (streak >= 7) add("Серия 7 дней")
            if (totalCards >= 50) add("Колода 50+ карточек")
        }

        return GamificationStats(
            currentStreakDays = streak,
            bestStreakDays = bestStreak,
            reviewsToday = reviewsToday,
            totalReviews = totalReviews,
            unlockedAchievements = achievements
        )
    }

    private fun calculateCurrentStreak(reviewDays: List<LocalDate>, today: LocalDate): Int {
        if (reviewDays.isEmpty()) return 0
        val set = reviewDays.toSet()

        var cursor = when {
            today in set -> today
            today.minusDays(1) in set -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (cursor in set) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun calculateBestStreak(reviewDays: List<LocalDate>): Int {
        if (reviewDays.isEmpty()) return 0

        val sorted = reviewDays.sorted()
        var best = 1
        var current = 1

        for (i in 1..sorted.lastIndex) {
            if (sorted[i - 1].plusDays(1) == sorted[i]) {
                current += 1
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }
}
