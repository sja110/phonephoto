package com.twobecome.phonephoto

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.SplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen().setOnExitAnimationListener { splash ->
                // 남아있는 시스템 스플래시 뷰를 즉시 제거
                splash.remove()
            }
        }
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AnimatedSplash(
                    onFinished = {
                        startActivity(Intent(this, MainActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                )
            }
        }
    }
}
@Composable
fun AnimatedSplash(
    onFinished: () -> Unit,
    title: String = "Phone-Photo",
    durationMillis: Int = 1400
) {
    // 0→1 진행도
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
        delay(60)
        onFinished()
    }
    val p = progress.value

    // 은은한 배경: 살짝 움직이는 듀얼 그라디언트 (저자극)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // 깜빡임 방지용 베이스
    ) {
        SubtleGradientBackdrop(progress = p)

        // 로고: 알파/스케일/리프트만 살짝
        val logoAlpha  = lerp(0f, 1f, easeInOut(p, 0.05f, 0.45f))
        val logoScale  = lerp(0.96f, 1.0f, Overshoot(1.2f).transform(easeInOut(p, 0.10f, 0.60f)))
        val logoLiftPx = lerp(14f, 0f, easeInOut(p, 0.10f, 0.60f)) // 아래서 위로 14px

        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                        translationY = logoLiftPx
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(52.dp)
                )
            }

            // 타이틀: 천천히 페이드인
            val titleAlpha = lerp(0f, 1f, easeInOut(p, 0.35f, 0.90f))
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = titleAlpha),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        // 하단 얇은 진행선 (미세 딜레이로 등장 → 끝나며 살짝 사라짐)
        val barIn  = easeInOut(p, 0.12f, 0.35f)
        val barOut = 1f - easeInOut(p, 0.88f, 1.00f)
        val barAlpha = (barIn * barOut).coerceIn(0f, 1f)

        SubtleProgressBar(
            progress = p,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 28.dp)
                .alpha(barAlpha)
        )
    }
}

@Composable
private fun SubtleGradientBackdrop(progress: Float) {
    // 두 개의 광원이 천천히 엇갈리며 이동하는 간단한 드리프트
    val t1 = (progress * 1.2f * 2f * PI).toFloat()
    val t2 = (progress * 0.9f  * 2f * PI).toFloat()

    val c1 = androidx.compose.ui.geometry.Offset(
        x = (0.5f + 0.20f * cos(t1)).toFloat(),
        y = (0.5f + 0.12f * sin(t1)).toFloat()
    )
    val c2 = androidx.compose.ui.geometry.Offset(
        x = (0.5f + 0.18f * cos(t2 + 0.8f)).toFloat(),
        y = (0.5f + 0.10f * sin(t2 + 0.8f)).toFloat()
    )

    Canvas(Modifier.fillMaxSize()) {
        val p1 = androidx.compose.ui.geometry.Offset(size.width * c1.x, size.height * c1.y)
        val p2 = androidx.compose.ui.geometry.Offset(size.width * c2.x, size.height * c2.y)

        // 메인 그라디언트 (저채도)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF2A2A2A),
                    Color(0xFF1E2630),
                    Color(0xFF2C2540)
                ),
                start = p1, end = p2
            ),
            size = size,
            alpha = 1f
        )
        // 은은한 라디얼 글로우 2개 (아주 약하게)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF).copy(alpha = 0.08f), Color.Transparent),
                center = p1, radius = size.minDimension * 0.55f
            ),
            radius = size.minDimension * 0.55f, center = p1
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF).copy(alpha = 0.06f), Color.Transparent),
                center = p2, radius = size.minDimension * 0.50f
            ),
            radius = size.minDimension * 0.50f, center = p2
        )
    }
}

@Composable
private fun SubtleProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    // 얇은 라인 형태 프로그레스
    Box(
        modifier
            .height(4.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .background(Color.White.copy(alpha = 0.65f))
        )
    }
}

// ── 작은 유틸 ────────────────────────────────────────────────
private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
private fun easeInOut(p: Float, startAt: Float, endAt: Float): Float {
    val x = ((p - startAt) / (endAt - startAt)).coerceIn(0f, 1f)
    return if (x < 0.5f) 2f * x * x else -1f + (4f - 2f * x) * x
}
private class Overshoot(private val tension: Float = 1.3f) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction
        return ((tension + 1) * t - tension) * t * t
    }
}