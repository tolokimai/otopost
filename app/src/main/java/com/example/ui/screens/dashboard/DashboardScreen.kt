package com.example.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import com.example.viewmodel.MainTab
import com.example.viewmodel.StudioSubMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AutoPostViewModel,
    modifier: Modifier = Modifier
) {
    val scheduledCount by viewModel.scheduledCount.collectAsState()
    val draftCount by viewModel.draftCount.collectAsState()
    val postedCount by viewModel.postedCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()
    val allPosts by viewModel.allPosts.collectAsState()
    val postingLogs by viewModel.postingLogs.collectAsState()
    val filterDays by viewModel.dashboardTimelineFilterDays.collectAsState()

    var showLogModal by remember { mutableStateOf(false) }

    // Filter posts for timeline (e.g. next 7 or 30 days)
    val nowMillis = System.currentTimeMillis()
    val maxTimelineMillis = nowMillis + (filterDays.toLong() * 24 * 60 * 60 * 1000)
    val timelinePosts = allPosts.filter {
        it.scheduledTimeMillis in (nowMillis - 24 * 3600 * 1000)..maxTimelineMillis || it.status == PostStatus.FAILED
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header (Clean Utility Minimal) ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AutoPost Studio",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Hello, Creator",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Clean Avatar Action Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { showLogModal = true }
                        .testTag("btn_creator_profile")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "AS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // --- KPI Metric Grid (Clean Utility Cards) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MinimalKpiCard(
                    label = "SCHEDULED",
                    count = scheduledCount.toString(),
                    valueColor = UtilityBlue600,
                    modifier = Modifier.weight(1f)
                )
                MinimalKpiCard(
                    label = "DRAFTS",
                    count = draftCount.toString(),
                    valueColor = Slate700,
                    modifier = Modifier.weight(1f)
                )
                MinimalKpiCard(
                    label = "LIVE",
                    count = postedCount.toString(),
                    valueColor = StatusPosted,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // If there are failures, show a clean alert badge
        if (failedCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TikTokBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TikTokText.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = TikTokText, modifier = Modifier.size(18.dp))
                            Text(
                                text = "$failedCount konten gagal diposting. Ketuk untuk memeriksa.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TikTokText
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TikTokText, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // --- Upcoming Timeline Section (Clean Utility White Box) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Timeline Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upcoming Timeline",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 7 Days / 30 Days Clean Filter Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(2.dp)
                        ) {
                            TimelineFilterPill(
                                label = "7 DAYS",
                                selected = filterDays == 7,
                                onClick = { viewModel.setTimelineFilter(7) }
                            )
                            TimelineFilterPill(
                                label = "30 DAYS",
                                selected = filterDays == 30,
                                onClick = { viewModel.setTimelineFilter(30) }
                            )
                        }
                    }

                    // Timeline Post Items
                    if (timelinePosts.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Slate400
                            )
                            Text(
                                text = "No Scheduled Content Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Create a plan or craft your first post in Studio.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            timelinePosts.forEach { post ->
                                MinimalTimelinePostItem(
                                    post = post,
                                    onPostNow = { viewModel.publishPostImmediately(post) },
                                    onRetry = { viewModel.retryPost(post.id) },
                                    onDelete = { viewModel.deletePost(post) }
                                )
                            }
                        }
                    }

                    // Bottom Action Row (Generate Plan & Quick Plus Button)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.selectTab(MainTab.ContentPlan) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_generate_plan"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate Plan",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Slate900,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.setStudioSubMode(StudioSubMode.CAROUSEL)
                                    viewModel.selectTab(MainTab.Studio)
                                }
                                .testTag("btn_quick_add_post")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Content",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }

    // Modal Logs / Notification Status Details
    if (showLogModal) {
        AlertDialog(
            onDismissRequest = { showLogModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Riwayat & Status Posting", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (postingLogs.isEmpty()) {
                    Text("Belum ada riwayat aktivitas posting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(postingLogs) { log ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (log.isSuccess) Slate100 else TikTokBg
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (log.isSuccess) Slate200 else TikTokText.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.platform.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (log.isSuccess) StatusPosted else TikTokText
                                        )
                                        Text(
                                            text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(log.timestampMillis)),
                                            fontSize = 10.sp,
                                            color = Slate500
                                        )
                                    }
                                    Text(
                                        text = log.postTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = log.message,
                                        fontSize = 11.sp,
                                        color = if (log.isSuccess) Slate600 else TikTokText
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogModal = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun MinimalKpiCard(
    label: String,
    count: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Slate400
            )
            Text(
                text = count,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
fun TimelineFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MinimalTimelinePostItem(
    post: ScheduledPostEntity,
    onPostNow: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val monthFormat = SimpleDateFormat("MMM", Locale.US)
    val dayFormat = SimpleDateFormat("dd", Locale.US)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val date = Date(post.scheduledTimeMillis)
    val monthStr = monthFormat.format(date).uppercase()
    val dayStr = dayFormat.format(date)
    val timeStr = "${timeFormat.format(date)} WIB"

    val primaryPlatform = post.targetPlatforms.firstOrNull() ?: SocialPlatform.TIKTOK
    val (platformBg, platformText) = when (primaryPlatform) {
        SocialPlatform.TIKTOK -> TikTokBg to TikTokText
        SocialPlatform.INSTAGRAM -> InstagramBg to InstagramText
        SocialPlatform.YOUTUBE_SHORTS -> YouTubeBg to YouTubeText
        SocialPlatform.FACEBOOK_REELS -> FacebookBg to FacebookText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Square Date Box (bg-slate-100 rounded-xl)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Slate100,
            modifier = Modifier.size(48.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = monthStr,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    lineHeight = 10.sp
                )
                Text(
                    text = dayStr,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate700,
                    lineHeight = 15.sp
                )
            }
        }

        // Title and Platform pill
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 2.dp)
        ) {
            Text(
                text = post.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = platformBg
                ) {
                    Text(
                        text = when (primaryPlatform) {
                            SocialPlatform.TIKTOK -> "TIKTOK"
                            SocialPlatform.INSTAGRAM -> "INSTAGRAM"
                            SocialPlatform.YOUTUBE_SHORTS -> "YOUTUBE"
                            SocialPlatform.FACEBOOK_REELS -> "FACEBOOK"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = platformText,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = Slate400
                )
            }
        }

        // Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (post.status == PostStatus.FAILED) {
                IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = TikTokText, modifier = Modifier.size(16.dp))
                }
            } else if (post.status == PostStatus.SCHEDULED || post.status == PostStatus.DRAFT) {
                IconButton(onClick = onPostNow, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Send, contentDescription = "Post Now", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Slate400, modifier = Modifier.size(14.dp))
            }
        }
    }
}

