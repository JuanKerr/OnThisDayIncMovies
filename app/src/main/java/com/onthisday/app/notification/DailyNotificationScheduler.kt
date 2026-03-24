package com.onthisday.app.notification

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyNotificationScheduler {

    private const val WORK_NAME = "daily_notification"

    fun schedule(context: Context) {
        // Schedule to fire at 9:00 AM each day
        val now = Calendar.getInstance()
        val next9am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delayMs = next9am.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
