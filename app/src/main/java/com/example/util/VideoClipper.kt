package com.example.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/** Hasil satu potongan video yang tersimpan. */
data class ClipResult(
    val uri: String,
    val displayName: String,
    val filePath: String?
)

/**
 * Memotong segmen video secara lossless (tanpa re-encode) menggunakan
 * MediaExtractor + MediaMuxer. Mempertahankan resolusi & orientasi asli (HD).
 *
 * Catatan: versi ini melakukan POTONG lurus (straight cut). Reframe portrait /
 * auto-fokus ke pembicara memerlukan re-encode (MediaCodec) + deteksi wajah dan
 * dijadwalkan sebagai tahap lanjutan (kandidat jalur server/worker).
 */
object VideoClipper {
    private const val TAG = "VideoClipper"

    fun cutSegment(
        context: Context,
        srcPath: String,
        startMs: Long,
        endMs: Long,
        displayName: String
    ): ClipResult? {
        if (startMs >= endMs) return null
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var tempFile: File? = null
        try {
            extractor.setDataSource(srcPath)
            val trackCount = extractor.trackCount
            val indexMap = HashMap<Int, Int>()

            tempFile = File(context.cacheDir, "clip_" + System.currentTimeMillis() + ".mp4")
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var maxInputSize = 1024 * 1024
            var rotation = 0
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                    extractor.selectTrack(i)
                    if (mime.startsWith("video/")) {
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxInputSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        }
                        if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                            rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                        }
                    }
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                }
            }
            if (indexMap.isEmpty()) {
                Log.w(TAG, "no audio/video tracks")
                return null
            }
            if (rotation != 0) muxer.setOrientationHint(rotation)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxInputSize.coerceAtLeast(512 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()
            val startUs = startMs * 1000
            val endUs = endMs * 1000

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var offsetUs = -1L

            while (true) {
                bufferInfo.offset = 0
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break
                if (offsetUs < 0L) offsetUs = sampleTime
                val trackIndex = extractor.sampleTrackIndex
                val dstIndex = indexMap[trackIndex]
                if (dstIndex != null) {
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = (sampleTime - offsetUs).coerceAtLeast(0)
                    bufferInfo.flags = sampleFlagsToMuxerFlags(extractor.sampleFlags)
                    muxer.writeSampleData(dstIndex, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()

            return persist(context, tempFile, displayName)
        } catch (e: Exception) {
            Log.w(TAG, "cut failed", e)
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            return null
        } finally {
            tempFile?.let { if (it.exists()) it.delete() }
        }
    }

    private fun persist(context: Context, temp: File, displayName: String): ClipResult? {
        val base = displayName.replace(Regex("[^A-Za-z0-9-_]"), "_").take(60).ifBlank { "clip" }
        val safeName = base + "_" + System.currentTimeMillis() + ".mp4"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AutoPostStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                ClipResult(uri.toString(), safeName, null)
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AutoPostStudio").apply { mkdirs() }
                val out = File(dir, safeName)
                temp.inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
                ClipResult(Uri.fromFile(out).toString(), safeName, out.absolutePath)
            }
        } catch (e: Exception) {
            Log.w(TAG, "persist failed", e)
            null
        }
    }

    private fun sampleFlagsToMuxerFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return flags
    }
}
