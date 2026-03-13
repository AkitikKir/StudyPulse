package com.growl.studypulse.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(
    private val auth: FirebaseAuth = Firebase.auth,
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    suspend fun sync(
        cards: List<CloudCard>,
        stats: CloudStats
    ): Result<String> = runCatching {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: error("Firebase auth failed")

        val payload = mapOf(
            "updatedAt" to ServerValue.TIMESTAMP,
            "stats" to mapOf(
                "currentStreakDays" to stats.currentStreakDays,
                "bestStreakDays" to stats.bestStreakDays,
                "reviewsToday" to stats.reviewsToday,
                "totalReviews" to stats.totalReviews,
                "achievements" to stats.achievements
            ),
            "cards" to cards.associate { card ->
                card.id to mapOf(
                    "front" to card.front,
                    "back" to card.back,
                    "nextReviewAt" to card.nextReviewAt,
                    "updatedAt" to card.updatedAt
                )
            }
        )

        db.reference
            .child("users")
            .child(user.uid)
            .child("studypulse")
            .setValue(payload)
            .await()

        "Firebase sync ok (uid=${user.uid.take(8)}...)"
    }
}

data class CloudCard(
    val id: String,
    val front: String,
    val back: String,
    val nextReviewAt: Long,
    val updatedAt: Long
)

data class CloudStats(
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val reviewsToday: Int,
    val totalReviews: Int,
    val achievements: List<String>
)
