package com.growl.studypulse

import com.growl.studypulse.data.CardEntity
import com.growl.studypulse.domain.ReviewGrade
import com.growl.studypulse.domain.SpacedRepetitionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionEngineTest {
    private val engine = SpacedRepetitionEngine()

    @Test
    fun `first successful review gives day 1 interval`() {
        val card = CardEntity(front = "a", back = "b")
        val updated = engine.review(card, ReviewGrade.OK, now = 0L)

        assertEquals(1, updated.intervalDays)
        assertEquals(1, updated.repetitions)
    }

    @Test
    fun `hard review resets repetitions`() {
        val card = CardEntity(front = "a", back = "b", repetitions = 3, intervalDays = 7)
        val updated = engine.review(card, ReviewGrade.HARD, now = 0L)

        assertEquals(0, updated.repetitions)
        assertEquals(1, updated.intervalDays)
    }

    @Test
    fun `easy review increases easiness`() {
        val card = CardEntity(front = "a", back = "b", repetitions = 2, intervalDays = 3, easiness = 2.5)
        val updated = engine.review(card, ReviewGrade.EASY, now = 0L)

        assertTrue(updated.easiness > card.easiness)
    }
}
