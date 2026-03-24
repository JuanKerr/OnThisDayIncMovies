package com.onthisday.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onthisday.app.R
import com.onthisday.app.data.MediaRepository
import com.onthisday.app.data.Prefs
import com.onthisday.app.ui.MainActivity
import java.util.Calendar

class DailyNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "on_this_day_daily"
    }

    override suspend fun doWork(): Result {
        val repo  = MediaRepository(context)
        val prefs = Prefs(context)
        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val day   = cal.get(Calendar.DAY_OF_MONTH)
        val year  = cal.get(Calendar.YEAR)

        val photos = repo.getPhotosOnThisDay(month, day, year, prefs.selectedFolders)
        if (photos.isEmpty()) return Result.success()

        createChannel()

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val years = photos.map { it.year }.distinct().sorted()
        val yearsText = when {
            years.size == 1 -> years[0].toString()
            years.size <= 3 -> years.dropLast(1).joinToString(", ") + " & " + years.last()
            else            -> years.take(2).joinToString(", ") + " & ${years.size - 2} more"
        }

        val title = context.resources.getQuantityString(
            R.plurals.notif_title, photos.size, photos.size)
        val body  = context.getString(R.string.notif_body, yearsText)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, notif)

        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily memories",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Shows today's on-this-day memories" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
