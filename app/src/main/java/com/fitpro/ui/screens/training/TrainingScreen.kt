package com.fitpro.ui.screens.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.TrainingDayData
import com.fitpro.data.local.entity.*
import com.fitpro.data.repository.TrainingRepository
import com.fitpro.ui.components.SectionCard
import com.fitpro.ui.components.TrainingHeatmap
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val repo: TrainingRepository
) : ViewModel() {

    private val heatmapFrom = LocalDate.now().minusWeeks(26)
    private val heatmapTo   = LocalDate.now()

    val heatmapData: StateFlow<Map<LocalDate, TrainingDayData>> = repo
        .getHeatmapData(heatmapFrom, heatmapTo)
        .map { list -> list.associateBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recentSessions: StateFlow<List<TrainingSessionEntity>> = repo
        .getRecentSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streak = MutableStateFlow(0)

    init {
        viewModelScope.launch { streak.value = repo.getStreakDays() }
    }

    fun addSession(type: TrainingType, durationMin: Int, intensity: Intensity,
                   distanceKm: Float?, notes: String) {
        viewModelScope.launch {
            repo.addSession(TrainingSessionEntity(
                date = LocalDate.now(), type = type,
                durationMinutes = durationMin, intensity = intensity,
                distanceKm = distanceKm, notes = notes
            ))
            streak.value = repo.getStreakDays()
        }
    }

    fun deleteSession(session: TrainingSessionEntity) {
        viewModelScope.launch { repo.deleteSession(session) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(vm: TrainingViewModel = hiltViewModel()) {
    val heatmap  by vm.heatmapData.collectAsStateWithLifecycle()
    val sessions by vm.recentSessions.collectAsStateWithLifecycle()
    val streak   by vm.streak.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Treinos", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium)
            }

            // Stats row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatPill("Sequência", "$streak dias", Icons.Outlined.LocalFireDepartment,
                        if (streak > 0) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.weight(1f))
                    StatPill("Este mês",
                        "${sessions.count { it.date >= LocalDate.now().withDayOfMonth(1) }} treinos",
                        Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary,
                        Modifier.weight(1f))
                }
            }

            // Heatmap
            item {
                SectionCard(title = "Atividade — últimos 6 meses") {
                    TrainingHeatmap(data = heatmap, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Menos", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        listOf(HeatLevel0, HeatLevel1, HeatLevel2, HeatLevel3, HeatLevel4).forEach { c ->
                            Box(Modifier
                                .padding(horizontal = 1.dp)
                                .size(10.dp)
                                .background(c, RoundedCornerShape(2.dp)))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Mais", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text("Histórico", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
            }

            if (sessions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("Nenhum treino registrado",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(session = session, onDelete = { vm.deleteSession(session) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Outlined.Add, "Adicionar treino") }
    }

    if (showAddSheet) {
        AddTrainingSheet(
            onDismiss = { showAddSheet = false },
            onSave = { type, dur, intensity, dist, notes ->
                vm.addSession(type, dur, intensity, dist, notes)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun StatPill(label: String, value: String,
                     icon: androidx.compose.ui.graphics.vector.ImageVector,
                     tint: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Column {
                Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionCard(session: TrainingSessionEntity, onDelete: () -> Unit) {
    val typeLabel = when (session.type) {
        TrainingType.CARDIO   -> "Cardio"
        TrainingType.STRENGTH -> "Musculação"
        TrainingType.RUN      -> "Corrida"
        TrainingType.HIIT     -> "HIIT"
        TrainingType.SWIM     -> "Natação"
        TrainingType.YOGA     -> "Yoga"
        TrainingType.OTHER    -> "Outro"
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(typeLabel, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge)
                    SuggestionChip(onClick = {},
                        label = { Text(session.intensity.name.lowercase()
                            .replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                        modifier = Modifier.height(22.dp))
                }
                Text(session.date.format(DateTimeFormatter.ofPattern(
                    "EEEE, d 'de' MMM", Locale("pt","BR"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${session.durationMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    session.distanceKm?.let {
                        Text("${"%.1f".format(it)} km",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (session.notes.isNotBlank()) {
                    Text(session.notes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "Excluir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTrainingSheet(
    onDismiss: () -> Unit,
    onSave: (TrainingType, Int, Intensity, Float?, String) -> Unit
) {
    var selectedType      by remember { mutableStateOf(TrainingType.STRENGTH) }
    var durationText      by remember { mutableStateOf("60") }
    var selectedIntensity by remember { mutableStateOf(Intensity.MODERATE) }
    var distanceText      by remember { mutableStateOf("") }
    var notes             by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Registrar treino", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium)

            Text("Tipo", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TrainingType.STRENGTH to "Musculação", TrainingType.CARDIO to "Cardio",
                    TrainingType.RUN to "Corrida", TrainingType.HIIT to "HIIT").forEach { (type, lbl) ->
                    FilterChip(selected = selectedType == type, onClick = { selectedType = type },
                        label = { Text(lbl, fontSize = 12.sp) })
                }
            }

            OutlinedTextField(
                value = durationText,
                onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) durationText = it },
                label = { Text("Duração (minutos)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )

            Text("Intensidade", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Intensity.LOW to "Leve", Intensity.MODERATE to "Moderado",
                    Intensity.HIGH to "Intenso", Intensity.MAX to "Máximo").forEach { (i, lbl) ->
                    FilterChip(selected = selectedIntensity == i, onClick = { selectedIntensity = i },
                        label = { Text(lbl, fontSize = 12.sp) })
                }
            }

            if (selectedType in listOf(TrainingType.CARDIO, TrainingType.RUN)) {
                OutlinedTextField(distanceText, { distanceText = it },
                    label = { Text("Distância km (opcional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            }

            OutlinedTextField(notes, { notes = it },
                label = { Text("Observações (opcional)") },
                modifier = Modifier.fillMaxWidth(), maxLines = 3)

            Button(
                onClick = {
                    onSave(selectedType, durationText.toIntOrNull() ?: 60,
                        selectedIntensity, distanceText.toFloatOrNull(), notes)
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar treino") }
        }
    }
}
