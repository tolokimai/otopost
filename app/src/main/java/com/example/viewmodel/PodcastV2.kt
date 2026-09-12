package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.data.remote.ClipContent
import com.example.data.repository.AutoPostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Kontroller fitur v2 Podcast Clip Studio agar AutoPostViewModel tetap ramping:
 * - Multi-select segmen (centang beberapa/semua rekomendasi AI)
 * - Pemutar video IN-APP (dipakai bareng Media3/ExoPlayer di UI), tanpa aplikasi eksternal
 * - Konten per-klip (AI): metadata + copywriting tiap potongan video
 * - Thumbnail (frame) tiap klip tersimpan untuk daftar horizontal
 */
class PodcastV2(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val repository: AutoPostRepository,
    private val onMessage: (String) -> Unit
) {
    // --- Multi-select segmen ---
    private val _selectedSegmentIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedSegmentIndices = _selectedSegmentIndices.asStateFlow()

    fun toggleSegmentSelection(index: Int) {
        val cur = _selectedSegmentIndices.value.toMutableSet()
        if (!cur.add(index)) cur.remove(index)
        _selectedSegmentIndices.value = cur
    }

    fun selectAll(count: Int) {
        _selectedSegmentIndices.value = (0 until count).toSet()
    }

    fun clearSelection() {
        _selectedSegmentIndices.value = emptySet()
    }

    // --- Pemutar video IN-APP ---
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

    // --- Konten per-klip (AI) ---
    private val _clipContents = MutableStateFlow<Map<String, ClipContent>>(emptyMap())
    val clipContents = _clipContents.asStateFlow()

    private val _generatingClipContentFor = MutableStateFlow<String?>(null)
    val generatingClipContentFor = _generatingClipContentFor.asStateFlow()

    // --- Thumbnail per klip: uri -> base64 JPEG ---
    private val _clipThumbnails = MutableStateFlow<Map<String, String>>(emptyMap())
    val clipThumbnails = _clipThumbnails.asStateFlow()

    /** Reset semua state v2 saat video/transkrip baru dimuat, default centang semua segmen. */
    fun resetForNewVideo(segmentCount: Int) {
        _selectedSegmentIndices.value = (0 until segmentCount).toSet()
        _clipContents.value = emptyMap()
        _clipThumbnails.value = emptyMap()
        _playingClipUrl.value = null
        _isPlayerFullscreen.value = false
    }

    fun generateClipContent(clipUri: String, clipTitle: String, snippet: String, topic: String) {
        scope.launch {
            _generatingClipContentFor.value = clipUri
            val content = try {
                repository.clipContentService.generateClipContent(clipTitle, snippet, topic)
            } catch (e: Exception) {
                null
            }
            if (content != null) {
                _clipContents.value = _clipContents.value.toMutableMap().apply { put(clipUri, content) }
                onMessage("\u2728 Konten AI untuk '${clipTitle.take(24)}' siap. Tekan Copy untuk menyalin.")
            } else {
                onMessage("\u274c Gagal generate konten AI untuk klip ini. Cek API key Gemini di Settings.")
            }
            _generatingClipContentFor.value = null
        }
    }

    /** Ekstrak frame thumbnail (detik ke-1) tiap klip tersimpan menjadi base64 JPEG kecil. */
    fun generateThumbnails(uris: List<String>) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            for (uri in uris) {
                if (_clipThumbnails.value.containsKey(uri)) continue
                val b64 = try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(appContext, Uri.parse(uri))
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
                    _clipThumbnails.value = _clipThumbnails.value.toMutableMap().apply { put(uri, b64) }
                }
            }
        }
    }
}
