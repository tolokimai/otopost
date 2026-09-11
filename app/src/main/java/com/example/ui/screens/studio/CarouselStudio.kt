package com.example.ui.screens.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CarouselDesign
import com.example.data.local.entity.CarouselElement
import com.example.data.local.entity.CarouselPresets
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.SlideRole
import com.example.data.local.entity.TextAlignH
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun aspectFloatOf(key: String): Float = when (key) {
    "1:1" -> 1f
    "4:5" -> 4f / 5f
    "3:4" -> 3f / 4f
    "9:16" -> 9f / 16f
    "16:9" -> 16f / 9f
    else -> 4f / 5f
}

private fun labelForElement(e: CarouselElement): String = when (e) {
    CarouselElement.HEADLINE -> "Headline"
    CarouselElement.BODY -> "Isi"
    CarouselElement.SUBTEXT -> "Subteks"
    CarouselElement.CTA -> "CTA"
    CarouselElement.LOGO -> "Logo"
    CarouselElement.WATERMARK -> "Watermark"
    CarouselElement.PAGE_NUMBER -> "No. Halaman"
}

private fun roleLabel(r: SlideRole): String = when (r) {
    SlideRole.HOOK -> "HOOK (Slide Pertama)"
    SlideRole.BODY -> "ISI (Slide Tengah)"
    SlideRole.CTA -> "CTA (Slide Akhir)"
}

@Composable
private fun rememberSlideBitmap(design: CarouselDesign, slide: CarouselSlide, index: Int, total: Int): ImageBitmap? {
    val context = LocalContext.current
    val role = CarouselPresets.roleForSlide(index, total)
    val state = produceState<ImageBitmap?>(null, design, slide, index, total) {
        value = withContext(Dispatchers.Default) {
            try {
                CarouselRendererBridge.render(context, design, slide, role, index + 1, total)
            } catch (e: Exception) {
                null
            }
        }
    }
    return state.value
}

// Jembatan kecil supaya pemanggilan renderer tetap rapi dari Compose.
private object CarouselRendererBridge {
    fun render(context: android.content.Context, design: CarouselDesign, slide: CarouselSlide, role: SlideRole, num: Int, total: Int): ImageBitmap =
        com.example.util.CarouselRenderer.render(context, design, slide, role, num, total).asImageBitmap()
}

@Composable
private fun PanelCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselStudioContent(viewModel: AutoPostViewModel) {
    val slides by viewModel.carouselSlides.collectAsState()
    val design by viewModel.carouselDesign.collectAsState()
    val isGeneratingAi by viewModel.isGeneratingCarouselAi.collectAsState()
    val isGeneratingImg by viewModel.isGeneratingSlideImage.collectAsState()
    val isExporting by viewModel.isExportingCarousel.collectAsState()
    val title by viewModel.studioContentTitle.collectAsState()
    val hook by viewModel.studioContentHook.collectAsState()

    val total = slides.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val activeIndex = if (slides.isEmpty()) 0 else pagerState.currentPage.coerceIn(0, slides.size - 1)
    val role = CarouselPresets.roleForSlide(activeIndex, total)

    var editMode by remember { mutableStateOf(true) }
    var selectedElement by remember { mutableStateOf(CarouselElement.HEADLINE) }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setSlideBackgroundFromUri(activeIndex, uri)
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setLogoFromUri(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Studio Carousel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Rasio ${design.aspectRatio} - ${design.typographyStyle} - ${roleLabel(role)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilterChip(
                selected = editMode,
                onClick = { editMode = !editMode },
                label = { Text(if (editMode) "Atur Posisi: ON" else "Atur Posisi: OFF", fontSize = 10.sp) }
            )
        }

        // 1. Swipeable preview pager (WYSIWYG)
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val slide = slides.getOrNull(page)
            if (slide != null) {
                val pageRole = CarouselPresets.roleForSlide(page, total)
                val bmp = rememberSlideBitmap(design, slide, page, total)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectFloatOf(design.aspectRatio))
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val boxWpx = with(density) { maxWidth.toPx() }
                    val boxHpx = with(density) { maxHeight.toPx() }

                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Slide ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }

                    if (editMode && page == activeIndex) {
                        design.layoutFor(pageRole).forEach { el ->
                            if (el.visible) {
                                key(el.element) {
                                    var pos by remember(el.element, el.xFraction, el.yFraction, pageRole) {
                                        mutableStateOf(Offset(el.xFraction, el.yFraction))
                                    }
                                    val selected = selectedElement == el.element
                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset((pos.x * boxWpx - 44f).roundToInt(), (pos.y * boxHpx - 16f).roundToInt()) }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f))
                                            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                            .clickable { selectedElement = el.element }
                                            .pointerInput(el.element, pageRole, boxWpx, boxHpx) {
                                                detectDragGestures(
                                                    onDragEnd = { viewModel.setElementPosition(pageRole, el.element, pos.x, pos.y) }
                                                ) { change, drag ->
                                                    change.consume()
                                                    pos = Offset(
                                                        (pos.x + drag.x / boxWpx).coerceIn(0f, 1f),
                                                        (pos.y + drag.y / boxHpx).coerceIn(0f, 1f)
                                                    )
                                                    selectedElement = el.element
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(labelForElement(el.element), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Slide dots
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            slides.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .padding(3.dp)
                        .size(if (i == activeIndex) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == activeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
            }
        }

        if (editMode) {
            Text(
                "Tips: geser label elemen di preview untuk mengatur posisi, lalu tekan 'Tetapkan Favorit' agar dipakai ulang oleh mesin.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Slide management + text editor (req 1 reorder)
        PanelCard("Kelola Slide (geser untuk berpindah)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.moveSlide(activeIndex, activeIndex - 1) }, enabled = activeIndex > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Geser kiri")
                }
                Text(
                    "Slide ${activeIndex + 1} / ${slides.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.moveSlide(activeIndex, activeIndex + 1) }, enabled = activeIndex < slides.size - 1) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Geser kanan")
                }
                IconButton(onClick = { viewModel.addCarouselSlide() }) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Tambah", tint = MaterialTheme.colorScheme.primary)
                }
                if (slides.size > 1) {
                    IconButton(onClick = { viewModel.removeCarouselSlide(activeIndex) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = StatusFailed)
                    }
                }
            }
            val slide = slides.getOrNull(activeIndex)
            if (slide != null) {
                OutlinedTextField(
                    value = slide.headline,
                    onValueChange = { viewModel.updateCarouselSlide(activeIndex, it, slide.body, slide.subtext) },
                    label = { Text("Headline") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = slide.body,
                    onValueChange = { viewModel.updateCarouselSlide(activeIndex, slide.headline, it, slide.subtext) },
                    label = { Text("Isi / Poin") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = slide.subtext,
                    onValueChange = { viewModel.updateCarouselSlide(activeIndex, slide.headline, slide.body, it) },
                    label = { Text("Subteks (chip)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. Aspect ratio
        PanelCard("Rasio & Ukuran") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CarouselPresets.aspectRatios) { spec ->
                    FilterChip(
                        selected = design.aspectRatio == spec.key,
                        onClick = { viewModel.setDesignAspectRatio(spec.key) },
                        label = { Text(spec.label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // 3. Typography + mini preview + font family
        PanelCard("Gaya & Tipografi") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CarouselPresets.typographyStyles) { style ->
                    val selected = design.typographyStyle == style
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(92.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setDesignTypography(style) }
                            .padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aa", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(style, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            Text("Jenis Huruf:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CarouselPresets.fontFamilies) { f ->
                    FilterChip(
                        selected = design.fontFamily == f,
                        onClick = { viewModel.setDesignFontFamily(f) },
                        label = { Text(f, fontSize = 10.sp) }
                    )
                }
            }
        }

        // 4. Background theme + local file + AI + preview
        PanelCard("Tema Visual & Background") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CarouselPresets.backgroundThemes) { t ->
                    FilterChip(
                        selected = design.backgroundTheme == t,
                        onClick = { viewModel.setDesignBackgroundTheme(t) },
                        label = { Text(t, fontSize = 11.sp) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { bgPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dari File", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { viewModel.generateBackgroundForSlide(activeIndex) }, enabled = !isGeneratingImg, modifier = Modifier.weight(1f)) {
                    Text("AI: Slide Ini", fontSize = 10.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.generateBackgroundsAllSlides() }, enabled = !isGeneratingImg, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI: Semua", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { viewModel.clearSlideBackground(activeIndex) }, modifier = Modifier.weight(1f)) {
                    Text("Hapus BG", fontSize = 10.sp)
                }
            }
            if (isGeneratingImg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Memproses background...", fontSize = 11.sp)
                }
            }
        }

        // 5. CTA icon + text
        PanelCard("Ikon & Teks CTA") {
            OutlinedTextField(
                value = design.ctaText,
                onValueChange = { viewModel.setDesignCtaText(it) },
                label = { Text("Teks CTA") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Ikon CTA:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CarouselPresets.ctaIcons) { icon ->
                    val selected = design.ctaIcon == icon
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.clickable { viewModel.setDesignCtaIcon(icon) }
                    ) {
                        Text(
                            if (icon.isBlank()) "(tanpa)" else icon,
                            fontSize = if (icon.isBlank()) 10.sp else 16.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 6. Logo & watermark
        PanelCard("Logo & Watermark") {
            OutlinedTextField(
                value = design.watermarkText,
                onValueChange = { viewModel.setDesignWatermark(it) },
                label = { Text("Teks Watermark (kosongkan = tanpa watermark)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.setWatermarkFromPersona() }, modifier = Modifier.weight(1f)) {
                    Text("Dari Persona", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text(if (design.logoBase64 != null) "Ganti Logo" else "Pilih Logo", fontSize = 10.sp)
                }
                if (design.logoBase64 != null) {
                    OutlinedButton(onClick = { viewModel.clearLogo() }, modifier = Modifier.weight(1f)) {
                        Text("Hapus Logo", fontSize = 10.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = design.showPageNumber, onCheckedChange = { viewModel.toggleDesignPageNumber() })
                Text("Tampilkan nomor halaman", fontSize = 12.sp)
            }
        }

        // 7 & 8. Text & element settings + drag/assign
        PanelCard("Pengaturan Teks & Elemen") {
            Text("Peran slide aktif: ${roleLabel(role)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Pilih elemen (atau geser langsung di preview):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(design.layoutFor(role)) { el ->
                    FilterChip(
                        selected = selectedElement == el.element,
                        onClick = { selectedElement = el.element },
                        label = { Text(labelForElement(el.element), fontSize = 10.sp) }
                    )
                }
            }
            val current = design.elementIn(role, selectedElement)
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ukuran:", fontSize = 11.sp)
                    OutlinedButton(onClick = { viewModel.adjustElementScale(role, selectedElement, -0.1f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("-") }
                    Text(String.format("%.1fx", current.fontScale), fontSize = 11.sp)
                    OutlinedButton(onClick = { viewModel.adjustElementScale(role, selectedElement, 0.1f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
                }
                if (selectedElement != CarouselElement.LOGO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = current.bold, onClick = { viewModel.toggleElementBold(role, selectedElement) }, label = { Text("B", fontWeight = FontWeight.Bold) })
                        FilterChip(selected = current.italic, onClick = { viewModel.toggleElementItalic(role, selectedElement) }, label = { Text("I", fontStyle = FontStyle.Italic) })
                        FilterChip(selected = current.underline, onClick = { viewModel.toggleElementUnderline(role, selectedElement) }, label = { Text("U") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Rata:", fontSize = 11.sp)
                        FilterChip(selected = current.align == TextAlignH.START, onClick = { viewModel.setElementAlign(role, selectedElement, TextAlignH.START) }, label = { Text("Kiri", fontSize = 10.sp) })
                        FilterChip(selected = current.align == TextAlignH.CENTER, onClick = { viewModel.setElementAlign(role, selectedElement, TextAlignH.CENTER) }, label = { Text("Tengah", fontSize = 10.sp) })
                        FilterChip(selected = current.align == TextAlignH.END, onClick = { viewModel.setElementAlign(role, selectedElement, TextAlignH.END) }, label = { Text("Kanan", fontSize = 10.sp) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = current.visible, onCheckedChange = { viewModel.toggleElementVisible(role, selectedElement) })
                    Text("Tampilkan elemen ini", fontSize = 12.sp)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ukuran teks global:", fontSize = 11.sp)
                OutlinedButton(onClick = { viewModel.adjustDesignBaseFontScale(-0.05f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("-") }
                Text(String.format("%.2fx", design.baseFontScale), fontSize = 11.sp)
                OutlinedButton(onClick = { viewModel.adjustDesignBaseFontScale(0.05f) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
            }
        }

        // 8 & 9. Assign positions & automation
        PanelCard("Penetapan Posisi & Otomatis") {
            Text(
                "Posisi & gaya disimpan per-peran (HOOK/ISI/CTA) lalu dipakai ulang saat konten di-generate mesin (isi & background saja yang berganti).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.resetRoleLayout(role) }, modifier = Modifier.weight(1f)) {
                    Text("Reset Posisi Peran", fontSize = 10.sp)
                }
                Button(onClick = { viewModel.saveDesignAsFavorite() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tetapkan Favorit", fontSize = 10.sp)
                }
            }
            Button(
                onClick = { viewModel.autoDesignCarouselWithAi() },
                enabled = !isGeneratingImg,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Auto Desain oleh AI", fontWeight = FontWeight.Bold)
            }
        }

        // Generate text + download
        Button(
            onClick = { viewModel.generateCarouselSlidesFromCurrent(title, hook, slides.size.coerceAtLeast(3)) },
            enabled = !isGeneratingAi,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isGeneratingAi) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Menyusun teks slide...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Ulang Teks (AI)")
            }
        }

        Button(
            onClick = { viewModel.exportCarousel() },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Menyimpan ke penyimpanan...")
            } else {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download Semua Slide ke Lokal", fontWeight = FontWeight.Bold)
            }
        }
    }
}
