package com.onthisday.app.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val year: Int,
    val bucketName: String,
    val isVideo: Boolean = false
)

sealed class GalleryItem {
    data class Header(val year: Int, val count: Int) : GalleryItem()
    data class PhotoItem(val photo: Photo) : GalleryItem()
}
