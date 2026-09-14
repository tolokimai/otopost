package com.example.ui.screens.remake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.preferences.RemakeMediaItem
import com.example.data.preferences.SettingsManager
import com.example.data.remote.ClipServerService
import com.example.data.remote.ElevenLabsService
import com.example.data.remote.RemakeVoiceScriptService
import kotlinx.coroutines.launch
import java.io.File

/**
 * Layar "Remake Suara / Lipsync".
 *
 * Alur:
 *  1) (opsional) generate naskah narasi dari AI berdasarkan judul/hook/caption (data Plan).
 *  2) buat suara: TTS ElevenLabs, rekam sendiri, atau upload file audio.
 *  3) pilih video/foto (galeri device) atau pakai media tersimpan (bisa set default).
 *  4) kirim ke server -> tempel suara baru (TRUE lipsync bila server ENABLE_WAV2LIP,
 *     jika tidak, overlay suara). Durasi mengikuti SUARA. Hasil disimpan ke galeri.
 *
 * Self-contained: membuat service sendiri via SettingsManager, tidak bergantung pada ViewModel.
 */
@Composable
fun RemakeVoiceScreen(
    incomingTitle: String = "",
    incomingHook: String = "",
    incomingCaption: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context.applicationContext) }
    val settings by settingsManager.settings.collectAsState()

    val elevenLabs = remember { ElevenLabsService { settingsManager.getEffectiveElevenLabsKey() } }
    val scriptService = remember { RemakeVoiceScriptService { settingsManager.getEffectiveGeminiKey() } }
    val clipServer = remember {
        ClipServerService(
            { settingsManager.settings.value.clipServerUrl },
            { settingsManager.settings.value.clipServerToken }
        )
    }

    // --- Konfigurasi ---
    var elevenKey by remember { mutableStateOf(settings.elevenLabsApiKey) }
    var voiceId by remember { mutableStateOf(settings.elevenLabsVoiceId) }
    var serverUrl by remember { mutableStateOf(settings.clipServerUrl) }
    var serverToken by remember { mutableStateOf(settings.clipServerToken) }
    var showConfig by remember { mutableStateOf(settings.elevenLabsApiKey.isBlank() || settings.clipServerUrl.isBlank()) }
    var voices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var voiceMenuOpen by remember { mutableStateOf(false) }

    // --- Naskah ---
    var title by remember { mutableStateOf("") }
    var hook by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var script by remember { mutableStateOf("") }
    var targetSeconds by remember { mutableStateOf(30) }

    // --- Suara ---
    var audioMode by remember { mutableStateOf("tts") } // tts | record | upload
    var audioFile by remember { mutableStateOf<File?>(null) }
    var audioLabel by remember { mutableStateOf("") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    // --- Media (video/foto) ---
    var mediaFile by remember { mutableStateOf<File?>(null) }
    var mediaKind by remember { mutableStateOf("video") } // video | photo
    var mediaLabel by remember { mutableStateOf("") }
    var gallery by remember { mutableStateOf(settingsManager.loadRemakeMedia()) }

    // --- Opsi output ---
    var aspect by remember { mutableStateOf(settings.defaultClipAspectRatio.ifBlank { "9:16" }) }
    var lipsyncMode by remember { mutableStateOf(true) }
    var subtitle by remember { mutableStateOf(false) }

    // --- Status ---
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var resultDuration by remember { mutableStateOf(0) }

    // Sinkronisasi data dari halaman Plan (dikirim via "Kirim ke Studio").
    LaunchedEffect(incomingTitle, incomingHook, incomingCaption) {
        if (incomingTitle.isNotBlank() || incomingHook.isNotBlank() || incomingCaption.isNotBlank()) {
            title = incomingTitle
            hook = incomingHook
            caption = incomingCaption
            if (script.isBlank()) {
                script = listOf(incomingHook, incomingTitle, incomingCaption)
                    .filter { it.isNotBlank() }.joinToString(" ").trim()
            }
        }
    }

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun doStartRecording() {
        try {
            val dir = File(context.cacheDir, "remake_rec")
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, "rec_" + System.currentTimeMillis() + ".m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128000)
            rec.setAudioSamplingRate(44100)
            rec.setOutputFile(out.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            isRecording = true
            audioFile = out
            audioLabel = out.name
            status = "Merekam... tekan Stop bila selesai."
        } catch (e: Exception) {
            status = "Gagal mulai rekam: " + (e.message ?: "")
        }
    }

    fun doStopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            // abaikan
        } finally {
            recorder = null
            isRecording = false
            val f = audioFile
            if (f != null) {
                audioLabel = f.name + " (rekaman)"
                status = "Rekaman selesai."
            }
        }
    }

    val recordPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doStartRecording() else status = "Izin rekam suara ditolak."
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val f = copyUriToFile(context, uri, "remake_media", "video_" + System.currentTimeMillis() + ".mp4")
            if (f != null) {
                mediaFile = f; mediaKind = "video"; mediaLabel = f.name
                status = "Video dipilih."
            } else status = "Gagal membaca video."
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val f = copyUriToFile(context, uri, "remake_media", "photo_" + System.currentTimeMillis() + ".jpg")
            if (f != null) {
                mediaFile = f; mediaKind = "photo"; mediaLabel = f.name
                status = "Foto dipilih."
            } else status = "Gagal membaca foto."
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val f = copyUriToFile(context, uri, "remake_audio_up", "audio_" + System.currentTimeMillis() + ".mp3")
            if (f != null) {
                audioFile = f; audioLabel = f.name + " (upload)"
                status = "Audio diupload."
            } else status = "Gagal membaca audio."
        }
    }

    fun saveConfig() {
        settingsManager.updateSettings(
            settingsManager.settings.value.copy(
                elevenLabsApiKey = elevenKey.trim(),
                elevenLabsVoiceId = voiceId.trim(),
                clipServerUrl = serverUrl.trim(),
                clipServerToken = serverToken.trim()
            )
        )
        status = "Konfigurasi disimpan."
    }

    fun runLoadVoices() {
        if (!elevenLabs.isConfigured()) { status = "Isi API key ElevenLabs dulu."; return }
        busy = true
        scope.launch {
            try {
                voices = elevenLabs.listVoices()
                status = if (voices.isEmpty())
                    "Gagal memuat suara: " + (elevenLabs.lastError ?: "0 suara (cek API key / izin key di ElevenLabs)")
                else
                    "Ditemukan " + voices.size + " suara."
            } catch (e: Exception) {
                status = "Gagal memuat daftar suara."
            } finally {
                busy = false
            }
        }
    }

    fun runGenerateScript() {
        busy = true; status = "Membuat naskah dari AI..."
        scope.launch {
            try {
                val s = scriptService.generateScript(
                    title, hook, caption,
                    settingsManager.settings.value.appLanguage.ifBlank { "ID" },
                    targetSeconds
                )
                script = s
                status = if (s.isBlank()) "Naskah kosong, coba isi judul/hook." else "Naskah siap. Edit bila perlu."
            } catch (e: Exception) {
                status = "Gagal buat naskah: " + (e.message ?: "")
            } finally {
                busy = false
            }
        }
    }

    fun runTts() {
        if (script.isBlank()) { status = "Isi / generate naskah dulu."; return }
        if (!elevenLabs.isConfigured()) { status = "API key ElevenLabs kosong (isi di Konfigurasi)."; return }
        busy = true; status = "Membuat suara (ElevenLabs)..."
        scope.launch {
            try {
                val v = elevenLabs.synthesizeToFile(context, script, settingsManager.getEffectiveElevenLabsVoiceId())
                if (v != null) {
                    audioFile = v.file
                    audioLabel = "TTS ElevenLabs (~" + v.approxDurationSec + " dtk)"
                    status = "Suara siap."
                } else status = "Gagal membuat suara: " + (elevenLabs.lastError ?: "cek API key / kuota")
            } catch (e: Exception) {
                status = "Error TTS: " + (e.message ?: "")
            } finally {
                busy = false
            }
        }
    }

    fun addCurrentMediaToGallery() {
        val m = mediaFile ?: run { status = "Belum ada media untuk disimpan."; return }
        settingsManager.addRemakeMedia(RemakeMediaItem(uri = m.absolutePath, kind = mediaKind, name = m.name, isDefault = false))
        gallery = settingsManager.loadRemakeMedia()
        status = "Media disimpan ke galeri."
    }

    fun selectGalleryItem(item: RemakeMediaItem) {
        val f = File(item.uri)
        if (f.exists()) {
            mediaFile = f; mediaKind = item.kind
            mediaLabel = item.name.ifBlank { f.name } + " (galeri)"
            status = "Media galeri dipilih."
        } else status = "File galeri tidak ditemukan (mungkin terhapus)."
    }

    fun runRemake() {
        val audio = audioFile
        val media = mediaFile
        if (audio == null) { status = "Belum ada suara. Buat / rekam / upload dulu."; return }
        if (media == null) { status = "Belum ada video/foto."; return }
        if (settingsManager.settings.value.clipServerUrl.isBlank()) { status = "URL server kosong. Isi di Konfigurasi."; return }
        busy = true; status = "Mengunggah media..."
        scope.launch {
            try {
                val up = clipServer.uploadMedia(media, mediaKind)
                if (up == null) { status = "Gagal upload media."; busy = false; return@launch }
                status = "Mengunggah suara..."
                val upAudio = clipServer.uploadMedia(audio, "audio")
                if (upAudio == null) { status = "Gagal upload suara."; busy = false; return@launch }
                status = if (lipsyncMode) "Memproses lipsync di server..." else "Menempel suara di server..."
                val res = clipServer.requestRemake(
                    mediaId = up.mediaId,
                    mediaKind = mediaKind,
                    audioId = upAudio.mediaId,
                    mode = if (lipsyncMode) "lipsync" else "overlay",
                    aspectRatio = aspect,
                    subtitle = subtitle,
                    subtitleStyle = "clean"
                )
                if (res == null) { status = "Server gagal memproses remake."; busy = false; return@launch }
                status = "Mengunduh hasil ke galeri..."
                val saved = clipServer.downloadClipToGallery(context, res.downloadUrl, "remake_" + title.ifBlank { "video" })
                resultDuration = res.durationSec
                status = if (saved != null)
                    "Selesai! Tersimpan di galeri: " + saved.displayName + " (" + res.durationSec + " dtk)."
                else
                    "Selesai (durasi " + res.durationSec + " dtk), tapi gagal menyimpan ke galeri."
            } catch (e: Exception) {
                status = "Error: " + (e.message ?: "tidak diketahui")
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val def = settingsManager.getDefaultRemakeMedia()
        if (def != null) {
            val f = File(def.uri)
            if (f.exists()) {
                mediaFile = f; mediaKind = def.kind
                mediaLabel = def.name.ifBlank { f.name } + " (default)"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Remake Suara / Lipsync", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Ganti suara video/foto dengan suara baru. Durasi mengikuti suara secara otomatis.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (status.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(status, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }

        // ---------- Konfigurasi ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Text("Konfigurasi", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showConfig = !showConfig }) {
                        Text(if (showConfig) "Sembunyikan" else "Tampilkan")
                    }
                }
                if (showConfig) {
                    OutlinedTextField(
                        value = elevenKey, onValueChange = { elevenKey = it },
                        label = { Text("ElevenLabs API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = voiceId, onValueChange = { voiceId = it },
                        label = { Text("Voice ID (kosong = default Rachel)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runLoadVoices() }, enabled = !busy) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Muat suara")
                        }
                        if (voices.isNotEmpty()) {
                            Box {
                                OutlinedButton(onClick = { voiceMenuOpen = true }) { Text("Pilih (" + voices.size + ")") }
                                DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                                    voices.forEach { (id, name) ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = {
                                            voiceId = id; voiceMenuOpen = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = serverUrl, onValueChange = { serverUrl = it },
                        label = { Text("URL Server (mis. https://chat.agenthebat.com/handle)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverToken, onValueChange = { serverToken = it },
                        label = { Text("Token Server (opsional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { saveConfig() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Simpan Konfigurasi")
                    }
                }
            }
        }

        // ---------- Naskah ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Naskah", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Isi dari halaman Plan (judul/hook/caption), lalu generate naskah AI atau tulis sendiri.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Judul konten") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hook, onValueChange = { hook = it },
                    label = { Text("Hook (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = caption, onValueChange = { caption = it },
                    label = { Text("Inti pesan / caption (opsional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target durasi:", fontSize = 13.sp)
                    listOf(15, 30, 45, 60).forEach { sec ->
                        val sel = targetSeconds == sec
                        if (sel) Button(onClick = { targetSeconds = sec }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text(sec.toString() + "s", fontSize = 12.sp) }
                        else OutlinedButton(onClick = { targetSeconds = sec }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text(sec.toString() + "s", fontSize = 12.sp) }
                    }
                }
                Button(onClick = { runGenerateScript() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Generate Naskah (AI)")
                }
                OutlinedTextField(value = script, onValueChange = { script = it },
                    label = { Text("Naskah (bisa diedit)") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            }
        }

        // ---------- Suara ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2. Suara baru", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("tts" to "ElevenLabs", "record" to "Rekam", "upload" to "Upload").forEach { (mode, label) ->
                        val sel = audioMode == mode
                        if (sel) Button(onClick = { audioMode = mode }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                        else OutlinedButton(onClick = { audioMode = mode }, modifier = Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                    }
                }
                when (audioMode) {
                    "tts" -> {
                        Button(onClick = { runTts() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Buat Suara dari Naskah")
                        }
                    }
                    "record" -> {
                        if (!isRecording) {
                            Button(onClick = {
                                if (hasRecordPermission()) doStartRecording()
                                else recordPerm.launch(Manifest.permission.RECORD_AUDIO)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Mulai Rekam")
                            }
                        } else {
                            Button(onClick = { doStopRecording() }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Stop Rekam")
                            }
                        }
                    }
                    else -> {
                        Button(onClick = { pickAudio.launch("audio/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Pilih File Audio")
                        }
                    }
                }
                if (audioLabel.isNotBlank()) {
                    Text("Suara siap: " + audioLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---------- Media ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Video / Foto", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { pickVideo.launch("video/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Video")
                    }
                    OutlinedButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Foto")
                    }
                }
                if (mediaLabel.isNotBlank()) {
                    Text("Media: " + mediaLabel + "  [" + mediaKind + "]", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { addCurrentMediaToGallery() }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Simpan ke galeri (pakai ulang)")
                    }
                }
                if (gallery.isNotEmpty()) {
                    Text("Galeri media tersimpan:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    gallery.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (item.kind == "photo") Icons.Default.Photo else Icons.Default.Movie,
                                    contentDescription = null, modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    (item.name.ifBlank { "media" }) + (if (item.isDefault) "  (default)" else ""),
                                    fontSize = 12.sp, modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { selectGalleryItem(item) }) { Text("Pakai") }
                                IconButton(onClick = { settingsManager.setDefaultRemakeMedia(item.uri); gallery = settingsManager.loadRemakeMedia() }) {
                                    Icon(if (item.isDefault) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Default", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { settingsManager.removeRemakeMedia(item.uri); gallery = settingsManager.loadRemakeMedia() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- Opsi & Proses ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("4. Opsi & Proses", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Rasio:", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("9:16", "1:1", "16:9").forEach { r ->
                        val sel = aspect == r
                        if (sel) Button(onClick = { aspect = r }, modifier = Modifier.weight(1f)) { Text(r, fontSize = 12.sp) }
                        else OutlinedButton(onClick = { aspect = r }, modifier = Modifier.weight(1f)) { Text(r, fontSize = 12.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = lipsyncMode, onCheckedChange = { lipsyncMode = it })
                    Text("Lipsync asli (bibir ikut kata). Mati = overlay suara saja.", fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = subtitle, onCheckedChange = { subtitle = it })
                    Text("Bakar subtitle otomatis", fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                Button(onClick = { runRemake() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Buat Video Remake")
                }
                if (resultDuration > 0) {
                    Text("Durasi hasil terakhir: " + resultDuration + " detik.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Salin isi content-uri ke file persisten di app storage. Return null bila gagal. */
private fun copyUriToFile(context: Context, uri: Uri, subDir: String, fileName: String): File? {
    return try {
        val dir = File(context.filesDir, subDir)
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, fileName)
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { o -> input.copyTo(o) }
            true
        } ?: false
        if (ok && out.exists() && out.length() > 0) out else null
    } catch (e: Exception) {
        null
    }
}
