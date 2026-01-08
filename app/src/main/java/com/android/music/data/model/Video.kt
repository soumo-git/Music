package com.android.music.data.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

data class Video(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val thumbnailUri: Uri?,
    val dateAdded: Long = 0
) : Parcelable {
    val formattedDuration: String
        get() {
            val minutes = (duration / 1000) / 60
            val seconds = (duration / 1000) % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readParcelable(Uri::class.java.classLoader),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeLong(duration)
        parcel.writeString(path)
        parcel.writeParcelable(thumbnailUri, flags)
        parcel.writeLong(dateAdded)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Video> {
        override fun createFromParcel(parcel: Parcel): Video = Video(parcel)
        override fun newArray(size: Int): Array<Video?> = arrayOfNulls(size)
    }
}
