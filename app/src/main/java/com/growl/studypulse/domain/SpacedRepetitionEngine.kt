package com.growl.studypulse.domain

import com.growl.studypulse.data.CardEntity
import kotlin.math.max
import kotlin.math.roundToInt

class SpacedRepetitionEngine {
    fun review(card: CardEntity, grade: ReviewGrade, now: Long = System.currentTimeMillis()): CardEntity {
        val quality = grade.quality
        var easiness = card.easiness + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
        easiness = max(1.3, easiness)

        val newRepetitions: Int
        val interval: Int

        if (quality < 3) {
            newRepetitions = 0
            interval = 1
        } else {
            newRepetitions = card.repetitions + 1
            interval = when (newRepetitions) {
                1 -> 1
                2 -> 3
                else -> max(1, (card.intervalDays * easiness).roundToInt())
            }
        }

        val nextReview = now + interval * DAY_MS
        return card.copy(
            easiness = easiness,
            intervalDays = interval,
            repetitions = newRepetitions,
            nextReviewAt = nextReview,
            updatedAt = now
        )
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
