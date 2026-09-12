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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.SocialPlatform
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import com.example.viewmodel.StudioSubMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: AutoPostViewModel,
    modifier: Modifier = Modifier
) {
    val subMode by viewModel.studioSubMode.collectAsState()
    val title by viewModel.studioContentTitle.collectAsState()
    val hook by viewModel.studioContentHook.collectAsState()
    val caption by viewModel.studioContentCaption.collectAsState()
    val hashtags by viewModel.studioContentHashtags.collectAsState()

    var showScheduleDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Column {
                Text(
                    text = "Studio Produksi Konten",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Buat visual carousel, klip podcast YouTube, & naskah video siap publish.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Sub-Mode Selector Tabs (4 Modes) ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ScrollableTabRow(
                    selectedTabIndex = subMode.ordinal,
                    containerColor = Color.Transparent,
                    edgePadding = 4.dp
                ) {
                    Tab(
                        selected = subMode == StudioSubMode.CAROUSEL,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.CAROUSEL) },
                        text = { Text("Carousel", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = subMode == StudioSubMode.PODCAST_CLIP,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.PODCAST_CLIP) },
                        text = { Text("Podcast Clip", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = subMode == StudioSubMode.SELF_VIDEO,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.SELF_VIDEO) },
                        text = { Text("Video Sendiri", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = subMode == StudioSubMode.AI_VIDEO,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.AI_VIDEO) },
                        text = { Text("Buat Video", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // --- Sub-Mode Specific Content ---
        when (subMode) {
            StudioSubMode.CAROUSEL -> {
                item { CarouselStudioContent(viewModel) }
            }
            StudioSubMode.PODCAST_CLIP -> {
                item { PodcastClipStudioContent(viewModel) }
            }
            StudioSubMode.SELF_VIDEO -> {
                item { SelfVideoStudioContent(viewModel) }
            }
            StudioSubMode.AI_VIDEO -> {
                item { AiVideoStudioContent(viewModel) }
            }
        }

        // --- Common Metadata Editor (Title, Hook, Caption, Hashtags) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Metadata & Copywriting Konten",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { viewModel.updateStudioCopy(it, hook, caption, hashtags) },
                        label = { Text("Judul Konten") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hook,
                        onValueChange = { viewModel.updateStudioCopy(title, it, caption, hashtags) },
                        label = { Text("Hook 3 Detik") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = caption,
                        onValueChange = { viewModel.updateStudioCopy(title, hook, it, hashtags) },
                        label = { Text("Caption & Call to Action (CTA)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hashtags,
                        onValueChange = { viewModel.updateStudioCopy(title, hook, caption, it) },
                        label = { Text("Hashtags Relevan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- Action Buttons: Jadwalkan & Draft ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.saveCurrentStudioToSchedule(
                            targetPlatforms = listOf(SocialPlatform.TIKTOK, SocialPlatform.INSTAGRAM, SocialPlatform.YOUTUBE_SHORTS),
                            scheduledTimeMillis = System.currentTimeMillis() + 3600_000,
                            asDraft = true
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Draft", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { showScheduleDialog = true },
                    modifier = Modifier.weight(1.3f).height(48.dp).testTag("btn_schedule_content"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jadwalkan Posting", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showScheduleDialog) {
        SchedulePostDialog(
            defaultHour = viewModel.settings.value.defaultPostHour,
            defaultMinute = viewModel.settings.value.defaultPostMinute,
            onDismiss = { showScheduleDialog = false },
            onConfirmSchedule = { platforms, scheduledMillis ->
                viewModel.saveCurrentStudioToSchedule(
                    targetPlatforms = platforms,
                    scheduledTimeMillis = scheduledMillis,
                    asDraft = false
                )
                showScheduleDialog = false
            }
        )
    }
}

// ==========================================
// 4a. CAROUSEL STUDIO COMPONENT dipindah ke CarouselStudio.kt (package sama)
// ==========================================

// ==========================================
// 4b. PODCAST CLIP STUDIO COMPONENT (pipeline nyata: cari -> pilih -> transkrip ->
//     rekomendasi segmen AI -> unduh HD -> potong per-segmen -> simpan)
// ==========================================
private fun formatViewCount(v: Long): String {
    return when {
        v >= 1_000_000 -> String.format("%.1fJt", v / 1_000_000.0)
        v >= 1_000 -> String.format("%.1frb", v / 1_000.0)
        else -> v.toString()
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
    val selectedIndex by viewModel.selectedHighlightIndex.collectAsState()
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

    var themeInput by remember { mutableStateOf("") }
    var manualUrl by remember { mutableStateOf("") }
    var showFullTranscriptDialog by remember { mutableStateOf(false) }

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

            // 4) UNDUH VIDEO
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("4. Unduh Video HD", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (serverReady) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Clip Server aktif \u2014 unduh HD & potong ditangani server. Lewati langkah ini, langsung ke 'Potong Semua Segmen' di bawah.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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

        // 5) SEGMEN REKOMENDASI AI + POTONG
        if (highlights.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("5. Segmen Rekomendasi AI (${highlights.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (fullTranscript.isNotBlank()) {
                    TextButton(onClick = { showFullTranscriptDialog = true }) {
                        Icon(Icons.Default.Subject, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Naskah", fontSize = 11.sp)
                    }
                }
            }

            if (!serverReady && downloadedPath == null) {
                Text(
                    "Tip: aktifkan Clip Server di Settings agar bisa langsung memotong tanpa unduh manual, atau tekan 'Unduh Video' dulu.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { viewModel.cutAllSegments() },
                enabled = (serverReady || downloadedPath != null) && !isCutting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isCutting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Memotong...", fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Potong Semua Segmen & Simpan", fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                highlights.forEachIndexed { index, hl ->
                    val isSelected = selectedIndex == index
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(20.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Text(text = hl.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(text = hl.durationFormatted, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(text = "Hook: ${hl.hook}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Potensi Viral: ${hl.reasonWhyViral}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.cutSegmentAt(index) },
                                    enabled = (serverReady || downloadedPath != null) && !isCutting,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Potong & Simpan", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectPodcastHighlight(index)
                                        viewModel.setStudioMetadata(
                                            title = "${videoInfo?.channelName ?: "Podcast"} Klip: ${hl.title}",
                                            hook = hl.hook,
                                            caption = "Wajib dengar potongan obrolan ini! #podcast #shorts #viral",
                                            hashtags = "#podcast #clips #shorts #reels #tiktok"
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Pakai Metadata", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6) KLIP TERSIMPAN
        if (savedClips.isNotEmpty()) {
            Text("Klip Tersimpan (${savedClips.size}) - folder Movies/AutoPostStudio:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                savedClips.forEach { clip ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = UtilityBlue400, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(clip.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${clip.startSec}s - ${clip.endSec}s  \u2022  ${clip.displayName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

// ==========================================
// 4d. BUAT VIDEO AI (VEO 3.1 FAST) COMPONENT
// ==========================================
@Composable
fun AiVideoStudioContent(viewModel: AutoPostViewModel) {
    val prompt by viewModel.aiVideoPrompt.collectAsState()
    val aspectRatio by viewModel.aiVideoAspectRatio.collectAsState()
    val style by viewModel.aiVideoStyle.collectAsState()
    val durationSeconds by viewModel.aiVideoDurationSeconds.collectAsState()
    val isGenerating by viewModel.isGeneratingAiVideo.collectAsState()
    val generatedVideo by viewModel.generatedVideoResult.collectAsState()

    val title by viewModel.studioContentTitle.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Preview Video AI Generator (Veo 3.1):", fontSize = 14.sp, fontWeight = FontWeight.Bold)

        val isVertical = aspectRatio == "9:16"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isVertical) 380.dp else 220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        when (style) {
                            "Cyberpunk / Neon" -> listOf(Color(0xFF0D0221), Color(0xFF0F084B), Color(0xFF26408B))
                            "Cinematic 4K" -> listOf(Color(0xFF0B0F19), Color(0xFF1E293B), Color(0xFF0F172A))
                            "3D Animation / CGI" -> listOf(Color(0xFF1E1B4B), Color(0xFF4338CA), Color(0xFF312E81))
                            else -> listOf(Slate900, Slate800, Color(0xFF020617))
                        }
                    )
                )
                .border(2.dp, if (generatedVideo != null) UtilityBlue400 else Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = UtilityBlue400.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = UtilityBlue400, modifier = Modifier.size(12.dp))
                            Text("Veo 3.1 Fast", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = UtilityBlue400)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "$aspectRatio - ${durationSeconds}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = UtilityBlue400, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                        Text(
                            text = "Model veo-3.1-fast sedang merender video AI...",
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    } else if (generatedVideo != null) {
                        Surface(
                            shape = CircleShape,
                            color = UtilityBlue400,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text(
                            text = generatedVideo?.stylePreset ?: "Video Siap Ditayangkan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        Text(
                            text = "Tulis Prompt di Bawah & Klik Generate Video",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prompt.ifBlank { "Prompt: Visual sinematik modern..." },
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Deskripsi Video / Prompt Teks (Veo 3.1):", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { viewModel.setAiVideoPrompt(it) },
                    label = { Text("Prompt Generator Video") },
                    placeholder = { Text("mis. Drone sinematik di atas gedung futuristic kota Jakarta malam hari dengan lampu neon, 4K...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Inspirasi Prompt Cepat:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = { viewModel.setAiVideoPrompt("Cinematic drone shot of futuristic glowing AI tech workspace, hyperrealistic 4K, soft volumetric lighting") },
                        label = { Text("Tech 4K", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { viewModel.setAiVideoPrompt("Dynamic POV running through colorful cyberpunk Tokyo street in the rain with neon reflection") },
                        label = { Text("Cyberpunk", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { viewModel.setAiVideoPrompt("Minimalist luxury product showcase rotating smoothly on stone pedestal with water ripples") },
                        label = { Text("Luxury Product", fontSize = 11.sp) }
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Pengaturan Rasio & Style Video:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                Text("Rasio Format:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = aspectRatio == "9:16",
                        onClick = { viewModel.setAiVideoAspectRatio("9:16") },
                        label = { Text("9:16 (Shorts/TikTok/Reels)") }
                    )
                    FilterChip(
                        selected = aspectRatio == "16:9",
                        onClick = { viewModel.setAiVideoAspectRatio("16:9") },
                        label = { Text("16:9 (Landscape/YouTube)") }
                    )
                }

                Text("Gaya Sinematik / Visual Preset:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val styles = listOf("Cinematic 4K", "Cyberpunk / Neon", "Photorealistic Studio", "3D Animation / CGI", "Minimal Aesthetic")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(styles) { s ->
                        FilterChip(
                            selected = style == s,
                            onClick = { viewModel.setAiVideoStyle(s) },
                            label = { Text(s, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Durasi Video:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = durationSeconds == 5, onClick = { viewModel.setAiVideoDurationSeconds(5) }, label = { Text("5 Detik") })
                        FilterChip(selected = durationSeconds == 10, onClick = { viewModel.setAiVideoDurationSeconds(10) }, label = { Text("10 Detik") })
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.generateAiVideoFromText() },
            enabled = !isGenerating && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Veo 3.1 Sedang Merender Video AI...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Video AI (veo-3.1-fast)", fontWeight = FontWeight.Bold)
            }
        }

        if (generatedVideo != null) {
            Button(
                onClick = { viewModel.applyAiVideoToPost() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gunakan Video Ini Untuk Postingan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 4c. SELF VIDEO STUDIO COMPONENT
// ==========================================
@Composable
fun SelfVideoStudioContent(viewModel: AutoPostViewModel) {
    val hookText by viewModel.selfVideoHookText.collectAsState()
    val subtitles by viewModel.selfVideoSubtitles.collectAsState()
    val isGeneratingCopy by viewModel.isGeneratingVideoCopy.collectAsState()
    val title by viewModel.studioContentTitle.collectAsState()

    var showAddSubtitleDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Preview Video & Subtitle Overlay:", fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Slate900, Slate800, Color(0xFF020617))))
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Yellow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = hookText.ifBlank { "HOOK 3 DETIK VIDEO DI SINI" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(UtilityBlue600.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = UtilityBlue400, modifier = Modifier.size(36.dp))
                    }
                    Text(
                        text = "9:16 Vertical Creator Ratio",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Subtitle Dinamis:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = UtilityBlue400
                        )
                        Text(
                            text = subtitles.joinToString(" ") { it },
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.generateVideoScript(title) },
            enabled = !isGeneratingCopy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isGeneratingCopy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Gemini AI Membuat Script...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Hook Banner & Subtitle dengan AI")
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Teks Hook Atas Video:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = hookText,
                    onValueChange = { viewModel.setSelfVideoHookText(it) },
                    label = { Text("Teks Hook (Maks 10 kata)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daftar Subtitle / Baris Naskah:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showAddSubtitleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah Subtitle", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                subtitles.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(20.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val updated = subtitles.toMutableList()
                                updated.removeAt(index)
                                viewModel.updateSelfVideoSubtitles(updated)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(14.dp), tint = StatusFailed)
                        }
                    }
                }
            }
        }
    }

    if (showAddSubtitleDialog) {
        var newLine by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSubtitleDialog = false },
            title = { Text("Tambah Baris Subtitle", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newLine,
                    onValueChange = { newLine = it },
                    label = { Text("Kalimat Subtitle") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLine.isNotBlank()) {
                            viewModel.updateSelfVideoSubtitles(subtitles + newLine)
                            showAddSubtitleDialog = false
                        }
                    }
                ) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubtitleDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// ==========================================
// COMMON SCHEDULE POST DIALOG
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePostDialog(
    defaultHour: Int,
    defaultMinute: Int,
    onDismiss: () -> Unit,
    onConfirmSchedule: (List<SocialPlatform>, Long) -> Unit
) {
    var tiktokSelected by remember { mutableStateOf(true) }
    var instagramSelected by remember { mutableStateOf(true) }
    var youtubeSelected by remember { mutableStateOf(true) }
    var facebookSelected by remember { mutableStateOf(false) }

    var postHour by remember { mutableStateOf(defaultHour) }
    var postMinute by remember { mutableStateOf(defaultMinute) }
    var daysAhead by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Jadwalkan Publikasi Konten", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Pilih Platform Target:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tiktokSelected, onCheckedChange = { tiktokSelected = it })
                    Text("TikTok Video (@creator_tiktok)", fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = instagramSelected, onCheckedChange = { instagramSelected = it })
                    Text("Instagram Reels / Carousel (@creator_reels)", fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = youtubeSelected, onCheckedChange = { youtubeSelected = it })
                    Text("YouTube Shorts (Creator Channel)", fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = facebookSelected, onCheckedChange = { facebookSelected = it })
                    Text("Facebook Reels (Page)", fontSize = 13.sp)
                }

                HorizontalDivider()

                Text("Waktu Penayangan:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = daysAhead == 0, onClick = { daysAhead = 0 }, label = { Text("Hari Ini") })
                    FilterChip(selected = daysAhead == 1, onClick = { daysAhead = 1 }, label = { Text("Besok") })
                    FilterChip(selected = daysAhead == 2, onClick = { daysAhead = 2 }, label = { Text("+2 Hari") })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Jam Tayang:", fontSize = 13.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = String.format("%02d:%02d WIB", postHour, postMinute),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val platforms = mutableListOf<SocialPlatform>()
                    if (tiktokSelected) platforms.add(SocialPlatform.TIKTOK)
                    if (instagramSelected) platforms.add(SocialPlatform.INSTAGRAM)
                    if (youtubeSelected) platforms.add(SocialPlatform.YOUTUBE_SHORTS)
                    if (facebookSelected) platforms.add(SocialPlatform.FACEBOOK_REELS)

                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, daysAhead)
                    cal.set(Calendar.HOUR_OF_DAY, postHour)
                    cal.set(Calendar.MINUTE, postMinute)
                    cal.set(Calendar.SECOND, 0)

                    onConfirmSchedule(platforms, cal.timeInMillis)
                },
                enabled = tiktokSelected || instagramSelected || youtubeSelected || facebookSelected
            ) {
                Text("Konfirmasi Jadwal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
