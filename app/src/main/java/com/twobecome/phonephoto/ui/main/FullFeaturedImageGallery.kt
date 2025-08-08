
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullFeaturedImageGallery() {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var imageUris by remember { mutableStateOf(listOf<Uri>()) }
    var videoUris by remember { mutableStateOf(listOf<Uri>()) }
    val imageLoader = remember { createCustomImageLoader(context) }

    LaunchedEffect(Unit) {
        imageUris = queryAllImages(contentResolver)
        videoUris = queryAllVideos(contentResolver)
    }

    val totalDiskInfo = remember { getDiskUsageInfo() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("PhonePhoto-TWOBECOME") },
                    navigationIcon = {
                        if (selectedIndex != null) {
                            IconButton(onClick = { selectedIndex = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                            }
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🧠 디스크: ${totalDiskInfo.first} / ${totalDiskInfo.second}", style = MaterialTheme.typography.bodySmall)
                    Text("🖼️ 사진: ${imageUris.size} / 🎬 영상: ${videoUris.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    ) { padding ->
        if (selectedIndex != null) {
            ZoomablePager(
                items = imageUris,
                startIndex = selectedIndex!!,
                imageLoader = imageLoader,
                onDismiss = { selectedIndex = null },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(imageUris) { index, uri ->
                    val animatedAlpha = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        animatedAlpha.animateTo(1f, animationSpec = tween(500))
                    }
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            imageLoader = imageLoader
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .graphicsLayer(alpha = animatedAlpha.value)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { selectedIndex = index }
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomablePager(
    items: List<Uri>,
    startIndex: Int,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = startIndex)
    var scale by remember { mutableStateOf(1f) }
    val transformState = rememberTransformableState { zoom, _, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
    }

    Column(
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            count = items.size,
            modifier = Modifier.weight(1f)
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(items[page])
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        imageLoader = imageLoader
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
    BackHandler(onBack = onDismiss)
}

fun queryAllImages(contentResolver: ContentResolver): List<Uri> {
    val imageList = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val cursor = contentResolver.query(queryUri, projection, null, null, sortOrder)
    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val uri = ContentUris.withAppendedId(queryUri, id)
            imageList.add(uri)
        }
    }
    return imageList
}

fun queryAllVideos(contentResolver: ContentResolver): List<Uri> {
    val videoList = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Video.Media._ID)
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
    val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val cursor = contentResolver.query(queryUri, projection, null, null, sortOrder)
    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val uri = ContentUris.withAppendedId(queryUri, id)
            videoList.add(uri)
        }
    }
    return videoList
}

fun getDiskUsageInfo(): Pair<String, String> {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val totalBytes = stat.totalBytes
    val availableBytes = stat.availableBytes
    val usedBytes = totalBytes - availableBytes
    val formatter = DecimalFormat("#.##")
    val usedGB = formatter.format(usedBytes / 1e+9) + " GB"
    val totalGB = formatter.format(totalBytes / 1e+9) + " GB"
    return usedGB to totalGB
}

fun createCustomImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(context).maxSizePercent(0.25).build()
        }
        .diskCache {
            DiskCache.Builder().directory(File(context.cacheDir, "image_cache")).build()
        }
        .respectCacheHeaders(false)
        .build()
}
