package com.fitpro.ui.screens.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.entity.ChatMessageEntity
import com.fitpro.data.local.entity.ChatRole
import com.fitpro.data.preferences.UserPreferencesRepository
import com.fitpro.data.remote.FitProSystemPrompt
import com.fitpro.data.repository.AiRepository
import com.fitpro.data.repository.FoodRepository
import com.fitpro.data.repository.HealthRepository
import com.fitpro.data.repository.TrainingRepository
import com.fitpro.ui.theme.CyanPrimary
import com.fitpro.ui.theme.StatusAltered
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepo: AiRepository,
    private val foodRepo: FoodRepository,
    private val healthRepo: HealthRepository,
    private val trainingRepo: TrainingRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = aiRepo.getMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prefs = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun sendMessage(text: String) {
        val apiKey = prefs.value?.anthropicApiKey
        if (apiKey.isNullOrBlank()) {
            errorMessage.value = "Configure sua chave API em Perfil → Configurações"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            // Build context
            val today = LocalDate.now()
            val todayMeals = foodRepo.getMealsForDate(today).first()
            val todayCalories = todayMeals.sumOf { (it.entry.quantityG / 100f * it.food.caloriesPer100g).toDouble() }.toFloat()
            val todayProtein  = todayMeals.sumOf { (it.entry.quantityG / 100f * it.food.proteinPer100g).toDouble() }.toFloat()
            val todayCarbs    = todayMeals.sumOf { (it.entry.quantityG / 100f * it.food.carbsPer100g).toDouble() }.toFloat()
            val todayFat      = todayMeals.sumOf { (it.entry.quantityG / 100f * it.food.fatPer100g).toDouble() }.toFloat()
            val goal          = foodRepo.getGoal().first()
            val latestMetric  = healthRepo.getLatestMetric()
            val recentSessions = trainingRepo.getRecentSessions().first()
            val lastSession   = recentSessions.firstOrNull()?.let {
                "${it.type.name} - ${it.durationMinutes}min (${it.date.format(DateTimeFormatter.ofPattern("d/MM", Locale("pt","BR")))})"
            }
            val alteredExams = healthRepo.getAlteredExams().first()
            val examNotes = alteredExams.take(5).joinToString("\n") {
                "- ${it.examName}: ${it.value} ${it.unit} (${it.status.name.lowercase()})"
            }.ifBlank { null }

            val systemPrompt = FitProSystemPrompt.build(
                weightKg          = latestMetric?.weightKg,
                heightCm          = prefs.value?.heightCm ?: 177f,
                ageYears          = prefs.value?.ageYears ?: 25,
                dailyCalorieGoal  = goal?.caloriesKcal ?: 2052,
                proteinGoalG      = goal?.proteinG ?: 160,
                carbsGoalG        = goal?.carbsG ?: 207,
                fatGoalG          = goal?.fatG ?: 65,
                todayCalories     = todayCalories,
                todayProtein      = todayProtein,
                todayCarbs        = todayCarbs,
                todayFat          = todayFat,
                lastTraining      = lastSession,
                examNotes         = examNotes
            )

            val history = messages.value.takeLast(40)
            aiRepo.sendMessage(text, apiKey, systemPrompt, history)
            isLoading.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch { aiRepo.clearHistory() }
    }

    fun dismissError() { errorMessage.value = null }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(vm: AiChatViewModel = hiltViewModel()) {
    val messages  by vm.messages.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error     by vm.errorMessage.collectAsStateWithLifecycle()
    val prefs     by vm.prefs.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val hasApiKey = prefs?.anthropicApiKey?.isNotBlank() == true

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                Column {
                    Text("FitPro AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text("Claude · Personalizado", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (messages.isNotEmpty()) {
                IconButton(onClick = { vm.clearHistory() }) {
                    Icon(Icons.Outlined.DeleteOutline, "Limpar conversa",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider()

        // API key warning
        if (!hasApiKey) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Configure sua chave API Anthropic em Perfil → Configurações para usar o FitPro AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Text("FitPro AI", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Assistente personalizado com dados dos seus exames, peso, treinos e dieta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        // Quick prompt suggestions
                        listOf(
                            "Como estou em relação às minhas metas de hoje?",
                            "Sugira uma refeição para completar minha proteína",
                            "Analise meus exames alterados",
                            "Crie um treino para hoje baseado no meu histórico"
                        ).forEach { suggestion ->
                            SuggestionChip(
                                onClick = { vm.sendMessage(suggestion) },
                                label   = { Text(suggestion, fontSize = 12.sp) },
                                icon    = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        AiTypingIndicator()
                    }
                }
            }
        }

        // Error snackbar
        error?.let { err ->
            Snackbar(
                modifier = Modifier.padding(horizontal = 16.dp),
                action   = { TextButton(onClick = { vm.dismissError() }) { Text("OK") } }
            ) { Text(err, fontSize = 13.sp) }
        }

        // Input bar
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = inputText,
                onValueChange = { inputText = it },
                placeholder   = { Text("Mensagem para o FitPro AI…", fontSize = 14.sp) },
                modifier      = Modifier.weight(1f),
                shape         = RoundedCornerShape(24.dp),
                maxLines      = 4,
                enabled       = !isLoading
            )
            FilledIconButton(
                onClick  = {
                    val text = inputText.trim()
                    if (text.isNotBlank()) {
                        inputText = ""
                        vm.sendMessage(text)
                    }
                },
                enabled  = inputText.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Outlined.Send, "Enviar")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape  = RoundedCornerShape(50),
                color  = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp).align(Alignment.Bottom)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else if (message.isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isUser         -> MaterialTheme.colorScheme.onPrimary
                    message.isError-> MaterialTheme.colorScheme.onErrorContainer
                    else           -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                lineHeight = 20.sp
            )
        }
    }

    // Timestamp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        Text(
            message.timestamp.format(fmt),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AiTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f, label = "alpha",
        animationSpec = infiniteRepeatable(
            tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(start = 34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha * (0.5f + i * 0.25f)))
                )
            }
        }
    }
}
