package com.android.music.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.net.toUri
import com.android.music.data.model.Album
import com.android.music.data.model.Artist
import com.android.music.data.model.Folder
import com.android.music.data.model.Song
import com.android.music.data.model.SortOption
import com.android.music.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val contentResolver: ContentResolver) {

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown artist"
                val album = cursor.getString(albumColumn) ?: "Unknown album"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(pathColumn) ?: ""
                val albumId = cursor.getLong(albumIdColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)

                val albumArtUri = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(),
                    albumId
                )

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        path = path,
                        albumArtUri = albumArtUri,
                        dateAdded = dateAdded
                    )
                )
            }
        }

        songs
    }

    suspend fun getAllVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED
        )

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(pathColumn) ?: ""
                val dateAdded = cursor.getLong(dateAddedColumn)

                videos.add(
                    Video(
                        id = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        path = path,
                        thumbnailUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        ),
                        dateAdded = dateAdded
                    )
                )
            }
        }

        videos
    }

    suspend fun getAllArtists(songs: List<Song>): List<Artist> = withContext(Dispatchers.IO) {
        val artistMap = mutableMapOf<String, Artist>()

        songs.forEach { song ->
            val artistName = song.artist
            artistMap[artistName] = artistMap[artistName]?.copy(
                songCount = (artistMap[artistName]?.songCount ?: 0) + 1
            ) ?: Artist(
                id = artistName.hashCode().toLong(),
                name = artistName,
                songCount = 1
            )
        }

        artistMap.values.sortedBy { it.name }
    }

    suspend fun getAllAlbums(songs: List<Song>): List<Album> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<String, Album>()

        songs.forEach { song ->
            val albumKey = "${song.album}|${song.artist}"
            albumMap[albumKey] = albumMap[albumKey]?.copy(
                songCount = (albumMap[albumKey]?.songCount ?: 0) + 1
            ) ?: Album(
                id = albumKey.hashCode().toLong(),
                title = song.album,
                artist = song.artist,
                songCount = 1,
                albumArtUri = song.albumArtUri
            )
        }

        albumMap.values.sortedBy { it.title }
    }

    suspend fun getAllFolders(songs: List<Song>, videos: List<Video>): List<Folder> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, Folder>()

        songs.forEach { song ->
            val folder = File(song.path).parentFile
            folder?.let {
                val folderPath = it.absolutePath
                folderMap[folderPath] = folderMap[folderPath]?.copy(
                    songCount = (folderMap[folderPath]?.songCount ?: 0) + 1
                ) ?: Folder(
                    id = folderPath.hashCode().toLong(),
                    name = it.name,
                    path = folderPath,
                    songCount = 1
                )
            }
        }

        videos.forEach { video ->
            val folder = File(video.path).parentFile
            folder?.let {
                val folderPath = it.absolutePath
                folderMap[folderPath] = folderMap[folderPath]?.copy(
                    videoCount = (folderMap[folderPath]?.videoCount ?: 0) + 1
                ) ?: Folder(
                    id = folderPath.hashCode().toLong(),
                    name = it.name,
                    path = folderPath,
                    videoCount = 1
                )
            }
        }

        folderMap.values.sortedBy { it.name }
    }

    fun getSongsForArtist(songs: List<Song>, artistName: String): List<Song> {
        return songs.filter { it.artist == artistName }
    }

    fun getSongsForAlbum(songs: List<Song>, albumTitle: String, artistName: String): List<Song> {
        return songs.filter { it.album == albumTitle && it.artist == artistName }
    }

    fun getSongsForFolder(songs: List<Song>, folderPath: String): List<Song> {
        return songs.filter { File(it.path).parent == folderPath }
    }

    fun sortSongs(songs: List<Song>, sortOption: SortOption): List<Song> {
        return when (sortOption) {
            SortOption.ADDING_TIME -> songs.sortedByDescending { it.dateAdded }
            SortOption.NAME -> songs.sortedBy { it.title.lowercase() }
            SortOption.PLAY_COUNT -> songs.sortedByDescending { it.playCount }
        }
    }

    fun searchSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        val lowerQuery = query.lowercase()
        return songs.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.artist.lowercase().contains(lowerQuery) ||
            it.album.lowercase().contains(lowerQuery)
        }
    }
}
