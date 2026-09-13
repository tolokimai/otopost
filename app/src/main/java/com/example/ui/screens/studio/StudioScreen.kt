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
import com.example.ui.screens.remake.RemakeVoiceScreen
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import com.example.viewmodel.StudioSubMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioSubModeTabs(subMode: StudioSubMode, viewModel: AutoPostViewModel) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        ScrollableTabRow(
            selectedTabIndex = subMode.ordinal.coerceIn(0, 2),
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
                selected = subMode == StudioSubMode.REMAKE,
                onClick = { viewModel.setStudioSubMode(StudioSubMode.REMAKE) },
                text = { Text("Remake", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }
    }
}

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

    // Mode REMAKE tampil penuh di dalam Studio (early-return agar RemakeVoiceScreen yang
    // punya scroll sendiri tidak bentrok dengan LazyColumn / nested scroll crash).
    if (subMode == StudioSubMode.REMAKE) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Studio Produksi Konten",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            StudioSubModeTabs(subMode, viewModel)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                RemakeVoiceScreen()
            }
        }
        return
    }

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
                    text = "Buat visual carousel, klip podcast YouTube, & remake video (lipsync) siap publish.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Sub-Mode Selector Tabs (Carousel / Podcast Clip / Remake) ---
        item {
            StudioSubModeTabs(subMode, viewModel)
        }

        // --- Sub-Mode Specific Content ---
        when (subMode) {
            StudioSubMode.CAROUSEL -> {
                item { CarouselStudioContent(viewModel) }
            }
            StudioSubMode.PODCAST_CLIP -> {
                item { PodcastClipStudioContent(viewModel) }
            }
            else -> {
                // REMAKE ditangani lewat early-return di atas.
                // SELF_VIDEO & AI_VIDEO sudah dihapus dari Studio.
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
