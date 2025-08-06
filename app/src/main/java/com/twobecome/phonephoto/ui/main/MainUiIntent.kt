package com.twobecome.phonephoto.ui.main

sealed class MainUiIntent {
    object WifiConnected : MainUiIntent()
    object StartImageUpload : MainUiIntent()
    data class UploadCompleted(val successCount: Int) : MainUiIntent()
    data class UploadFailed(val error: String) : MainUiIntent()
}