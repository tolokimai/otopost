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
                        text = { Text("📑 Carousel", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = subMode == StudioSubMode.PODCAST_CLIP,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.PODCAST_CLIP) },
                        text = { Text("🎙️ Podcast Clip", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = subMode == StudioSubMode.SELF_VIDEO,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.SELF_VIDEO) },
                        text = { Text("🎬 Video Sendiri", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = subMode == StudioSubMode.AI_VIDEO,
                        onClick = { viewModel.setStudioSubMode(StudioSubMode.AI_VIDEO) },
                        text = { Text("✨ Buat Video", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
// 4a. CAROUSEL STUDIO COMPONENT
// ==========================================
// ==========================================
// 4a. CAROUSEL STUDIO COMPONENT
// ==========================================
@Composable
fun CarouselStudioContent(viewModel: AutoPostViewModel) {
    val slides by viewModel.carouselSlides.collectAsState()
    val bgPreset by viewModel.carouselBackgroundPreset.collectAsState()
    val carouselTheme by viewModel.carouselTheme.collectAsState()
    val aspectRatio by viewModel.carouselAspectRatio.collectAsState()
    val typographyStyle by viewModel.carouselTypographyStyle.collectAsState()
    val watermark by viewModel.carouselWatermark.collectAsState()
    val fontChoice by viewModel.carouselFontFamily.collectAsState()
    val isGeneratingAi by viewModel.isGeneratingCarouselAi.collectAsState()
    val isGeneratingImg by viewModel.isGeneratingSlideImage.collectAsState()
    val title by viewModel.studioContentTitle.collectAsState()
    val hook by viewModel.studioContentHook.collectAsState()

    var activeSlideIndex by remember { mutableStateOf(0) }
    val currentSlide = slides.getOrNull(activeSlideIndex) ?: slides.firstOrNull()

    // Decode base64 image if present
    val slideBitmap = remember(currentSlide?.imageBase64) {
        currentSlide?.imageBase64?.let { b64 ->
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val aspectFloat = when (aspectRatio) {
        "4:5" -> 4f / 5f
        "3:4" -> 3f / 4f
        "9:16" -> 9f / 16f
        else -> 1f
    }

    val activeFontFamily = when (fontChoice) {
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- Live Carousel Visual Preview Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Pratinjau Slide Fullscreen:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Rasio $aspectRatio • Gaya $typographyStyle", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = UtilityBlue400.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Tema: $carouselTheme",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UtilityBlue400,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // --- Fullscreen Background Card with Live Dynamic Typography Overlay ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectFloat)
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            // 1. Pure Fullscreen Background (Image or Fallback Gradient)
            if (slideBitmap != null) {
                Image(
                    bitmap = slideBitmap,
                    contentDescription = "Background Slide",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            when (bgPreset) {
                                "DARK_CYAN" -> Brush.verticalGradient(listOf(Color(0xFF042F2E), Color(0xFF115E59), Color(0xFF0E7490)))
                                "WARM_SUNSET" -> Brush.verticalGradient(listOf(Color(0xFF4C0519), Color(0xFF831843), Color(0xFF9D174D)))
                                "SOLID_DARK" -> Brush.verticalGradient(listOf(Color(0xFF18181B), Color(0xFF27272A)))
                                else -> Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF312E81)))
                            }
                        )
                )
            }

            // 2. Readability Scrim / Backdrop Overlay based on style
            when (typographyStyle) {
                "Bottom Scrim" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.9f)),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                }
                "Glassmorphism Card" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }
                "Cyber Neon" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF080216).copy(alpha = 0.4f), Color(0xFF080216).copy(alpha = 0.75f))
                                )
                            )
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.65f))
                                )
                            )
                    )
                }
            }

            // 3. Dynamic Typography Content Overlay
            SlideTypographyLayout(
                slideNumber = activeSlideIndex + 1,
                totalSlides = slides.size,
                headline = currentSlide?.headline ?: "Judul Slide",
                body = currentSlide?.body ?: "Deskripsi teks slide...",
                subtext = currentSlide?.subtext ?: "Geser ➡️",
                watermark = watermark,
                style = typographyStyle,
                fontFamily = activeFontFamily,
                aspectRatio = aspectRatio
            )
        }

        // --- Slide Navigation Selector ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(slides) { idx, s ->
                    FilterChip(
                        selected = activeSlideIndex == idx,
                        onClick = { activeSlideIndex = idx },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Slide ${idx + 1}")
                                if (s.imageBase64 != null) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(12.dp), tint = UtilityBlue400)
                                }
                            }
                        }
                    )
                }
            }

            IconButton(onClick = { viewModel.addCarouselSlide() }) {
                Icon(Icons.Default.AddCircle, contentDescription = "Tambah Slide", tint = MaterialTheme.colorScheme.primary)
            }

            if (slides.size > 1) {
                IconButton(onClick = {
                    viewModel.removeCarouselSlide(activeSlideIndex)
                    if (activeSlideIndex >= slides.size - 1) {
                        activeSlideIndex = (slides.size - 2).coerceAtLeast(0)
                    }
                }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Slide", tint = StatusFailed)
                }
            }
        }

        // --- 1. ASPECT RATIO SELECTOR ---
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📐 Pilihan Ukuran & Aspek Rasio:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                val ratioOptions = listOf(
                    "1:1" to "1:1 (Square IG)",
                    "4:5" to "4:5 (Portrait IG)",
                    "3:4" to "3:4 (Feed Post)",
                    "9:16" to "9:16 (Story/Reels)"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ratioOptions) { (ratioKey, ratioLabel) ->
                        FilterChip(
                            selected = aspectRatio == ratioKey,
                            onClick = { viewModel.setCarouselAspectRatio(ratioKey) },
                            label = { Text(ratioLabel, fontSize = 11.sp, fontWeight = if (aspectRatio == ratioKey) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }

        // --- 2. TYPOGRAPHY STYLE SELECTOR ---
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✨ Pilihan Gaya & Model Tipografi:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                val typographyStyles = listOf(
                    "Modern Minimalist",
                    "Glassmorphism Card",
                    "Bold Hero",
                    "Editorial Serif",
                    "Bottom Scrim",
                    "Cyber Neon"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(typographyStyles) { styleName ->
                        FilterChip(
                            selected = typographyStyle == styleName,
                            onClick = { viewModel.setCarouselTypographyStyle(styleName) },
                            label = { Text(styleName, fontSize = 11.sp, fontWeight = if (typographyStyle == styleName) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }

        // --- 3. THEME & IMAGE GENERATION SECTION ---
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🎨 Tema Visual Background (Tanpa Teks):", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                val themes = listOf("Minimalist Tech", "Cyber Neon", "Aesthetic Pastel", "Dark Luxury", "Vintage Retro", "3D Illustration")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(themes) { t ->
                        FilterChip(
                            selected = carouselTheme == t,
                            onClick = { viewModel.setCarouselTheme(t) },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.generateImageForSingleSlide(activeSlideIndex) },
                        enabled = !isGeneratingImg,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isGeneratingImg) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Memproses...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Background Slide Ini", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.generateImagesForAllSlides() },
                        enabled = !isGeneratingImg,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Generate Semua Background", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- AI Generator Button for Carousel Text Content ---
        Button(
            onClick = { viewModel.generateCarouselSlidesFromCurrent(title, hook, 5) },
            enabled = !isGeneratingAi,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isGeneratingAi) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Gemini Sedang Menyusun Teks Slide...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Ulang Teks 5 Slide dengan AI")
            }
        }

        // --- Slide Editor Fields ---
        if (currentSlide != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Edit Teks Slide ${activeSlideIndex + 1}:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = currentSlide.headline,
                        onValueChange = { viewModel.updateCarouselSlide(activeSlideIndex, it, currentSlide.body, currentSlide.subtext) },
                        label = { Text("Headline Slide") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentSlide.body,
                        onValueChange = { viewModel.updateCarouselSlide(activeSlideIndex, currentSlide.headline, it, currentSlide.subtext) },
                        label = { Text("Isi / Poin Penjelasan") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentSlide.subtext,
                        onValueChange = { viewModel.updateCarouselSlide(activeSlideIndex, currentSlide.headline, currentSlide.body, it) },
                        label = { Text("Subtext / CTA (mis. Geser ➡️)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- Visual & Font Customization Toolbar ---
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Pengaturan Teks & Akun:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = watermark,
                        onValueChange = { viewModel.setCarouselWatermark(it) },
                        label = { Text("Nama Akun / Watermark") },
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jenis Font:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = fontChoice == "Sans", onClick = { viewModel.setCarouselFontFamily("Sans") }, label = { Text("Sans", fontSize = 10.sp) })
                            FilterChip(selected = fontChoice == "Serif", onClick = { viewModel.setCarouselFontFamily("Serif") }, label = { Text("Serif", fontSize = 10.sp) })
                            FilterChip(selected = fontChoice == "Monospace", onClick = { viewModel.setCarouselFontFamily("Monospace") }, label = { Text("Mono", fontSize = 10.sp) })
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// DYNAMIC TYPOGRAPHY LAYOUT COMPONENT
// ==========================================
@Composable
fun SlideTypographyLayout(
    slideNumber: Int,
    totalSlides: Int,
    headline: String,
    body: String,
    subtext: String,
    watermark: String,
    style: String,
    fontFamily: FontFamily,
    aspectRatio: String
) {
    val paddingHorizontal = if (aspectRatio == "9:16") 20.dp else 16.dp
    val paddingVertical = if (aspectRatio == "9:16") 28.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- 1. HEADER (Slide Number Badge & Watermark) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (style) {
                "Cyber Neon" -> {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF00F0FF).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF))
                    ) {
                        Text(
                            text = "SLIDE 0$slideNumber / 0$totalSlides",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00F0FF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = watermark.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFF007F)
                    )
                }
                "Editorial Serif" -> {
                    Text(
                        text = "$slideNumber / $totalSlides",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = watermark,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = "$slideNumber / $totalSlides",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = watermark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // --- 2. BODY CONTENT (Based on Typography Style) ---
        when (style) {
            "Glassmorphism Card" -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = headline,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontFamily = fontFamily,
                            lineHeight = 24.sp
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.25f),
                            thickness = 1.dp,
                            modifier = Modifier.width(60.dp)
                        )
                        Text(
                            text = body,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.95f),
                            textAlign = TextAlign.Center,
                            fontFamily = fontFamily,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            "Bold Hero" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = headline,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = fontFamily,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = body,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            lineHeight = 20.sp,
                            fontFamily = fontFamily,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            "Editorial Serif" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "“",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        color = UtilityBlue400,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = headline,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 25.sp
                    )
                    HorizontalDivider(
                        color = UtilityBlue400.copy(alpha = 0.6f),
                        thickness = 1.5.dp,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        fontFamily = FontFamily.Serif,
                        lineHeight = 19.sp
                    )
                }
            }

            "Cyber Neon" -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "► $headline",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00F0FF),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 23.sp
                        )
                        Text(
                            text = body,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            "Bottom Scrim" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = headline,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = fontFamily,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        fontFamily = fontFamily,
                        lineHeight = 18.sp
                    )
                }
            }

            else -> { // Modern Minimalist
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = headline,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontFamily = fontFamily,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        fontFamily = fontFamily,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // --- 3. BOTTOM CTA / SUBTEXT ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = subtext,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

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

                // Action: Download Audio & Full Transcribe via gemini-3.5-transcribe
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
                            Text("Transkripsi (gemini-3.5)...", fontSize = 12.sp)
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
                            text = "${videoInfo?.channelName ?: "YouTube Podcast"} • ${activeHighlight?.durationFormatted ?: "00:${cutStartSec.toInt()} - 01:${(cutEndSec % 60).toInt()}"}",
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
                            text = activeHighlight?.hook ?: "🔥 99% Orang Belum Tahu Formula Ini!",
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
                            text = "💬 Auto-Subtitles Highlight:",
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
                    Text("✂️ Pemotong Klip Video (Detik):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                            Text(text = "🔥 Hook: ${hl.hook}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "💡 Potensi Viral: ${hl.reasonWhyViral}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Naskah Transkripsi Full (gemini-3.5)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        // --- Live AI Video Simulation Canvas ---
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
                // Top Badges
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
                            text = "$aspectRatio • ${durationSeconds}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Center Content: Play Button or Animation Visualizer
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

                // Bottom: Prompt Preview Summary & Watermark
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

        // --- Prompt Input Box ---
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

                // Quick Prompt Inspiration Chips
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

        // --- Video Configuration Toolbar (Aspect Ratio, Style, Duration) ---
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Pengaturan Rasio & Style Video:", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                // Aspect Ratio Selector
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

                // Style Preset Selector
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

                // Duration Selector
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

        // --- Action Buttons: Generate Video & Apply to Post ---
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
        // --- 9:16 Video Canvas Preview Box ---
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
                // Top Bold Hook Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Yellow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = hookText.ifBlank { "HOOK 3 DETIK VIDEO DI SINI 🔥" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                    )
                }

                // Center Icon Video Camera
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

                // Bottom Subtitles Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💬 Subtitle Dinamis:",
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

        // --- AI Generator Button for Video Copy ---
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

        // --- Hook Banner Text Editor ---
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

        // --- Subtitles Editor ---
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
    var daysAhead by remember { mutableStateOf(0) } // 0 = Hari ini, 1 = Besok, 2 = Lusa

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
