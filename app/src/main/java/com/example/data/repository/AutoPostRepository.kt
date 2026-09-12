package com.example.data.repository

import android.content.Context
import androidx.work.*
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsManager
import com.example.data.remote.AiImageService
import com.example.data.remote.ClipServerService
import com.example.data.remote.GeminiService
import com.example.data.remote.ImageSearchService
import com.example.data.remote.SocialMediaPublisher
import com.example.data.remote.YouTubeDownloadService
import com.example.data.remote.YouTubeSearchService
import com.example.data.remote.YouTubeTranscriptService
import com.example.worker.PostPublishWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Repository pusat yang menyatukan sumber data lokal (Room), preferensi, dan
 * layanan remote (Gemini teks, generate gambar AI, pencarian gambar internet,
 * publisher sosial media, pencarian/unduh/transkrip YouTube, clip server).
 */
class AutoPostRepository(
    private val context: Context,
    val database: AppDatabase,
    val settingsManager: SettingsManager
) {
    private val personaDao = database.personaDao()
    private val planDao = database.contentPlanDao()
    private val postDao = database.scheduledPostDao()
    private val logDao = database.postingLogDao()

    // Layanan teks Gemini (persona, plan, caption, transkrip, dll).
    val geminiService = GeminiService { settingsManager.getEffectiveGeminiKey() }

    // Layanan khusus generate GAMBAR via model image Gemini/Imagen (model bisa dipilih user).
    val aiImageService = AiImageService(
        getApiKey = { settingsManager.getEffectiveGeminiKey() },
        getPreferredModel = { settingsManager.getEffectiveGeminiImageModel() }
    )

    // Pencarian gambar dari internet (Wikimedia Commons + Openverse bila ada kredensial).
    val imageSearchService = ImageSearchService(
        getOpenverseCreds = {
            val s = settingsManager.settings.value
            if (s.openverseClientId.isNotBlank() && s.openverseClientSecret.isNotBlank())
                Pair(s.openverseClientId, s.openverseClientSecret) else null
        }
    )

    // Publisher ke platform sosial media.
    val socialPublisher = SocialMediaPublisher()

    // Layanan transkrip YouTube.
    val youTubeTranscriptService = YouTubeTranscriptService()

    // Pencarian video YouTube relevan berdasarkan tema (butuh YouTube Data API key).
    val youTubeSearchService = YouTubeSearchService(context) { settingsManager.settings.value.youTubeApiKey }

    // Pengunduh video YouTube on-device (best-effort).
    val youTubeDownloadService = YouTubeDownloadService()

    // Klien OtoPost Clip Server (transkrip asli + download HD + potong + reframe wajah).
    val clipServerService = ClipServerService(
        getBaseUrl = { settingsManager.settings.value.clipServerUrl },
        getToken = { settingsManager.settings.value.clipServerToken }
    )

    // --- Personas ---
    val allPersonas: Flow<List<PersonaEntity>> = personaDao.getAllPersonas()
    val defaultPersona: Flow<PersonaEntity?> = personaDao.getDefaultPersona()

    suspend fun savePersona(persona: PersonaEntity): Long {
        val id = personaDao.insertPersona(persona)
        if (persona.isDefault) {
            personaDao.setDefaultPersona(if (persona.id == 0L) id else persona.id)
        }
        return id
    }

    suspend fun setDefaultPersona(id: Long) = personaDao.setDefaultPersona(id)
    suspend fun deletePersona(persona: PersonaEntity) = personaDao.deletePersona(persona)
    suspend fun getPersonaById(id: Long) = personaDao.getPersonaById(id)

    // --- Content Plans ---
    val allPlans: Flow<List<ContentPlanEntity>> = planDao.getAllPlans()
    val allPlanItems: Flow<List<ContentPlanItemEntity>> = planDao.getAllPlanItems()

    fun getItemsForPlan(planId: Long): Flow<List<ContentPlanItemEntity>> = planDao.getItemsForPlan(planId)

    suspend fun createPlanWithItems(
        plan: ContentPlanEntity,
        items: List<ContentPlanItemEntity>
    ): Long {
        val planId = planDao.insertPlan(plan)
        val itemsWithPlanId = items.map { it.copy(planId = planId) }
        planDao.insertPlanItems(itemsWithPlanId)
        return planId
    }

    suspend fun updatePlanItem(item: ContentPlanItemEntity) = planDao.updatePlanItem(item)
    suspend fun deletePlanItem(item: ContentPlanItemEntity) = planDao.deletePlanItem(item)
    suspend fun markItemSentToStudio(itemId: Long) = planDao.markItemSentToStudio(itemId)
    suspend fun getPlanItemById(itemId: Long) = planDao.getPlanItemById(itemId)
    suspend fun deletePlan(plan: ContentPlanEntity) {
        planDao.deleteItemsByPlanId(plan.id)
        planDao.deletePlan(plan)
    }

    // --- Scheduled Posts & Dashboard KPIs ---
    val allScheduledPosts: Flow<List<ScheduledPostEntity>> = postDao.getAllScheduledPosts()
    val scheduledCount: Flow<Int> = postDao.getScheduledCount()
    val draftCount: Flow<Int> = postDao.getDraftCount()
    val postedCount: Flow<Int> = postDao.getPostedCount()
    val failedCount: Flow<Int> = postDao.getFailedCount()

    val allPostingLogs: Flow<List<PostingLogEntity>> = logDao.getAllLogs()

    suspend fun savePost(post: ScheduledPostEntity): Long {
        val id = postDao.insertPost(post)
        if (post.status == PostStatus.SCHEDULED) {
            scheduleWorkManagerPosting(id, post.scheduledTimeMillis)
        }
        return id
    }

    suspend fun updatePost(post: ScheduledPostEntity) {
        postDao.updatePost(post)
        if (post.status == PostStatus.SCHEDULED) {
            scheduleWorkManagerPosting(post.id, post.scheduledTimeMillis)
        }
    }

    suspend fun deletePost(post: ScheduledPostEntity) {
        WorkManager.getInstance(context).cancelUniqueWork("post_publish_${post.id}")
        postDao.deletePost(post)
    }

    suspend fun getPostById(id: Long) = postDao.getPostById(id)

    suspend fun retryPostNow(postId: Long) {
        val post = postDao.getPostById(postId) ?: return
        postDao.updatePostStatus(postId, PostStatus.SCHEDULED, null)
        executeImmediatePublish(post)
    }

    suspend fun executeImmediatePublish(post: ScheduledPostEntity) {
        postDao.updatePostStatus(post.id, PostStatus.POSTING)
        val settings = settingsManager.settings.value
        var anyFailure = false
        var lastErrorMessage = ""

        val platformsToPost = if (post.targetPlatforms.isNotEmpty()) {
            post.targetPlatforms
        } else {
            listOf(SocialPlatform.TIKTOK, SocialPlatform.INSTAGRAM, SocialPlatform.YOUTUBE_SHORTS)
        }

        for (platform in platformsToPost) {
            val result = socialPublisher.publishToPlatform(post, platform, settings)
            logDao.insertLog(
                PostingLogEntity(
                    postId = post.id,
                    postTitle = post.title,
                    platform = platform,
                    isSuccess = result.isSuccess,
                    message = result.message,
                    responseData = result.postIdOnPlatform ?: ""
                )
            )
            if (!result.isSuccess) {
                anyFailure = true
                lastErrorMessage = result.message
            }
        }

        if (anyFailure) {
            postDao.markPostFailed(post.id, lastErrorMessage)
        } else {
            postDao.markPostSuccess(post.id)
        }
    }

    fun scheduleWorkManagerPosting(postId: Long, scheduledTimeMillis: Long) {
        try {
            val delayMillis = (scheduledTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
            val data = workDataOf(PostPublishWorker.KEY_POST_ID to postId)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val postWorkRequest = OneTimeWorkRequestBuilder<PostPublishWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "post_publish_$postId",
                ExistingWorkPolicy.REPLACE,
                postWorkRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Schedule periodic worker to check queued posts
    fun initPeriodicPostingWorker() {
        try {
            val periodicRequest = PeriodicWorkRequestBuilder<PostPublishWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "autopost_periodic_scheduler",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
