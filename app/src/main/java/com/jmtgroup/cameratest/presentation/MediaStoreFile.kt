package com.jmtgroup.cameratest.presentation

import android.net.Uri
import java.io.File

data class MediaStoreFile(
    val uri: Uri,
    val file: File,
    val id: Long
)
