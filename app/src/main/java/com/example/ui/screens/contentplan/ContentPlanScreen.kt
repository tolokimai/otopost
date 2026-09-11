package com.example.ui.screens.contentplan

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ContentFormat
import com.example.data.local.entity.ContentPlanItemEntity
import com.example.data.local.entity.PersonaEntity
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentPlanScreen(
    viewModel: AutoPostViewModel,
    modifier: Modifier = Modifier
) {
    val personas by viewModel.personas.collectAsState()
    val defaultPersona by viewModel.defaultPersona.collectAsState()
    val selectedPersona by viewModel.selectedPersonaForPlan.collectAsState()
    val bigThemes by viewModel.bigThemes.collectAsState()
    val isGeneratingThemes by viewModel.isGeneratingThemes.collectAsState()
    val isGeneratingPlan by viewModel.isGeneratingPlan.collectAsState()
    val planItems by viewModel.planItems.collectAsState()

    val activePersona = selectedPersona ?: defaultPersona ?: personas.firstOrNull()

    var durationDays by remember { mutableStateOf(7) }
    var bigThemeInput by remember { mutableStateOf("30 Hari Menguasai Content Creation dari Nol") }
    var showEditItemModal by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ContentPlanItemEntity?>(null) }
    var showAddItemModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Column {
                Text(
                    text = "AI Content Plan Generator",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Buat rencana konten terjadwal 7 atau 30 hari otomatis berbasis persona.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Step 1: Pilih Persona & Tema Besar ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Pilih Persona & Tema Besar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Persona Selector
                    if (personas.isEmpty()) {
                        Text(
                            text = "⚠️ Belum ada persona. Buat persona di menu 'Persona' terlebih dahulu.",
                            fontSize = 12.sp,
                            color = StatusFailed
                        )
                    } else {
                        Text("Pilih Persona:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(personas) { p ->
                                val isSelected = activePersona?.id == p.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectPersonaForPlan(p) },
                                    label = { Text(p.brandName) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Big Theme Input & AI Suggestions
                    OutlinedTextField(
                        value = bigThemeInput,
                        onValueChange = { bigThemeInput = it },
                        label = { Text("Tema Besar Kampanye Konten") },
                        placeholder = { Text("mis. Strategi Scaling Bisnis Digital, Diet Sehat 30 Hari") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Theme generator button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Saran Tema AI:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = {
                                activePersona?.let { viewModel.generateThemeSuggestions(it) }
                            },
                            enabled = activePersona != null && !isGeneratingThemes
                        ) {
                            if (isGeneratingThemes) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Generate Ide Tema", fontSize = 11.sp)
                            }
                        }
                    }

                    if (bigThemes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            bigThemes.forEach { theme ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { bigThemeInput = theme }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                        Text(text = theme, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // Duration Selector (7 vs 30 Days)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Durasi Rencana:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = durationDays == 7,
                                onClick = { durationDays = 7 },
                                label = { Text("7 Hari (1 Minggu)") }
                            )
                            FilterChip(
                                selected = durationDays == 30,
                                onClick = { durationDays = 30 },
                                label = { Text("30 Hari (1 Bulan)") }
                            )
                        }
                    }

                    // Generate Button
                    Button(
                        onClick = {
                            activePersona?.let {
                                viewModel.generateAndSaveContentPlan(it, bigThemeInput, durationDays)
                            }
                        },
                        enabled = activePersona != null && bigThemeInput.isNotBlank() && !isGeneratingPlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_generate_content_plan"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isGeneratingPlan) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Gemini AI Sedang Merancang Plan...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate $durationDays Hari Content Plan dengan AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Step 2: Tabel & Daftar Item Content Plan ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Rencana Konten (${planItems.size} Item)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = { showAddItemModal = true }) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Tambah Item", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (planItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada rencana konten. Klik tombol Generate di atas untuk membuat!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(planItems, key = { it.id }) { item ->
                ContentPlanItemCard(
                    item = item,
                    onEdit = {
                        itemToEdit = item
                        showEditItemModal = true
                    },
                    onDelete = { viewModel.deletePlanItem(item) },
                    onSendToStudio = { viewModel.sendPlanItemToStudio(item) }
                )
            }
        }

        item {
            Spacer(Modifier.height(80.dp))
        }
    }

    // Modal Edit / Add Item Plan
    if (showEditItemModal && itemToEdit != null) {
        PlanItemEditorDialog(
            initialItem = itemToEdit!!,
            onDismiss = { showEditItemModal = false },
            onSave = { updated ->
                viewModel.updatePlanItem(updated)
                showEditItemModal = false
            }
        )
    }

    if (showAddItemModal) {
        PlanItemEditorDialog(
            initialItem = ContentPlanItemEntity(
                planId = 0,
                dayNumber = planItems.size + 1,
                scheduledDateMillis = System.currentTimeMillis() + (planItems.size * 24L * 3600 * 1000),
                title = "Ide Konten Baru",
                format = ContentFormat.CAROUSEL,
                hook = "Hook menarik di sini",
                captionDraft = "Caption lengkap...",
                hashtags = "#tips #creator"
            ),
            onDismiss = { showAddItemModal = false },
            onSave = { newItem ->
                viewModel.updatePlanItem(newItem)
                showAddItemModal = false
            }
        )
    }
}

@Composable
fun ContentPlanItemCard(
    item: ContentPlanItemEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSendToStudio: () -> Unit
) {
    val dateFormat = SimpleDateFormat("EEE, dd MMM", Locale("id", "ID"))
    val dateStr = dateFormat.format(Date(item.scheduledDateMillis))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Day Number, Date, Format Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Hari ${item.dayNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(text = dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (item.format) {
                        ContentFormat.CAROUSEL -> UtilityBlue100
                        ContentFormat.PODCAST_CLIP -> YouTubeBg
                        ContentFormat.SELF_VIDEO -> Slate100
                        ContentFormat.AI_VIDEO -> UtilityBlue100.copy(alpha = 0.8f)
                    }
                ) {
                    Text(
                        text = when (item.format) {
                            ContentFormat.CAROUSEL -> "📑 CAROUSEL"
                            ContentFormat.PODCAST_CLIP -> "🎙️ PODCAST CLIP"
                            ContentFormat.SELF_VIDEO -> "🎬 VIDEO SENDIRI"
                            ContentFormat.AI_VIDEO -> "✨ BUAT VIDEO (AI)"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.format) {
                            ContentFormat.CAROUSEL -> UtilityBlue700
                            ContentFormat.PODCAST_CLIP -> YouTubeText
                            ContentFormat.SELF_VIDEO -> Slate700
                            ContentFormat.AI_VIDEO -> UtilityBlue700
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Title
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Hook
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                    Text(text = "🔥 Hook: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = item.hook, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Caption Draft & Hashtags
            Text(
                text = item.captionDraft,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.hashtags.isNotBlank()) {
                Text(
                    text = item.hashtags,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions & Kirim ke Studio Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", modifier = Modifier.size(16.dp), tint = StatusFailed)
                    }
                }

                Button(
                    onClick = onSendToStudio,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kirim ke Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanItemEditorDialog(
    initialItem: ContentPlanItemEntity,
    onDismiss: () -> Unit,
    onSave: (ContentPlanItemEntity) -> Unit
) {
    var title by remember { mutableStateOf(initialItem.title) }
    var format by remember { mutableStateOf(initialItem.format) }
    var hook by remember { mutableStateOf(initialItem.hook) }
    var captionDraft by remember { mutableStateOf(initialItem.captionDraft) }
    var hashtags by remember { mutableStateOf(initialItem.hashtags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item Rencana Konten", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Konten") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Format Konten:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = format == ContentFormat.CAROUSEL,
                        onClick = { format = ContentFormat.CAROUSEL },
                        label = { Text("Carousel", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = format == ContentFormat.PODCAST_CLIP,
                        onClick = { format = ContentFormat.PODCAST_CLIP },
                        label = { Text("Podcast", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = format == ContentFormat.SELF_VIDEO,
                        onClick = { format = ContentFormat.SELF_VIDEO },
                        label = { Text("Video", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = hook,
                    onValueChange = { hook = it },
                    label = { Text("Hook 3 Detik") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = captionDraft,
                    onValueChange = { captionDraft = it },
                    label = { Text("Draft Caption & CTA") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hashtags,
                    onValueChange = { hashtags = it },
                    label = { Text("Hashtags") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialItem.copy(
                            title = title,
                            format = format,
                            hook = hook,
                            captionDraft = captionDraft,
                            hashtags = hashtags
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
