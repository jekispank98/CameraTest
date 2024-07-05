package com.jmtgroup.cameratest.presentation

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreUtil(private val context: Context) {

    val mediaStoreCollection: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        context.getExternalFilesDir(null)?.toUri()
    }

    private suspend fun getMediaStoreImageCursor(mediaStoreCollection: Uri): Cursor? {
        var cursor: Cursor?
        withContext(Dispatchers.IO) {
            val projection = arrayOf(imageDataColumnIndex, imageIdColumnIndex)
            val sortOrder = "DATE_ADDED DESC"
            cursor = context.contentResolver.query(
                mediaStoreCollection, projection, null, null, sortOrder
            )
        }
        return cursor
    }

    suspend fun getLatestImageFilename(): String? {
        var filename: String?
        if (mediaStoreCollection == null) return null

        getMediaStoreImageCursor(mediaStoreCollection).use { cursor ->
            if (cursor?.moveToFirst() != true) return null
            filename = cursor.getString(cursor.getColumnIndexOrThrow(imageDataColumnIndex))
        }

        return filename
    }

    suspend fun getImages(): MutableList<MediaStoreFile> {
        val files = mutableListOf<MediaStoreFile>()
        if (mediaStoreCollection == null) return files

        getMediaStoreImageCursor(mediaStoreCollection).use { cursor ->
            val imageDataColumn = cursor?.getColumnIndexOrThrow(imageDataColumnIndex)
            val imageIdColumn = cursor?.getColumnIndexOrThrow(imageIdColumnIndex)

            if (cursor != null && imageDataColumn != null && imageIdColumn != null) {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(imageIdColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val contentFile = File(cursor.getString(imageDataColumn))
                    files.add(MediaStoreFile(contentUri, contentFile, id))
                }
            }
        }

        return files
    }

    companion object {
        @Suppress("DEPRECATION")
        private const val imageDataColumnIndex = MediaStore.Images.Media.DATA
        private const val imageIdColumnIndex = MediaStore.Images.Media._ID
    }
}
