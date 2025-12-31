package com.android.music.data.model

import android.net.Uri

data class Video(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val thumbnailUri: Uri?,
    val dateAdded: Long = 0
) {
    val formattedDuration: String
        get() {
            val minutes = (duration / 1000) / 60
            val seconds = (duration / 1000) % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
