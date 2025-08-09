package com.twobecome.phonephoto.ui.main

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

@HiltViewModel
class WifiViewModel @Inject constructor() : ViewModel() {
    private val _ssid = mutableStateOf("Unknown")
    val ssid: State<String> get() = _ssid

    fun updateSsid(newSsid: String) {
        _ssid.value = newSsid
    }

}