package com.android.music.data.model

data class Folder(
    val id: Long,
    val name: String,
    val path: String,
    val songCount: Int = 0,
    val videoCount: Int = 0
)
