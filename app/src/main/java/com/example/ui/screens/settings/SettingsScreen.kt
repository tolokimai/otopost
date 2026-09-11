package com.example.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppSettings
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AutoPostViewModel,
    modifier: Modifier = Modifier
) {
    val currentSettings by viewModel.settings.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()

    var geminiKey by remember(currentSettings) { mutableStateOf(currentSettings.geminiApiKey) }
    var tikTokKey by remember(currentSettings) { mutableStateOf(currentSettings.tikTokApiKey) }
    var metaToken by remember(currentSettings) { mutableStateOf(currentSettings.metaAccessToken) }
    var youTubeKey by remember(currentSettings) { mutableStateOf(currentSettings.youTubeApiKey) }

    var isTikTokConnected by remember(currentSettings) { mutableStateOf(currentSettings.isTikTokConnected) }
    var isInstagramConnected by remember(currentSettings) { mutableStateOf(currentSettings.isInstagramConnected) }
    var isYouTubeConnected by remember(currentSettings) { mutableStateOf(currentSettings.isYouTubeConnected) }
    var isFacebookConnected by remember(currentSettings) { mutableStateOf(currentSettings.isFacebookConnected) }

    var postHour by remember(currentSettings) { mutableStateOf(currentSettings.defaultPostHour) }
    var postMinute by remember(currentSettings) { mutableStateOf(currentSettings.defaultPostMinute) }
    var autoRetry by remember(currentSettings) { mutableStateOf(currentSettings.autoRetryOnError) }

    var showApiGuideDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pengaturan & Integrasi",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Kredensial API, Akun Sosial Media, & Jadwal Default",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { showApiGuideDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Panduan API",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- Section 1: API Keys & Integrasi Engine ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. API Keys & AI Engine",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Gemini 3.5 Flash",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Gemini API Key Field & Test Button
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Google Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            trailingIcon = {
                                TextButton(
                                    onClick = { viewModel.testApiConnection("GEMINI", geminiKey) },
                                    enabled = !isTesting
                                ) {
                                    Text("Tes Koneksi", fontSize = 11.sp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Kunci otomatis terisi dari Secrets Environment (.env / BuildConfig) jika tersedia.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // TikTok API Key / Token
                    OutlinedTextField(
                        value = tikTokKey,
                        onValueChange = { tikTokKey = it },
                        label = { Text("TikTok Developer Access Token / App Secret") },
                        placeholder = { Text("tt_live_token_...") },
                        trailingIcon = {
                            TextButton(
                                onClick = { viewModel.testApiConnection("TIKTOK", tikTokKey) },
                                enabled = !isTesting
                            ) {
                                Text("Tes", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Meta / Instagram Access Token
                    OutlinedTextField(
                        value = metaToken,
                        onValueChange = { metaToken = it },
                        label = { Text("Meta / Instagram Graph API Access Token") },
                        placeholder = { Text("EAAG...") },
                        trailingIcon = {
                            TextButton(
                                onClick = { viewModel.testApiConnection("INSTAGRAM", metaToken) },
                                enabled = !isTesting
                            ) {
                                Text("Tes", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // YouTube API Key / OAuth Token
                    OutlinedTextField(
                        value = youTubeKey,
                        onValueChange = { youTubeKey = it },
                        label = { Text("YouTube Data API v3 Key / OAuth Token") },
                        placeholder = { Text("AIzaSy... / ya29...") },
                        trailingIcon = {
                            TextButton(
                                onClick = { viewModel.testApiConnection("YOUTUBE", youTubeKey) },
                                enabled = !isTesting
                            ) {
                                Text("Tes", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Diagnostic Test Result Banner
                    if (testResult != null) {
                        val (platform, result) = testResult!!
                        val (isSuccess, message) = result
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSuccess) StatusPosted.copy(alpha = 0.12f) else StatusFailed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) StatusPosted else StatusFailed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hasil Uji [$platform]: ${if (isSuccess) "SUKSES" else "GAGAL"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSuccess) StatusPosted else StatusFailed
                                    )
                                    Text(
                                        text = message,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { viewModel.clearTestResult() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Section 2: Akun Sosial Media Terhubung ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. Status Akun Media Sosial",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AccountConnectionRow(
                        platformName = "TikTok Creator",
                        accountHandle = currentSettings.tikTokAccountName,
                        badgeColor = Color.Black,
                        isConnected = isTikTokConnected,
                        onToggleConnect = { isTikTokConnected = it }
                    )

                    AccountConnectionRow(
                        platformName = "Instagram Reels",
                        accountHandle = currentSettings.instagramAccountName,
                        badgeColor = InstagramPurple,
                        isConnected = isInstagramConnected,
                        onToggleConnect = { isInstagramConnected = it }
                    )

                    AccountConnectionRow(
                        platformName = "YouTube Shorts",
                        accountHandle = currentSettings.youTubeAccountName,
                        badgeColor = YouTubeRed,
                        isConnected = isYouTubeConnected,
                        onToggleConnect = { isYouTubeConnected = it }
                    )

                    AccountConnectionRow(
                        platformName = "Facebook Reels",
                        accountHandle = "Halaman Kreator FB",
                        badgeColor = FacebookBlue,
                        isConnected = isFacebookConnected,
                        onToggleConnect = { isFacebookConnected = it }
                    )
                }
            }
        }

        // --- Section 3: Jadwal & Waktu Default ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "3. Jadwal & Frekuensi Default",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Jam Tayang Default:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Jam prima penayangan konten harian", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = String.format("%02d:%02d WIB", postHour, postMinute),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Zona Waktu:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(currentSettings.timezone, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text("WIB (GMT+7)", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Otomatis Retry Saat Gagal:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Mencoba ulang hingga 3x via WorkManager", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoRetry,
                            onCheckedChange = { autoRetry = it }
                        )
                    }
                }
            }
        }

        // --- Save Settings Button ---
        item {
            Button(
                onClick = {
                    viewModel.updateSettings(
                        currentSettings.copy(
                            geminiApiKey = geminiKey,
                            tikTokApiKey = tikTokKey,
                            metaAccessToken = metaToken,
                            youTubeApiKey = youTubeKey,
                            isTikTokConnected = isTikTokConnected,
                            isInstagramConnected = isInstagramConnected,
                            isYouTubeConnected = isYouTubeConnected,
                            isFacebookConnected = isFacebookConnected,
                            defaultPostHour = postHour,
                            defaultPostMinute = postMinute,
                            autoRetryOnError = autoRetry
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_save_settings"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Simpan Semua Pengaturan", fontWeight = FontWeight.Bold)
            }
        }

        // --- Guide Section Banner ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showApiGuideDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Panduan API Resmi & Developer Approval", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Pelajari syarat approval Meta, TikTok & YouTube API", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showApiGuideDialog) {
        ApiGuideDialog(onDismiss = { showApiGuideDialog = false })
    }
}

@Composable
fun AccountConnectionRow(
    platformName: String,
    accountHandle: String,
    badgeColor: Color,
    isConnected: Boolean,
    onToggleConnect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = badgeColor, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(platformName.take(1), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Column {
                Text(text = platformName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = accountHandle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isConnected) StatusPosted.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (isConnected) "AKTIF" else "NONAKTIF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) StatusPosted else Color.Gray,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Switch(
                checked = isConnected,
                onCheckedChange = onToggleConnect
            )
        }
    }
}

@Composable
fun ApiGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Panduan Integrasi API & Approval", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "1. Google Gemini API (Teks & Ide)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Model: gemini-3.5-flash (sangat cepat & hemat kuota).\n• Biaya: Tersedia tier gratis di Google AI Studio (hingga 15 RPM). Cukup dapatkan API key gratis di aistudio.google.com.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text(
                        text = "2. TikTok Content Posting API",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Endpoint: /v2/post/publish/video/init/\n• Approval: Membutuhkan verifikasi Developer App & persetujuan izin 'video.publish' & 'video.upload' dari TikTok for Developers.\n• Alternatif bila belum diapprove: Engine AutoPost Studio otomatis beralih ke Simulated Publisher dengan log riwayat posting lokal yang transparan.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text(
                        text = "3. Meta / Instagram Graph API",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Endpoint: graph.facebook.com/v19.0/{user_id}/media\n• Approval: Memerlukan Akun Instagram Professional (Business/Creator) yang terhubung ke Facebook Page serta App Review untuk permission 'instagram_content_publish'.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text(
                        text = "4. YouTube Data API v3",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Endpoint: /youtube/v3/videos (Upload Shorts)\n• Kuota: 10,000 unit/hari gratis dari Google Cloud Console. 1 video upload membutuhkan ~1600 unit kuota.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Mengerti")
            }
        }
    )
}
