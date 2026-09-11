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

                    // Overlay elemen Canva: hanya pada slide aktif & saat mode edit.
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
                                    var pos by remember(el.element, el.xFraction, el.yFraction, pageRole) {
                                        mutableStateOf(Offset(el.xFraction, el.yFraction))
                                    }
                                    var elemSize by remember(el.element) { mutableStateOf(IntSize.Zero) }
                                    val selected = selectedElement == el.element
                                    val elWidthDp = maxWidth * el.widthFraction
                                    val sizePx = boxWpx * 0.052f * design.baseFontScale * el.fontScale
                                    val fontSizeSp = with(density) { sizePx.toSp() }
                                    val ts = styleForElement(el, design, fontSizeSp, sizePx)

                                    Box(
                                        modifier = Modifier
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
                                            .clickable { selectedElement = el.element }
                                            .pointerInput(el.element, pageRole, boxWpx, boxHpx) {
                                                detectDragGestures(
                                                    onDragStart = { selectedElement = el.element },
                                                    onDragEnd = { viewModel.setElementPosition(pageRole, el.element, pos.x, pos.y) }
                                                ) { change, drag ->
                                                    change.consume()
                                                    pos = Offset(
                                                        (pos.x + drag.x / boxWpx).coerceIn(0f, 1f),
                                                        (pos.y + drag.y / boxHpx).coerceIn(0f, 1f)
                                                    )
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
                "Canva-style: ketuk elemen lalu geser langsung untuk memindahnya. Tekan 'Tetapkan Favorit' agar posisi & gaya dipakai ulang oleh mesin.",
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
                    modifier = Modifier.fillMa