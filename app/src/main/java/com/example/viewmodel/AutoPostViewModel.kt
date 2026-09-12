package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoPostApplication
import com.example.data.local.entity.*
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SavedCarouselStyle
import com.example.data.remote.ClipContent
import com.example.data.remote.GeneratedPersona
import com.example.data.remote.GeneratedPlanItem
import com.example.data.remote.PodcastSegmentHighlight
import com.example.data.remote.VideoCopyResult
import com.example.data.remote.YouTubeCandidate
import com.example.data.remote.YouTubeVideoInfo
import com.example.util.CarouselExporter
import com.example.util.CarouselRenderer
import com.example.util.VideoClipper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar

sealed class MainTab(val route: String, val titleId: String, val iconName: String) {
    object Dashboard : MainTab("dashboard", "Dashboard", "dashboard")
    object Persona : MainTab("persona", "Persona", "person")
    object ContentPlan : MainTab("content_plan", "Content Plan", "calendar_today")
    object Studio : MainTab("studio", "Studio", "movie_edit")
    object Settings : MainTab("settings", "Settings", "settings")
}

enum class StudioSubMode {
    CAROUSEL,
    PODCAST_CLIP,
    SELF_VIDEO,
    AI_VIDEO
}

/** Satu hasil pencarian gambar internet siap ditampilkan di grid Studio. */
data class StudioImageResult(
    val fullUrl: String,
    val thumbBase64: String?,
    val title: String
)

/** Satu potongan podcast yang sudah tersimpan sebagai file video. */
data class SavedPodcastClip(
    val title: String,
    val uri: String,
    val displayName: String,
    val startSec: Int,
    val endSec: Int
)

class AutoPostViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AutoPostApplication).repository
    val settingsManager = repository.settingsManager

    // --- Navigation & Tab State ---
    private val _currentTab = MutableStateFlow<MainTab>(MainTab.Dashboard)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

    // --- Global Notifications / Toast / Snackbar ---
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun showMessage(msg: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(msg)
        }
    }

    // --- Settings State ---
    val settings: StateFlow<AppSettings> = settingsManager.settings

    fun updateSettings(newSettings: AppSettings) {
        settingsManager.updateSettings(newSettings)
        showMessage("Pengaturan berhasil disimpan!")
    }

    // --- Test Connection State ---
    private val _testConnectionResult = MutableStateFlow<Pair<String, Pair<Boolean, String>>?>(null)
    val testConnectionResult = _testConnectionResult.asStateFlow()
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection = _isTestingConnection.asStateFlow()

    fun testApiConnection(platformKey: String, keyOrToken: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            val result = when (platformKey) {
                "GEMINI" -> repository.geminiService.testConnection(keyOrToken)
                "TIKTOK" -> repository.socialPublisher.testPlatformConnection(SocialPlatform.TIKTOK, keyOrToken)
                "INSTAGRAM" -> repository.socialPublisher.testPlatformConnection(SocialPlatform.INSTAGRAM, keyOrToken)
                "YOUTUBE" -> repository.socialPublisher.testPlatformConnection(SocialPlatform.YOUTUBE_SHORTS, keyOrToken)
                else -> Pair(false, "Platform tidak dikenal")
            }
            _testConnectionResult.value = Pair(platformKey, result)
            _isTestingConnection.value = false
        }
    }

    fun clearTestResult() {
        _testConnectionResult.value = null
    }

    // --- Dashboard State ---
    val scheduledCount: StateFlow<Int> = repository.scheduledCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val draftCount: StateFlow<Int> = repository.draftCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val postedCount: StateFlow<Int> = repository.postedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val failedCount: StateFlow<Int> = repository.failedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allPosts: StateFlow<List<ScheduledPostEntity>> = repository.allScheduledPosts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val postingLogs: StateFlow<List<PostingLogEntity>> = repository.allPostingLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dashboardTimelineFilterDays = MutableStateFlow(7)
    val dashboardTimelineFilterDays: StateFlow<Int> = _dashboardTimelineFilterDays.asStateFlow()

    fun setTimelineFilter(days: Int) {
        _dashboardTimelineFilterDays.value = days
    }

    fun retryPost(postId: Long) {
        viewModelScope.launch {
            showMessage("Mencoba memposting ulang konten...")
            repository.retryPostNow(postId)
            showMessage("Proses posting selesai. Periksa status pada log.")
        }
    }

    fun publishPostImmediately(post: ScheduledPostEntity) {
        viewModelScope.launch {
            showMessage("Memposting sekarang ke ${post.targetPlatforms.joinToString { it.name }}...")
            repository.executeImmediatePublish(post)
            showMessage("Proses posting selesai.")
        }
    }

    fun deletePost(post: ScheduledPostEntity) {
        viewModelScope.launch {
            repository.deletePost(post)
            showMessage("Jadwal posting dihapus.")
        }
    }

    // --- Personas ---
    val personas: StateFlow<List<PersonaEntity>> = repository.allPersonas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val defaultPersona: StateFlow<PersonaEntity?> = repository.defaultPersona.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isGeneratingPersonaAi = MutableStateFlow(false)
    val isGeneratingPersonaAi = _isGeneratingPersonaAi.asStateFlow()

    fun savePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            repository.savePersona(persona)
            showMessage("Persona '${persona.brandName}' berhasil disimpan!")
        }
    }

    fun setDefaultPersona(id: Long) {
        viewModelScope.launch {
            repository.setDefaultPersona(id)
            showMessage("Persona default diperbarui.")
        }
    }

    fun deletePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            repository.deletePersona(persona)
            showMessage("Persona dihapus.")
        }
    }

    fun generatePersonaWithAi(keywords: String, niche: String, language: String, onResult: (GeneratedPersona) -> Unit) {
        viewModelScope.launch {
            _isGeneratingPersonaAi.value = true
            val generated = repository.geminiService.generatePersona(keywords, niche, language)
            _isGeneratingPersonaAi.value = false
            onResult(generated)
            showMessage("Persona berhasil di-generate oleh Gemini AI!")
        }
    }

    // --- Content Plan ---
    private val _selectedPersonaForPlan = MutableStateFlow<PersonaEntity?>(null)
    val selectedPersonaForPlan: StateFlow<PersonaEntity?> = _selectedPersonaForPlan.asStateFlow()

    fun selectPersonaForPlan(persona: PersonaEntity) {
        _selectedPersonaForPlan.value = persona
    }

    private val _bigThemes = MutableStateFlow<List<String>>(emptyList())
    val bigThemes: StateFlow<List<String>> = _bigThemes.asStateFlow()

    private val _isGeneratingThemes = MutableStateFlow(false)
    val isGeneratingThemes = _isGeneratingThemes.asStateFlow()

    fun generateThemeSuggestions(persona: PersonaEntity) {
        viewModelScope.launch {
            _isGeneratingThemes.value = true
            val themes = repository.geminiService.generateThemes(persona, 4)
            _bigThemes.value = themes
            _isGeneratingThemes.value = false
        }
    }

    val contentPlans: StateFlow<List<ContentPlanEntity>> = repository.allPlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val planItems: StateFlow<List<ContentPlanItemEntity>> = repository.allPlanItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGeneratingPlan = MutableStateFlow(false)
    val isGeneratingPlan = _isGeneratingPlan.asStateFlow()

    fun generateAndSaveContentPlan(
        persona: PersonaEntity,
        theme: String,
        durationDays: Int
    ) {
        viewModelScope.launch {
            _isGeneratingPlan.value = true
            showMessage("Gemini AI sedang merancang Content Plan $durationDays hari...")
            val generatedItems = repository.geminiService.generateContentPlan(persona, theme, durationDays)

            val calendar = Calendar.getInstance()
            val hour = settings.value.defaultPostHour
            val minute = settings.value.defaultPostMinute
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)

            val planEntity = ContentPlanEntity(
                personaId = persona.id,
                theme = theme,
                durationDays = durationDays
            )

            val itemEntities = generatedItems.mapIndexed { index, item ->
                calendar.add(Calendar.DAY_OF_YEAR, if (index == 0) 0 else 1)
                ContentPlanItemEntity(
                    planId = 0,
                    dayNumber = item.dayNumber,
                    scheduledDateMillis = calendar.timeInMillis,
                    title = item.title,
                    format = item.format,
                    hook = item.hook,
                    captionDraft = item.captionDraft,
                    hashtags = item.hashtags
                )
            }

            repository.createPlanWithItems(planEntity, itemEntities)
            _isGeneratingPlan.value = false
            showMessage("Content Plan $durationDays hari berhasil dibuat!")
        }
    }

    fun updatePlanItem(item: ContentPlanItemEntity) {
        viewModelScope.launch {
            repository.updatePlanItem(item)
            showMessage("Item rencana konten diperbarui.")
        }
    }

    fun deletePlanItem(item: ContentPlanItemEntity) {
        viewModelScope.launch {
            repository.deletePlanItem(item)
            showMessage("Item rencana konten dihapus.")
        }
    }

    fun deleteEntirePlan(plan: ContentPlanEntity) {
        viewModelScope.launch {
            repository.deletePlan(plan)
            showMessage("Content plan dihapus.")
        }
    }

    // --- Studio & Sub-Modes ---
    private val _studioSubMode = MutableStateFlow(StudioSubMode.CAROUSEL)
    val studioSubMode: StateFlow<StudioSubMode> = _studioSubMode.asStateFlow()

    fun setStudioSubMode(mode: StudioSubMode) {
        _studioSubMode.value = mode
    }

    // Studio Active Input State (Pre-filled from Content Plan or manual)
    private val _studioActivePlanItemId = MutableStateFlow<Long?>(null)
    val studioActivePlanItemId = _studioActivePlanItemId.asStateFlow()

    private val _studioContentTitle = MutableStateFlow("Rahasia Sukses Konten 2026")
    val studioContentTitle = _studioContentTitle.asStateFlow()

    private val _studioContentHook = MutableStateFlow("99% Orang Salah Paham Soal Ini!")
    val studioContentHook = _studioContentHook.asStateFlow()

    private val _studioContentCaption = MutableStateFlow("Simak penjelasan lengkapnya sampai habis. Share ke temanmu yang butuh insight ini! \ud83d\udc47")
    val studioContentCaption = _studioContentCaption.asStateFlow()

    private val _studioContentHashtags = MutableStateFlow("#creator #fyp #contenttips #viral")
    val studioContentHashtags = _studioContentHashtags.asStateFlow()

    fun updateStudioCopy(title: String, hook: String, caption: String, hashtags: String) {
        _studioContentTitle.value = title
        _studioContentHook.value = hook
        _studioContentCaption.value = caption
        _studioContentHashtags.value = hashtags
    }

    fun sendPlanItemToStudio(item: ContentPlanItemEntity) {
        viewModelScope.launch {
            repository.markItemSentToStudio(item.id)
            _studioActivePlanItemId.value = item.id
            _studioContentTitle.value = item.title
            _studioContentHook.value = item.hook
            _studioContentCaption.value = item.captionDraft
            _studioContentHashtags.value = item.hashtags

            when (item.format) {
                ContentFormat.CAROUSEL -> {
                    _studioSubMode.value = StudioSubMode.CAROUSEL
                    generateCarouselSlidesFromCurrent(item.title, item.hook, 5)
                }
                ContentFormat.PODCAST_CLIP -> {
                    _studioSubMode.value = StudioSubMode.PODCAST_CLIP
                    searchPodcastCandidates(item.title)
                }
                ContentFormat.SELF_VIDEO -> {
                    _studioSubMode.value = StudioSubMode.SELF_VIDEO
                    generateVideoScript(item.title)
                }
                ContentFormat.AI_VIDEO -> {
                    _studioSubMode.value = StudioSubMode.AI_VIDEO
                    setAiVideoPrompt("${item.title}, ${item.hook}")
                }
            }
            selectTab(MainTab.Studio)
            showMessage("Item '${item.title.take(25)}...' dikirim ke Studio!")
        }
    }

    // 4a. CAROUSEL STUDIO STATE
    private val _carouselSlides = MutableStateFlow<List<CarouselSlide>>(
        listOf(
            CarouselSlide(1, "5 Langkah Sukses Content Creation", "Panduan praktis dari nol untuk pemula di 2026.", "", themeName = "Minimalist Tech"),
            CarouselSlide(2, "01. Temukan Niche Spesifik", "Fokus pada satu problem audiens yang belum banyak dibahas orang.", "", themeName = "Minimalist Tech"),
            CarouselSlide(3, "02. Kuasai Hook 3 Detik", "Buat kalimat pembuka yang memicu rasa ingin tahu (curiosity gap).", "", themeName = "Minimalist Tech"),
            CarouselSlide(4, "03. Konsistensi Posting", "Jadwalkan postinganmu secara otomatis tiap hari di jam optimal.", "", themeName = "Minimalist Tech"),
            CarouselSlide(5, "Simpan Post Ini!", "Bagikan postingan ini ke teman kreatormu & follow untuk tips lainnya.", "", themeName = "Minimalist Tech")
        )
    )
    val carouselSlides = _carouselSlides.asStateFlow()

    private val _carouselBackgroundPreset = MutableStateFlow("INDIGO_GRADIENT")
    val carouselBackgroundPreset = _carouselBackgroundPreset.asStateFlow()

    private val _carouselTheme = MutableStateFlow("Minimalist Tech")
    val carouselTheme = _carouselTheme.asStateFlow()

    private val _carouselAspectRatio = MutableStateFlow("1:1")
    val carouselAspectRatio = _carouselAspectRatio.asStateFlow()

    private val _carouselTypographyStyle = MutableStateFlow("Modern Minimalist")
    val carouselTypographyStyle = _carouselTypographyStyle.asStateFlow()

    private val _carouselWatermark = MutableStateFlow("@AutoPostStudio")
    val carouselWatermark = _carouselWatermark.asStateFlow()

    private val _carouselFontFamily = MutableStateFlow("Sans")
    val carouselFontFamily = _carouselFontFamily.asStateFlow()

    // --- NEW: Full Carousel Design Model (WYSIWYG, manual + otomatis) ---
    private val _carouselDesign = MutableStateFlow(settingsManager.loadFavoriteCarouselDesign())
    val carouselDesign = _carouselDesign.asStateFlow()

    private val _isExportingCarousel = MutableStateFlow(false)
    val isExportingCarousel = _isExportingCarousel.asStateFlow()

    private val _isGeneratingCarouselAi = MutableStateFlow(false)
    val isGeneratingCarouselAi = _isGeneratingCarouselAi.asStateFlow()

    private val _isGeneratingSlideImage = MutableStateFlow(false)
    val isGeneratingSlideImage = _isGeneratingSlideImage.asStateFlow()

    // --- Pencarian gambar internet (Wikimedia Commons + fallback) ---
    private val _imageSearchResults = MutableStateFlow<List<StudioImageResult>>(emptyList())
    val imageSearchResults = _imageSearchResults.asStateFlow()
    private val _isSearchingImages = MutableStateFlow(false)
    val isSearchingImages = _isSearchingImages.asStateFlow()

    // --- Galeri background upload (persisten) & gaya carousel tersimpan bernama ---
    private val _uploadedBackgrounds = MutableStateFlow(settingsManager.loadUploadedBackgrounds())
    val uploadedBackgrounds = _uploadedBackgrounds.asStateFlow()

    private val _savedStyles = MutableStateFlow(settingsManager.loadSavedStyles())
    val savedStyles = _savedStyles.asStateFlow()

    fun setCarouselTheme(theme: String) {
        _carouselTheme.value = theme
    }

    fun setCarouselAspectRatio(ratio: String) {
        _carouselAspectRatio.value = ratio
    }

    fun setCarouselTypographyStyle(style: String) {
        _carouselTypographyStyle.value = style
    }

    fun updateCarouselSlide(index: Int, headline: String, body: String, subtext: String) {
        val list = _carouselSlides.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(headline = headline, body = body, subtext = subtext)
            _carouselSlides.value = list
        }
    }

    fun addCarouselSlide() {
        val list = _carouselSlides.value.toMutableList()
        val nextNum = list.size + 1
        list.add(CarouselSlide(nextNum, "Slide $nextNum Poin Baru", "Tambahkan penjelasan detail slide di sini.", "", themeName = _carouselTheme.value))
        _carouselSlides.value = list
    }

    fun removeCarouselSlide(index: Int) {
        val list = _carouselSlides.value.toMutableList()
        if (list.size > 1 && index in list.indices) {
            list.removeAt(index)
            _carouselSlides.value = list.mapIndexed { i, s -> s.copy(slideNumber = i + 1) }
        }
    }

    fun setCarouselBackgroundPreset(preset: String) {
        _carouselBackgroundPreset.value = preset
    }

    fun setCarouselWatermark(watermark: String) {
        _carouselWatermark.value = watermark
    }

    fun setCarouselFontFamily(font: String) {
        _carouselFontFamily.value = font
    }

    fun generateCarouselSlidesFromCurrent(topic: String, hook: String, count: Int = 5) {
        viewModelScope.launch {
            _isGeneratingCarouselAi.value = true
            val slides = repository.geminiService.generateCarouselSlides(topic, hook, count)
            val themedSlides = slides.map { it.copy(themeName = _carouselTheme.value) }
            _carouselSlides.value = themedSlides
            _isGeneratingCarouselAi.value = false
            showMessage("Slide Carousel berhasil di-generate AI!")
        }
    }

    fun generateImageForSingleSlide(index: Int) {
        viewModelScope.launch {
            val list = _carouselSlides.value.toMutableList()
            if (index in list.indices) {
                _isGeneratingSlideImage.value = true
                val slide = list[index]
                val currentTheme = _carouselTheme.value
                val currentRatio = _carouselAspectRatio.value
                showMessage("Memproses background untuk Slide ${slide.slideNumber} ($currentTheme, $currentRatio)...")
                val imgResult = repository.geminiService.generateSlideImage(slide.headline, slide.body, currentTheme, slide.slideNumber, currentRatio)
                list[index] = slide.copy(
                    imageBase64 = imgResult.base64Data,
                    imagePrompt = imgResult.promptUsed,
                    themeName = currentTheme
                )
                _carouselSlides.value = list
                _isGeneratingSlideImage.value = false
                val msg = if (imgResult.isAiGenerated) {
                    "\u2728 Background AI Imagen 3 berhasil dibuat untuk Slide ${slide.slideNumber}!"
                } else {
                    "\ud83c\udfa8 Background '$currentTheme' berhasil dibuat untuk Slide ${slide.slideNumber}!"
                }
                showMessage(msg)
            }
        }
    }

    fun generateImagesForAllSlides() {
        viewModelScope.launch {
            _isGeneratingSlideImage.value = true
            val currentTheme = _carouselTheme.value
            val currentRatio = _carouselAspectRatio.value
            showMessage("Memproses visual background untuk semua slide ($currentTheme, $currentRatio)...")
            val list = _carouselSlides.value.toMutableList()
            var aiCount = 0
            for (i in list.indices) {
                val slide = list[i]
                val imgResult = repository.geminiService.generateSlideImage(slide.headline, slide.body, currentTheme, slide.slideNumber, currentRatio)
                if (imgResult.isAiGenerated) aiCount++
                list[i] = slide.copy(
                    imageBase64 = imgResult.base64Data,
                    imagePrompt = imgResult.promptUsed,
                    themeName = currentTheme
                )
            }
            _carouselSlides.value = list
            _isGeneratingSlideImage.value = false
            val msg = if (aiCount > 0) {
                "\u2728 $aiCount background AI Imagen 3 & grafis slide berhasil dibuat!"
            } else {
                "\ud83c\udfa8 Semua background '$currentTheme' berhasil dipasang pada slide!"
            }
            showMessage(msg)
        }
    }

    // ============================================================
    // NEW CAROUSEL DESIGN ENGINE (manual + otomatis AI, WYSIWYG)
    // ============================================================
    private fun updateDesign(block: (CarouselDesign) -> CarouselDesign) {
        _carouselDesign.value = block(_carouselDesign.value)
    }

    fun setDesignAspectRatio(key: String) = updateDesign { it.copy(aspectRatio = key) }
    fun setDesignTypography(style: String) = updateDesign { it.copy(typographyStyle = style).withTypographyApplied() }
    fun setDesignBackgroundTheme(theme: String) = updateDesign { it.copy(backgroundTheme = theme) }
    fun setDesignFontFamily(family: String) = updateDesign { it.copy(fontFamily = family) }
    fun setDesignCtaText(text: String) = updateDesign { it.copy(ctaText = text) }
    fun setDesignCtaIcon(icon: String) = updateDesign { it.copy(ctaIcon = icon) }
    fun toggleDesignSwipe() = updateDesign { it.copy(swipeEnabled = !it.swipeEnabled) }
    fun setDesignSwipeText(text: String) = updateDesign { it.copy(swipeText = text) }
    fun setDesignSwipeBuiltinIcon(icon: String) = updateDesign { it.copy(swipeIconBuiltin = icon, swipeIconBase64 = null) }
    fun toggleSwipeIconOnly() = updateDesign { it.copy(swipeIconOnly = !it.swipeIconOnly) }
    fun toggleSwipeShowOnLast() = updateDesign { it.copy(swipeShowOnLastSlide = !it.swipeShowOnLastSlide) }
    fun clearSwipeIcon() {
        updateDesign { it.copy(swipeIconBase64 = null) }
        showMessage("Ikon PNG geser dihapus.")
    }
    fun setSwipeIconFromUri(uri: Uri) {
        viewModelScope.launch {
            val b64 = uriToBase64(uri)
            if (b64 != null) {
                updateDesign { it.copy(swipeIconBase64 = b64) }
                showMessage("Ikon PNG geser dipasang.")
            } else showMessage("Gagal membaca file ikon.")
        }
    }
    fun setDesignWatermark(text: String) = updateDesign { it.copy(watermarkText = text) }
    fun setDesignTextColor(hex: String) = updateDesign { it.copy(textColorHex = hex) }
    fun setDesignAccentColor(hex: String) = updateDesign { it.copy(accentColorHex = hex) }
    fun toggleDesignPageNumber() = updateDesign { it.copy(showPageNumber = !it.showPageNumber) }
    fun adjustDesignBaseFontScale(delta: Float) = updateDesign { it.copy(baseFontScale = (it.baseFontScale + delta).coerceIn(0.6f, 2.0f)) }

    fun setDesignAiPrompt(text: String) = updateDesign { it.copy(aiBackgroundPrompt = text) }
    fun setDesignLayoutTemplate(name: String) = updateDesign { it.withLayoutTemplate(name).withTypographyApplied() }
    fun setElementCase(role: SlideRole, element: CarouselElement, case: TextCase) = mutateElement(role, element) { it.copy(case = case) }
    fun setElementEffect(role: SlideRole, element: CarouselElement, effect: TextEffect) = mutateElement(role, element) { it.copy(effect = effect) }
    fun setElementColor(role: SlideRole, element: CarouselElement, hex: String?) = mutateElement(role, element) { it.copy(colorHex = hex) }

    fun setWatermarkFromPersona() {
        val p = defaultPersona.value
        if (p != null) {
            updateDesign { it.copy(watermarkText = "@" + p.brandName.trim().replace(" ", "").lowercase()) }
            showMessage("Watermark diambil dari persona '${p.brandName}'.")
        } else {
            showMessage("Belum ada persona default untuk diambil.")
        }
    }

    private fun mutateElement(role: SlideRole, element: CarouselElement, transform: (ElementLayout) -> ElementLayout) {
        updateDesign { d ->
            val el = d.elementIn(role, element) ?: return@updateDesign d
            d.withElement(role, transform(el))
        }
    }

    fun setElementPosition(role: SlideRole, element: CarouselElement, x: Float, y: Float) =
        mutateElement(role, element) { it.copy(xFraction = x.coerceIn(0f, 1f), yFraction = y.coerceIn(0f, 1f)) }

    fun toggleElementBold(role: SlideRole, element: CarouselElement) = mutateElement(role, element) { it.copy(bold = !it.bold) }
    fun toggleElementItalic(role: SlideRole, element: CarouselElement) = mutateElement(role, element) { it.copy(italic = !it.italic) }
    fun toggleElementUnderline(role: SlideRole, element: CarouselElement) = mutateElement(role, element) { it.copy(underline = !it.underline) }
    fun toggleElementVisible(role: SlideRole, element: CarouselElement) = mutateElement(role, element) { it.copy(visible = !it.visible) }
    fun adjustElementScale(role: SlideRole, element: CarouselElement, delta: Float) = mutateElement(role, element) { it.copy(fontScale = (it.fontScale + delta).coerceIn(0.4f, 3.0f)) }
    fun setElementScale(role: SlideRole, element: CarouselElement, scale: Float) = mutateElement(role, element) { it.copy(fontScale = scale.coerceIn(0.4f, 3.0f)) }
    fun setElementAlign(role: SlideRole, element: CarouselElement, align: TextAlignH) = mutateElement(role, element) { it.copy(align = align) }

    fun applyElementStyleToAllRoles(fromRole: SlideRole, element: CarouselElement) {
        val source = _carouselDesign.value.elementIn(fromRole, element) ?: return
        updateDesign { d ->
            var design = d
            for (r in SlideRole.values()) {
                if (r == fromRole) continue
                val target = design.elementIn(r, element) ?: continue
                design = design.withElement(
                    r,
                    target.copy(
                        fontScale = source.fontScale,
                        bold = source.bold,
                        italic = source.italic,
                        underline = source.underline,
                        align = source.align,
                        colorHex = source.colorHex,
                        visible = source.visible,
                        case = source.case,
                        effect = source.effect,
                        letterSpacing = source.letterSpacing
                    )
                )
            }
            design
        }
        showMessage("Gaya elemen diterapkan ke semua peran (HOOK / ISI / CTA).")
    }

    fun resetRoleLayout(role: SlideRole) {
        updateDesign { d ->
            val m = d.layouts.toMutableMap()
            m[role] = CarouselPresets.defaultLayoutFor(role)
            d.copy(layouts = m)
        }
        showMessage("Posisi elemen untuk peran ${role.name} direset.")
    }

    fun saveDesignAsFavorite() {
        settingsManager.saveFavoriteCarouselDesign(_carouselDesign.value)
        showMessage("Desain ditetapkan sebagai default/favorit. Konten baru otomatis pakai gaya & posisi ini.")
    }

    fun saveCurrentDesignAsStyle(name: String) {
        val n = name.trim()
        if (n.isBlank()) {
            showMessage("Beri nama gaya dulu sebelum menyimpan.")
            return
        }
        val list = _savedStyles.value.toMutableList()
        val idx = list.indexOfFirst { it.name.equals(n, ignoreCase = true) }
        val style = SavedCarouselStyle(n, _carouselDesign.value)
        if (idx >= 0) list[idx] = style else list.add(0, style)
        while (list.size > 20) list.removeAt(list.size - 1)
        _savedStyles.value = list
        settingsManager.saveSavedStyles(list)
        showMessage("Gaya '$n' disimpan. Bisa dipakai ulang kapan saja.")
    }

    fun applySavedStyle(name: String) {
        val style = _savedStyles.value.firstOrNull { it.name == name } ?: return
        _carouselDesign.value = style.design
        showMessage("Gaya '${style.name}' diterapkan.")
    }

    fun deleteSavedStyle(name: String) {
        val list = _savedStyles.value.toMutableList()
        if (list.removeAll { it.name == name }) {
            _savedStyles.value = list
            settingsManager.saveSavedStyles(list)
            showMessage("Gaya '$name' dihapus.")
        }
    }

    fun moveSlide(from: Int, to: Int) {
        val list = _carouselSlides.value.toMutableList()
        if (from in list.indices && to in list.indices && from != to) {
            val item = list.removeAt(from)
            list.add(to, item)
            _carouselSlides.value = list.mapIndexed { i, s -> s.copy(slideNumber = i + 1) }
        }
    }

    fun setLogoFromUri(uri: Uri) {
        viewModelScope.launch {
            val b64 = uriToBase64(uri)
            if (b64 != null) {
                updateDesign { it.copy(logoBase64 = b64) }
                showMessage("Logo dipasang.")
            } else showMessage("Gagal membaca file logo.")
        }
    }

    fun clearLogo() {
        updateDesign { it.copy(logoBase64 = null) }
        showMessage("Logo dihapus.")
    }

    fun setSlideBackgroundFromUri(index: Int, uri: Uri) {
        viewModelScope.launch {
            val b64 = uriToBase64(uri)
            if (b64 != null) {
                addUploadedBackground(b64)
                val list = _carouselSlides.value.toMutableList()
                if (index in list.indices) {
                    list[index] = list[index].copy(imageBase64 = b64)
                    _carouselSlides.value = list
                    showMessage("Background dari file dipasang di Slide ${index + 1} & disimpan ke galeri.")
                }
            } else showMessage("Gagal membaca gambar.")
        }
    }

    fun clearSlideBackground(index: Int) {
        val list = _carouselSlides.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(imageBase64 = null)
            _carouselSlides.value = list
            showMessage("Background Slide ${index + 1} dikosongkan (pakai tema).")
        }
    }

    private fun addUploadedBackground(b64: String) {
        val list = _uploadedBackgrounds.value.toMutableList()
        list.remove(b64)
        list.add(0, b64)
        while (list.size > 15) list.removeAt(list.size - 1)
        _uploadedBackgrounds.value = list
        settingsManager.saveUploadedBackgrounds(list)
    }

    fun removeUploadedBackground(b64: String) {
        val list = _uploadedBackgrounds.value.toMutableList()
        if (list.remove(b64)) {
            _uploadedBackgrounds.value = list
            settingsManager.saveUploadedBackgrounds(list)
            showMessage("Gambar dihapus dari galeri unggahan.")
        }
    }

    fun applyUploadedBackgroundToSlide(index: Int, b64: String) {
        val list = _carouselSlides.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(imageBase64 = b64)
            _carouselSlides.value = list
            showMessage("Background dipasang di Slide ${index + 1}.")
        }
    }

    fun applyUploadedBackgroundToAllSlides(b64: String) {
        val list = _carouselSlides.value.toMutableList()
        for (i in list.indices) list[i] = list[i].copy(imageBase64 = b64)
        _carouselSlides.value = list
        showMessage("Background dipasang ke semua slide.")
    }

    private suspend fun uriToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val ctx = getApplication<Application>().applicationContext
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generateBackgroundForSlide(index: Int) {
        viewModelScope.launch {
            val list = _carouselSlides.value.toMutableList()
            if (index in list.indices) {
                _isGeneratingSlideImage.value = true
                val design = _carouselDesign.value
                val theme = design.backgroundTheme
                val ratio = design.aspectRatio
                val headline = list[index].headline
                val result = smartBackground(headline, theme, ratio, design.aiBackgroundPrompt)
                if (result != null) {
                    val cur = _carouselSlides.value.toMutableList()
                    if (index in cur.indices) {
                        cur[index] = cur[index].copy(imageBase64 = result.first, imagePrompt = design.aiBackgroundPrompt, themeName = theme)
                        _carouselSlides.value = cur
                    }
                    showMessage("\u2728 Background Slide ${index + 1} terpasang via ${result.second}.")
                } else {
                    showMessage("\u274c Gagal memasang background: AI, gambar internet, & galeri lokal semuanya tidak tersedia. Cek API key/model gambar di Settings atau koneksi internet.")
                }
                _isGeneratingSlideImage.value = false
            }
        }
    }

    fun generateBackgroundsAllSlides() {
        viewModelScope.launch {
            _isGeneratingSlideImage.value = true
            val design = _carouselDesign.value
            val theme = design.backgroundTheme
            val ratio = design.aspectRatio
            val list = _carouselSlides.value.toMutableList()
            var ok = 0
            var lastSource = ""
            for (i in list.indices) {
                val res = smartBackground(list[i].headline, theme, ratio, design.aiBackgroundPrompt)
                if (res != null) {
                    ok++
                    lastSource = res.second
                    list[i] = list[i].copy(imageBase64 = res.first, imagePrompt = design.aiBackgroundPrompt, themeName = theme)
                }
            }
            _carouselSlides.value = list
            _isGeneratingSlideImage.value = false
            if (ok > 0) {
                showMessage("\u2728 $ok/${list.size} background terpasang (sumber terakhir: $lastSource).")
            } else {
                showMessage("\u274c Gagal memasang background dari AI, internet, maupun galeri lokal. Cek API key/model gambar & koneksi internet.")
            }
        }
    }

    private suspend fun smartBackground(headline: String, theme: String, ratio: String, prompt: String): Pair<String, String>? {
        val ai = repository.aiImageService.generateBackground(headline, theme, ratio, prompt)
        val aiB64 = ai.base64
        if (ai.isAiGenerated && !aiB64.isNullOrBlank()) {
            return Pair(aiB64, "AI " + (ai.modelUsed ?: ""))
        }
        val query = buildImageQuery(headline, theme, prompt)
        val web = try {
            repository.imageSearchService.search(query, 12)
        } catch (e: Exception) {
            emptyList()
        }
        for (r in web) {
            val b64 = repository.imageSearchService.downloadAsBase64(r.fullUrl)
            if (!b64.isNullOrBlank()) {
                addUploadedBackground(b64)
                return Pair(b64, "gambar internet (disimpan ke galeri)")
            }
        }
        val local = _uploadedBackgrounds.value.firstOrNull { it.isNotBlank() }
        if (local != null) {
            return Pair(local, "galeri lokal")
        }
        return null
    }

    private fun buildImageQuery(headline: String, theme: String, prompt: String): String {
        val p = prompt.trim()
        if (p.isNotBlank()) return p
        return when (theme.uppercase()) {
            "MINIMALIST TECH", "MINIMAL TECH" -> "minimal technology gradient background"
            "CYBER NEON", "NEON" -> "cyberpunk neon abstract background"
            "AESTHETIC PASTEL", "PASTEL" -> "soft pastel aesthetic gradient background"
            "DARK LUXURY", "LUXURY" -> "black gold luxury texture background"
            "VINTAGE RETRO", "RETRO" -> "retro sunset gradient background"
            "3D ILLUSTRATION", "3D ABSTRACT", "3D" -> "3d abstract render background"
            else -> "abstract aesthetic background"
        }
    }

    fun searchBackgroundImages(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            showMessage("Ketik kata kunci gambar dulu, mis. 'mountain sunset minimal'.")
            return
        }
        viewModelScope.launch {
            _isSearchingImages.value = true
            _imageSearchResults.value = emptyList()
            val found = repository.imageSearchService.search(q, 24)
            if (found.isEmpty()) {
                _isSearchingImages.value = false
                showMessage("Tidak ada gambar untuk '$q'. Coba kata kunci lain (disarankan bahasa Inggris).")
                return@launch
            }
            val limited = found.take(18)
            val mapped = limited.map { r ->
                val thumb = repository.imageSearchService.downloadAsBase64(r.thumbnailUrl)
                StudioImageResult(fullUrl = r.fullUrl, thumbBase64 = thumb, title = r.title)
            }
            _imageSearchResults.value = mapped
            _isSearchingImages.value = false
            showMessage("Ditemukan ${mapped.size} gambar. Ketuk salah satu untuk memasangnya sebagai background.")
        }
    }

    fun applySearchImageToSlide(index: Int, fullUrl: String) {
        viewModelScope.launch {
            _isGeneratingSlideImage.value = true
            val b64 = repository.imageSearchService.downloadAsBase64(fullUrl)
            if (b64 != null) {
                addUploadedBackground(b64)
                val list = _carouselSlides.value.toMutableList()
                if (index in list.indices) {
                    list[index] = list[index].copy(imageBase64 = b64)
                    _carouselSlides.value = list
                    showMessage("\ud83d\uddbc\ufe0f Gambar internet dipasang di Slide ${index + 1} & disimpan ke galeri.")
                }
            } else showMessage("Gagal mengunduh gambar. Coba gambar lain.")
            _isGeneratingSlideImage.value = false
        }
    }

    fun applySearchImageToAllSlides(fullUrl: String) {
        viewModelScope.launch {
            _isGeneratingSlideImage.value = true
            val b64 = repository.imageSearchService.downloadAsBase64(fullUrl)
            if (b64 != null) {
                addUploadedBackground(b64)
                val list = _carouselSlides.value.toMutableList()
                for (i in list.indices) list[i] = list[i].copy(imageBase64 = b64)
                _carouselSlides.value = list
                showMessage("\ud83d\uddbc\ufe0f Gambar internet dipasang ke semua slide & disimpan ke galeri.")
            } else showMessage("Gagal mengunduh gambar. Coba gambar lain.")
            _isGeneratingSlideImage.value = false
        }
    }

    fun clearImageSearch() {
        _imageSearchResults.value = emptyList()
    }

    fun autoDesignCarouselWithAi() {
        viewModelScope.launch {
            val niche = (defaultPersona.value?.niche ?: "").lowercase()
            val picked = when {
                niche.contains("tech") || niche.contains("bisnis") || niche.contains("produktivitas") || niche.contains("startup") ->
                    Triple("Minimalist Tech", "Bold Hero", "Sans")
                niche.contains("beauty") || niche.contains("fashion") || niche.contains("lifestyle") || niche.contains("wellness") ->
                    Triple("Aesthetic Pastel", "Editorial Serif", "Serif")
                niche.contains("gaming") || niche.contains("crypto") || niche.contains("ai") || niche.contains("web3") ->
                    Triple("Cyber Neon", "Cyber Neon", "Monospace")
                niche.contains("luxury") || niche.contains("finance") || niche.contains("keuangan") || niche.contains("invest") ->
                    Triple("Dark Luxury", "Magazine", "Serif")
                else -> Triple("Gradient Indigo", "Center Stage", "Sans")
            }
            val template = when {
                niche.contains("tech") || niche.contains("bisnis") || niche.contains("produktivitas") || niche.contains("startup") -> "Top Heading"
                niche.contains("beauty") || niche.contains("fashion") || niche.contains("lifestyle") || niche.contains("wellness") -> "Big Quote"
                niche.contains("gaming") || niche.contains("crypto") || niche.contains("ai") || niche.contains("web3") -> "Bottom Bar"
                niche.contains("luxury") || niche.contains("finance") || niche.contains("keuangan") || niche.contains("invest") -> "Left Aligned"
                else -> "Classic Center"
            }
            updateDesign {
                it.copy(backgroundTheme = picked.first, typographyStyle = picked.second, fontFamily = picked.third)
                    .withLayoutTemplate(template)
                    .withTypographyApplied()
            }
            showMessage("\ud83e\udd16 Auto desain lengkap: ${picked.first} \u2022 ${picked.second} \u2022 layout $template. Membuat background...")
            generateBackgroundsAllSlides()
        }
    }

    fun exportCarousel() {
        viewModelScope.launch {
            _isExportingCarousel.value = true
            showMessage("Menyimpan semua slide ke penyimpanan lokal...")
            val ctx = getApplication<Application>().applicationContext
            val design = _carouselDesign.value
            val slides = _carouselSlides.value
            val titleForName = _studioContentTitle.value
            val saved = withContext(Dispatchers.Default) {
                val bmps = CarouselRenderer.renderAll(ctx, design, slides)
                CarouselExporter.saveBitmaps(ctx, bmps, titleForName)
            }
            _isExportingCarousel.value = false
            if (saved.isEmpty()) showMessage("Gagal menyimpan gambar.")
            else showMessage("\u2705 ${saved.size} slide tersimpan di galeri (folder Pictures/AutoPostStudio).")
        }
    }

    // 4b. PODCAST CLIP STUDIO STATE
    private val _podcastVideoInfo = MutableStateFlow<YouTubeVideoInfo?>(null)
    val podcastVideoInfo = _podcastVideoInfo.asStateFlow()

    private val _podcastHighlights = MutableStateFlow<List<PodcastSegmentHighlight>>(emptyList())
    val podcastHighlights = _podcastHighlights.asStateFlow()

    private val _podcastTranscriptionResult = MutableStateFlow<com.example.data.remote.TranscriptionResult?>(null)
    val podcastTranscriptionResult = _podcastTranscriptionResult.asStateFlow()

    private val _selectedHighlightIndex = MutableStateFlow(0)
    val selectedHighlightIndex = _selectedHighlightIndex.asStateFlow()

    private val _clipStartSec = MutableStateFlow(15)
    val clipStartSec = _clipStartSec.asStateFlow()

    private val _clipEndSec = MutableStateFlow(75)
    val clipEndSec = _clipEndSec.asStateFlow()

    private val _isLoadingPodcast = MutableStateFlow(false)
    val isLoadingPodcast = _isLoadingPodcast.asStateFlow()

    private val _isDownloadingPodcast = MutableStateFlow(false)
    val isDownloadingPodcast = _isDownloadingPodcast.asStateFlow()
    val isTranscribingPodcast = _isDownloadingPodcast.asStateFlow()

    private val _podcastFullTranscript = MutableStateFlow("")
    val podcastFullTranscript = _podcastFullTranscript.asStateFlow()

    private val _isCuttingClip = MutableStateFlow(false)
    val isCuttingClip = _isCuttingClip.asStateFlow()

    private val _podcastCandidates = MutableStateFlow<List<YouTubeCandidate>>(emptyList())
    val podcastCandidates = _podcastCandidates.asStateFlow()

    private val _isSearchingPodcast = MutableStateFlow(false)
    val isSearchingPodcast = _isSearchingPodcast.asStateFlow()

    private val _noRelevantPodcast = MutableStateFlow(false)
    val noRelevantPodcast = _noRelevantPodcast.asStateFlow()

    private val _selectedCandidate = MutableStateFlow<YouTubeCandidate?>(null)
    val selectedCandidate = _selectedCandidate.asStateFlow()

    private val _clipAspectRatio = MutableStateFlow(settingsManager.settings.value.defaultClipAspectRatio)
    val clipAspectRatio = _clipAspectRatio.asStateFlow()

    private val _downloadedVideoPath = MutableStateFlow<String?>(null)
    val downloadedVideoPath = _downloadedVideoPath.asStateFlow()

    private val _isDownloadingVideoFile = MutableStateFlow(false)
    val isDownloadingVideoFile = _isDownloadingVideoFile.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _savedClips = MutableStateFlow<List<SavedPodcastClip>>(emptyList())
    val savedClips = _savedClips.asStateFlow()

    // --- Subtitle burn-in (opsional, via Clip Server) ---
    private val _subtitleEnabled = MutableStateFlow(false)
    val subtitleEnabled = _subtitleEnabled.asStateFlow()

    private val _subtitleStyle = MutableStateFlow("clean") // clean, bold, box, yellow, tiktok, karaoke, minimal, highlight, neon, pop
    val subtitleStyle = _subtitleStyle.asStateFlow()

    fun setSubtitleEnabled(enabled: Boolean) {
        _subtitleEnabled.value = enabled
    }

    fun setSubtitleStyle(style: String) {
        _subtitleStyle.value = style
    }

    // ============================================================
    // V2: Multi-select segmen, pemutar in-app, konten per-klip, thumbnail
    // ============================================================

    // --- Multi-select segmen (centang beberapa/semua rekomendasi AI) ---
    private val _selectedSegmentIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedSegmentIndices = _selectedSegmentIndices.asStateFlow()

    fun toggleSegmentSelection(index: Int) {
        val cur = _selectedSegmentIndices.value.toMutableSet()
        if (!cur.add(index)) cur.remove(index)
        _selectedSegmentIndices.value = cur
    }

    fun selectAllSegments() {
        _selectedSegmentIndices.value = _podcastHighlights.value.indices.toSet()
    }

    fun clearSegmentSelection() {
        _selectedSegmentIndices.value = emptySet()
    }

    // --- Pemutar video IN-APP (Media3/ExoPlayer), tanpa aplikasi eksternal ---
    private val _playingClipUrl = MutableStateFlow<String?>(null)
    val playingClipUrl = _playingClipUrl.asStateFlow()

    private val _isPlayerFullscreen = MutableStateFlow(false)
    val isPlayerFullscreen = _isPlayerFullscreen.asStateFlow()

    fun playClipInApp(uriString: String) {
        _playingClipUrl.value = uriString
    }

    fun closeInAppPlayer() {
        _playingClipUrl.value = null
        _isPlayerFullscreen.value = false
    }

    fun togglePlayerFullscreen() {
        _isPlayerFullscreen.value = !_isPlayerFullscreen.value
    }

    // --- Konten per-klip (AI): metadata + copywriting tiap potongan video ---
    private val _clipContents = MutableStateFlow<Map<String, ClipContent>>(emptyMap())
    val clipContents = _clipContents.asStateFlow()

    private val _generatingClipContentFor = MutableStateFlow<String?>(null)
    val generatingClipContentFor = _generatingClipContentFor.asStateFlow()

    // --- Thumbnail (frame) tiap klip tersimpan: uri -> base64 JPEG ---
    private val _clipThumbnails = MutableStateFlow<Map<String, String>>(emptyMap())
    val clipThumbnails = _clipThumbnails.asStateFlow()

    private fun snippetForClip(clip: SavedPodcastClip): String {
        return _podcastHighlights.value.firstOrNull {
            it.startSec == clip.startSec && it.endSec == clip.endSec
        }?.transcriptSnippet
            ?: _podcastHighlights.value.getOrNull(_selectedHighlightIndex.value)?.transcriptSnippet
            ?: _podcastFullTranscript.value.take(600)
    }

    fun generateClipContent(clip: SavedPodcastClip) {
        viewModelScope.launch {
            _generatingClipContentFor.value = clip.uri
            val topic = _podcastVideoInfo.value?.title ?: _studioContentTitle.value
            val content = try {
                repository.clipContentService.generateClipContent(clip.title, snippetForClip(clip), topic)
            } catch (e: Exception) {
                null
            }
            if (content != null) {
                _clipContents.value = _clipContents.value.toMutableMap().apply { put(clip.uri, content) }
                showMessage("\u2728 Konten AI untuk '${clip.title.take(24)}' siap. Tekan Copy untuk menyalin.")
            } else {
                showMessage("\u274c Gagal generate konten AI untuk klip ini. Cek API key Gemini.")
            }
            _generatingClipContentFor.value = null
        }
    }

    fun generateContentForAllClips() {
        viewModelScope.launch {
            val clips = _savedClips.value
            if (clips.isEmpty()) {
                showMessage("Belum ada klip tersimpan.")
                return@launch
            }
            val topic = _podcastVideoInfo.value?.title ?: _studioContentTitle.value
            var ok = 0
            for (clip in clips) {
                if (_clipContents.value.containsKey(clip.uri)) continue
                _generatingClipContentFor.value = clip.uri
                val content = try {
                    repository.clipContentService.generateClipContent(clip.title, snippetForClip(clip), topic)
                } catch (e: Exception) {
                    null
                }
                if (content != null) {
                    ok++
                    _clipContents.value = _clipContents.value.toMutableMap().apply { put(clip.uri, content) }
                }
            }
            _generatingClipContentFor.value = null
            showMessage("\u2728 Konten AI dibuat untuk $ok klip.")
        }
    }

    private fun generateThumbnailsFor(clips: List<SavedPodcastClip>) {
        if (clips.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            for (clip in clips) {
                if (_clipThumbnails.value.containsKey(clip.uri)) continue
                val b64 = try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(ctx, Uri.parse(clip.uri))
                    val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    if (frame != null && frame.width > 0) {
                        val targetW = 240
                        val targetH = (targetW.toFloat() * frame.height / frame.width).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(frame, targetW, targetH, true)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    } else null
                } catch (e: Exception) {
                    null
                }
                if (b64 != null) {
                    _clipThumbnails.value = _clipThumbnails.value.toMutableMap().apply { put(clip.uri, b64) }
                }
            }
        }
    }

    private var podcastSourceUrl: String = ""

    fun setStudioMetadata(title: String, hook: String, caption: String, hashtags: String) {
        updateStudioCopy(title, hook, caption, hashtags)
    }

    fun transcribeAndProcessPodcast(urlOrTopic: String) {
        downloadAndTranscribeFullVideo(urlOrTopic)
    }

    fun searchPodcastCandidates(theme: String) {
        viewModelScope.launch {
            _isSearchingPodcast.value = true
            _noRelevantPodcast.value = false
            _podcastCandidates.value = emptyList()
            _selectedCandidate.value = null
            _downloadedVideoPath.value = null
            _savedClips.value = emptyList()
            _podcastHighlights.value = emptyList()
            _podcastVideoInfo.value = null

            val key = settings.value.youTubeApiKey.trim()
            if (key.isBlank()) {
                _isSearchingPodcast.value = false
                showMessage("Isi 'YouTube API Key' dulu di Settings agar mesin bisa mencari video podcast relevan.")
                return@launch
            }
            val results = repository.youTubeSearchService.searchRelevantVideos(theme, 3)
            _podcastCandidates.value = results
            _isSearchingPodcast.value = false
            if (results.isEmpty()) {
                _noRelevantPodcast.value = true
                showMessage("Tidak ada video YouTube relevan untuk tema ini \u2014 sebaiknya ganti isi konten rencana ini.")
                return@launch
            }
            if (settings.value.autoPickBestPodcast) {
                val best = results.maxByOrNull { it.viewCount } ?: results.first()
                selectPodcastCandidate(best)
                showMessage("Mesin otomatis memilih: ${best.title.take(40)}. Bisa diganti manual kapan saja.")
            } else {
                showMessage("Ditemukan ${results.size} video relevan. Pilih salah satu untuk lanjut.")
            }
        }
    }

    fun selectPodcastCandidate(candidate: YouTubeCandidate) {
        _selectedCandidate.value = candidate
        _downloadedVideoPath.value = null
        _savedClips.value = emptyList()
        podcastSourceUrl = candidate.url.ifBlank { watchUrlFor(candidate.videoId) }
        viewModelScope.launch {
            _isLoadingPodcast.value = true
            val info = fetchTranscriptSmart(
                videoUrl = podcastSourceUrl,
                videoId = candidate.videoId,
                fallbackTitle = candidate.title,
                fallbackChannel = candidate.channelName,
                durationFormatted = candidate.durationFormatted
            )
            _podcastVideoInfo.value = info
            resolveTranscriptAndHighlights(info, applyMetadata = true)
            _isLoadingPodcast.value = false
            val src = if (info.isSample) "\u26a0\ufe0f transkrip CONTOH (server/caption tak tersedia)" else "transkrip ASLI"
            showMessage("$src \u2022 ${_podcastHighlights.value.size} segmen rekomendasi AI siap. " + clipActionHint())
        }
    }

    fun setClipAspectRatio(ratio: String) {
        _clipAspectRatio.value = ratio
    }

    fun saveDefaultClipAspectRatio(ratio: String) {
        _clipAspectRatio.value = ratio
        settingsManager.saveDefaultClipAspectRatio(ratio)
        showMessage("Rasio default klip di-set ke $ratio.")
    }

    fun downloadSelectedVideo() {
        val candidate = _selectedCandidate.value
        if (candidate == null) {
            showMessage("Pilih video dulu sebelum mengunduh.")
            return
        }
        if (repository.clipServerService.isConfigured()) {
            showMessage("Pakai server: tak perlu unduh manual. Langsung tekan 'Potong Semua' \u2014 server otomatis mengunduh HD & memotong.")
            return
        }
        viewModelScope.launch {
            _isDownloadingVideoFile.value = true
            _downloadProgress.value = 0f
            showMessage("Mengunduh video HD (best-effort) dari YouTube...")
            val ctx = getApplication<Application>().applicationContext
            val result = repository.youTubeDownloadService.downloadVideo(ctx, candidate.videoId) { p ->
                _downloadProgress.value = p
            }
            _isDownloadingVideoFile.value = false
            if (result != null) {
                _downloadedVideoPath.value = result.filePath
                showMessage("\u2705 Video terunduh (${result.qualityLabel}). Siap dipotong per segmen.")
            } else {
                _downloadedVideoPath.value = null
                showMessage("\u274c Unduh otomatis gagal (YouTube memblokir/berubah). Isi Clip Server URL di Settings untuk unduhan andal via server.")
            }
        }
    }

    fun cutSegmentAt(index: Int) {
        val highlight = _podcastHighlights.value.getOrNull(index) ?: return
        if (repository.clipServerService.isConfigured()) {
            _selectedHighlightIndex.value = index
            serverCutSegments(listOf(Triple(highlight.startSec, highlight.endSec, "clip_" + (index + 1) + "_" + highlight.title.take(24))))
            return
        }
        val path = _downloadedVideoPath.value
        if (path.isNullOrBlank()) {
            showMessage("Unduh videonya dulu sebelum memotong.")
            return
        }
        viewModelScope.launch {
            _isCuttingClip.value = true
            _selectedHighlightIndex.value = index
            val ctx = getApplication<Application>().applicationContext
            val name = "clip_" + (index + 1) + "_" + highlight.title.take(24)
            val result = withContext(Dispatchers.IO) {
                VideoClipper.cutSegment(ctx, path, highlight.startSec * 1000L, highlight.endSec * 1000L, name)
            }
            _isCuttingClip.value = false
            if (result != null) {
                val newClip = SavedPodcastClip(
                    highlight.title, result.uri, result.displayName, highlight.startSec, highlight.endSec
                )
                _savedClips.value = _savedClips.value + newClip
                generateThumbnailsFor(listOf(newClip))
                showMessage("\u2705 Segmen ${index + 1} tersimpan: ${result.displayName}")
            } else {
                showMessage("\u274c Gagal memotong segmen ${index + 1}.")
            }
        }
    }

    fun cutAllSegments() {
        val highlights = _podcastHighlights.value
        if (highlights.isEmpty()) {
            showMessage("Belum ada rekomendasi segmen.")
            return
        }
        if (repository.clipServerService.isConfigured()) {
            serverCutSegments(highlights.mapIndexed { i, h ->
                Triple(h.startSec, h.endSec, "clip_" + (i + 1) + "_" + h.title.take(24))
            })
            return
        }
        val path = _downloadedVideoPath.value
        if (path.isNullOrBlank()) {
            showMessage("Unduh videonya dulu sebelum memotong.")
            return
        }
        viewModelScope.launch {
            _isCuttingClip.value = true
            showMessage("Memotong ${highlights.size} segmen sesuai rekomendasi AI...")
            val ctx = getApplication<Application>().applicationContext
            var ok = 0
            val newClips = mutableListOf<SavedPodcastClip>()
            for ((i, h) in highlights.withIndex()) {
                val name = "clip_" + (i + 1) + "_" + h.title.take(24)
                val result = withContext(Dispatchers.IO) {
                    VideoClipper.cutSegment(ctx, path, h.startSec * 1000L, h.endSec * 1000L, name)
                }
                if (result != null) {
                    ok++
                    newClips.add(SavedPodcastClip(h.title, result.uri, result.displayName, h.startSec, h.endSec))
                }
            }
            _savedClips.value = _savedClips.value + newClips
            generateThumbnailsFor(newClips)
            _isCuttingClip.value = false
            showMessage("\u2705 $ok/${highlights.size} segmen dipotong & disimpan (Movies/AutoPostStudio).")
        }
    }

    /** Potong hanya segmen yang dicentang (v2). Fallback ke on-device bila server tak aktif. */
    fun cutSelectedSegments() {
        val highlights = _podcastHighlights.value
        val sel = _selectedSegmentIndices.value.sorted()
        if (highlights.isEmpty()) {
            showMessage("Belum ada rekomendasi segmen.")
            return
        }
        if (sel.isEmpty()) {
            showMessage("Centang minimal satu segmen dulu.")
            return
        }
        val chosen = sel.mapNotNull { i -> highlights.getOrNull(i)?.let { i to it } }
        if (repository.clipServerService.isConfigured()) {
            serverCutSegments(chosen.map { (i, h) ->
                Triple(h.startSec, h.endSec, "clip_" + (i + 1) + "_" + h.title.take(24))
            })
            return
        }
        val path = _downloadedVideoPath.value
        if (path.isNullOrBlank()) {
            showMessage("Unduh videonya dulu sebelum memotong.")
            return
        }
        viewModelScope.launch {
            _isCuttingClip.value = true
            showMessage("Memotong ${chosen.size} segmen terpilih...")
            val ctx = getApplication<Application>().applicationContext
            var ok = 0
            val newClips = mutableListOf<SavedPodcastClip>()
            for ((i, h) in chosen) {
                val name = "clip_" + (i + 1) + "_" + h.title.take(24)
                val result = withContext(Dispatchers.IO) {
                    VideoClipper.cutSegment(ctx, path, h.startSec * 1000L, h.endSec * 1000L, name)
                }
                if (result != null) {
                    ok++
                    newClips.add(SavedPodcastClip(h.title, result.uri, result.displayName, h.startSec, h.endSec))
                }
            }
            _savedClips.value = _savedClips.value + newClips
            generateThumbnailsFor(newClips)
            _isCuttingClip.value = false
            showMessage("\u2705 $ok/${chosen.size} segmen terpilih dipotong & disimpan.")
        }
    }

    fun loadPodcastForTopic(urlOrTopic: String) {
        viewModelScope.launch {
            _isLoadingPodcast.value = true
            val info = fetchTranscriptSmartForUrl(urlOrTopic)
            podcastSourceUrl = if (looksLikeUrl(urlOrTopic)) urlOrTopic.trim()
                else if (info.videoId.isNotBlank()) watchUrlFor(info.videoId) else ""
            _podcastVideoInfo.value = info
            resolveTranscriptAndHighlights(info, applyMetadata = true)
            _isLoadingPodcast.value = false
            val src = if (info.isSample) "\u26a0\ufe0f CONTOH" else "ASLI"
            showMessage("Transkrip podcast ($src) dimuat \u2022 ${_podcastHighlights.value.size} segmen viral diidentifikasi! " + clipActionHint())
        }
    }

    fun downloadAndTranscribeFullVideo(urlOrTopic: String) {
        viewModelScope.launch {
            _isDownloadingPodcast.value = true
            showMessage("Mengambil transkrip & menganalisis video...")
            val info = fetchTranscriptSmartForUrl(urlOrTopic)
            podcastSourceUrl = if (looksLikeUrl(urlOrTopic)) urlOrTopic.trim()
                else if (info.videoId.isNotBlank()) watchUrlFor(info.videoId) else ""
            _podcastVideoInfo.value = info
            resolveTranscriptAndHighlights(info, applyMetadata = false)
            _isDownloadingPodcast.value = false
            val src = if (info.isSample) "\u26a0\ufe0f CONTOH" else "ASLI"
            showMessage("Video ditranskrip ($src) \u2022 ${_podcastHighlights.value.size} segmen siap dipotong. " + clipActionHint())
        }
    }

    fun setClipRange(startSec: Int, endSec: Int) {
        _clipStartSec.value = startSec
        _clipEndSec.value = endSec
    }

    fun selectPodcastHighlight(index: Int) {
        _selectedHighlightIndex.value = index
        val highlight = _podcastHighlights.value.getOrNull(index)
        if (highlight != null) {
            _clipStartSec.value = highlight.startSec
            _clipEndSec.value = highlight.endSec
            _studioContentHook.value = highlight.hook
            _studioContentTitle.value = highlight.title
        }
    }

    fun executeAiCutClip(startSec: Int, endSec: Int, customTitle: String? = null) {
        _clipStartSec.value = startSec
        _clipEndSec.value = endSec
        if (!customTitle.isNullOrBlank()) {
            _studioContentTitle.value = customTitle
        }
        if (repository.clipServerService.isConfigured()) {
            val title = _studioContentTitle.value.ifBlank { "clip_" + startSec + "_" + endSec }
            serverCutSegments(listOf(Triple(startSec, endSec, title)))
            return
        }
        viewModelScope.launch {
            _isCuttingClip.value = true
            val path = _downloadedVideoPath.value
            if (path.isNullOrBlank()) {
                _isCuttingClip.value = false
                showMessage("Rentang klip $startSec-$endSec detik disetel. Unduh video lalu tekan 'Potong & Simpan'.")
                return@launch
            }
            val ctx = getApplication<Application>().applicationContext
            val name = "clip_" + startSec + "_" + endSec
            val result = withContext(Dispatchers.IO) {
                VideoClipper.cutSegment(ctx, path, startSec * 1000L, endSec * 1000L, name)
            }
            _isCuttingClip.value = false
            if (result != null) {
                val newClip = SavedPodcastClip(
                    _studioContentTitle.value, result.uri, result.displayName, startSec, endSec
                )
                _savedClips.value = _savedClips.value + newClip
                generateThumbnailsFor(listOf(newClip))
                showMessage("\u2705 Klip $startSec-$endSec detik dipotong & disimpan: ${result.displayName}")
            } else {
                showMessage("\u274c Gagal memotong klip $startSec-$endSec detik.")
            }
        }
    }

    // ============================================================
    // HELPER MODE SERVER (Clip Server) untuk fitur podcast
    // ============================================================
    private fun watchUrlFor(videoId: String): String = "https://www.youtube.com/watch?v=" + videoId

    private fun looksLikeUrl(s: String): Boolean {
        val t = s.trim()
        return t.startsWith("http://") || t.startsWith("https://") ||
            t.contains("youtu.be/") || t.contains("youtube.com/")
    }

    private fun clipActionHint(): String =
        if (repository.clipServerService.isConfigured())
            "Tekan 'Potong Semua' \u2014 server akan unduh HD & memotong otomatis (reframe ke wajah)."
        else "Unduh video lalu potong per segmen."

    /**
     * Tentukan transkrip final & rekomendasi segmen. Bila transkrip ASLI tersedia (server/caption),
     * pakai langsung tanpa 'menerjemahkan' ulang ke Gemini (yang bisa jadi teks contoh).
     * Jumlah segmen mengikuti rekomendasi AI, dinamis sesuai durasi & panjang transkrip.
     */
    private suspend fun resolveTranscriptAndHighlights(info: YouTubeVideoInfo, applyMetadata: Boolean) {
        val finalTranscript: String
        if (!info.isSample && info.transcriptText.trim().isNotBlank()) {
            finalTranscript = info.transcriptText
            _podcastTranscriptionResult.value = null
            _podcastFullTranscript.value = finalTranscript
        } else {
            val transcription = repository.geminiService.transcribeAudioWithGemini(info.transcriptText, info.title)
            _podcastTranscriptionResult.value = transcription
            finalTranscript = transcription.fullText
            _podcastFullTranscript.value = finalTranscript
        }

        val maxSegments = recommendedSegmentCount(info.durationFormatted, finalTranscript)
        val highlights = repository.geminiService.analyzePodcastTranscript(info.title, finalTranscript, maxSegments)
        _podcastHighlights.value = highlights
        _selectedHighlightIndex.value = 0
        _selectedSegmentIndices.value = highlights.indices.toSet()
        _clipContents.value = emptyMap()
        _clipThumbnails.value = emptyMap()
        if (highlights.isNotEmpty()) {
            _clipStartSec.value = highlights[0].startSec
            _clipEndSec.value = highlights[0].endSec
            if (applyMetadata) {
                _studioContentHook.value = highlights[0].hook
                _studioContentTitle.value = highlights[0].title
            }
        }
    }

    /** Perkiraan jumlah segmen ideal berdasarkan durasi video & panjang transkrip (3-10). */
    private fun recommendedSegmentCount(durationFormatted: String, transcript: String): Int {
        val totalSec = parseDurationToSeconds(durationFormatted)
        val byDuration = when {
            totalSec <= 0 -> 0
            totalSec < 300 -> 3
            totalSec < 900 -> 5
            totalSec < 1800 -> 7
            totalSec < 3600 -> 9
            else -> 10
        }
        val byText = when {
            transcript.length < 1500 -> 3
            transcript.length < 4000 -> 5
            transcript.length < 8000 -> 7
            else -> 10
        }
        return maxOf(3, maxOf(byDuration, byText)).coerceAtMost(10)
    }

    /** Parse "HH:MM:SS" atau "MM:SS" menjadi detik. Return 0 bila tak dikenal. */
    private fun parseDurationToSeconds(formatted: String): Int {
        val t = formatted.trim()
        if (t.isBlank()) return 0
        val parts = t.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                1 -> parts[0].toIntOrNull() ?: 0
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /** Putar klip tersimpan (preview) memakai pemutar IN-APP (Media3), tanpa aplikasi eksternal. */
    fun playSavedClip(uriString: String) {
        _playingClipUrl.value = uriString
    }

    private suspend fun fetchTranscriptSmart(
        videoUrl: String,
        videoId: String,
        fallbackTitle: String,
        fallbackChannel: String,
        durationFormatted: String
    ): YouTubeVideoInfo {
        if (repository.clipServerService.isConfigured() && videoUrl.isNotBlank()) {
            val st = try {
                repository.clipServerService.fetchTranscript(videoUrl)
            } catch (e: Exception) {
                null
            }
            if (st != null && st.hasTranscript && st.transcriptText.isNotBlank()) {
                return YouTubeVideoInfo(
                    videoId = videoId,
                    title = st.title.ifBlank { fallbackTitle },
                    channelName = st.channelName.ifBlank { fallbackChannel },
                    durationFormatted = durationFormatted,
                    transcriptText = st.transcriptText,
                    isSample = false
                )
            }
        }
        return repository.youTubeTranscriptService.fetchTranscriptForVideoId(
            videoId, fallbackTitle, fallbackChannel, durationFormatted
        )
    }

    private suspend fun fetchTranscriptSmartForUrl(urlOrTopic: String): YouTubeVideoInfo {
        if (repository.clipServerService.isConfigured() && looksLikeUrl(urlOrTopic)) {
            val st = try {
                repository.clipServerService.fetchTranscript(urlOrTopic.trim())
            } catch (e: Exception) {
                null
            }
            if (st != null && st.hasTranscript && st.transcriptText.isNotBlank()) {
                return YouTubeVideoInfo(
                    videoId = st.videoId,
                    title = st.title.ifBlank { "Podcast" },
                    channelName = st.channelName,
                    durationFormatted = "",
                    transcriptText = st.transcriptText,
                    isSample = false
                )
            }
        }
        return repository.youTubeTranscriptService.fetchVideoInfoAndTranscript(urlOrTopic)
    }

    private fun serverCutSegments(segments: List<Triple<Int, Int, String>>) {
        val url = podcastSourceUrl.ifBlank {
            _selectedCandidate.value?.let { c -> c.url.ifBlank { watchUrlFor(c.videoId) } }
                ?: _podcastVideoInfo.value?.let { info -> if (info.videoId.isNotBlank()) watchUrlFor(info.videoId) else "" }
                ?: ""
        }
        if (url.isBlank()) {
            showMessage("Sumber video belum ada. Pilih video atau tempel link dulu.")
            return
        }
        if (segments.isEmpty()) {
            showMessage("Belum ada segmen untuk dipotong.")
            return
        }
        viewModelScope.launch {
            _isCuttingClip.value = true
            val subInfo = if (_subtitleEnabled.value) " + subtitle (${_subtitleStyle.value})" else ""
            showMessage("\u2702\ufe0f Server mengunduh video HD & memotong ${segments.size} segmen (reframe ke wajah)$subInfo... Ini bisa beberapa menit, mohon tunggu.")
            val ctx = getApplication<Application>().applicationContext
            val clips = try {
                repository.clipServerService.requestClips(
                    videoUrl = url,
                    segments = segments,
                    aspectRatio = _clipAspectRatio.value,
                    reframe = true,
                    subtitle = _subtitleEnabled.value,
                    subtitleStyle = _subtitleStyle.value
                )
            } catch (e: Exception) {
                emptyList()
            }
            if (clips.isEmpty()) {
                _isCuttingClip.value = false
                showMessage("\u274c Server gagal memotong. Cek Clip Server URL di Settings & log server (video mungkin butuh cookies).")
                return@launch
            }
            var ok = 0
            val saved = mutableListOf<SavedPodcastClip>()
            for (c in clips) {
                val f = repository.clipServerService.downloadClipToGallery(ctx, c.downloadUrl, c.title)
                if (f != null) {
                    ok++
                    saved.add(SavedPodcastClip(c.title, f.uri, f.displayName, c.startSec, c.endSec))
                }
            }
            _savedClips.value = _savedClips.value + saved
            generate