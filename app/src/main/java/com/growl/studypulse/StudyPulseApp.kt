package com.growl.studypulse

import android.app.Application
import com.growl.studypulse.notifications.ReminderScheduler

class StudyPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.schedule(this)
    }
}
