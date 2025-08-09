
package com.twobecome.phonephoto.ui.main.GalleryRoot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

// ----------------------- 데이터 모델 -----------------------
data class MediaItem(val uri: Uri, val takenAtMillis: Long)
data class MonthSection(val ym: YearMonth, val title: String, val items: List<MediaItem>)

// 화면 상태
private sealed interface Screen {
    data object Overview : Screen
    data class YearDetail(val year: Year) : Screen
    data class SearchResult(val title: String, val sections: List<MonthSection>) : Screen
}

// ----------------------- 엔트리 -----------------------
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GalleryRoot() {
    val context = LocalContext.current
    val activity = context as? Activity
    val cr = context.contentResolver
    val imageLoader = remember { createCustomImageLoader(context) }

    var allImages by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var photoCount by remember { mutableStateOf(0) }
    var videoCount by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Overview) }
    var inputValue by remember { mutableStateOf("") }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // 초기 로드
    LaunchedEffect(Unit) {
        val images = queryAllImagesWithDates(cr)
        allImages = images
        photoCount = images.size
        videoCount = queryAllVideosCount(cr)
    }

    // 화면 전환 시 키보드 숨김
    LaunchedEffect(screen) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // 디스크 사용량
    val (usedBytes, totalBytes) = remember { getDiskUsageBytes() }

    // 루트에서 백키 2번 종료
    var backPressedOnce by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = screen is Screen.Overview) {
        if (backPressedOnce) {
            activity?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "한번 더 뒤로가기를 누르면 앱이 종료됩니다", Toast.LENGTH_SHORT).show()
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                CenterAlignedTopAppBar(
                    title = {
                        val titleText = when (val s = screen) {
                            Screen.Overview -> "갤러리 개요"
                            is Screen.YearDetail -> "${s.year.value}년 사진"
                            is Screen.SearchResult -> s.title
                        }
                        SoftRevealTitle(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    navigationIcon = {} // 뒤로가기 아이콘 없음
                )

                UsageAndCountBar(
                    usedBytes = usedBytes,
                    totalBytes = totalBytes,
                    photoCount = photoCount,
                    videoCount = videoCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                SearchBarDaysOnly(
                    value = inputValue,
                    onValueChange = { inputValue = it.filter(Char::isDigit) },
                    onClear = { inputValue = "" },
                    onSearch = {
                        val days = inputValue.toLongOrNull() ?: return@SearchBarDaysOnly
                        val targetDate = LocalDate.now().minusDays(days)
                        val targetDateMillis = targetDate
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()

                        val sections = searchImagesByDate(allImages, targetDateMillis)
                        val title = "검색결과: ${targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                        screen = Screen.SearchResult(title, sections)
                    },
                    canGoBack = screen is Screen.SearchResult,
                    onBack = { screen = Screen.Overview },
                    animateKey = screen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Divider()
            }
        }
    ) { padding ->
        // 썸네일 캐시 워밍
        PrefetchThumbnailsOnce(imageLoader = imageLoader, items = allImages)

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val enter = slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn()
                val exit = slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut()
                enter togetherWith exit
            },
            label = "ScreenTransition"
        ) { s ->
            when (s) {
                Screen.Overview -> {
                    OverviewByYear(
                        allImages = allImages,
                        imageLoader = imageLoader,
                        onClickAny = { yearClicked -> screen = Screen.YearDetail(yearClicked) },
                        modifier = Modifier.padding(padding)
                    )
                }
                is Screen.YearDetail -> {
                    YearDetailScreen(
                        year = s.year,
                        allImages = allImages,
                        imageLoader = imageLoader,
                        onBack = { screen = Screen.Overview },
                        modifier = Modifier.padding(padding)
                    )
                }
                is Screen.SearchResult -> {
                    SearchResultScreen(
                        title = s.title,
                        sections = s.sections,
                        imageLoader = imageLoader,
                        onBack = { screen = Screen.Overview },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

// ----------------------- 타이틀: 글자 전체 소프트 리빌 -----------------------
@Composable
fun SoftRevealTitle(
    text: String,
    style: TextStyle,
    appearMillis: Int = 420,
    liftStiffness: Float = Spring.StiffnessLow,
    startLetterSpacingEm: Float = -0.02f,
    startScale: Float = 0.985f,
    startAlpha: Float = 0.88f,
    startTranslateYdp: Float = 6f
) {
    val density = LocalDensity.current
    val progress = remember(text) { Animatable(0f) }
    val lift = remember(text) { Animatable(startTranslateYdp) }

    LaunchedEffect(text) {
        progress.snapTo(0f)
        lift.snapTo(startTranslateYdp)
        progress.animateTo(1f, tween(appearMillis, easing = LinearOutSlowInEasing))
        lift.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = liftStiffness))
    }

    val p = progress.value
    val scale = startScale + (1f - startScale) * p
    val overallAlpha = startAlpha + (1f - startAlpha) * p
    val translateYpx = with(density) { (startTranslateYdp.dp.toPx() * (1f - p)) }
    val letterSpacingEm = startLetterSpacingEm * (1f - p)

    val color = style.color.takeOrElse { MaterialTheme.colorScheme.onSurface }
    val annotated = remember(text, p) {
        buildAnnotatedString {
            pushStyle(SpanStyle(color = color, letterSpacing = letterSpacingEm.em))
            append(text)
            pop()
        }
    }

    Text(
        text = annotated,
        style = style,
        maxLines = 1,
        modifier = Modifier.graphicsLayer(
            translationY = translateYpx,
            scaleX = scale,
            scaleY = scale,
            alpha = overallAlpha
        )
    )
}

// ----------------------- 디스크 사용량 + Wi-Fi + 카운트 -----------------------
@Composable
fun UsageAndCountBar(
    usedBytes: Long,
    totalBytes: Long,
    photoCount: Int,
    videoCount: Int,
    modifier: Modifier = Modifier
) {
    val usedGB = (usedBytes.toDouble() / 1_000_000_000).let { String.format("%.1f GB", it) }
    val totalGB = (totalBytes.toDouble() / 1_000_000_000).let { String.format("%.1f GB", it) }
    val targetProgress = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(targetProgress) {
        animProgress.animateTo(
            targetValue = targetProgress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 900)
        )
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("디스크 사용량", style = MaterialTheme.typography.labelLarge)
            Text("$usedGB / $totalGB", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = animProgress.value,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.height(8.dp))

        val wifiLabel by rememberWifiLabel()
        val connected = remember(wifiLabel) { wifiLabel.startsWith("Wi-Fi 연결됨") }
        val ssid = remember(wifiLabel) { wifiLabel.substringAfter(" / ", "") }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 왼쪽: Wi-Fi 상태 (연결됨=초록, 연결없음=빨강) + SSID
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📶 WI-FI ", style = MaterialTheme.typography.labelMedium)
                Crossfade(targetState = Pair(connected, ssid), label = "WifiStatusFade") { (isConn, ssidNow) ->
                    val statusText = if (isConn) "연결됨" else "연결없음"
                    val statusColor = if (isConn) Color(0xFF4CAF50) else Color(0xFFF44336)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (isConn && ssidNow.isNotBlank()) {
                            Text(
                                text = " / $ssidNow",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // 오른쪽: 사진/동영상 카운트
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "\uD83D\uDDBC️ 사진 : $photoCount / \uD83C\uDF9C️ 동영상 : $videoCount",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ----------------------- Wi-Fi 상태 관찰 -----------------------
@SuppressLint("MissingPermission")
private fun readCurrentSsid(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ssid = wifiManager.connectionInfo?.ssid ?: return null
    val clean = ssid.replace("\"", "")
    return if (clean.equals("<unknown ssid>", ignoreCase = true)) null else clean
}

/** "Wi-Fi 연결없음" | "Wi-Fi 연결됨" | "Wi-Fi 연결됨 / <SSID>" */
@Composable
fun rememberWifiLabel(): State<String> {
    val context = LocalContext.current
    val labelState = remember { mutableStateOf("Wi-Fi 연결없음") }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val ssid = readCurrentSsid(context)
                labelState.value = if (ssid.isNullOrBlank()) "Wi-Fi 연결됨" else "Wi-Fi 연결됨 / $ssid"
            }
            override fun onLost(network: Network) {
                labelState.value = "Wi-Fi 연결없음"
            }
            override fun onUnavailable() {
                labelState.value = "Wi-Fi 연결없음"
            }
        }

        cm.registerNetworkCallback(request, callback)

        // 초기 상태 세팅
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val ssid = readCurrentSsid(context)
            labelState.value = if (ssid.isNullOrBlank()) "Wi-Fi 연결됨" else "Wi-Fi 연결됨 / $ssid"
        } else {
            labelState.value = "Wi-Fi 연결없음"
        }

        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }

    return labelState
}

// ----------------------- 검색 바 -----------------------
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchBarDaysOnly(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    animateKey: Any,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // 컨테이너 등장 애니메이션
    val intro = remember { Animatable(0f) }
    LaunchedEffect(animateKey) {
        intro.snapTo(0f)
        intro.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
    }
    val introAlpha = 0.9f + 0.1f * intro.value
    val introTranslate = 6f * (1f - intro.value)

    // 버튼 딜레이
    val btnIntro = remember { Animatable(0f) }
    LaunchedEffect(animateKey) {
        btnIntro.snapTo(0f)
        delay(90)
        btnIntro.animateTo(1f, tween(220, easing = LinearOutSlowInEasing))
    }
    val btnAlpha = 0.85f + 0.15f * btnIntro.value
    val btnTranslate = 4f * (1f - btnIntro.value)

    // 포커스/밑줄
    var isFocused by rememberSaveable { mutableStateOf(false) }
    val underlineThickness by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(180, easing = LinearOutSlowInEasing)
    )

    // X 아이콘 영역 고정폭
    val clearSlotWidth = 32.dp

    Row(
        modifier = modifier.graphicsLayer(
            translationY = with(LocalDensity.current) { introTranslate.dp.toPx() },
            alpha = introAlpha
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 입력 영역 (Row 스코프에서 weight 사용) ──
        Box(
            modifier = Modifier
                .weight(1f)               // ✅ Row 안에서 weight 정상 사용
                .heightIn(min = 40.dp)
        ) {
            // 새로 입력된 부분 알파 애니메이션
            var prevValue by rememberSaveable { mutableStateOf("") }
            val animAlpha = remember { Animatable(1f) }
            var animStart by remember { mutableStateOf(0) }
            var animSegment by remember { mutableStateOf("") }

            LaunchedEffect(value) {
                val old = prevValue
                val new = value
                if (new.length > old.length && new.startsWith(old)) {
                    animStart = old.length
                    animSegment = new.substring(animStart)
                    animAlpha.snapTo(0f)
                    animAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 350, // ⏳ 기존 180 → 350으로 느리게
                            easing = FastOutSlowInEasing // 완만하게 시작해서 끝날 때 더 부드럽게
                        )
                    )
                } else {
                    animStart = new.length
                    animSegment = ""
                    animAlpha.snapTo(1f)
                }
                prevValue = new
            }

            BasicTextField(
                value = value,
                onValueChange = { newVal ->
                    val filtered = newVal.filter { it.isDigit() }
                    onValueChange(filtered)
                    if (filtered.isEmpty() && canGoBack) {
                        focusManager.clearFocus(true); keyboard?.hide(); onBack()
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    color = Color.Transparent // 커서는 유지, 텍스트는 오버레이로 그림
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (value.isNotBlank()) {
                            focusManager.clearFocus(true); keyboard?.hide()
                            onSearch()
                        }
                    }
                ),
                decorationBox = { inner ->
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = clearSlotWidth) // X 자리 미리 확보 → 흔들림 없음
                                .padding(vertical = 8.dp)
                        ) {
                            // 힌트: 입력칸 내부 오버레이
                            if (value.isEmpty()) {
                                Text(
                                    text = "숫자 입력",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            // 1) 실제 입력 필드(커서/선택 영역용)
                            inner()

                            // 2) 우리가 그리는 텍스트(추가분만 알파 페이드)
                            val baseColor = MaterialTheme.colorScheme.onSurface
                            val annotated = remember(value, animAlpha.value, animStart, animSegment) {
                                buildAnnotatedString {
                                    val stable = value.take(animStart)
                                    val anim = animSegment
                                    if (stable.isNotEmpty()) withStyle(SpanStyle(color = baseColor)) { append(stable) }
                                    if (anim.isNotEmpty()) withStyle(SpanStyle(color = baseColor.copy(alpha = animAlpha.value))) { append(anim) }
                                }
                            }
                            Text(
                                text = annotated,
                                fontSize = 14.sp,
                                color = baseColor,
                                maxLines = 1
                            )
                        }
                        Divider(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 0.9f else 0.6f),
                            thickness = underlineThickness
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )

            // X 아이콘 (입력칸 내부 오른쪽, 고정폭 슬롯)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(clearSlotWidth),
                contentAlignment = Alignment.Center
            ) {
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onClear()
                            if (canGoBack) {
                                focusManager.clearFocus(true); keyboard?.hide(); onBack()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "지우기",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // 검색 버튼(아이콘만) — 딜레이 애니메이션
        Button(
            onClick = {
                focusManager.clearFocus(true); keyboard?.hide()
                if (value.isNotBlank()) onSearch()
            },
            enabled = value.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.8f)
            ),
            modifier = Modifier.graphicsLayer(
                translationY = with(LocalDensity.current) { btnTranslate.dp.toPx() },
                alpha = btnAlpha
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "검색",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ----------------------- 개요/상세/결과/썸네일 -----------------------
@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun OverviewByYear(
    allImages: List<MediaItem>,
    imageLoader: ImageLoader,
    onClickAny: (Year) -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = remember { ZoneId.systemDefault() }
    val byYear: Map<Year, List<MediaItem>> = remember(allImages) {
        allImages.groupBy { media ->
            val y = Instant.ofEpochMilli(media.takenAtMillis).atZone(zone).year
            Year.of(y)
        }
    }

    val currentYear = Year.now()
    val sortedYears = byYear.keys.sortedByDescending { it.value }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        byYear[currentYear]?.let { list ->
            val topNine = list.take(9)
            stickyHeader { SectionHeader(title = "${currentYear.value}년 (최대 3줄)") }
            item {
                ThumbnailGrid(
                    items = topNine,
                    imageLoader = imageLoader,
                    maxColumns = 3,
                    onClick = { onClickAny(currentYear) }
                )
            }
        }
        items(sortedYears.filter { it != currentYear }) { year ->
            val medias = byYear[year].orEmpty()
            SectionHeader(title = "${year.value}년")
            ThumbnailGrid(
                items = medias.take(3),
                imageLoader = imageLoader,
                maxColumns = 3,
                onClick = { onClickAny(year) }
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun SectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun ThumbnailGrid(
    items: List<MediaItem>,
    imageLoader: ImageLoader,
    maxColumns: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseDelayPerItem = 40L
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        var rowIndex = 0
        items.chunked(maxColumns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEachIndexed { i, media ->
                    val alpha = remember(media.uri) { Animatable(0f) }
                    val scale = remember(media.uri) { Animatable(0.9f) }

                    LaunchedEffect(media.uri) {
                        val delayMs = baseDelayPerItem * (rowIndex * maxColumns + i)
                        delay(delayMs)
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        alpha.animateTo(1f, animationSpec = tween(220))
                    }

                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(media.uri)
                                .size(256)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            imageLoader
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer(
                                alpha = alpha.value,
                                scaleX = scale.value,
                                scaleY = scale.value
                            )
                            .clickable { onClick() }
                    )
                }
                repeat(maxColumns - rowItems.size) {
                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            rowIndex++
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun YearDetailScreen(
    year: Year,
    allImages: List<MediaItem>,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val monthSections = remember(year, allImages) { groupYearByMonth(year, allImages) }

    AnimatedContent(
        targetState = monthSections,
        transitionSpec = {
            val enter = fadeIn() + expandVertically()
            val exit = fadeOut() + shrinkVertically()
            enter togetherWith exit
        },
        label = "MonthSections"
    ) { sections ->
        LazyColumn(modifier = modifier.fillMaxSize()) {
            sections.forEach { sec ->
                stickyHeader { SectionHeader(title = sec.title) }
                item {
                    ThumbnailGrid(
                        items = sec.items,
                        imageLoader = imageLoader,
                        maxColumns = 3,
                        onClick = { /* TODO: 상세 뷰어 연결 */ }
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultScreen(
    title: String,
    sections: List<MonthSection>,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    AnimatedContent(
        targetState = sections,
        transitionSpec = {
            val enter = fadeIn() + expandVertically()
            val exit = fadeOut() + shrinkVertically()
            enter togetherWith exit
        },
        label = "SearchSections"
    ) { list ->
        LazyColumn(modifier = modifier.fillMaxSize()) {
            list.forEach { sec ->
                stickyHeader { SectionHeader(title = sec.title) }
                item {
                    ThumbnailGrid(
                        items = sec.items,
                        imageLoader = imageLoader,
                        maxColumns = 3,
                        onClick = { /* TODO: 상세 뷰어 */ }
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("검색 결과가 없습니다.")
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ----------------------- 프리페치 + ImageLoader -----------------------
@Composable
fun PrefetchThumbnailsOnce(
    imageLoader: ImageLoader,
    items: List<MediaItem>,
    count: Int = 60,
    thumbSizePx: Int = 256
) {
    var done by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(items.isNotEmpty()) {
        if (done || items.isEmpty()) return@LaunchedEffect
        items.take(count).forEach { media ->
            val req = ImageRequest.Builder(context)
                .data(media.uri)
                .size(thumbSizePx)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(req)
        }
        done = true
    }
}

fun createCustomImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.35)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "image_cache"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

// ----------------------- 쿼리/그룹핑/검색 유틸 -----------------------
@RequiresApi(Build.VERSION_CODES.O)
fun queryAllImagesWithDates(cr: ContentResolver): List<MediaItem> {
    val out = mutableListOf<MediaItem>()

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED
    )
    val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    cr.query(uri, projection, null, null, sort)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val taken = c.getLong(takenCol)
            val added = c.getLong(addedCol)
            val millis = when {
                taken > 0L -> taken
                added > 0L -> added * 1000L
                else -> 0L
            }
            out += MediaItem(ContentUris.withAppendedId(uri, id), millis)
        }
    }
    return out
}

fun queryAllVideosCount(cr: ContentResolver): Int {
    val projection = arrayOf(MediaStore.Video.Media._ID)
    val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    var count = 0
    cr.query(uri, projection, null, null, null)?.use { c -> count = c.count }
    return count
}

@RequiresApi(Build.VERSION_CODES.O)
fun groupYearByMonth(year: Year, all: List<MediaItem>): List<MonthSection> {
    if (all.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val filtered = all.filter { media ->
        val y = Instant.ofEpochMilli(media.takenAtMillis).atZone(zone).year
        y == year.value
    }
    val grouped = filtered.groupBy { media ->
        val dt = Instant.ofEpochMilli(media.takenAtMillis).atZone(zone).toLocalDate()
        YearMonth.of(dt.year, dt.month)
    }.toSortedMap(compareByDescending<YearMonth> { it.year }.thenByDescending { it.monthValue })

    return grouped.map { (ym, list) ->
        MonthSection(
            ym = ym,
            title = String.format("%04d년 %02d월", ym.year, ym.monthValue),
            items = list
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun groupByMonthForDate(all: List<MediaItem>, date: LocalDate): List<MonthSection> {
    if (all.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val dayItems = all.filter { it.takenAtMillis in start until end }
    if (dayItems.isEmpty()) return emptyList()

    val ym = YearMonth.of(date.year, date.month)
    return listOf(
        MonthSection(
            ym = ym,
            title = String.format("%04d년 %02d월", ym.year, ym.monthValue),
            items = dayItems.sortedByDescending { it.takenAtMillis }
        )
    )
}

@RequiresApi(Build.VERSION_CODES.O)
fun searchImagesByDate(
    allImages: List<MediaItem>,
    targetDateMillis: Long
): List<MonthSection> {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(targetDateMillis).atZone(zone).toLocalDate()
    return groupByMonthForDate(allImages, date)
}

fun getDiskUsageBytes(): Pair<Long, Long> {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val total = stat.totalBytes
    val avail = stat.availableBytes
    val used = (total - avail).coerceAtLeast(0)
    return used to total
}
