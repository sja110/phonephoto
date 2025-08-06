package com.twobecome.phonephoto.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobecome.phonephoto.domain.UploadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val uploadUseCase: UploadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onIntent(intent: MainUiIntent) {
        when (intent) {
            is MainUiIntent.WifiConnected -> {
                _uiState.update { it.copy(isWifiConnected = true) }
                onIntent(MainUiIntent.StartImageUpload)
            }

            is MainUiIntent.StartImageUpload -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isUploading = true) }
                    try {
                        val count = uploadUseCase.uploadTodayImages()
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadSuccessCount = count,
                                uploadError = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadError = e.message
                            )
                        }
                    }
                }
            }

            else -> {}
        }
    }
}