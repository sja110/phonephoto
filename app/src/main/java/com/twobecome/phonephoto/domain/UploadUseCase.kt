package com.twobecome.phonephoto.domain

import android.content.Context
import com.twobecome.phonephoto.data.ImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// domain/UploadUseCase.kt
class UploadUseCase @Inject constructor(
    private val repository: ImageRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun uploadTodayImages(): Int {
        val images = repository.getTodayImages(context)
        return images.size
//        var successCount = 0
//        images.forEach { uri ->
//            val success = repository.uploadImageToOTTBox(uri, context)
//            if (success) successCount++
//        }
//        return successCount
    }
}