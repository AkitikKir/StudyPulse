package com.growl.studypulse

import android.content.Context
import com.growl.studypulse.ai.GroqClient
import com.growl.studypulse.data.StudyPulseDatabase
import com.growl.studypulse.data.StudyRepository
import com.growl.studypulse.firebase.FirebaseSyncManager

object AppContainer {
    fun repository(context: Context): StudyRepository {
        val db = StudyPulseDatabase.get(context)
        val groq = GroqClient(apiKey = BuildConfig.GROQ_API_KEY)
        return StudyRepository(
            cardDao = db.cardDao(),
            reviewLogDao = db.reviewLogDao(),
            aiSuggestionDao = db.aiSuggestionDao(),
            groqClient = groq
        )
    }

    fun firebaseSyncManager(): FirebaseSyncManager = FirebaseSyncManager()
}
