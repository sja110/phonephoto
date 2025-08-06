package com.twobecome.phonephoto.ui.main

data class MainUiState(
    val isWifiConnected: Boolean = false,
    val isUploading: Boolean = false,
    val uploadSuccessCount: Int = 0,
    val uploadError: String? = null
)