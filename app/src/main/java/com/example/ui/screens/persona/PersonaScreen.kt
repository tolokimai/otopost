package com.example.ui.screens.persona

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
import com.example.data.local.entity.PersonaEntity
import com.example.ui.theme.*
import com.example.viewmodel.AutoPostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    viewModel: AutoPostViewModel,
    modifier: Modifier = Modifier
) {
    val personas by viewModel.personas.collectAsState()
    val isGeneratingAi by viewModel.isGeneratingPersonaAi.collectAsState()

    var showEditorModal by remember { mutableStateOf(false) }
    var personaToEdit by remember { mutableStateOf<PersonaEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    personaToEdit = null
                    showEditorModal = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Buat Persona", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_create_persona")
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Column {
                    Text(
                        text = "Persona Kreator & Brand",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tentukan identitas, gaya bahasa, & niche agar konten AI konsisten.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (personas.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Belum Ada Persona",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Buat persona brand Anda (bisa tulis manual atau generate otomatis pakai Gemini AI).",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    personaToEdit = null
                                    showEditorModal = true
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Buat Persona Sekarang")
                            }
                        }
                    }
                }
            } else {
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        onSetDefault = { viewModel.setDefaultPersona(persona.id) },
                        onEdit = {
                            personaToEdit = persona
                            showEditorModal = true
                        },
                        onDelete = { viewModel.deletePersona(persona) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showEditorModal) {
        PersonaEditorDialog(
            existingPersona = personaToEdit,
            isGeneratingAi = isGeneratingAi,
            onDismiss = { showEditorModal = false },
            onGenerateAi = { keywords, niche, lang, callback ->
                viewModel.generatePersonaWithAi(keywords, niche, lang, callback)
            },
            onSave = { savedPersona ->
                viewModel.savePersona(savedPersona)
                showEditorModal = false
            }
        )
    }
}

@Composable
fun PersonaCard(
    persona: PersonaEntity,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (persona.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Brand & Default badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = persona.brandName.take(1).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column {
                        Text(
                            text = persona.brandName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = persona.niche,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (persona.isDefault) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "⭐ DEFAULT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onSetDefault,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Set Default", fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Details
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PersonaDetailRow(icon = "🎯", label = "Target Audiens", value = persona.targetAudience)
                PersonaDetailRow(icon = "🗣️", label = "Gaya & Tone", value = "${persona.languageStyle} • ${persona.tone}")
                PersonaDetailRow(icon = "🌐", label = "Bahasa", value = if (persona.language == "ID") "Bahasa Indonesia (ID)" else "English (EN)")
                if (persona.accountReferences.isNotBlank()) {
                    PersonaDetailRow(icon = "🔗", label = "Referensi", value = persona.accountReferences)
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusFailed)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hapus")
                }
            }
        }
    }
}

@Composable
fun PersonaDetailRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditorDialog(
    existingPersona: PersonaEntity?,
    isGeneratingAi: Boolean,
    onDismiss: () -> Unit,
    onGenerateAi: (String, String, String, (com.example.data.remote.GeneratedPersona) -> Unit) -> Unit,
    onSave: (PersonaEntity) -> Unit
) {
    var modeTab by remember { mutableStateOf(if (existingPersona == null) 1 else 0) } // 0 = Manual, 1 = AI Generate, 2 = Edit AI Result

    // AI Generator inputs
    var aiKeywords by remember { mutableStateOf("") }
    var aiNicheHint by remember { mutableStateOf("") }

    // Persona Form Inputs
    var brandName by remember { mutableStateOf(existingPersona?.brandName ?: "") }
    var niche by remember { mutableStateOf(existingPersona?.niche ?: "") }
    var targetAudience by remember { mutableStateOf(existingPersona?.targetAudience ?: "") }
    var languageStyle by remember { mutableStateOf(existingPersona?.languageStyle ?: "Santai & Edukatif") }
    var tone by remember { mutableStateOf(existingPersona?.tone ?: "Energetic & Praktis") }
    var language by remember { mutableStateOf(existingPersona?.language ?: "ID") }
    var accountReferences by remember { mutableStateOf(existingPersona?.accountReferences ?: "") }
    var isDefault by remember { mutableStateOf(existingPersona?.isDefault ?: false) }

    val styleOptions = listOf("Santai & To-The-Point", "Formal & Profesional", "Lucu / Humoris", "Edukatif & Mendalam", "Inspiratif & Emosional")
    val toneOptions = listOf("Energetic & Hype", "Authoritative (Pakar)", "Empathetic & Warm", "Provocative (Bongkar Mitos)")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingPersona == null) "Buat Persona Baru" else "Edit Persona",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 3 Mode Tabs
                TabRow(selectedTabIndex = modeTab) {
                    Tab(
                        selected = modeTab == 0,
                        onClick = { modeTab = 0 },
                        text = { Text("Manual", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = modeTab == 1,
                        onClick = { modeTab = 1 },
                        text = { Text("✨ AI Gemini", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = modeTab == 2,
                        onClick = { modeTab = 2 },
                        text = { Text("Edit Hasil", fontSize = 12.sp) }
                    )
                }

                if (modeTab == 1) {
                    // --- Mode AI Generate ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Masukkan beberapa kata kunci atau topik, Gemini AI akan menyusun persona lengkap secara instan.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = aiKeywords,
                            onValueChange = { aiKeywords = it },
                            label = { Text("Kata Kunci Brand / Konsep") },
                            placeholder = { Text("mis. kopi kekinian, self improvement, saham pemula") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = aiNicheHint,
                            onValueChange = { aiNicheHint = it },
                            label = { Text("Niche / Kategori (Opsional)") },
                            placeholder = { Text("mis. Kuliner & Bisnis, Finansial") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bahasa:", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = language == "ID",
                                    onClick = { language = "ID" },
                                    label = { Text("Indonesia") }
                                )
                                FilterChip(
                                    selected = language == "EN",
                                    onClick = { language = "EN" },
                                    label = { Text("English") }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (aiKeywords.isNotBlank()) {
                                    onGenerateAi(aiKeywords, aiNicheHint, language) { gen ->
                                        brandName = gen.brandName
                                        niche = gen.niche
                                        targetAudience = gen.targetAudience
                                        languageStyle = gen.languageStyle
                                        tone = gen.tone
                                        language = gen.language
                                        accountReferences = gen.accountReferences
                                        modeTab = 2 // Switch to edit result
                                    }
                                }
                            },
                            enabled = aiKeywords.isNotBlank() && !isGeneratingAi,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isGeneratingAi) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Gemini Merancang Persona...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate dengan Gemini AI")
                            }
                        }
                    }
                } else {
                    // --- Mode Manual / Edit Hasil AI ---
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = brandName,
                                onValueChange = { brandName = it },
                                label = { Text("Nama Brand / Akun *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = niche,
                                onValueChange = { niche = it },
                                label = { Text("Niche / Topik Spesifik *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = targetAudience,
                                onValueChange = { targetAudience = it },
                                label = { Text("Target Audiens") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text("Gaya Bahasa:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(styleOptions) { opt ->
                                    FilterChip(
                                        selected = languageStyle == opt,
                                        onClick = { languageStyle = opt },
                                        label = { Text(opt, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        item {
                            Text("Tone:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(toneOptions) { opt ->
                                    FilterChip(
                                        selected = tone == opt,
                                        onClick = { tone = opt },
                                        label = { Text(opt, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = accountReferences,
                                onValueChange = { accountReferences = it },
                                label = { Text("Contoh Akun Referensi") },
                                placeholder = { Text("e.g. @garyvee, @feliciaputri") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isDefault,
                                    onCheckedChange = { isDefault = it }
                                )
                                Text("Jadikan Persona Default", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (modeTab != 1) {
                Button(
                    onClick = {
                        if (brandName.isNotBlank() && niche.isNotBlank()) {
                            onSave(
                                PersonaEntity(
                                    id = existingPersona?.id ?: 0,
                                    brandName = brandName,
                                    niche = niche,
                                    targetAudience = targetAudience.ifBlank { "Audiens umum media sosial" },
                                    languageStyle = languageStyle,
                                    tone = tone,
                                    language = language,
                                    accountReferences = accountReferences,
                                    isDefault = isDefault
                                )
                            )
                        }
                    },
                    enabled = brandName.isNotBlank() && niche.isNotBlank()
                ) {
                    Text("Simpan Persona")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
