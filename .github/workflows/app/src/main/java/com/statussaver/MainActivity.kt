package com.statussaver

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Green = Color(0xFF25D366)
private val DarkBg = Color(0xFF0B141A)
private val CardBg = Color(0xFF1F2C34)

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val modified: Long,
    val mime: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = DarkBg, surface = CardBg)) {
                StatusApp()
            }
        }
    }
}

// ---------- Data helpers ----------

private const val WA_STATUS_DOC =
    "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses"

fun loadStatuses(ctx: Context, tree: Uri?): List<StatusItem> {
    if (tree == null) return emptyList()
    return try {
        var dir = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyList()
        dir.findFile(".Statuses")?.let { dir = it }
        dir.listFiles()
            .filter { it.isFile }
            .mapNotNull { f ->
                val t = f.type ?: return@mapNotNull null
                if (t.startsWith("image/") || t.startsWith("video/")) {
                    StatusItem(f.uri, f.name ?: "status_${f.lastModified()}", t.startsWith("video/"), f.lastModified(), t)
                } else null
            }
            .sortedByDescending { it.modified }
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveToGallery(ctx: Context, item: StatusItem): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (item.isVideo) "Movies/StatusSaver" else "Pictures/StatusSaver"
            )
        }
        val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = ctx.contentResolver.insert(collection, values) ?: return false
        ctx.contentResolver.openInputStream(item.uri)?.use { input ->
            ctx.contentResolver.openOutputStream(out)?.use { output -> input.copyTo(output) }
        } ?: return false
        true
    } catch (e: Exception) {
        false
    }
}

// ---------- Root composable ----------

@Composable
fun StatusApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("status_saver", Context.MODE_PRIVATE) }
    var treeUri by remember { mutableStateOf(prefs.getString("tree", null)?.let { Uri.parse(it) }) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("tree", uri.toString()).apply()
            treeUri = uri
        }
    }
    val pickFolder = { launcher.launch(Uri.parse(WA_STATUS_DOC)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B141A), Color(0xFF12262E))))
    ) {
        if (treeUri == null) SetupScreen(onGrant = pickFolder)
        else HomeScreen(treeUri, prefs, onChangeFolder = pickFolder)
    }
}

// ---------- Setup (one-time) ----------

@Composable
fun SetupScreen(onGrant: () -> Unit) {
    val t = rememberInfiniteTransition(label = "spin")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val bob by t.animateFloat(-14f, 14f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "b")
    val density = LocalDensity.current.density

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(140.dp)
                .graphicsLayer {
                    rotationY = spin
                    rotationX = 18f
                    translationY = bob * density
                    cameraDistance = 16f * density
                }
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.linearGradient(listOf(Green, Color(0xFF128C7E)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(48.dp))
        Text("Status Saver", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "WhatsApp ke status aik tap mein gallery mein save karein.\nLogin ki zaroorat nahi — bas aik dafa folder ki ijazat dein.",
            color = Color(0xFFB0BEC5),
            textAlign = TextAlign.Center,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Filled.FolderOpen, null)
            Spacer(Modifier.size(10.dp))
            Text("Allow Access (aik dafa)", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Khulne wali screen mein neeche \"Use this folder\" dabayein.",
            color = Color(0xFF78909C),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- Home ----------

@Composable
fun HomeScreen(treeUri: Uri?, prefs: android.content.SharedPreferences, onChangeFolder: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(prefs.getStringSet("saved", emptySet()) ?: emptySet()) }
    var preview by remember { mutableStateOf<StatusItem?>(null) }
    var loaded by remember { mutableStateOf(false) }

    val loader = remember {
        ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    LaunchedEffect(treeUri, refreshKey) {
        items = withContext(Dispatchers.IO) { loadStatuses(ctx, treeUri) }
        loaded = true
    }

    val shown = items.filter { if (tab == 0) !it.isVideo else it.isVideo }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 36.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Status Saver", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, "Refresh", tint = Green) }
            IconButton(onClick = onChangeFolder) { Icon(Icons.Filled.FolderOpen, "Folder", tint = Green) }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Green) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Photos") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Videos") })
        }

        if (loaded && shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Koi status nahi mila.\nWhatsApp mein pehle status dekhein, phir refresh dabayein.",
                    color = Color(0xFF90A4AE),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(shown, key = { _, it -> it.uri.toString() }) { index, item ->
                    StatusCard(
                        item = item,
                        loader = loader,
                        index = index,
                        isSaved = item.name in saved,
                        onSave = {
                            val ok = withSave(ctx, item)
                            if (ok) {
                                saved = saved + item.name
                                prefs.edit().putStringSet("saved", saved).apply()
                            }
                        },
                        onOpen = { preview = item }
                    )
                }
            }
        }
    }

    preview?.let { PreviewDialog(it, loader) { preview = null } }
}

private fun withSave(ctx: Context, item: StatusItem): Boolean {
    val ok = saveToGallery(ctx, item)
    Toast.makeText(
        ctx,
        if (ok) "Gallery mein save ho gaya ✓" else "Save nahi ho saka",
        Toast.LENGTH_SHORT
    ).show()
    return ok
}

// ---------- 3D card ----------

@Composable
fun StatusCard(
    item: StatusItem,
    loader: ImageLoader,
    index: Int,
    isSaved: Boolean,
    onSave: () -> Unit,
    onOpen: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * 70L)
        visible = true
    }
    val entryRotation by animateFloatAsState(
        if (visible) 0f else -75f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "entry"
    )
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "fade")
    var pressed by remember { mutableStateOf(false) }
    val tiltX by animateFloatAsState(if (pressed) 10f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "tx")
    val tiltY by animateFloatAsState(if (pressed) -12f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "ty")
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "sc")
    val density = LocalDensity.current.density

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier
            .aspectRatio(0.72f)
            .graphicsLayer {
                rotationY = entryRotation + tiltY
                rotationX = tiltX
                alpha = fade
                scaleX = scale
                scaleY = scale
                cameraDistance = 14f * density
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onOpen() }
                )
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.uri,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.isVideo) {
                Icon(
                    Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .background(Color(0x66000000), CircleShape)
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            )
            IconButton(
                onClick = { if (!isSaved) onSave() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(44.dp)
                    .background(if (isSaved) Color(0xFF1B5E20) else Green, CircleShape)
            ) {
                Icon(
                    if (isSaved) Icons.Filled.CheckCircle else Icons.Filled.Download,
                    if (isSaved) "Saved" else "Save",
                    tint = if (isSaved) Color.White else Color.Black
                )
            }
        }
    }
}

// ---------- Preview ----------

@Composable
fun PreviewDialog(item: StatusItem, loader: ImageLoader, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (item.isVideo) {
                AndroidView(
                    factory = { c ->
                        VideoView(c).apply {
                            setVideoURI(item.uri)
                            setMediaController(MediaController(c).also { it.setAnchorView(this) })
                            setOnPreparedListener { it.isLooping = true; start() }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
