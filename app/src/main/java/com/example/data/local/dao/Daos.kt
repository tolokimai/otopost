package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY isDefault DESC, createdAt DESC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: Long): PersonaEntity?

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    fun getDefaultPersona(): Flow<PersonaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersona(persona: PersonaEntity): Long

    @Update
    suspend fun updatePersona(persona: PersonaEntity)

    @Query("UPDATE personas SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Transaction
    suspend fun setDefaultPersona(id: Long) {
        clearDefaultFlags()
        setSingleDefault(id)
    }

    @Query("UPDATE personas SET isDefault = 1 WHERE id = :id")
    suspend fun setSingleDefault(id: Long)

    @Delete
    suspend fun deletePersona(persona: PersonaEntity)
}

@Dao
interface ContentPlanDao {
    @Query("SELECT * FROM content_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<ContentPlanEntity>>

    @Query("SELECT * FROM content_plans WHERE id = :planId LIMIT 1")
    suspend fun getPlanById(planId: Long): ContentPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: ContentPlanEntity): Long

    @Delete
    suspend fun deletePlan(plan: ContentPlanEntity)

    @Query("SELECT * FROM content_plan_items WHERE planId = :planId ORDER BY dayNumber ASC")
    fun getItemsForPlan(planId: Long): Flow<List<ContentPlanItemEntity>>

    @Query("SELECT * FROM content_plan_items ORDER BY scheduledDateMillis ASC")
    fun getAllPlanItems(): Flow<List<ContentPlanItemEntity>>

    @Query("SELECT * FROM content_plan_items WHERE id = :itemId LIMIT 1")
    suspend fun getPlanItemById(itemId: Long): ContentPlanItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItems(items: List<ContentPlanItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItem(item: ContentPlanItemEntity): Long

    @Update
    suspend fun updatePlanItem(item: ContentPlanItemEntity)

    @Query("UPDATE content_plan_items SET isSentToStudio = 1 WHERE id = :itemId")
    suspend fun markItemSentToStudio(itemId: Long)

    @Delete
    suspend fun deletePlanItem(item: ContentPlanItemEntity)

    @Query("DELETE FROM content_plan_items WHERE planId = :planId")
    suspend fun deleteItemsByPlanId(planId: Long)
}

@Dao
interface ScheduledPostDao {
    @Query("SELECT * FROM scheduled_posts ORDER BY scheduledTimeMillis ASC")
    fun getAllScheduledPosts(): Flow<List<ScheduledPostEntity>>

    @Query("SELECT * FROM scheduled_posts WHERE status = :status ORDER BY scheduledTimeMillis ASC")
    fun getPostsByStatus(status: PostStatus): Flow<List<ScheduledPostEntity>>

    @Query("SELECT * FROM scheduled_posts WHERE id = :id LIMIT 1")
    suspend fun getPostById(id: Long): ScheduledPostEntity?

    @Query("SELECT * FROM scheduled_posts WHERE status = 'SCHEDULED' AND scheduledTimeMillis <= :currentTimeMillis")
    suspend fun getPendingPostsToPublish(currentTimeMillis: Long): List<ScheduledPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: ScheduledPostEntity): Long

    @Update
    suspend fun updatePost(post: ScheduledPostEntity)

    @Delete
    suspend fun deletePost(post: ScheduledPostEntity)

    @Query("UPDATE scheduled_posts SET status = :status, lastErrorMessage = :error WHERE id = :id")
    suspend fun updatePostStatus(id: Long, status: PostStatus, error: String? = null)

    @Query("UPDATE scheduled_posts SET status = 'POSTED', postedTimeMillis = :postedTime, lastErrorMessage = null WHERE id = :id")
    suspend fun markPostSuccess(id: Long, postedTime: Long = System.currentTimeMillis())

    @Query("UPDATE scheduled_posts SET status = 'FAILED', lastErrorMessage = :error, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markPostFailed(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM scheduled_posts WHERE status = 'SCHEDULED'")
    fun getScheduledCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scheduled_posts WHERE status = 'DRAFT'")
    fun getDraftCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scheduled_posts WHERE status = 'POSTED'")
    fun getPostedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scheduled_posts WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>
}

@Dao
interface PostingLogDao {
    @Query("SELECT * FROM posting_logs ORDER BY timestampMillis DESC LIMIT 100")
    fun getAllLogs(): Flow<List<PostingLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PostingLogEntity): Long

    @Query("DELETE FROM posting_logs")
    suspend fun clearLogs()
}
