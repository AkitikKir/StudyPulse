package com.growl.studypulse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growl.studypulse.data.StudyRepository
import com.growl.studypulse.domain.ReviewGrade
import com.growl.studypulse.firebase.CloudCard
import com.growl.studypulse.firebase.CloudStats
import com.growl.studypulse.firebase.FirebaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StudyPulseViewModel(
    private val repository: StudyRepository,
    private val firebaseSyncManager: FirebaseSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyPulseUiState())
    val uiState: StateFlow<StudyPulseUiState> = _uiState.asStateFlow()

    init {
        observeCards()
        refreshDueCards()
        refreshGamification()
    }

    fun updateFront(value: String) {
        _uiState.update { it.copy(frontInput = value) }
    }

    fun updateBack(value: String) {
        _uiState.update { it.copy(backInput = value) }
    }

    fun updateImageUri(value: String) {
        _uiState.update { it.copy(imageUriInput = value) }
    }

    fun addCard() {
        val front = uiState.value.frontInput.trim()
        val back = uiState.value.backInput.trim()
        if (front.isEmpty() || back.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.addCard(front, back, uiState.value.imageUriInput)
            _uiState.update {
                it.copy(
                    frontInput = "",
                    backInput = "",
                    imageUriInput = "",
                    aiStatus = null
                )
            }
            refreshDueCards()
            refreshGamification()
        }
    }

    fun autofillWithAi() {
        val term = uiState.value.frontInput
        if (term.isBlank()) {
            _uiState.update { it.copy(aiStatus = "Введите термин для AI") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(aiLoading = true, aiStatus = "Запрашиваю Groq...") }
            val result = repository.completeCardWithAi(term)
            result.onSuccess { suggestion ->
                val details = buildString {
                    append(suggestion.definition)
                    if (suggestion.translation.isNotBlank()) append("\nПеревод: ${suggestion.translation}")
                    if (suggestion.example.isNotBlank()) append("\nПример: ${suggestion.example}")
                }
                _uiState.update {
                    it.copy(
                        backInput = details,
                        aiLoading = false,
                        aiStatus = if (suggestion.fromCache) "AI-кэш использован" else "AI-ответ получен"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        aiLoading = false,
                        aiStatus = "Ошибка AI: ${error.message ?: "неизвестно"}"
                    )
                }
            }
        }
    }

    fun importCsv(csv: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isImporting = true, importStatus = "Импорт...", aiStatus = null) }
            val inserted = repository.importFromCsv(csv)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importStatus = if (inserted > 0) "Импортировано: $inserted" else "Ничего не импортировано"
                )
            }
            refreshDueCards()
            refreshGamification()
        }
    }

    fun syncToCloud() {
        val snapshot = uiState.value
        val cards = snapshot.cards.map { card ->
            CloudCard(
                id = card.id.toString(),
                front = card.front,
                back = card.back,
                nextReviewAt = card.nextReviewAt,
                updatedAt = card.updatedAt
            )
        }
        val stats = CloudStats(
            currentStreakDays = snapshot.currentStreakDays,
            bestStreakDays = snapshot.bestStreakDays,
            reviewsToday = snapshot.reviewsToday,
            totalReviews = snapshot.totalReviews,
            achievements = snapshot.achievements
        )

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncingCloud = true, cloudStatus = "Firebase sync...") }
            val result = firebaseSyncManager.sync(cards = cards, stats = stats)
            result.onSuccess { msg ->
                _uiState.update { it.copy(isSyncingCloud = false, cloudStatus = msg) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncingCloud = false,
                        cloudStatus = "Firebase sync error: ${err.message ?: "unknown"}"
                    )
                }
            }
        }
    }

    fun refreshDueCards() {
        viewModelScope.launch(Dispatchers.IO) {
            val due = repository.getDueCards()
            _uiState.update {
                val boundedIndex = it.currentSessionIndex.coerceAtMost((due.size - 1).coerceAtLeast(0))
                it.copy(
                    dueCards = due,
                    currentSessionIndex = if (due.isEmpty()) 0 else boundedIndex,
                    isAnswerVisible = false
                )
            }
        }
    }

    fun refreshGamification() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = repository.getGamificationStats()
            _uiState.update {
                it.copy(
                    currentStreakDays = stats.currentStreakDays,
                    bestStreakDays = stats.bestStreakDays,
                    reviewsToday = stats.reviewsToday,
                    totalReviews = stats.totalReviews,
                    achievements = stats.unlockedAchievements
                )
            }
        }
    }

    fun toggleAnswerVisibility() {
        _uiState.update { it.copy(isAnswerVisible = !it.isAnswerVisible) }
    }

    fun rateCurrentCard(grade: ReviewGrade) {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.reviewCard(card, grade)
            val nextAt = formatter.format(
                Instant.ofEpochMilli(updated.nextReviewAt).atZone(ZoneId.systemDefault())
            )
            val message = "${card.front}: следующее повторение $nextAt"

            val due = repository.getDueCards()
            _uiState.update {
                val newIndex = if (due.isEmpty()) 0 else it.currentSessionIndex.coerceAtMost(due.lastIndex)
                it.copy(
                    dueCards = due,
                    currentSessionIndex = newIndex,
                    isAnswerVisible = false,
                    lastReviewMessage = message
                )
            }
            refreshGamification()
        }
    }

    private fun observeCards() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeCards().collect { cards ->
                _uiState.update { it.copy(cards = cards) }
            }
        }
    }

    companion object {
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm")
    }
}

class StudyPulseViewModelFactory(
    private val repository: StudyRepository,
    private val firebaseSyncManager: FirebaseSyncManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyPulseViewModel::class.java)) {
            return StudyPulseViewModel(repository, firebaseSyncManager) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
