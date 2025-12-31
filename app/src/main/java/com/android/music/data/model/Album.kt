package com.android.music.data.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int = 0,
    val albumArtUri: Uri? = null
)
