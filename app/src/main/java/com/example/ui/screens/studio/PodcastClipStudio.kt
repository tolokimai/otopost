package com.example.ui.screens.studio

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.SocialPlatform
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import com.example.viewmodel.StudioSubMode
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 4b. PODCAST CLIP STUDIO COMPONENT v2 (pipeline nyata: cari -> pilih -> unduh server + transkrip ->
//     daftar rekomendasi AI seluruh video (checkbox) -> potong terpilih + subtitle (server) ->
//     hasil daftar horizontal + pemutar in-app -> konten per-klip (AI) + copy + thumbnail hook)
// ==========================================
private fun formatViewCount(v: Long): String {
    return when {
        v >= 1_000_000 -> String.format("%.1fJt", v / 1_000_000.0)
        v >= 1_000 -> String.format("%.1frb", v / 1_000.0)
        else -> v.toString()
    }
}

private fun secToClock(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return String.format("%d:%02d", m, s)
}

private val SUBTITLE_STYLES: List<Pair<String, String>> = listOf(
    "clean" to "Clean",
    "bold" to "Bold Putih",
    "box" to "Kotak Hitam",
    "yellow" to "Kuning Pop",
    "tiktok" to "TikTok",
    "karaoke" to "Karaoke",
    "minimal" to "Minimal",
    "highlight" to "Highlight",
    "neon" to "Neon",
    "pop" to "Pop"
)

/** Decode base64 JPEG jadi Image kecil; fallback kotak gelap dengan ikon film. */
@Composable
private fun Base64Thumb(base64: String?, modifier: Modifier) {
    val bmp = remember(base64) {
        if (base64.isNullOrBlank()) null
        else try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier = modifier.background(Slate800), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = UtilityBlue400, modifier = Modifier.size(22.dp))
        }
    }
}

/** Pemutar video IN-APP (Media3/ExoPlayer). Bisa mini atau fullscreen, tanpa aplikasi eksternal. */
@Composable
private fun InAppClipPlayer(
    uri: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uri) {
        onDispose { exoPlayer.release() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (fullscreen) 460.dp else 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, UtilityBlue400, RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun PodcastClipStudioContent(viewModel: AutoPostViewModel) {
    val candidates by viewModel.podcastCandidates.collectAsState()
    val isSearching by viewModel.isSearchingPodcast.collectAsState()
    val noRelevant by viewModel.noRelevantPodcast.collectAsState()
    val selectedCandidate by viewModel.selectedCandidate.collectAsState()
    val videoInfo by viewModel.podcastVideoInfo.collectAsState()
    val highlights by viewModel.podcastHighlights.collectAsState()
    val isLoading by viewModel.isLoadingPodcast.collectAsState()
    val clipAspectRatio by viewModel.clipAspectRatio.collectAsState()
    val downloadedPath by viewModel.downloadedVideoPath.collectAsState()
    val isDownloading by viewModel.isDownloadingVideoFile.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val savedClips by viewModel.savedClips.collectAsState()
    val isCutting by viewModel.isCuttingClip.collectAsState()
    val fullTranscript by viewModel.podcastFullTranscript.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    val serverReady = appSettings.clipServerUrl.isNotBlank()
    val subtitleEnabled by viewModel.subtitleEnabled.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()

    // v2 state
    val selectedSegmentIndices by viewModel.podcastV2.selectedSegmentIndices.collectAsState()
    val playingClipUrl by viewModel.podcastV2.playingClipUrl.collectAsState()
    val isPlayerFullscreen by viewModel.podcastV2.isPlayerFullscreen.collectAsState()
    val clipContents by viewModel.podcastV2.clipContents.collectAsState()
    val generatingClipContentFor by viewModel.podcastV2.generatingClipContentFor.collectAsState()
    val clipThumbnails by viewModel.podcastV2.clipThumbnails.collectAsState()

    val clipboard = LocalClipboardManager.current

    var themeInput by remember { mutableStateOf("") }
    var manualUrl by remember { mutableStateOf("") }
    var showFullTranscriptDialog by remember { mutableStateOf(false) }
    var manualStartInput by remember { mutableStateOf("") }
    var manualEndInput by remember { mutableStateOf("") }
    var manualTitleInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1) CARI VIDEO DARI TEMA / RENCANA
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1. Cari Video Podcast dari Tema", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Mesin mencari 2-3 video YouTube relevan (butuh YouTube API Key di Settings). Jika tidak ada yang relevan, ganti isi konten rencana ini.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = themeInput,
                    onValueChange = { themeInput = it },
                    label = { Text("Tema / kata kunci konten") },
                    placeholder = { Text("mis. mindset bisnis anak muda, produktivitas") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { viewModel.searchPodcastCandidates(themeInput) },
                    enabled = themeInput.isNotBlank() && !isSearching,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Mencari video relevan...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cari 2-3 Video Relevan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider()
                Text("Atau tempel link YouTube manual:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("Link YouTube / topik manual") },
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.loadPodcastForTopic(manualUrl) },
                            enabled = manualUrl.isNotBlank() && !isLoading
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Subject, contentDescription = "Ambil transkrip manual")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (noRelevant) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = StatusFailed.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusFailed, modifier = Modifier.size(20.dp))
                    Text(
                        "Tidak ada video YouTube relevan untuk tema ini. Sebaiknya ganti isi konten rencana ini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 2) DAFTAR KANDIDAT VIDEO (pilih otomatis/manual)
        if (candidates.isNotEmpty()) {
            Text("2. Pilih Video (mesin sudah memilih otomatis, bisa diganti manual):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { c ->
                    val sel = selectedCandidate?.videoId == c.videoId
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectPodcastCandidate(c) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(116.dp)
                                    .height(66.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                            ) {
                                if (c.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = c.thumbnailUrl,
                                        contentDescription = c.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                ) {
                                    Text(
                                        c.durationFormatted,
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(c.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(c.channelName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${formatViewCount(c.viewCount)} views", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (sel) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Text("Dipilih", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3) RASIO KLIP (bisa diset default)
        if (selectedCandidate != null || videoInfo != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("3. Rasio Hasil Potong", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = clipAspectRatio == "9:16",
                            onClick = { viewModel.setClipAspectRatio("9:16") },
                            label = { Text("9:16 Potrait") }
                        )
                        FilterChip(
                            selected = clipAspectRatio == "16:9",
                            onClick = { viewModel.setClipAspectRatio("16:9") },
                            label = { Text("16:9 Landscape") }
                        )
                        TextButton(onClick = { viewModel.saveDefaultClipAspectRatio(clipAspectRatio) }) {
                            Text("Jadikan default", fontSize = 11.sp)
                        }
                    }
                    Text(
                        if (serverReady)
                            "Server memotong video HD & otomatis reframe potrait fokus wajah (1 wajah). Active-speaker menyusul."
                        else
                            "Catatan: versi on-device memotong video apa adanya (kualitas asli). Reframe otomatis ke potrait & fokus wajah pembicara sedang disiapkan (butuh proses berat / jalur server).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4) UNDUH VIDEO DARI SERVER & TRANSKRIP
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("4. Unduh dari Server & Transkrip", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (serverReady) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Clip Server aktif \u2014 unduh HD & transkrip ditangani server. Transkrip muncul di bawah, potong dilakukan di langkah 6.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { if (downloadProgress > 0f) downloadProgress else 0.02f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Mengunduh... ${(downloadProgress * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(
                            onClick = { viewModel.downloadSelectedVideo() },
                            enabled = selectedCandidate != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (downloadedPath != null) "Unduh Ulang" else "Unduh Video (best-effort)", fontWeight = FontWeight.Bold)
                        }
                        if (downloadedPath != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text("Video siap dipotong.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Text(
                                "Jika unduh otomatis gagal (YouTube berubah/blokir), isi Clip Server URL di Settings untuk jalur server (yt-dlp) yang lebih andal.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Mengambil transkrip & menganalisis segmen viral...", fontSize = 13.sp)
            }
        }

        // NASKAH TRANSKRIP LENGKAP (tampilkan semua transkrip dulu, baru tentukan)
        if (fullTranscript.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Subject, contentDescription = null, tint = UtilityBlue400, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Transkrip Lengkap Tersedia", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Lihat seluruh transkrip asli, lalu pakai rekomendasi AI atau potong manual.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { showFullTranscriptDialog = true }, shape = RoundedCornerShape(8.dp)) {
                        Text("Lihat Naskah", fontSize = 11.sp)
                    }
                }
            }
        }

        // 5) SEGMEN REKOMENDASI AI (SELURUH VIDEO) - DAFTAR CHECKBOX
        val hasPodcastContext = selectedCandidate != null || videoInfo != null ||
            fullTranscript.isNotBlank() || highlights.isNotEmpty() || isLoading
        if (hasPodcastContext) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("5. Rekomendasi Potongan AI (${highlights.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (highlights.isNotEmpty()) {
                    Row {
                        TextButton(onClick = { viewModel.podcastV2.selectAll(highlights.size) }) { Text("Centang Semua", fontSize = 11.sp) }
                        TextButton(onClick = { viewModel.podcastV2.clearSelection() }) { Text("Hapus", fontSize = 11.sp) }
                    }
                }
            }

            when {
                isLoading -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "AI sedang menganalisis seluruh transkrip asli untuk menemukan semua segmen menarik (bisa lebih dari 3, sesuai isi video)...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                highlights.isNotEmpty() -> {
                    Text(
                        "Centang segmen yang ingin dipotong (bisa semua atau sebagian). Tersimpan ${selectedSegmentIndices.size} terpilih.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        highlights.forEachIndexed { index, hl ->
                            val checked = selectedSegmentIndices.contains(index)
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.podcastV2.toggleSegmentSelection(index) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { viewModel.podcastV2.toggleSegmentSelection(index) }
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("#${index + 1} \u2022 ${hl.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Surface(shape = RoundedCornerShape(6.dp), color = UtilityBlue600.copy(alpha = 0.2f)) {
                                                Text(
                                                    "${secToClock(hl.startSec)} - ${secToClock(hl.endSec)}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = UtilityBlue400,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (hl.hook.isNotBlank()) {
                                            Text("Hook: ${hl.hook}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        if (hl.transcriptSnippet.isNotBlank()) {
                                            Text(
                                                hl.transcriptSnippet,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (hl.reasonWhyViral.isNotBlank()) {
                                            Text("\ud83d\udd25 ${hl.reasonWhyViral}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusFailed.copy(alpha = 0.10f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = StatusFailed, modifier = Modifier.size(20.dp))
                                Text("Belum ada rekomendasi AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "AI belum menghasilkan segmen dari transkrip asli video ini. Ini bisa terjadi jika transkrip belum termuat, video tanpa caption / diblokir, GEMINI API Key belum diisi di Settings, atau tidak ada bagian yang lolos validasi transkrip asli. Rekomendasi tidak pernah dikarang \u2014 hanya diambil dari transkrip nyata.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    when {
                                        selectedCandidate != null -> viewModel.selectPodcastCandidate(selectedCandidate!!)
                                        manualUrl.isNotBlank() -> viewModel.loadPodcastForTopic(manualUrl)
                                        else -> viewModel.showMessage("Pilih video atau tempel link YouTube dulu, lalu coba lagi.")
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Analisis Ulang Transkrip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Atau gunakan \"Potong Manual\" di bawah untuk menentukan menit/detik sendiri.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 6) POTONG DARI SERVER + GAYA SUBTITLE (sebelum potong) + HASIL DAFTAR HORIZONTAL + PEMUTAR IN-APP
        if (highlights.isNotEmpty() || savedClips.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("6. Potong Video (Server) + Subtitle", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    // Gaya subtitle dipilih SEBELUM potong (gabungan segmen 7)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subtitle Otomatis (burn-in)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Pilih gaya subtitle sebelum memotong. Butuh Clip Server & video bercaption.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = subtitleEnabled,
                            onCheckedChange = { viewModel.setSubtitleEnabled(it) },
                            enabled = serverReady
                        )
                    }
                    if (subtitleEnabled) {
                        Text("Gaya Subtitle (banyak opsi):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(SUBTITLE_STYLES) { pair ->
                                FilterChip(
                                    selected = subtitleStyle == pair.first,
                                    onClick = { viewModel.setSubtitleStyle(pair.first) },
                                    label = { Text(pair.second, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    if (!serverReady) {
                        Text(
                            "Subtitle otomatis hanya via Clip Server. Isi Clip Server URL di Settings untuk mengaktifkan.",
                            fontSize = 10.sp,
                            color = StatusFailed
                        )
                    }

                    HorizontalDivider()

                    if (!serverReady && downloadedPath == null) {
                        Text(
                            "Tip: aktifkan Clip Server di Settings agar bisa langsung memotong tanpa unduh manual, atau tekan 'Unduh Video' dulu.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCutting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Server memproses (unduh HD + potong + subtitle). Bisa beberapa menit, mohon tunggu...", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.cutSelectedSegments() },
                        enabled = (serverReady || downloadedPath != null) && !isCutting && selectedSegmentIndices.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Potong Terpilih (${selectedSegmentIndices.size})", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.cutAllSegments() },
                        enabled = (serverReady || downloadedPath != null) && !isCutting && highlights.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Potong Semua (${highlights.size})", fontSize = 12.sp)
                    }

                    // Pemutar IN-APP
                    if (playingClipUrl != null) {
                        InAppClipPlayer(
                            uri = playingClipUrl!!,
                            fullscreen = isPlayerFullscreen,
                            onToggleFullscreen = { viewModel.podcastV2.togglePlayerFullscreen() },
                            onClose = { viewModel.podcastV2.closeInAppPlayer() }
                        )
                    }

                    // HASIL POTONGAN - DAFTAR HORIZONTAL DENGAN THUMBNAIL (bisa digeser)
                    if (savedClips.isNotEmpty()) {
                        Text("Hasil Potongan (${savedClips.size}) \u2014 geser ke kanan, ketuk untuk putar di aplikasi:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(savedClips) { i, clip ->
                                Column(
                                    modifier = Modifier.width(140.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Slate800)
                                            .clickable { viewModel.playSavedClip(clip.uri) }
                                    ) {
                                        Base64Thumb(clipThumbnails[clip.uri], modifier = Modifier.fillMaxSize())
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = Color.White, modifier = Modifier.size(34.dp).padding(4.dp))
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                        ) {
                                            Text("${secToClock(clip.startSec)}-${secToClock(clip.endSec)}", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    Text(clip.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5b) POTONG MANUAL (tentukan menit/detik sendiri)
        if (selectedCandidate != null || videoInfo != null || downloadedPath != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Potong Manual (tentukan sendiri)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Isi detik mulai & selesai, lalu potong satu klip khusus di luar rekomendasi AI.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualStartInput,
                            onValueChange = { v -> manualStartInput = v.filter { it.isDigit() } },
                            label = { Text("Mulai (dtk)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualEndInput,
                            onValueChange = { v -> manualEndInput = v.filter { it.isDigit() } },
                            label = { Text("Selesai (dtk)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = manualTitleInput,
                        onValueChange = { manualTitleInput = it },
                        label = { Text("Judul klip (opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val s = manualStartInput.toIntOrNull() ?: 0
                            val e = manualEndInput.toIntOrNull() ?: 0
                            if (e > s) {
                                viewModel.executeAiCutClip(s, e, manualTitleInput.ifBlank { null })
                            }
                        },
                        enabled = (serverReady || downloadedPath != null) && !isCutting &&
                            (manualEndInput.toIntOrNull() ?: 0) > (manualStartInput.toIntOrNull() ?: 0),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Potong Klip Manual", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 7) KONTEN PER-KLIP (AI) - metadata + copywriting + copy + thumbnail hook
        if (savedClips.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("7. Konten per Klip (AI)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.generateContentForAllClips() }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Generate Semua", fontSize = 11.sp)
                }
            }
            Text("Metadata & copywriting dibuat AI per potongan video. Tekan Copy untuk langsung menyalin.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedClips.forEach { clip ->
                    val content = clipContents[clip.uri]
                    val generating = generatingClipContentFor == clip.uri
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Thumbnail + desain hook overlay
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                                    .clickable { viewModel.playSavedClip(clip.uri) }
                            ) {
                                Base64Thumb(clipThumbnails[clip.uri], modifier = Modifier.fillMaxSize())
                                val hookText = content?.thumbnailHook
                                if (!hookText.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            hookText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Yellow,
                                            textAlign = TextAlign.Center,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(clip.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (content == null) {
                                    Button(
                                        onClick = { viewModel.generateClipContent(clip) },
                                        enabled = !generating,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (generating) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Membuat...", fontSize = 11.sp)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Generate Konten AI", fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    Text("Hook: ${content.hook}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(content.caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    Text(content.hashtags, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                val text = content.hook + "\n\n" + content.caption + "\n\n" + content.hashtags
                                                clipboard.setText(AnnotatedString(text))
                                                viewModel.showMessage("Konten klip disalin ke clipboard.")
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.generateClipContent(clip) },
                                            enabled = !generating,
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Ulangi", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullTranscriptDialog) {
        AlertDialog(
            onDismissRequest = { showFullTranscriptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subject, contentDescription = null, tint = UtilityBlue400)
                    Spacer(Modifier.width(8.dp))
                    Text("Naskah Transkripsi Full", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            text = fullTranscript,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFullTranscriptDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
