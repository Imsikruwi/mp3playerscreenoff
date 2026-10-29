package com.example.tvmp3

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.content.ContentUris

object MusicRepository {
    fun load(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val uri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                list += MediaItem.Builder()
                    .setMediaId(id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(c.getString(1) ?: "Tanpa judul")
                            .setArtist(c.getString(2)?.takeIf { it != "<unknown>" } ?: "")
                            .build()
                    ).build()
            }
        }
        return list
    }
}
