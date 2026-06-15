package com.fitpro.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpro.data.local.entity.TrainingType
import com.fitpro.ui.components.*
import com.fitpro.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToDiary: () -> Unit,
    onNavigateToTraining: () -> Unit,
    onNavigateToProfile: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val greeting = remember {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            h < 12 -> "Bom dia"
            h < 18 -> "Boa tarde"
            else   -> "Boa noite"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "$greeting, ${state.prefs?.userName ?: "Lucas"}!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR"))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNavigateToProfile) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = "Perfil", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Quick stats row (weight, BMI, streak)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    label = "Peso",
                    value = state.latestMetric?.let { "%.1f".format(it.weightKg) } ?: "--",
                    unit = "kg",
                    icon = Icons.Outlined.Monitor,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    label = "IMC",
                    value = state.bmi?.let { "%.1f".format(it) } ?: "--",
                    unit = "",
                    icon = Icons.Outlined.Analytics,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    label = "Sequência",
                    value = "${state.streakDays}",
                    unit = "dias",
                    icon = Icons.Outlined.LocalFireDepartment,
                    modifier = Modifier.weight(1f),
                    valueColor = if (state.streakDays > 0) CyanPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Macro rings
        item {
            SectionCard(
                title = "Nutrição de hoje",
                action = {
                    TextButton(onClick = onNavigateToDiary) {
                        Text("Ver diário", fontSize = 12.sp)
                    }
                }
            ) {
                val todaySum = state.todaySummary
                val goal = state.goal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroProgressRing(
                        label   = "Calorias",
                        current = todaySum?.calories ?: 0f,
                        goal    = goal.caloriesKcal.toFloat(),
                        unit    = "kcal",
                        color   = CalorieOrange,
                        size    = 88.dp
                    )
                    MacroProgressRing(
                        label   = "Proteína",
                        current = todaySum?.protein ?: 0f,
                        goal    = goal.proteinG.toFloat(),
                        unit    = "g",
                        color   = ProteinBlue
                    )
                    MacroProgressRing(
                        label   = "Carbs",
                        current = todaySum?.carbs ?: 0f,
                        goal    = goal.carbsG.toFloat(),
                        unit    = "g",
                        color   = CarbAmber
                    )
                    MacroProgressRing(
                        label   = "Gordura",
                        current = todaySum?.fat ?: 0f,
                        goal    = goal.fatG.toFloat(),
                        unit    = "g",
                        color   = FatPink
                    )
                }
                Spacer(Modifier.height(12.dp))
                MacroSummaryBar(
                    calories     = todaySum?.calories ?: 0f,
                    protein      = todaySum?.protein ?: 0f,
                    carbs        = todaySum?.carbs ?: 0f,
                    fat          = todaySum?.fat ?: 0f,
                    goalCalories = goal.caloriesKcal,
                    goalProtein  = goal.proteinG,
                    goalCarbs    = goal.carbsG,
                    goalFat      = goal.fatG
                )
            }
        }

        // Quick add meal button
        item {
            Button(
                onClick  = onNavigateToDiary,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.AddCircleOutline, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Adicionar Refeição")
            }
        }

        // Recent training sessions
        if (state.recentSessions.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Treinos recentes",
                    action = {
                        TextButton(onClick = onNavigateToTraining) {
                            Text("Ver todos", fontSize = 12.sp)
                        }
                    }
                ) {
                    state.recentSessions.take(3).forEach { session ->
                        TrainingSessionRow(session = session)
                        if (session != state.recentSessions.take(3).last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        } else {
            item {
                SectionCard(title = "Treinos") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nenhum treino registrado ainda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onNavigateToTraining) {
                            Text("Registrar primeiro treino")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = valueColor)
                if (unit.isNotEmpty()) Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrainingSessionRow(session: com.fitpro.data.local.entity.TrainingSessionEntity) {
    val icon = when (session.type) {
        TrainingType.CARDIO   -> Icons.Outlined.DirectionsRun
        TrainingType.STRENGTH -> Icons.Outlined.FitnessCenter
        TrainingType.RUN      -> Icons.Outlined.DirectionsRun
        TrainingType.HIIT     -> Icons.Outlined.Whatshot
        else                  -> Icons.Outlined.SelfImprovement
    }
    val typeLabel = when (session.type) {
        TrainingType.CARDIO   -> "Cardio"
        TrainingType.STRENGTH -> "Musculação"
        TrainingType.RUN      -> "Corrida"
        TrainingType.HIIT     -> "HIIT"
        TrainingType.SWIM     -> "Natação"
        TrainingType.YOGA     -> "Yoga"
        TrainingType.OTHER    -> "Outro"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                session.date.format(DateTimeFormatter.ofPattern("d MMM", Locale("pt","BR"))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${session.durationMinutes} min",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
