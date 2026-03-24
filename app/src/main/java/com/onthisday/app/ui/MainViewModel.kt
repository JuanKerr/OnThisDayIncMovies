package com.onthisday.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.onthisday.app.data.BucketInfo
import com.onthisday.app.data.GalleryItem
import com.onthisday.app.data.MediaRepository
import com.onthisday.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = MediaRepository(app)
    private val prefs = Prefs(app)

    private val _galleryItems = MutableLiveData<List<GalleryItem>>()
    val galleryItems: LiveData<List<GalleryItem>> = _galleryItems

    private val _allBuckets = MutableLiveData<List<BucketInfo>>()
    val allBuckets: LiveData<List<BucketInfo>> = _allBuckets

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val today     = Calendar.getInstance()
    val currentYear: Int  = today.get(Calendar.YEAR)

    // These are only ever read/written on the main thread
    var selectedMonth: Int = today.get(Calendar.MONTH) + 1   // 1-based
        private set
    var selectedDay: Int   = today.get(Calendar.DAY_OF_MONTH)
        private set

    private val _selectedDateLabel = MutableLiveData<String>()
    val selectedDateLabel: LiveData<String> = _selectedDateLabel

    init {
        updateDateLabel()
    }

    fun loadBuckets() {
        viewModelScope.launch {
            val buckets = withContext(Dispatchers.IO) { repo.getAllBuckets() }
            _allBuckets.value = buckets
        }
    }

    fun loadPhotos() {
        // Capture the values NOW on the main thread so the coroutine can't see stale data
        val month   = selectedMonth
        val day     = selectedDay
        val year    = currentYear
        val folders       = prefs.selectedFolders
        val includeMovies = prefs.includeMovies

        _loading.value = true

        viewModelScope.launch {
            val photos = withContext(Dispatchers.IO) {
                repo.getPhotosOnThisDay(month, day, year, folders, includeMovies)
            }

            // Group by year, most recent first
            val grouped = photos.groupBy { it.year }
            val items   = mutableListOf<GalleryItem>()
            grouped.keys.sortedDescending().forEach { yr ->
                val yearPhotos = grouped[yr]!!
                items.add(GalleryItem.Header(yr, yearPhotos.size))
                yearPhotos.forEach { items.add(GalleryItem.PhotoItem(it)) }
            }

            _galleryItems.value = items
            _loading.value = false
        }
    }

    fun setDate(month: Int, day: Int) {
        selectedMonth = month
        selectedDay   = day
        updateDateLabel()
        loadPhotos()
    }

    fun getSelectedFolders(): Set<String> = prefs.selectedFolders

    fun setSelectedFolders(folders: Set<String>) {
        prefs.selectedFolders = folders
        loadPhotos()
    }

    fun getIncludeMovies(): Boolean = prefs.includeMovies

    fun setIncludeMovies(include: Boolean) {
        prefs.includeMovies = include
        loadPhotos()
    }

    private fun updateDateLabel() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, selectedMonth - 1)
            set(Calendar.DAY_OF_MONTH, selectedDay)
        }
        _selectedDateLabel.value = SimpleDateFormat("d MMMM", Locale.getDefault()).format(cal.time)
    }
}
