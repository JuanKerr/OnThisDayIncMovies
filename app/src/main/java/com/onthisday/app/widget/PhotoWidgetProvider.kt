package com.onthisday.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.onthisday.app.R
import com.onthisday.app.data.MediaRepository
import com.onthisday.app.data.Prefs
import com.onthisday.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PhotoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_photo)

        // Tap → open app
        val tapIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_image, tapIntent)

        // Load a random photo on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            val repo   = MediaRepository(context)
            val prefs  = Prefs(context)
            val cal    = Calendar.getInstance()
            val photos = repo.getPhotosOnThisDay(
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.YEAR),
                prefs.selectedFolders
            )

            if (photos.isEmpty()) {
                withContext(Dispatchers.Main) { manager.updateAppWidget(widgetId, views) }
                return@launch
            }

            val photo = photos.random()
            val bitmap: Bitmap? = try {
                context.contentResolver.openInputStream(photo.uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) { null }

            val fmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
            val dateStr = fmt.format(java.util.Date(photo.dateTaken))

            withContext(Dispatchers.Main) {
                if (bitmap != null) views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setTextViewText(R.id.widget_date, dateStr)
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}
