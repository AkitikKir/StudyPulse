package com.growl.studypulse.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.growl.studypulse.R
import com.growl.studypulse.AppContainer

class ReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val repository = AppContainer.repository(applicationContext)
        val dueCount = repository.getDueCount()

        if (dueCount <= 0) return Result.success()

        ensureChannel()
        maybeShowNotification(dueCount)
        return Result.success()
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "StudyPulse Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminds you to complete a 5-minute repetition session"
        }
        manager.createNotificationChannel(channel)
    }

    private fun maybeShowNotification(dueCount: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val text = "📚 Пора повторить $dueCount карточек. Это займет всего 5 минут!"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("StudyPulse")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(REMINDER_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "studypulse_reminders"
        private const val REMINDER_ID = 1201
    }
}
