package com.twobecome.phonephoto.ui.viewer

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.twobecome.phonephoto.config.AppProfile
import com.twobecome.phonephoto.thumb.ShimmerBox
import com.twobecome.phonephoto.thumb.ThumbnailEngineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val uris = intent.getStringArrayListExtra(EXTRA_URIS)?.map(Uri::parse).orEmpty()
        setContent {
            MaterialTheme {
                ViewerScreen(
                    resolver = contentResolver,
                    uris = uris,
                    startIndex = startIndex,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_START_INDEX = "startIndex"
        const val EXTRA_URIS = "uris"
    }
}
@Composable
fun ViewerScreen(
    resolver: ContentResolver,
    uris: List<Uri>,
    startIndex: Int,
    onClose: () -> Unit
) {
    // 엔진: 뷰어는 큰 사이즈 권장
    val engine = rememberThumbEngine(defaultSize = maxOf(1200, AppProfile.THUMB_SIZE))
    val VIEWER_SIZE = 2000

    val pagerScope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) } // 0f..2f
    var contrast   by remember { mutableFloatStateOf(1f) } // 0.5f..1.8f
    var rotation   by remember { mutableFloatStateOf(0f) } // degree
    var cropPreset by remember { mutableStateOf<CropPreset?>(null) } // null=원본
    var saving by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) } // 비교(보정 끄기)

    var currentPage by rememberSaveable { mutableIntStateOf(startIndex.coerceIn(0, (uris.size - 1).coerceAtLeast(0))) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // 합성 컬러 매트릭스 (밝기*대비*채도)
    val cm = remember(brightness, contrast, saturation) {
        val contrastM = run {
            val c = contrast
            val t = (1f - c) * 128f
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        }
        val satM = run {
            val s = saturation
            val inv = 1f - s
            val R = 0.213f * inv
            val G = 0.715f * inv
            val B = 0.072f * inv
            floatArrayOf(
                R + s, G    , B    , 0f, 0f,
                R    , G + s, B    , 0f, 0f,
                R    , G    , B + s, 0f, 0f,
                0f   , 0f   , 0f   , 1f, 0f
            )
        }
        val briM = run {
            val br = brightness
            floatArrayOf(
                br, 0f, 0f, 0f, 0f,
                0f, br, 0f, 0f, 0f,
                0f, 0f, br, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }
        ColorMatrix(mulColorMatrix(mulColorMatrix(briM, contrastM), satM))
    }

    // 현재 페이지 추적
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { idx -> currentPage = idx }
    }

    // 현재 페이지 주변 프리페치
    LaunchedEffect(currentPage, uris) {
        for (i in (currentPage - 10)..(currentPage + 14)) {
            engine.prefetchIndex(i, uris, size = 240) // 썸네일
        }
        for (i in (currentPage + 1)..(currentPage + 2)) {
            engine.prefetchIndex(i, uris, size = VIEWER_SIZE) // 뷰어
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 메인 페이저
        LazyRow(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(uris, key = { _, u -> u.toString() }) { _, uri ->
                Box(Modifier.fillParentMaxSize()) {
                    val bmp by produceState<Bitmap?>(initialValue = null, key1 = uri) {
                        value = engine.loadOrCreate(uri, size = VIEWER_SIZE)
                    }
                    // 줌/이동 상태
                    var scale by remember(uri) { mutableFloatStateOf(1f) }
                    var offset by remember(uri) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                    if (bmp == null) {
                        ShimmerBox(modifier = Modifier.fillMaxSize())
                    } else {
                        Image(
                            bitmap = bmp!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = if (comparing) null else ColorFilter.colorMatrix(cm),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rotation
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                                // 편집 모드: 두 손가락 이상 제스처만 우리가 소비(한 손가락은 페이저)
                                .pointerInput(editing) {
                                    if (editing) {
                                        awaitEachGesture {
                                            do {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.any { it.pressed }
                                                if (!pressed) break
                                                if (event.changes.size >= 2) {
                                                    val zoomChange = event.calculateZoom()
                                                    val panChange = event.calculatePan()
                                                    scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                                                    offset += panChange
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } while (true)
                                        }
                                    }
                                }
                                // 롱프레스 비교(원본 보기): 길게 눌렀을 때만 comparing = true, 놓으면 false
                                .pointerInput(Unit) {
                                    // 단순 long-press 비교 (드래그 스와이프와 충돌 적음)
                                    detectTapGestures(
                                        onLongPress = { comparing = true },
                                        onPress = {
                                            try { awaitRelease() } finally { comparing = false }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }

        // 상단 바
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            Text(
                text = "${(currentPage + 1).coerceAtMost(uris.size.coerceAtLeast(1))} / ${uris.size}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 비교 토글
                IconToggleButton(checked = comparing, onCheckedChange = { comparing = it }) {
                    Icon(if (comparing) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.White)
                }
                FilledTonalIconButton(onClick = { editing = !editing }) { Icon(Icons.Default.Edit, null) }
            }
        }

        // 하단: 편집 패널(있으면) + 썸네일 스트립(항상)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            if (editing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x88000000))
                        .padding(12.dp)
                ) {
                    // 밝기/채도/대비
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("밝기", color = Color.White)
                        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.5f..1.5f, modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("채도", color = Color.White)
                        Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f, modifier = Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("대비", color = Color.White)
                        Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.8f, modifier = Modifier.weight(1f))
                    }

                    // 회전/저장
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { rotation = (rotation - 90f) % 360f }) { Icon(Icons.Default.RotateLeft, null, tint = Color.White) }
                        IconButton(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.Default.RotateRight, null, tint = Color.White) }

                        // 크롭 프리셋
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = cropPreset == CropPreset.SQUARE, onClick = { cropPreset = CropPreset.SQUARE }, label = { Text("1:1") })
                            Spacer(Modifier.width(6.dp))
                            FilterChip(selected = cropPreset == CropPreset.R4_3, onClick = { cropPreset = CropPreset.R4_3 }, label = { Text("4:3") })
                            Spacer(Modifier.width(6.dp))
                            FilterChip(selected = cropPreset == CropPreset.R16_9, onClick = { cropPreset = CropPreset.R16_9 }, label = { Text("16:9") })
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = {
                                brightness = 1f; saturation = 1f; contrast = 1f; rotation = 0f; cropPreset = null
                            }) { Text("Reset") }
                        }

                        val scopeLocal = rememberCoroutineScope()
                        IconButton(onClick = {
                            scopeLocal.launch {
                                saving = true
                                saveEdited(
                                    cr = resolver,
                                    srcUri = uris[currentPage],
                                    brightness = brightness,
                                    rotation = rotation,
                                    contrast = contrast,
                                    saturation = saturation,
                                    cropPreset = cropPreset
                                )
                                saving = false
                            }
                        }) { Icon(Icons.Default.Save, null, tint = Color.White) }
                    }
                }
            }

            ThumbnailStrip(
                engine = engine,
                uris = uris,
                current = currentPage,
                onClickIndex = { target -> pagerScope.launch { state.animateScrollToItem(target) } }
            )
        }

        if (saving) {
            Box(
                Modifier.fillMaxSize().background(Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 엔진 remember (뷰어에서 1개 재사용)
// ──────────────────────────────────────────────────────────────
@Composable
private fun rememberThumbEngine(defaultSize: Int = AppProfile.THUMB_SIZE): ThumbnailEngineV2 {
    val ctx = LocalContext.current
    val engine = remember { ThumbnailEngineV2(ctx, defaultSize = defaultSize) }
    DisposableEffect(Unit) { onDispose { engine.close() } }
    return engine
}

// ──────────────────────────────────────────────────────────────
// 하단 썸네일 스트립 (엔진 + ShimmerBox + 프리페치)
// ──────────────────────────────────────────────────────────────
@Composable
private fun ThumbnailStrip(
    engine: ThumbnailEngineV2,
    uris: List<Uri>,
    current: Int,
    onClickIndex: (Int) -> Unit
) {
    val stripState = rememberLazyListState()
    val STRIP_SIZE = 240

    LaunchedEffect(current) {
        val target = (current - 2).coerceIn(0, (uris.size - 1).coerceAtLeast(0))
        stripState.animateScrollToItem(target)
    }
    LaunchedEffect(current, uris) {
        for (i in (current - 10)..(current + 14)) {
            engine.prefetchIndex(i, uris, size = STRIP_SIZE)
        }
    }

    LazyRow(
        state = stripState,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(Color(0xAA000000))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(uris, key = { _, uri -> uri.toString() }) { index, uri ->
            val isSelected = index == current
            val shape = RoundedCornerShape(8.dp)
            val borderWidth = if (isSelected) 2.dp else 1.dp
            val borderColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                Color.White.copy(alpha = 0.5f)

            val thumb by produceState<Bitmap?>(initialValue = null, key1 = uri) {
                value = engine.loadOrCreate(uri, size = STRIP_SIZE)
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(shape)
                    .border(BorderStroke(borderWidth, borderColor), shape)
                    .clickable { onClickIndex(index) }
            ) {
                if (thumb == null) {
                    ShimmerBox(modifier = Modifier.fillMaxSize(), corner = shape)
                } else {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 저장: 밝기/채도/대비/회전 + 센터 크롭 프리셋 적용
// ──────────────────────────────────────────────────────────────
private enum class CropPreset(val w: Int, val h: Int) { SQUARE(1,1), R4_3(4,3), R16_9(16,9) }

private suspend fun saveEdited(
    cr: ContentResolver,
    srcUri: Uri,
    brightness: Float,
    rotation: Float,
    contrast: Float = 1f,
    saturation: Float = 1f,
    cropPreset: CropPreset? = null
) = withContext(Dispatchers.IO) {
    val bmp = loadBitmapBlocking(cr, srcUri, maxSide = 4000) ?: return@withContext

    // 1) 회전
    val oriented = run {
        val m = android.graphics.Matrix().apply { postRotate(rotation, bmp.width/2f, bmp.height/2f) }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    // 2) 센터 크롭 (프리셋)
    val cropped = if (cropPreset != null) {
        val w = oriented.width
        val h = oriented.height
        val targetRatio = cropPreset.w.toFloat() / cropPreset.h.toFloat()
        val curRatio = w.toFloat() / h.toFloat()
        val rect = if (curRatio > targetRatio) {
            val newW = (h * targetRatio).toInt()
            android.graphics.Rect((w - newW)/2, 0, (w + newW)/2, h)
        } else {
            val newH = (w / targetRatio).toInt()
            android.graphics.Rect(0, (h - newH)/2, w, (h + newH)/2)
        }
        Bitmap.createBitmap(oriented, rect.left, rect.top, rect.width(), rect.height())
    } else oriented

    // 3) ColorMatrix (android.graphics)
    val cm = android.graphics.ColorMatrix().apply {
        // 채도
        val sat = android.graphics.ColorMatrix()
        sat.setSaturation(saturation)
        postConcat(sat)
        // 대비(+오프셋)
        run {
            val c = contrast
            val t = (1f - c) * 128f
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        // 밝기
        run {
            val br = brightness
            val m = android.graphics.ColorMatrix()
            m.setScale(br, br, br, 1f)
            postConcat(m)
        }
    }

    val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        isFilterBitmap = true
    }
    android.graphics.Canvas(out).drawBitmap(cropped, 0f, 0f, paint)

    // 4) MediaStore 저장
    val filename = "PP_edited_${System.currentTimeMillis()}.jpg"
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhonePhotoEdited")
        }
    }

    val outUri = cr.insert(collection, values) ?: return@withContext
    cr.openOutputStream(outUri)?.use { os -> saveBitmapJpeg(out, os) }
}

private fun saveBitmapJpeg(bmp: Bitmap, os: OutputStream) {
    bmp.compress(Bitmap.CompressFormat.JPEG, 92, os)
    os.flush()
}

// 원본 로드(저장용)
private fun loadBitmapBlocking(cr: ContentResolver, uri: Uri, maxSide: Int = 2048): Bitmap? = try {
    val src = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(cr, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(cr, uri)
    }
    val w = src.width
    val h = src.height
    val scale = maxSide.toFloat() / maxOf(w, h)
    if (scale >= 1f) src else Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
} catch (_: Exception) { null }

// Compose ColorMatrix 4x5 곱셈
private fun mulColorMatrix(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0..3) {
        val r0 = a[row*5 + 0]; val r1 = a[row*5 + 1]; val r2 = a[row*5 + 2]; val r3 = a[row*5 + 3]; val r4 = a[row*5 + 4]
        for (col in 0..4) {
            val c0 = b[col + 0]
            val c1 = b[col + 5]
            val c2 = b[col +10]
            val c3 = b[col +15]
            out[row*5 + col] = r0*c0 + r1*c1 + r2*c2 + r3*c3 + if (col==4) r4 else 0f
        }
    }
    return out
}

// ──────────────────────────────────────────────────────────────
// 엔진 remember (뷰어에서 1개 재사용)
// ──────────────────────────────────────────────────────────────
