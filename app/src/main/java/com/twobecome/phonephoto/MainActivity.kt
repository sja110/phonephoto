package com.twobecome.phonephoto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.twobecome.phonephoto.data.ImageRepository
import com.twobecome.phonephoto.domain.UploadUseCase
import com.twobecome.phonephoto.ui.main.MainViewModel
import com.twobecome.phonephoto.ui.theme.PhonephotoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { state ->
                if (state.isUploading) {
                    Log.d("Upload", "업로드 중...")
                } else if (state.uploadError != null) {
                    Log.e("Upload", "실패: ${state.uploadError}")
                } else if (state.uploadSuccessCount > 0) {
                    Log.d("Upload", "성공: ${state.uploadSuccessCount}개")
                }
            }
        }

        checkPermissions()

    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // OK
        } else {
            Toast.makeText(this, "이미지 접근 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        }
    }

    override fun onResume() {
        super.onResume()

        Toast.makeText(this, "오늘 생성된사진 갯수 : "+ImageRepository().getTodayImages(this).size, Toast.LENGTH_LONG).show()
    }

}