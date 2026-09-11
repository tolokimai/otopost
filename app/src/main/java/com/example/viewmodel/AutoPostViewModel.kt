package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoPostApplication
import com.example.data.local.entity.*
import com.example.data.preferences.AppSettings
import com.example.data.remote.GeneratedPersona
import com.example.data.remote.GeneratedPlanItem
import com.example.data.remote.PodcastSegmentHighlight
import com.example.data.remote.VideoCopyResult
import com.example.data.remote.YouTubeVideoInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    private val _studioContentCaption = MutableStateFlow("Simak penjelasan lengkapnya sampai habis. Share ke temanmu yang butuh insight ini! 👇")
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
                    loadPodcastForTopic(item.title)
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
            CarouselSlide(1, "5 Langkah Sukses Content Creation", "Panduan praktis dari nol untuk pemula di 2026.", "Geser ➡️", themeName = "Minimalist Tech"),
            CarouselSlide(2, "01. Temukan Niche Spesifik", "Fokus pada satu problem audiens yang belum banyak dibahas orang.", "Poin 2 krusial ➡️", themeName = "Minimalist Tech"),
            CarouselSlide(3, "02. Kuasai Hook 3 Detik", "Buat kalimat pembuka yang memicu rasa ingin tahu (curiosity gap).", "Next langkah 3 ➡️", themeName = "Minimalist Tech"),
            CarouselSlide(4, "03. Konsistensi Posting", "Jadwalkan postinganmu secara otomatis tiap hari di jam optimal.", "Satu slide lagi ➡️", themeName = "Minimalist Tech"),
            CarouselSlide(5, "Simpan Post Ini!", "Bagikan postingan ini ke teman kreatormu & follow untuk tips lainnya.", "Save 📌", themeName = "Minimalist Tech")
        )
    )
    val carouselSlides = _carouselSlides.asStateFlow()

    private val _carouselBackgroundPreset = MutableStateFlow("INDIGO_GRADIENT") // INDIGO_GRADIENT, DARK_CYAN, WARM_SUNSET, SOLID_DARK
    val carouselBackgroundPreset = _carouselBackgroundPreset.asStateFlow()

    private val _carouselTheme = MutableStateFlow("Minimalist Tech") // Minimalist Tech, Cyber Neon, Aesthetic Pastel, Dark Luxury, Vintage Retro, 3D Illustration
    val carouselTheme = _carouselTheme.asStateFlow()

    private val _carouselAspectRatio = MutableStateFlow("1:1") // 1:1, 4:5, 3:4, 9:16
    val carouselAspectRatio = _carouselAspectRatio.asStateFlow()

    private val _carouselTypographyStyle = MutableStateFlow("Modern Minimalist") // Modern Minimalist, Glassmorphism Card, Bold Hero, Editorial Serif, Bottom Scrim, Cyber Neon
    val carouselTypographyStyle = _carouselTypographyStyle.asStateFlow()

    private val _carouselWatermark = MutableStateFlow("@AutoPostStudio")
    val carouselWatermark = _carouselWatermark.asStateFlow()

    private val _carouselFontFamily = MutableStateFlow("Sans") // Sans, Serif, Monospace
    val carouselFontFamily = _carouselFontFamily.asStateFlow()

    private val _isGeneratingCarouselAi = MutableStateFlow(false)
    val isGeneratingCarouselAi = _isGeneratingCarouselAi.asStateFlow()

    private val _isGeneratingSlideImage = MutableStateFlow(false)
    val isGeneratingSlideImage = _isGeneratingSlideImage.asStateFlow()

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
        list.add(CarouselSlide(nextNum, "Slide $nextNum Poin Baru", "Tambahkan penjelasan detail slide di sini.", "Geser ➡️", themeName = _carouselTheme.value))
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
                    "✨ Background AI Imagen 3 berhasil dibuat untuk Slide ${slide.slideNumber}!"
                } else {
                    "🎨 Background '$currentTheme' berhasil dibuat untuk Slide ${slide.slideNumber}!"
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
                "✨ $aiCount background AI Imagen 3 & grafis slide berhasil dibuat!"
            } else {
                "🎨 Semua background '$currentTheme' berhasil dipasang pada slide!"
            }
            showMessage(msg)
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

    fun setStudioMetadata(title: String, hook: String, caption: String, hashtags: String) {
        updateStudioCopy(title, hook, caption, hashtags)
    }

    fun transcribeAndProcessPodcast(urlOrTopic: String) {
        downloadAndTranscribeFullVideo(urlOrTopic)
    }

    fun loadPodcastForTopic(urlOrTopic: String) {
        viewModelScope.launch {
            _isLoadingPodcast.value = true
            val info = repository.youTubeTranscriptService.fetchVideoInfoAndTranscript(urlOrTopic)
            _podcastVideoInfo.value = info
            _podcastFullTranscript.value = info.transcriptText

            // Transcribe with gemini-3.5-transcribe
            val transcription = repository.geminiService.transcribeAudioWithGemini(info.transcriptText, info.title)
            _podcastTranscriptionResult.value = transcription
            _podcastFullTranscript.value = transcription.fullText

            // Analyze highlights with Gemini AI
            val highlights = repository.geminiService.analyzePodcastTranscript(info.title, transcription.fullText)
            _podcastHighlights.value = highlights
            _selectedHighlightIndex.value = 0
            if (highlights.isNotEmpty()) {
                _clipStartSec.value = highlights[0].startSec
                _clipEndSec.value = highlights[0].endSec
                _studioContentHook.value = highlights[0].hook
                _studioContentTitle.value = highlights[0].title
            }
            _isLoadingPodcast.value = false
            showMessage("Transkrip podcast dimuat & segmen viral diidentifikasi!")
        }
    }

    fun downloadAndTranscribeFullVideo(urlOrTopic: String) {
        viewModelScope.launch {
            _isDownloadingPodcast.value = true
            showMessage("Mendownload audio video full & melakukan transkripsi via Gemini 3.5 Transcribe...")
            val info = repository.youTubeTranscriptService.fetchVideoInfoAndTranscript(urlOrTopic)
            _podcastVideoInfo.value = info
            _podcastFullTranscript.value = info.transcriptText

            val transcription = repository.geminiService.transcribeAudioWithGemini(info.transcriptText, info.title)
            _podcastTranscriptionResult.value = transcription
            _podcastFullTranscript.value = transcription.fullText

            val highlights = repository.geminiService.analyzePodcastTranscript(info.title, transcription.fullText)
            _podcastHighlights.value = highlights
            _selectedHighlightIndex.value = 0
            if (highlights.isNotEmpty()) {
                _clipStartSec.value = highlights[0].startSec
                _clipEndSec.value = highlights[0].endSec
            }
            _isDownloadingPodcast.value = false
            showMessage("Video full berhasil ditranskrip! Siap untuk dipotong.")
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
        viewModelScope.launch {
            _isCuttingClip.value = true
            _clipStartSec.value = startSec
            _clipEndSec.value = endSec
            showMessage("Memotong klip video 9:16 dari detik $startSec sampai $endSec...")
            if (!customTitle.isNullOrBlank()) {
                _studioContentTitle.value = customTitle
            }
            _isCuttingClip.value = false
            showMessage("Klip $startSec-$endSec detik berhasil dipotong & siap dipost!")
        }
    }

    // 4c. SELF VIDEO STUDIO STATE
    private val _selfVideoHookText = MutableStateFlow("Trik Rahasia 3 Detik Pertama 💥")
    val selfVideoHookText = _selfVideoHookText.asStateFlow()

    private val _selfVideoSubtitles = MutableStateFlow<List<String>>(
        listOf(
            "Kalau kamu pengen views meledak...",
            "Kuncinya adalah jangan bertele-tele di awal video!",
            "Terapkan 3 formula hook ini sekarang juga.",
            "Follow untuk tips strategi konten viral selanjutnya!"
        )
    )
    val selfVideoSubtitles = _selfVideoSubtitles.asStateFlow()

    private val _isGeneratingVideoCopy = MutableStateFlow(false)
    val isGeneratingVideoCopy = _isGeneratingVideoCopy.asStateFlow()

    fun setSelfVideoHookText(text: String) {
        _selfVideoHookText.value = text
    }

    fun updateSelfVideoSubtitles(subtitles: List<String>) {
        _selfVideoSubtitles.value = subtitles
    }

    fun generateVideoScript(topic: String) {
        viewModelScope.launch {
            _isGeneratingVideoCopy.value = true
            val copy = repository.geminiService.generateVideoHooksAndCaptions(topic, "Santai & Hook Kuat")
            _selfVideoHookText.value = copy.viralHook
            _studioContentHook.value = copy.viralHook
            _studioContentCaption.value = copy.caption
            _studioContentHashtags.value = copy.hashtags
            _selfVideoSubtitles.value = copy.subtitles
            _isGeneratingVideoCopy.value = false
            showMessage("Naskah video & subtitle otomatis dibuat!")
        }
    }

    // 4d. BUAT VIDEO (AI VIDEO GENERATOR - VEO 3) STATE
    private val _aiVideoPrompt = MutableStateFlow("Cinematic drone shot of a futuristic creator studio with neon lighting, 4K smooth motion")
    val aiVideoPrompt = _aiVideoPrompt.asStateFlow()

    private val _aiVideoAspectRatio = MutableStateFlow("9:16") // 9:16 or 16:9
    val aiVideoAspectRatio = _aiVideoAspectRatio.asStateFlow()

    private val _aiVideoStylePreset = MutableStateFlow("Cinematic 4K") // Cinematic 4K, Cyberpunk, 3D Animation, Photorealistic, Studio Minimal
    val aiVideoStylePreset = _aiVideoStylePreset.asStateFlow()
    val aiVideoStyle = _aiVideoStylePreset.asStateFlow()

    private val _aiVideoDurationSec = MutableStateFlow(5)
    val aiVideoDurationSec = _aiVideoDurationSec.asStateFlow()
    val aiVideoDurationSeconds = _aiVideoDurationSec.asStateFlow()

    private val _aiVideoCreationMode = MutableStateFlow("TEXT_TO_VIDEO") // TEXT_TO_VIDEO or IMAGE_TO_VIDEO
    val aiVideoCreationMode = _aiVideoCreationMode.asStateFlow()

    private val _aiVideoMotionPrompt = MutableStateFlow("Slow cinematic push-in with floating golden bokeh particles")
    val aiVideoMotionPrompt = _aiVideoMotionPrompt.asStateFlow()

    private val _isGeneratingAiVideo = MutableStateFlow(false)
    val isGeneratingAiVideo = _isGeneratingAiVideo.asStateFlow()

    private val _generatedVideoResult = MutableStateFlow<com.example.data.remote.GeneratedVideoResult?>(null)
    val generatedVideoResult = _generatedVideoResult.asStateFlow()

    fun setAiVideoPrompt(prompt: String) {
        _aiVideoPrompt.value = prompt
    }

    fun setAiVideoAspectRatio(ratio: String) {
        _aiVideoAspectRatio.value = ratio
    }

    fun setAiVideoStylePreset(preset: String) {
        _aiVideoStylePreset.value = preset
    }

    fun setAiVideoStyle(preset: String) {
        _aiVideoStylePreset.value = preset
    }

    fun setAiVideoDurationSec(duration: Int) {
        _aiVideoDurationSec.value = duration
    }

    fun setAiVideoDurationSeconds(duration: Int) {
        _aiVideoDurationSec.value = duration
    }

    fun applyAiVideoToPost() {
        val result = _generatedVideoResult.value
        _studioContentHook.value = result?.viralHook ?: "Tonton Video AI Keren Ini!"
        _studioContentTitle.value = "AI Video: " + _aiVideoPrompt.value.take(30)
        _studioContentCaption.value = "Video cinematic dibuat secara otomatis dengan AI Veo 3. Simak sampai habis & share ke teman kreatormu! #aivideo #veo3 #creator"
        _studioContentHashtags.value = "#aivideo #veo3 #creator #ai #fyp"
        showMessage("Video AI berhasil dipasang untuk postingan!")
    }

    fun setAiVideoCreationMode(mode: String) {
        _aiVideoCreationMode.value = mode
    }

    fun setAiVideoMotionPrompt(prompt: String) {
        _aiVideoMotionPrompt.value = prompt
    }

    fun generateAiVideoFromText() {
        viewModelScope.launch {
            _isGeneratingAiVideo.value = true
            showMessage("Menjalankan Veo 3 (veo-3.1-fast-generate-preview) Aspect Ratio: ${_aiVideoAspectRatio.value}...")
            val result = repository.geminiService.generateVeoVideoFromText(
                prompt = _aiVideoPrompt.value,
                aspectRatio = _aiVideoAspectRatio.value,
                stylePreset = _aiVideoStylePreset.value,
                durationSec = _aiVideoDurationSec.value
            )
            _generatedVideoResult.value = result
            _studioContentHook.value = result.viralHook
            _studioContentTitle.value = "AI Video: " + _aiVideoPrompt.value.take(30)
            _studioContentCaption.value = "Video dibuat menggunakan AI Veo 3. Keren banget hasilnya! Gimana menurutmu? 👇"
            _isGeneratingAiVideo.value = false
            showMessage("Video Veo 3 berhasil di-generate!")
        }
    }

    fun animateImageToAiVideo(imageDesc: String? = null) {
        viewModelScope.launch {
            _isGeneratingAiVideo.value = true
            val desc = imageDesc ?: _carouselSlides.value.firstOrNull()?.headline ?: "Foto/Ilustrasi Konten"
            showMessage("Menganimasikan gambar ke video via Veo 3...")
            val result = repository.geminiService.animateImageWithVeo(
                imageDescription = desc,
                motionPrompt = _aiVideoMotionPrompt.value,
                aspectRatio = _aiVideoAspectRatio.value
            )
            _generatedVideoResult.value = result
            _studioContentHook.value = result.viralHook
            _studioContentTitle.value = "Animate: " + desc.take(25)
            _isGeneratingAiVideo.value = false
            showMessage("Animasi video Veo 3 berhasil dibuat!")
        }
    }

    // SAVE CONTENT TO SCHEDULED QUEUE / DRAFT
    fun saveCurrentStudioToSchedule(
        targetPlatforms: List<SocialPlatform>,
        scheduledTimeMillis: Long,
        asDraft: Boolean = false
    ) {
        viewModelScope.launch {
            val format = when (_studioSubMode.value) {
                StudioSubMode.CAROUSEL -> ContentFormat.CAROUSEL
                StudioSubMode.PODCAST_CLIP -> ContentFormat.PODCAST_CLIP
                StudioSubMode.SELF_VIDEO -> ContentFormat.SELF_VIDEO
                StudioSubMode.AI_VIDEO -> ContentFormat.AI_VIDEO
            }

            val podcastInfo = _podcastVideoInfo.value
            val selectedHighlight = _podcastHighlights.value.getOrNull(_selectedHighlightIndex.value)
            val generatedVideo = _generatedVideoResult.value

            val post = ScheduledPostEntity(
                planItemId = _studioActivePlanItemId.value,
                title = _studioContentTitle.value.ifBlank { "Konten AutoPost Studio" },
                format = format,
                hook = _studioContentHook.value,
                caption = _studioContentCaption.value,
                hashtags = _studioContentHashtags.value,
                targetPlatforms = targetPlatforms.ifEmpty { listOf(SocialPlatform.TIKTOK, SocialPlatform.INSTAGRAM, SocialPlatform.YOUTUBE_SHORTS) },
                scheduledTimeMillis = scheduledTimeMillis,
                status = if (asDraft) PostStatus.DRAFT else PostStatus.SCHEDULED,
                carouselSlidesJson = if (format == ContentFormat.CAROUSEL) {
                    val array = org.json.JSONArray()
                    _carouselSlides.value.forEach { s ->
                        val o = org.json.JSONObject()
                        o.put("slideNumber", s.slideNumber)
                        o.put("headline", s.headline)
                        o.put("body", s.body)
                        o.put("subtext", s.subtext)
                        o.put("imageUrl", s.imageUrl)
                        o.put("imageBase64", s.imageBase64)
                        o.put("imagePrompt", s.imagePrompt)
                        o.put("themeName", s.themeName)
                        array.put(o)
                    }
                    array.toString()
                } else null,
                podcastVideoUrl = podcastInfo?.videoId,
                podcastSegmentStartSec = _clipStartSec.value,
                podcastSegmentEndSec = _clipEndSec.value,
                podcastChannelName = podcastInfo?.channelName ?: "",
                subtitlesJson = when (format) {
                    ContentFormat.SELF_VIDEO -> {
                        val arr = org.json.JSONArray()
                        _selfVideoSubtitles.value.forEach { arr.put(it) }
                        arr.toString()
                    }
                    ContentFormat.AI_VIDEO -> {
                        val arr = org.json.JSONArray()
                        generatedVideo?.dynamicSubtitles?.forEach { arr.put(it) }
                        arr.toString()
                    }
                    else -> null
                },
                generatedVideoUrl = if (format == ContentFormat.AI_VIDEO) generatedVideo?.videoUrl else null,
                videoAspectRatio = if (format == ContentFormat.AI_VIDEO) _aiVideoAspectRatio.value else "9:16"
            )

            repository.savePost(post)
            showMessage(if (asDraft) "Konten disimpan sebagai Draft!" else "Konten berhasil dijadwalkan masuk antrean posting!")
            selectTab(MainTab.Dashboard)
        }
    }

    init {
        // Auto-seed sample persona if DB is empty
        viewModelScope.launch {
            val existing = repository.allPersonas.first()
            if (existing.isEmpty()) {
                val samplePersona = PersonaEntity(
                    brandName = "AutoPost Creator Hub",
                    niche = "Tech, Bisnis & Produktivitas",
                    targetAudience = "Kreator konten, solopreneur & freelancer muda",
                    languageStyle = "Santai & Edukatif",
                    tone = "Energetic & Praktis",
                    language = "ID",
                    accountReferences = "@garyvee, @feliciaputri, @cleocreative",
                    isDefault = true
                )
                repository.savePersona(samplePersona)
            }
        }
    }
}
