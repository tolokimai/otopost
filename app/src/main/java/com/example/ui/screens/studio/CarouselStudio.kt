package com.example.ui.screens.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CarouselDesign
import com.example.data.local.entity.CarouselElement
import com.example.data.local.entity.CarouselPresets
import com.example.data.local.entity.CarouselSlide
import com.example.data.local.entity.ElementLayout
import com.example.data.local.entity.SlideRole
import com.example.data.local.entity.TextAlignH
import com.example.data.local.entity.TextCase
import com.example.data.local.entity.TextEffect
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
    CarouselElement.SWIPE -> "Geser"
}

private fun roleLabel(r: SlideRole): String = when (r) {
    SlideRole.HOOK -> "HOOK (Slide Pertama)"
    SlideRole.BODY -> "ISI (Slide Tengah)"
    SlideRole.CTA -> "CTA (Slide Akhir)"
}

private fun caseLabel(c: TextCase): String = when (c) {
    TextCase.NORMAL -> "Normal"
    TextCase.UPPER -> "UPPER"
    TextCase.LOWER -> "lower"
    TextCase.TITLE -> "Title"
}

private fun effectLabel(e: TextEffect): String = when (e) {
    TextEffect.NONE -> "Polos"
    TextEffect.SHADOW -> "Bayangan"
    TextEffect.OUTLINE -> "Outline"
    TextEffect.HIGHLIGHT -> "Stabilo"
    TextEffect.NEON -> "Neon"
    TextEffect.GRADIENT -> "Gradasi"
}

private fun famOf(family: String): FontFamily = when (family) {
    "Serif" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

private fun caseText(t: String, c: TextCase): String = when (c) {
    TextCase.UPPER -> t.uppercase()
    TextCase.LOWER -> t.lowercase()
    TextCase.TITLE -> t.split(" ").joinToString(" ") { w -> if (w.isNotEmpty()) w.substring(0, 1).uppercase() + w.substring(1).lowercase() else w }
    else -> t
}

private fun alignCompose(a: TextAlignH): TextAlign = when (a) {
    TextAlignH.START -> TextAlign.Start
    TextAlignH.END -> TextAlign.End
    else -> TextAlign.Center
}

private fun hexColor(hex: String?, fallback: Color): Color = try {
    if (hex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) { fallback }

@Composable
private fun rememberBase64Image(b64: String?): ImageBitmap? = remember(b64) {
    if (b64.isNullOrBlank()) null else try {
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) { null }
}

@Composable
private fun rememberSlideBitmap(design: CarouselDesign, slide: CarouselSlide, index: Int, total: Int, backgroundOnly: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val role = CarouselPresets.roleForSlide(index, total)
    val state = produceState<ImageBitmap?>(null, design, slide, index, total, backgroundOnly) {
        value = withContext(Dispatchers.Default) {
            try {
                if (backgroundOnly) CarouselRendererBridge.renderBackground(context, design, slide, index + 1)
                else CarouselRendererBridge.render(context, design, slide, role, index + 1, total)
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

    fun renderBackground(context: android.content.Context, design: CarouselDesign, slide: CarouselSlide, num: Int): ImageBitmap =
        com.example.util.CarouselRenderer.renderBackground(context, design, slide, num).asImageBitmap()
}

// Bangun TextStyle dari elemen (dipakai overlay Canva) supaya preview mirip hasil render.
private fun styleForElement(el: ElementLayout, design: CarouselDesign, fontSizeSp: androidx.compose.ui.unit.TextUnit, sizePx: Float): TextStyle {
    val baseColor = hexColor(el.colorHex ?: design.textColorHex, Color.White)
    val accent = hexColor(design.accentColorHex, Color(0xFF38BDF8))
    var ts = TextStyle(
        color = if (el.effect == TextEffect.NEON) Color.White else baseColor,
        fontSize = fontSizeSp,
        fontWeight = if (el.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (el.italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = famOf(design.fontFamily),
        textAlign = alignCompose(el.align),
        textDecoration = if (el.underline) TextDecoration.Underline else null,
        letterSpacing = el.letterSpacing.em
    )
    ts = when (el.effect) {
        TextEffect.SHADOW -> ts.copy(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, sizePx * 0.06f), sizePx * 0.18f))
        TextEffect.NEON -> ts.copy(shadow = Shadow(accent, Offset.Zero, sizePx * 0.5f))
        TextEffect.OUTLINE -> ts.copy(shadow = Shadow(Color.Black, Offset.Zero, sizePx * 0.14f))
        TextEffect.GRADIENT -> ts.copy(brush = Brush.verticalGradient(listOf(baseColor, accent)))
        else -> ts
    }
    return ts
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
    val swipeIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setSwipeIconFromUri(uri)
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
                label = { Text(if (editMode) "Mode Edit (Canva)" else "Mode Pratinjau", fontSize = 10.sp) }
            )
        }

        // 1. Swipeable preview pager. Edit mode = editor Canva (elemen bisa diklik & digeser).
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val slide = slides.getOrNull(page)
            if (slide != null) {
                val pageRole = CarouselPresets.roleForSlide(page, total)
                val bmp = rememberSlideBitmap(design, slide, page, total, editMode)
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

                    // Overlay editor kanvas WYSIWYG: hanya pada slide aktif & saat mode edit.
                    // PENTING: setiap elemen di-anchor ke Alignment.TopStart supaya .offset { } menjadi
                    // koordinat absolut dari pojok kiri-atas kanvas. Sebelumnya container memakai
                    // contentAlignment = Center sehingga anak diletakkan di tengah DULU lalu ditambah
                    // offset -> semua elemen tergeser ~setengah kanvas ke bawah/kanan (parah di layar
                    // besar/tablet). Anchor TopStart menghilangkan pergeseran itu.
                    if (editMode && page == activeIndex && bmp != null) {
                        design.layoutFor(pageRole).forEach { el ->
                            val display = when (el.element) {
                                CarouselElement.HEADLINE -> slide.headline
                                CarouselElement.BODY -> slide.body
                                CarouselElement.SUBTEXT -> slide.subtext
                                CarouselElement.CTA -> (design.ctaText + (if (design.ctaIcon.isNotBlank()) " " + design.ctaIcon else "")).trim()
                                CarouselElement.WATERMARK -> design.watermarkText
                                CarouselElement.PAGE_NUMBER -> String.format("%02d / %02d", page + 1, total)
                                CarouselElement.LOGO -> ""
                                CarouselElement.SWIPE -> design.swipeText
                            }
                            val skip = !el.visible ||
                                (el.element == CarouselElement.PAGE_NUMBER && !design.showPageNumber) ||
                                (el.element == CarouselElement.SWIPE && (!design.swipeEnabled || (page >= total - 1 && !design.swipeShowOnLastSlide))) ||
                                (el.element != CarouselElement.LOGO && el.element != CarouselElement.SWIPE && display.isBlank()) ||
                                (el.element == CarouselElement.LOGO && design.logoBase64 == null)
                            if (!skip) {
                                key(el.element) {
                                    // State transien lokal: posisi (fraksi 0..1) & skala. Kita commit ke
                                    // ViewModel HANYA saat gesture selesai, supaya background bitmap tidak
                                    // ikut re-render tiap frame (bebas lag saat menggeser/pinch).
                                    var pos by remember(el.element, el.xFraction, el.yFraction, pageRole) {
                                        mutableStateOf(Offset(el.xFraction, el.yFraction))
                                    }
                                    var scale by remember(el.element, el.fontScale, pageRole) {
                                        mutableStateOf(el.fontScale)
                                    }
                                    var elemSize by remember(el.element) { mutableStateOf(IntSize.Zero) }
                                    val selected = selectedElement == el.element
                                    val elWidthDp = maxWidth * el.widthFraction
                                    val sizePx = boxWpx * 0.052f * design.baseFontScale * scale
                                    val fontSizeSp = with(density) { sizePx.toSp() }
                                    val ts = styleForElement(el, design, fontSizeSp, sizePx)

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset {
                                                IntOffset(
                                                    (pos.x * boxWpx - elemSize.width / 2f).roundToInt(),
                                                    (pos.y * boxHpx - elemSize.height / 2f).roundToInt()
                                                )
                                            }
                                            .onSizeChanged { elemSize = it }
                                            .clip(RoundedCornerShape(6.dp))
                                            .then(
                                                if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                                else Modifier
                                            )
                                            .pointerInput(el.element, pageRole, boxWpx, boxHpx) {
                                                // Pola editor gambar umum (mis. PhotoEditor / Canva): satu
                                                // loop gesture menangani drag 1-jari (pan) DAN pinch 2-jari
                                                // (zoom -> skala). Commit posisi & skala saat jari diangkat.
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    selectedElement = el.element
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val panChange = event.calculatePan()
                                                        val zoomChange = event.calculateZoom()
                                                        if (panChange != Offset.Zero) {
                                                            pos = Offset(
                                                                (pos.x + panChange.x / boxWpx).coerceIn(0f, 1f),
                                                                (pos.y + panChange.y / boxHpx).coerceIn(0f, 1f)
                                                            )
                                                        }
                                                        if (zoomChange != 1f) {
                                                            scale = (scale * zoomChange).coerceIn(0.4f, 3f)
                                                        }
                                                        event.changes.forEach { if (it.pressed) it.consume() }
                                                        if (event.changes.none { it.pressed }) break
                                                    }
                                                    viewModel.setElementPosition(pageRole, el.element, pos.x, pos.y)
                                                    viewModel.setElementScale(pageRole, el.element, scale)
                                                }
                                            }
                                            .padding(2.dp)
                                    ) {
                                        when (el.element) {
                                            CarouselElement.LOGO -> {
                                                val logoImg = rememberBase64Image(design.logoBase64)
                                                if (logoImg != null) {
                                                    Image(
                                                        bitmap = logoImg,
                                                        contentDescription = "Logo",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.width(elWidthDp)
                                                    )
                                                }
                                            }
                                            CarouselElement.SWIPE -> {
                                                val accentSw = hexColor(design.accentColorHex, Color(0xFF38BDF8))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                        .border(1.dp, accentSw, RoundedCornerShape(50))
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    if (!design.swipeIconOnly && design.swipeText.isNotBlank()) {
                                                        Text(caseText(design.swipeText, el.case), style = ts.copy(shadow = null))
                                                        if (design.swipeIconBase64 != null || design.swipeIconBuiltin.isNotBlank()) Spacer(Modifier.width(4.dp))
                                                    }
                                                    val swipePng = rememberBase64Image(design.swipeIconBase64)
                                                    if (swipePng != null) {
                                                        Image(
                                                            bitmap = swipePng,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(with(density) { (sizePx * 1.2f).toDp() })
                                                        )
                                                    } else if (design.swipeIconBuiltin.isNotBlank()) {
                                                        Text(design.swipeIconBuiltin, style = ts.copy(shadow = null))
                                                    }
                                                }
                                            }
                                            CarouselElement.SUBTEXT, CarouselElement.CTA -> {
                                                val accent = hexColor(design.accentColorHex, Color(0xFF38BDF8))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                        .border(1.dp, accent, RoundedCornerShape(50))
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(caseText(display, el.case), style = ts.copy(shadow = null))
                                                }
                                            }
                                            else -> {
                                                val highlight = el.effect == TextEffect.HIGHLIGHT
                                                Text(
                                                    caseText(display, el.case),
                                                    style = ts,
                                                    modifier = Modifier
                                                        .widthIn(max = elWidthDp)
                                                        .then(
                                                            if (highlight) Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(Color.Black.copy(alpha = 0.6f))
                                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                                            else Modifier
                                                        )
                                                )
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
                "Editor kanvas: ketuk elemen untuk memilih, geser 1 jari untuk memindah, cubit 2 jari untuk ubah ukuran. Tekan 'Tetapkan Favorit' agar posisi & gaya dipakai ulang oleh mesin.",
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

        // 3. Typography (jenis huruf + model penulisan + efek art) + mini preview + font family
        PanelCard("Gaya & Tipografi") {
            Text("Preset tipografi (model penulisan + efek art):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CarouselPresets.typographyStyles) { style ->
                    val selected = design.typographyStyle == style
                    val preset = CarouselPresets.typographyPresetFor(style)
                    var sampleStyle = TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = if (preset.bold) FontWeight.ExtraBold else FontWeight.Normal,
                        fontFamily = famOf(preset.fontFamily),
                        letterSpacing = preset.letterSpacing.em,
                        shadow = when (preset.effect) {
                            TextEffect.SHADOW -> Shadow(Color.Black, Offset(0f, 3f), 6f)
                            TextEffect.NEON -> Shadow(Color(0xFF38BDF8), Offset.Zero, 18f)
                            TextEffect.OUTLINE -> Shadow(Color.Black, Offset.Zero, 5f)
                            else -> null
                        }
                    )
                    if (preset.effect == TextEffect.GRADIENT) {
                        @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
                        sampleStyle = sampleStyle.copy(brush = Brush.verticalGradient(listOf(Color.White, Color(0xFF38BDF8))))
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(96.dp)
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
                            Text(caseText("Aa", preset.case), style = sampleStyle)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(style, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
                        Text(preset.description, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // 3b. Template tata letak (mengatur posisi semua elemen sekaligus)
        PanelCard("Template Tata Letak") {
            Text("Atur posisi semua elemen sekaligus, lalu bebas geser manual.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CarouselPresets.layoutTemplates) { tpl ->
                    FilterChip(
                        selected = design.layoutTemplate == tpl,
                        onClick = { viewModel.setDesignLayoutTemplate(tpl) },
                        label = { Text(tpl, fontSize = 10.sp) }
                    )
                }
            }
        }

        // 4. Background theme + local file + AI (dengan kolom prompt) + cari gambar internet + preview
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
            OutlinedTextField(
                value = design.aiBackgroundPrompt,
                onValueChange = { viewModel.setDesignAiPrompt(it) },
                label = { Text("Prompt Background AI (opsional)") },
                placeholder = { Text("Cth: gradasi biru tosca lembut, bokeh, minimalis", fontSize = 11.sp) },
                supportingText = { Text("Sistem otomatis melarang teks/logo & konten tidak aman, maks 400 karakter.", fontSize = 9.sp) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
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

            // --- Cari gambar dari internet (Openverse, tanpa AI) ---
            HorizontalDivider()
            Text("Cari Gambar dari Internet (seperti Google Images/Pinterest):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Ketik kata kunci (disarankan bahasa Inggris), pilih gambar, langsung jadi background.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var imageQuery by remember { mutableStateOf("") }
            val imageResults by viewModel.imageSearchResults.collectAsState()
            val isSearchingImages by viewModel.isSearchingImages.collectAsState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = imageQuery,
                    onValueChange = { imageQuery = it },
                    label = { Text("Kata kunci gambar") },
                    placeholder = { Text("mis. mountain sunset minimal", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.searchBackgroundImages(imageQuery) }, enabled = !isSearchingImages) {
                    Icon(Icons.Default.Search, contentDescription = "Cari", modifier = Modifier.size(16.dp))
                }
            }
            if (isSearchingImages) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Mencari gambar dari internet...", fontSize = 11.sp)
                }
            }
            if (imageResults.isNotEmpty()) {
                Text("Ketuk = pasang di slide ini \u2022 tekan lama = semua slide", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageResults) { img ->
                        val thumb = rememberBase64Image(img.thumbBase64)
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .pointerInput(img.fullUrl) {
                                    detectTapGestures(
                                        onTap = { viewModel.applySearchImageToSlide(activeIndex, img.fullUrl) },
                                        onLongPress = { viewModel.applySearchImageToAllSlides(img.fullUrl) }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = img.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = { viewModel.clearImageSearch() }) {
                    Text("Bersihkan hasil pencarian", fontSize = 10.sp)
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

        // 5b. Indikator geser (swipe) - sepenuhnya bisa dikustom (teks/emoji/PNG/off)
        PanelCard("Indikator Geser (Swipe)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = design.swipeEnabled, onCheckedChange = { viewModel.toggleDesignSwipe() })
                Text("Tampilkan indikator geser", fontSize = 12.sp)
            }
            if (design.swipeEnabled) {
                OutlinedTextField(
                    value = design.swipeText,
                    onValueChange = { viewModel.setDesignSwipeText(it) },
                    label = { Text("Teks geser (boleh kosong)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Ikon geser (emoji bawaan):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CarouselPresets.swipeIcons) { icon ->
                        val selected = design.swipeIconBase64 == null && design.swipeIconBuiltin == icon
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable { viewModel.setDesignSwipeBuiltinIcon(icon) }
                        ) {
                            Text(
                                if (icon.isBlank()) "(tanpa)" else icon,
                                fontSize = if (icon.isBlank()) 10.sp else 16.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { swipeIconPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (design.swipeIconBase64 != null) "Ganti PNG" else "Upload PNG", fontSize = 10.sp)
                    }
                    if (design.swipeIconBase64 != null) {
                        OutlinedButton(onClick = { viewModel.clearSwipeIcon() }, modifier = Modifier.weight(1f)) {
                            Text("Hapus PNG", fontSize = 10.sp)
                        }
                    }
                }
                if (design.swipeIconBase64 != null) {
                    val swipePreview = rememberBase64Image(design.swipeIconBase64)
                    if (swipePreview != null) {
                        Image(bitmap = swipePreview, contentDescription = "Ikon geser", modifier = Modifier.size(32.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = design.swipeIconOnly, onCheckedChange = { viewModel.toggleSwipeIconOnly() })
                    Text("Ikon saja (tanpa teks)", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = design.swipeShowOnLastSlide, onCheckedChange = { viewModel.toggleSwipeShowOnLast() })
                    Text("Tampilkan juga di slide terakhir", fontSize = 12.sp)
                }
                Text(
                    "Indikator geser kini elemen mandiri: ketuk 'Geser' di daftar elemen di bawah untuk memindah/mengatur ukuran & warnanya seperti elemen lain.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

        // 7 & 8. Text & element settings + case/effect + drag/assign
        PanelCard("Pengaturan Teks & Elemen") {
            Text("Peran slide aktif: ${roleLabel(role)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Pilih elemen (atau ketuk & geser langsung di preview):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Model penulisan (besar/kecil huruf):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(TextCase.NORMAL, TextCase.UPPER, TextCase.LOWER, TextCase.TITLE)) { c ->
                            FilterChip(selected = current.case == c, onClick = { viewModel.setElementCase(role, selectedElement, c) }, label = { Text(caseLabel(c), fontSize = 10.sp) })
                        }
                    }
                    Text("Efek art huruf:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(TextEffect.NONE, TextEffect.SHADOW, TextEffect.OUTLINE, TextEffect.HIGHLIGHT, TextEffect.NEON, TextEffect.GRADIENT)) { ef ->
                            FilterChip(selected = current.effect == ef, onClick = { viewModel.setElementEffect(role, selectedElement, ef) }, label = { Text(effectLabel(ef), fontSize = 10.sp) })
                        }
                    }
                    Text("Warna teks (custom):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val isAuto = current.colorHex == null
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(if (isAuto) 2.dp else 1.dp, if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                    .clickable { viewModel.setElementColor(role, selectedElement, null) },
                                contentAlignment = Alignment.Center
                            ) { Text("A", fontSize = 11.sp) }
                        }
                        items(CarouselPresets.textColors) { hex ->
                            val selColor = current.colorHex?.equals(hex, ignoreCase = true) == true
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(hexColor(hex, Color.White))
                                    .border(if (selColor) 2.dp else 1.dp, if (selColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                    .clickable { viewModel.setElementColor(role, selectedElement, hex) }
                            )
                        }
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
                Text("Auto Desain oleh AI (tema, tipografi & posisi)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
