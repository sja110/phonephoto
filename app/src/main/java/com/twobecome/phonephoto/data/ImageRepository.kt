package com.twobecome.phonephoto.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// data/ImageRepository.kt
class ImageRepository @Inject constructor() {

    fun getTodayImages(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val todayStartMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } else {
            Toast.makeText(context, "API 레벨이 24 이하입니다.", Toast.LENGTH_SHORT).show()
        }

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN),
            "${MediaStore.Images.Media.DATE_TAKEN} >= ?",
            arrayOf(todayStartMillis.toString()),
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                imageUris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
            }
        }

        return imageUris
    }

    fun uploadImageToOTTBox(uri: Uri, context: Context): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val imageData = inputStream?.readBytes() ?: return false
            inputStream.close()

            val url = URL("http://192.168.0.100:8080/upload")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { it.write(imageData) }

            connection.responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}