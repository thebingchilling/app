@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.quartz.camera.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import app.quartz.core.media.MediaItem
import app.quartz.core.ui.components.ScrimIconButton
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.icons.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick review of Quartz's own captures. No editing: open in the gallery for that. */
@Composable
fun ReviewScreen(vm: CameraViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var info by remember { mutableStateOf<MediaItem?>(null) }
    var confirmDelete by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(Unit) { items = vm.recentMedia() }
    BackHandler { vm.setScreen(Screen.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val list = items
        if (list == null) {
            LoadingIndicator(Modifier.align(Alignment.Center))
            return@Box
        }
        if (list.isEmpty()) {
            Text("No photos or videos yet", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        val pager = rememberPagerState { list.size }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val item = list[page]
            val bmp by produceState<Bitmap?>(null, item.uri) {
                value = withContext(Dispatchers.IO) { loadPreview(context, item) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bmp?.let { Image(it.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                if (item.video) {
                    ScrimIconButton(Symbols.playArrow, "Play", { open(context, item) }, size = 72.dp, filled = true)
                }
            }
        }

        ScrimIconButton(
            Symbols.arrowBack,
            "Back to camera",
            { vm.setScreen(Screen.CAMERA) },
            modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart),
        )

        val current = list.getOrNull(pager.currentPage)
        if (current != null) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                IconButton(onClick = { share(context, current) }) { SymbolIcon(Symbols.share, "Share") }
                IconButton(onClick = { info = current }) { SymbolIcon(Symbols.info, "Details") }
                IconButton(onClick = { open(context, current) }) { SymbolIcon(Symbols.openInNew, "Open in gallery") }
                IconButton(onClick = { confirmDelete = current }) { SymbolIcon(Symbols.delete, "Delete") }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${if (item.video) "video" else "photo"}?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.deleteMedia(item.uri)
                        items = vm.recentMedia()
                        confirmDelete = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    info?.let { item ->
        ModalBottomSheet(onDismissRequest = { info = null }) {
            val rows by produceState<List<Pair<String, String>>>(emptyList(), item.uri) {
                value = withContext(Dispatchers.IO) { details(context, item) }
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                rows.forEach { (k, v) ->
                    Row {
                        Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

private fun loadPreview(context: android.content.Context, item: MediaItem): Bitmap? = runCatching {
    if (item.video) {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, item.uri)
            r.getFrameAtTime(0)
        } finally {
            r.release()
        }
    } else {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 2048 || bounds.outHeight / (sample * 2) >= 2048) sample *= 2
        val bmp = resolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val degrees = resolver.openInputStream(item.uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (degrees == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }
}.getOrNull()

private fun details(context: android.content.Context, item: MediaItem): List<Pair<String, String>> {
    if (item.video) return listOf("Type" to item.mime)
    return runCatching {
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            val e = ExifInterface(input)
            listOfNotNull(
                e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { "Taken" to it },
                e.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { "Lens" to it },
                e.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION)?.let { w ->
                    "Size" to "$w × ${e.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION) ?: "?"}"
                },
                e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO" to it },
                e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { "Shutter" to Format.shutter((it * 1e9).toLong()) },
                e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "Aperture" to "f/$it" },
                e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { "35 mm equivalent" to "$it mm" },
                e.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { "Processing" to it.removePrefix("Quartz natural: ") },
                "Type" to item.mime,
            )
        }
    }.getOrNull().orEmpty()
}

private fun share(context: android.content.Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mime
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private fun open(context: android.content.Context, item: MediaItem) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, item.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
    }
}

