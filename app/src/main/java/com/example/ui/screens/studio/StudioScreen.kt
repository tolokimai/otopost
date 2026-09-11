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
// 4b. PODCAST CLIP STUDIO COMPONENT
// ==========================================
@Composable
fun PodcastClipStudioContent(viewModel: AutoPostViewModel) {
    val videoInfo by viewModel.podcastVideoInfo.collectAsState()
    val highlights by viewModel.podcastHighlights.collectAsState()
    val selectedIndex by viewModel.selectedHighlightIndex.collectAsState()
    val isLoading by viewModel.isLoadingPodcast.collectAsState()
    val isTranscribing by viewModel.isTranscribingPodcast.collectAsState()
    val fullTranscript by viewModel.podcastFullTranscript.collectAsState()

    var inputUrlOrTopic by remember { mutableStateOf("https://youtube.com/watch?v=deddy_podcast") }
    var cutStartSec by remember { mutableStateOf(30f) }
    var cutEndSec by remember { mutableStateOf(85f) }
    var showFullTranscriptDialog by remember { mutableStateOf(false) }

    val activeHighlight = highlights.getOrNull(selectedIndex)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // --- YouTube URL / Topic Input Bar ---
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Sumber Podcast YouTube:", fontSize = 14.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = inputUrlOrTopic,
                    onValueChange = { inputUrlOrTopic = it },
                    label = { Text("Link YouTube Video / Kata Kunci Topik") },
                    placeholder = { Text("mis. Deddy Corbuzier bisnis, Alex Hormozi") },
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.loadPodcastForTopic(inputUrlOrTopic) },
                            enabled = inputUrlOrTopic.isNotBlank() && !isLoading && !isTranscribing
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Ambil")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Picks presets
                Text("Pilihan Cepat Podcast Trending:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {
                            inputUrlOrTopic = "Deddy Corbuzier Bisnis"
                            viewModel.loadPodcastForTopic(inputUrlOrTopic)
                        },
                        label = { Text("Deddy Corbuzier", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputUrlOrTopic = "Alex Hormozi Business"
                            viewModel.loadPodcastForTopic(inputUrlOrTopic)
                        },
                        label = { Text("Alex Hormozi", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            inputUrlOrTopic = "Creator Strategy 2026"
                            viewModel.loadPodcastForTopic(inputUrlOrTopic)
                        },
                        label = { Text("Creator Masterclass", fontSize = 11.sp) }
                    )
                }

                // Action: Download Audio & Full Transcribe
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.transcribeAndProcessPodcast(inputUrlOrTopic) },
                        enabled = !isTranscribing && !isLoading,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTranscribing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Transkripsi...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download & Transkrip Full", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (fullTranscript.isNotBlank()) {
                        OutlinedButton(
                            onClick = { showFullTranscriptDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Subject, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Naskah Full", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- 9:16 Vertical Video Frame Simulator ---
        Text("Preview 9:16 Shorts/Reels/TikTok Clip:", fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Slate900, Slate800, Color(0xFF000000))))
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Attribution Banner (Channel & Title)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(shape = CircleShape, color = YouTubeRed, modifier = Modifier.size(18.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Text(
                            text = "${videoInfo?.channelName ?: "YouTube Podcast"} - ${activeHighlight?.durationFormatted ?: "00:${cutStartSec.toInt()} - 01:${(cutEndSec % 60).toInt()}"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Center: Animated Audio Waveform & Viral Hook Overlay
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Hook Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YouTubeRed.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = activeHighlight?.hook ?: "99% Orang Belum Tahu Formula Ini!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    // Simulated Live Waveform
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "wave")
                        val barHeights = listOf(24.dp, 40.dp, 56.dp, 32.dp, 48.dp, 60.dp, 36.dp, 50.dp, 28.dp)
                        barHeights.forEachIndexed { i, h ->
                            val animH by infiniteTransition.animateValue(
                                initialValue = h * 0.4f,
                                targetValue = h,
                                typeConverter = androidx.compose.ui.unit.Dp.VectorConverter,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 400 + (i * 100), easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$i"
                            )
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(animH)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(UtilityBlue400)
                            )
                        }
                    }
                }

                // Bottom: Auto-Subtitles Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Auto-Subtitles Highlight:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = UtilityBlue400
                        )
                        Text(
                            text = "\"${activeHighlight?.transcriptSnippet ?: "Transkrip kalimat emas pembicara..."}\"",
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // --- Video Clip Cutting & Slider Tool ---
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
                    Text("Pemotong Klip Video (Detik):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${cutStartSec.toInt()}s - ${cutEndSec.toInt()}s (${(cutEndSec - cutStartSec).toInt()} detik)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Mulai:", fontSize = 11.sp)
                    Slider(
                        value = cutStartSec,
                        onValueChange = { if (it < cutEndSec - 5) cutStartSec = it },
                        valueRange = 0f..180f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Selesai:", fontSize = 11.sp)
                    Slider(
                        value = cutEndSec,
                        onValueChange = { if (it > cutStartSec + 5) cutEndSec = it },
                        valueRange = 10f..300f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val durationStr = String.format("%02d:%02d - %02d:%02d", (cutStartSec / 60).toInt(), (cutStartSec % 60).toInt(), (cutEndSec / 60).toInt(), (cutEndSec % 60).toInt())
                        val hook = activeHighlight?.hook ?: "Cuplikan Penting Podcast Ini!"
                        viewModel.setStudioMetadata(
                            title = "${videoInfo?.channelName ?: "Podcast"} Klip: ${activeHighlight?.title ?: "Highlights"}",
                            hook = hook,
                            caption = "Wajib dengar potongan obrolan ini dari ${videoInfo?.channelName ?: "Podcast"}! #podcast #shorts #viral",
                            hashtags = "#podcast #clips #shorts #reels #tiktok"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Potong & Terapkan Klip ke Metadata Post", fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- Gemini AI Identified Highlights List ---
        Text(
            text = "Klip Potongan Viral Pilihan AI (${highlights.size} Segmen):",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (highlights.isEmpty()) {
            if (isLoading || isTranscribing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Sedang memproses transkrip & menganalisis klip viral...", fontSize = 13.sp)
                }
            } else {
                Text(
                    text = "Klik 'Ambil' di atas atau pilih preset podcast untuk memuat highlight.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                highlights.forEachIndexed { index, hl ->
                    val isSelected = selectedIndex == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectPodcastHighlight(index)
                                cutStartSec = hl.startSec.toFloat()
                                cutEndSec = hl.endSec.toFloat()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
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
