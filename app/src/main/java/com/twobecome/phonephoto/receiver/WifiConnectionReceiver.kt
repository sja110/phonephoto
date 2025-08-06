package com.twobecome.phonephoto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.twobecome.phonephoto.ui.main.MainUiIntent
import com.twobecome.phonephoto.ui.main.MainViewModel

class WifiConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val isConnected = (context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?.activeNetworkInfo?.let { it.isConnected && it.type == ConnectivityManager.TYPE_WIFI } ?: false

        if (isConnected) {
            val viewModelStoreOwner = context as? ViewModelStoreOwner ?: return
            val viewModel = ViewModelProvider(viewModelStoreOwner)[MainViewModel::class.java]
            viewModel.onIntent(MainUiIntent.WifiConnected)
        }
    }
}