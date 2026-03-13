package com.growl.studypulse.ai

data class AiSuggestion(
    val definition: String,
    val translation: String,
    val example: String,
    val fromCache: Boolean
)
