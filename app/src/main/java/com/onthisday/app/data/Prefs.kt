package com.onthisday.app.data

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("onthisday_prefs", Context.MODE_PRIVATE)

    /** Folders the user has ticked. Empty set = all folders included. */
    var selectedFolders: Set<String>
        get() = prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_FOLDERS, value) }

    /** Whether the user has made any explicit selection yet. */
    val hasSelection: Boolean
        get() = prefs.contains(KEY_FOLDERS)

    /** Whether video files should be included alongside images. */
    var includeMovies: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_MOVIES, false)
        set(value) = prefs.edit { putBoolean(KEY_INCLUDE_MOVIES, value) }

    companion object {
        private const val KEY_FOLDERS        = "selected_folders"
        private const val KEY_INCLUDE_MOVIES = "include_movies"
    }
}
