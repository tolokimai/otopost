package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AutoPostApplication
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PostStatus
import com.example.data.local.entity.PostingLogEntity
import com.example.data.local.entity.ScheduledPostEntity
import com.example.data.local.entity.SocialPlatform
import com.example.data.preferences.SettingsManager
import com.example.data.remote.SocialMediaPublisher

class PostPublishWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_POST_ID = "key_post_id"
        const val NOTIFICATION_CHANNEL_ID = "autopost_channel_id"
        const val NOTIFICATION_CHANNEL_NAME = "AutoPost Status"
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(context)
        val postDao = database.scheduledPostDao()
        val logDao = database.postingLogDao()
        val settingsManager = SettingsManager(context)
        val publisher = SocialMediaPublisher()

        val specificPostId = inputData.getLong(KEY_POST_ID, -1L)

        val postsToPublish = if (specificPostId != -1L) {
            val post = postDao.getPostById(specificPostId)
            if (post != null && (post.status == PostStatus.SCHEDULED || post.status == PostStatus.FAILED)) {
                listOf(post)
            } else {
                emptyList()
            }
        } else {
            postDao.getPendingPostsToPublish(System.currentTimeMillis() + 60_000)
        }

        if (postsToPublish.isEmpty()) {
            return Result.success()
        }

        val settings = settingsManager.settings.value
        var hasAnyFailure = false

        for (post in postsToPublish) {
            postDao.updatePostStatus(post.id, PostStatus.POSTING)
            val platforms = if (post.targetPlatforms.isNotEmpty()) {
                post.targetPlatforms
            } else {
                listOf(SocialPlatform.TIKTOK, SocialPlatform.INSTAGRAM, SocialPlatform.YOUTUBE_SHORTS)
            }

            var postFailed = false
            var failureReason = ""

            for (platform in platforms) {
                try {
                    val result = publisher.publishToPlatform(post, platform, settings)
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
                        postFailed = true
                        failureReason = result.message
                    }
                } catch (e: Exception) {
                    postFailed = true
                    failureReason = e.localizedMessage ?: "Unknown network exception"
                    logDao.insertLog(
                        PostingLogEntity(
                            postId = post.id,
                            postTitle = post.title,
                            platform = platform,
                            isSuccess = false,
                            message = "Exception: $failureReason"
                        )
                    )
                }
            }

            if (postFailed) {
                hasAnyFailure = true
                postDao.markPostFailed(post.id, failureReason)
                showNotification(
                    title = "⚠️ Gagal Posting: ${post.title.take(30)}",
                    message = "Alasan: $failureReason. Tap untuk retry."
                )
            } else {
                postDao.markPostSuccess(post.id)
                showNotification(
                    title = "✅ Konten Berhasil Tayang!",
                    message = "${post.title.take(35)} telah diposting ke ${platforms.joinToString { it.name }}."
                )
            }
        }

        return if (hasAnyFailure && runAttemptCount < 3 && settings.autoRetryOnError) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status update posting media sosial AutoPost Studio"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
