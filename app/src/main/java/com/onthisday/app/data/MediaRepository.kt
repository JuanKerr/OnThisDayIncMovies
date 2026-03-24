package com.onthisday.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MediaRepository(private val context: Context) {

    companion object {
        private const val TAG = "MediaRepository"
        private val EXIF_FMT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        private const val NO_DATE = -1L
    }

    private val cache = ExifCache(context)

    /**
     * Main query. Returns images (and optionally videos) taken on the given month/day
     * in any year prior to currentYear.
     */
    fun getPhotosOnThisDay(
        month: Int,
        day: Int,
        currentYear: Int,
        includedBuckets: Set<String>,
        includeMovies: Boolean = false
    ): List<Photo> {

        Log.d(TAG, "Querying: month=$month day=$day currentYear=$currentYear includeMovies=$includeMovies")

        val images = queryImages(month, day, currentYear, includedBuckets)
        val videos = if (includeMovies) queryVideos(month, day, currentYear, includedBuckets) else emptyList()

        val combined = (images + videos).sortedWith(
            compareByDescending<Photo> { it.year }.thenByDescending { it.dateTaken }
        )

        Log.d(TAG, "Matched: ${combined.size} (images=${images.size}, videos=${videos.size})")
        return combined
    }

    private fun queryImages(
        month: Int,
        day: Int,
        currentYear: Int,
        includedBuckets: Set<String>
    ): List<Photo> {

        val cachedDates = cache.loadAll().toMutableMap()
        val cachedAdded = cache.loadDateAdded().toMutableMap()

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        data class Row(
            val id: Long, val name: String, val dateAdded: Long,
            val dateTaken: Long, val dateModified: Long, val bucket: String
        )

        val allRows = mutableListOf<Row>()
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val addedCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val takenCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val bucketCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                allRows.add(Row(
                    id           = cursor.getLong(idCol),
                    name         = cursor.getString(nameCol) ?: "",
                    dateAdded    = cursor.getLong(addedCol),
                    dateTaken    = cursor.getLong(takenCol),
                    dateModified = cursor.getLong(modifiedCol),
                    bucket       = cursor.getString(bucketCol) ?: "Unknown"
                ))
            }
        }

        val toRead = allRows.filter { row ->
            val previousAdded = cachedAdded[row.id]
            previousAdded == null || previousAdded != row.dateAdded
        }

        if (toRead.isNotEmpty()) {
            val newEntries = mutableListOf<Triple<Long, Long, Long>>()
            for (row in toRead) {
                val uri = ContentUris.withAppendedId(collection, row.id)
                val exifMs = exifDateMs(uri)
                val dateMs = exifMs ?: mediaStoreDateMs(row.dateTaken, row.dateModified, row.dateAdded) ?: NO_DATE
                newEntries.add(Triple(row.id, dateMs, row.dateAdded))
                cachedDates[row.id] = dateMs
            }
            cache.putAll(newEntries)
        }

        val liveIds = allRows.map { it.id }.toSet()
        cache.deleteIds(cachedDates.keys.filter { it !in liveIds })

        val photos = mutableListOf<Photo>()
        for (row in allRows) {
            val dateMs = cachedDates[row.id] ?: NO_DATE
            if (dateMs == NO_DATE) continue

            val cal       = Calendar.getInstance().apply { timeInMillis = dateMs }
            val photoYear = cal.get(Calendar.YEAR)
            val photoMon  = cal.get(Calendar.MONTH) + 1
            val photoDay  = cal.get(Calendar.DAY_OF_MONTH)

            if (photoYear >= currentYear || photoYear < 1990) continue
            if (photoMon != month || photoDay != day) continue

            val bucketOk = includedBuckets.isEmpty() ||
                includedBuckets.any { it.equals(row.bucket, ignoreCase = true) }
            if (!bucketOk) continue

            photos.add(Photo(
                id          = row.id,
                uri         = ContentUris.withAppendedId(collection, row.id),
                displayName = row.name,
                dateTaken   = dateMs,
                year        = photoYear,
                bucketName  = row.bucket,
                isVideo     = false
            ))
        }

        return photos
    }

    private fun queryVideos(
        month: Int,
        day: Int,
        currentYear: Int,
        includedBuckets: Set<String>
    ): List<Photo> {

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val videos = mutableListOf<Photo>()
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val takenCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val addedCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id           = cursor.getLong(idCol)
                val name         = cursor.getString(nameCol) ?: ""
                val dateTaken    = cursor.getLong(takenCol)
                val dateModified = cursor.getLong(modifiedCol)
                val dateAdded    = cursor.getLong(addedCol)
                val bucket       = cursor.getString(bucketCol) ?: "Unknown"

                val dateMs = mediaStoreDateMs(dateTaken, dateModified, dateAdded) ?: NO_DATE
                if (dateMs == NO_DATE) continue

                val cal       = Calendar.getInstance().apply { timeInMillis = dateMs }
                val videoYear = cal.get(Calendar.YEAR)
                val videoMon  = cal.get(Calendar.MONTH) + 1
                val videoDay  = cal.get(Calendar.DAY_OF_MONTH)

                if (videoYear >= currentYear || videoYear < 1990) continue
                if (videoMon != month || videoDay != day) continue

                val bucketOk = includedBuckets.isEmpty() ||
                    includedBuckets.any { it.equals(bucket, ignoreCase = true) }
                if (!bucketOk) continue

                videos.add(Photo(
                    id          = id,
                    uri         = ContentUris.withAppendedId(collection, id),
                    displayName = name,
                    dateTaken   = dateMs,
                    year        = videoYear,
                    bucketName  = bucket,
                    isVideo     = true
                ))
            }
        }

        return videos
    }

    private fun exifDateMs(uri: Uri): Long? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val raw =
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (raw == null) return@use null
                val date = EXIF_FMT.parse(raw) ?: return@use null
                val ms   = date.time
                val year = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
                if (year in 1990..2100) ms else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mediaStoreDateMs(rawTaken: Long, rawModified: Long, rawAdded: Long): Long? {
        val candidates = mutableListOf<Long>()
        if (rawTaken    > 0L) { candidates.add(rawTaken); candidates.add(rawTaken * 1000L) }
        if (rawModified > 0L) candidates.add(rawModified * 1000L)
        if (rawAdded    > 0L) candidates.add(rawAdded    * 1000L)
        for (ms in candidates) {
            val year = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
            if (year in 1990..2100) return ms
        }
        return null
    }

    fun getAllBuckets(): List<BucketInfo> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val seen   = mutableSetOf<String>()
        val result = mutableListOf<BucketInfo>()
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val name     = cursor.getString(nameCol) ?: continue
                if (name.isBlank()) continue
                val filePath = cursor.getString(dataCol) ?: continue
                val dir      = filePath.substringBeforeLast('/')
                if (seen.add(name)) result.add(BucketInfo(name, dir))
            }
        }
        return result.sortedWith(compareBy { it.path })
    }
}

data class BucketInfo(val displayName: String, val path: String)
