package com.growl.studypulse.ui

import com.growl.studypulse.data.CardEntity

data class StudyPulseUiState(
    val cards: List<CardEntity> = emptyList(),
    val dueCards: List<CardEntity> = emptyList(),
    val currentSessionIndex: Int = 0,
    val frontInput: String = "",
    val backInput: String = "",
    val imageUriInput: String = "",
    val isAnswerVisible: Boolean = false,
    val lastReviewMessage: String? = null,
    val aiStatus: String? = null,
    val aiLoading: Boolean = false,
    val importStatus: String? = null,
    val isImporting: Boolean = false,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val reviewsToday: Int = 0,
    val totalReviews: Int = 0,
    val achievements: List<String> = emptyList(),
    val cloudStatus: String? = null,
    val isSyncingCloud: Boolean = false
) {
    val dueCount: Int get() = dueCards.size
    val currentCard: CardEntity? get() = dueCards.getOrNull(currentSessionIndex)
}
