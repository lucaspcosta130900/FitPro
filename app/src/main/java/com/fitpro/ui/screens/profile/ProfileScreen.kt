package com.fitpro.ui.screens.profile

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpro.data.remote.ExamResultDto
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.entity.*
import com.fitpro.data.preferences.UserPreferences
import com.fitpro.data.preferences.UserPreferencesRepository
import com.fitpro.data.repository.HealthRepository
import com.fitpro.ui.components.SectionCard
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable
import com.fitpro.ui.theme.StatusAltered
import com.fitpro.ui.theme.StatusNormal
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val healthRepo: HealthRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val aiRepo: com.fitpro.data.repository.AiRepository
) : ViewModel() {

    val isPdfLoading = MutableStateFlow(false)
    val pdfResult    = MutableStateFlow<String?>(null)
    val pdfError     = MutableStateFlow<String?>(null)

    fun analyzeExamPdf(context: android.content.Context, uri: android.net.Uri, apiKey: String) {
        if (apiKey.isBlank()) { pdfError.value = "Configure sua chave API em Configuracoes primeiro"; return }
        viewModelScope.launch {
            isPdfLoading.value = true
            pdfResult.value    = null
            pdfError.value     = null
            try {
                val bytes   = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Nao foi possivel ler o arquivo")
                val base64  = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val results = aiRepo.analyzePdf(base64, apiKey)
                val today   = LocalDate.now()
                results.forEach { dto ->
                    val status = when (dto.status.uppercase()) {
                        "ALTERED"    -> ExamStatus.ALTERED
                        "BORDERLINE" -> ExamStatus.BORDERLINE
                        else         -> ExamStatus.NORMAL
                    }
                    healthRepo.insertExam(LabExamEntity(
                        date         = today,
                        examGroup    = dto.group,
                        examName     = dto.name,
                        value        = dto.value,
                        unit         = dto.unit,
                        referenceMin = dto.refMin,
                        referenceMax = dto.refMax,
                        referenceText= dto.refText,
                        status       = status
                    ))
                }
                val altered = results.count { it.status != "NORMAL" }
                pdfResult.value = "${results.size} resultados importados · $altered fora do intervalo"
            } catch (e: Exception) {
                pdfError.value = "Erro: ${e.message}"
            } finally {
                isPdfLoading.value = false
            }
        }
    }

    fun clearPdfState() { pdfResult.value = null; pdfError.value = null }

    val metrics: StateFlow<List<BodyMetricEntity>> = healthRepo.getAllMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val examDates: StateFlow<List<LocalDate>> = healthRepo.getExamDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allExams: StateFlow<List<LabExamEntity>> = healthRepo.getAllExams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alteredExams: StateFlow<List<LabExamEntity>> = healthRepo.getAlteredExams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prefs: StateFlow<UserPreferences?> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addBodyMetric(weightKg: Float, fatPercent: Float?, muscleMassKg: Float?, waistCm: Float?) {
        viewModelScope.launch {
            healthRepo.insertMetric(BodyMetricEntity(
                date = LocalDate.now(),
                weightKg = weightKg,
                bodyFatPercent = fatPercent,
                muscleMassKg = muscleMassKg,
                waistCm = waistCm
            ))
        }
    }

    fun addLabExam(date: LocalDate, group: String, name: String, value: Float,
                   unit: String, refMin: Float?, refMax: Float?, refText: String, status: ExamStatus) {
        viewModelScope.launch {
            healthRepo.insertExam(LabExamEntity(
                date = date, examGroup = group, examName = name,
                value = value, unit = unit, referenceMin = refMin,
                referenceMax = refMax, referenceText = refText, status = status
            ))
        }
    }

    fun saveApiKey(key: String) = viewModelScope.launch { prefsRepo.setApiKey(key) }
    fun saveName(name: String)  = viewModelScope.launch { prefsRepo.setUserName(name) }
    fun saveHeight(cm: Float)   = viewModelScope.launch { prefsRepo.setHeight(cm) }
    fun deleteMetric(m: BodyMetricEntity) = viewModelScope.launch { healthRepo.deleteMetric(m) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToAi: () -> Unit,
    vm: ProfileViewModel = hiltViewModel()
) {
    val metrics      by vm.metrics.collectAsStateWithLifecycle()
    val examDates    by vm.examDates.collectAsStateWithLifecycle()
    val allExams     by vm.allExams.collectAsStateWithLifecycle()
    var selectedExamDate by remember { mutableStateOf<LocalDate?>(null) }
    var showWeightHistory by remember { mutableStateOf(false) }
    val alteredExams by vm.alteredExams.collectAsStateWithLifecycle()
    val prefs        by vm.prefs.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val isPdfLoading by vm.isPdfLoading.collectAsStateWithLifecycle()
    val pdfResult    by vm.pdfResult.collectAsStateWithLifecycle()
    val pdfError     by vm.pdfError.collectAsStateWithLifecycle()

    val pdfPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val apiKey = prefs?.anthropicApiKey ?: ""
            vm.analyzeExamPdf(context, uri, apiKey)
        }
    }

    var showWeightSheet by remember { mutableStateOf(false) }
    var showApiKeySheet by remember { mutableStateOf(false) }
    var showExamSheet   by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }

        // User card
        prefs?.let { p ->
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            shape  = RoundedCornerShape(50),
                            color  = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(p.userName.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            val latest = metrics.firstOrNull()
                            Text(
                                buildString {
                                    latest?.let { append("%.1f kg".format(it.weightKg)) }
                                    append(" · ${p.heightCm.toInt()} cm · ${p.ageYears} anos")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            latest?.bodyFatPercent?.let {
                                Text("%.1f%% gordura".format(it), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Weight history
        item {
            SectionCard(
                title  = "Histórico de Peso",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(onClick = { showWeightHistory = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.History, "Histórico", Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { showWeightSheet = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Add, "Adicionar peso", Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            ) {
                if (metrics.isEmpty()) {
                    Text("Nenhum registro. Adicione seu peso para começar o acompanhamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    metrics.take(7).forEachIndexed { index, metric ->
                        WeightRow(metric = metric, onDelete = { vm.deleteMetric(metric) })
                        if (index < minOf(metrics.size, 7) - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    if (metrics.size > 2) {
                        val delta = metrics.first().weightKg - metrics.last().weightKg
                        val color = if (delta <= 0) StatusNormal else StatusAltered
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${if (delta <= 0) "" else "+"}${"%.1f".format(delta)} kg desde o início",
                            style = MaterialTheme.typography.labelMedium,
                            color = color
                        )
                    }
                }
            }
        }

        // Exams - altered flags
        if (alteredExams.isNotEmpty()) {
            item {
                SectionCard(title = "Exames com atenção") {
                    alteredExams.take(6).forEach { exam ->
                        ExamRow(exam = exam)
                        if (exam != alteredExams.take(6).last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // Exam dates
        item {
            SectionCard(
                title  = "Histórico de Exames",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // PDF import button
                        IconButton(onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.PictureAsPdf, "Importar PDF",
                                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { showExamSheet = true },
                            modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Add, "Adicionar exame", Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            ) {
                // PDF loading/result
                if (isPdfLoading) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Analisando PDF com Claude AI...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                pdfResult?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp),
                                tint = StatusNormal)
                            Text(msg, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                pdfError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(err, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (examDates.isEmpty() && !isPdfLoading && pdfResult == null) {
                    Text("Nenhum exame registrado. Adicione seus resultados de exames para acompanhamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    examDates.take(8).forEach { date ->
                        val examsOnDate = allExams.filter { it.date == date }
                        val altCount = examsOnDate.count { it.status != ExamStatus.NORMAL }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { selectedExamDate = date },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("pt","BR"))),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("${examsOnDate.size} exames${if (altCount > 0) " · $altCount alterado(s)" else " · todos normais"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (altCount > 0) StatusAltered else StatusNormal)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        // Settings
        item {
            SectionCard(title = "Configurações") {
                SettingRow(icon = Icons.Outlined.SmartToy, label = "Chave API Claude (Anthropic)",
                    value = if (prefs?.anthropicApiKey?.isNotBlank() == true) "Configurada" else "Não configurada",
                    onClick = { showApiKeySheet = true })
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingRow(icon = Icons.Outlined.AutoAwesome, label = "FitPro AI",
                    value = "Chat personalizado",
                    onClick = onNavigateToAi)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // Sheets
    // PDF feedback
    pdfResult?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(4000)
            vm.clearPdfState()
        }
    }

    if (showWeightSheet) {
        AddWeightSheet(
            onDismiss = { showWeightSheet = false },
            onSave    = { w, f, m, c ->
                vm.addBodyMetric(w, f, m, c)
                showWeightSheet = false
            }
        )
    }

    // PDF loading banner
    if (isPdfLoading || pdfResult != null || pdfError != null) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {  }  // handled via Snackbar approach below
    }

    // Exam detail sheet
    selectedExamDate?.let { date ->
        ExamDetailSheet(
            date   = date,
            exams  = allExams.filter { it.date == date },
            onDismiss = { selectedExamDate = null }
        )
    }

    // Weight history sheet
    if (showWeightHistory) {
        WeightHistorySheet(
            metrics   = metrics,
            onDelete  = { vm.deleteMetric(it) },
            onDismiss = { showWeightHistory = false }
        )
    }

    if (showApiKeySheet) {
        ApiKeySheet(
            currentKey = prefs?.anthropicApiKey ?: "",
            onDismiss  = { showApiKeySheet = false },
            onSave     = { key ->
                vm.saveApiKey(key)
                showApiKeySheet = false
            }
        )
    }

    if (showExamSheet) {
        AddExamSheet(
            onDismiss = { showExamSheet = false },
            onSave    = { date, grp, name, value, unit, min, max, refText, status ->
                vm.addLabExam(date, grp, name, value, unit, min, max, refText, status)
                showExamSheet = false
            }
        )
    }
}

@Composable
private fun WeightRow(metric: BodyMetricEntity, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("%.1f kg".format(metric.weightKg), fontWeight = FontWeight.Medium)
            Text(
                metric.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("pt","BR"))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        metric.bodyFatPercent?.let {
            Text("%.1f%% fat".format(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.DeleteOutline, "Excluir", Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
        }
    }
}

@Composable
private fun ExamRow(exam: LabExamEntity) {
    val statusColor = when (exam.status) {
        ExamStatus.NORMAL     -> StatusNormal
        ExamStatus.BORDERLINE -> StatusBorder
        ExamStatus.ALTERED    -> StatusAltered
    }
    // Compact layout: name + value on one line, ref below — works on any screen width
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                exam.examName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                "${"%.1f".format(exam.value)} ${exam.unit}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = statusColor
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                exam.examGroup,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (exam.referenceText.isNotBlank()) {
                Text(
                    "ref: ${exam.referenceText}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.ChevronRight, "Abrir", Modifier.size(20.dp))
        }
    }
}

// ─── Bottom Sheets ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWeightSheet(onDismiss: () -> Unit, onSave: (Float, Float?, Float?, Float?) -> Unit) {
    var weightText by remember { mutableStateOf("") }
    var fatText    by remember { mutableStateOf("") }
    var muscleText by remember { mutableStateOf("") }
    var waistText  by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Registrar peso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = weightText, onValueChange = { weightText = it },
                label = { Text("Peso (kg) *") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            OutlinedTextField(
                value = fatText, onValueChange = { fatText = it },
                label = { Text("% Gordura (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            OutlinedTextField(
                value = muscleText, onValueChange = { muscleText = it },
                label = { Text("Massa muscular kg (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            OutlinedTextField(
                value = waistText, onValueChange = { waistText = it },
                label = { Text("Cintura cm (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            Button(
                onClick = {
                    val w = weightText.toFloatOrNull() ?: return@Button
                    onSave(w, fatText.toFloatOrNull(), muscleText.toFloatOrNull(), waistText.toFloatOrNull())
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeySheet(currentKey: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(currentKey) }
    var visible by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Chave API Anthropic", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text("Insira sua chave de API da Anthropic para habilitar o FitPro AI (Claude). Obtenha em console.anthropic.com.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("sk-ant-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                    }
                }
            )
            Button(onClick = { onSave(key) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Salvar chave")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExamSheet(
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, Float, String, Float?, Float?, String, ExamStatus) -> Unit
) {
    var group    by remember { mutableStateOf("") }
    var name     by remember { mutableStateOf("") }
    var value    by remember { mutableStateOf("") }
    var unit     by remember { mutableStateOf("") }
    var refMin   by remember { mutableStateOf("") }
    var refMax   by remember { mutableStateOf("") }
    var refText  by remember { mutableStateOf("") }
    var status   by remember { mutableStateOf(ExamStatus.NORMAL) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Adicionar resultado de exame", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = group, onValueChange = { group = it },
                label = { Text("Grupo (ex: Lipídios, Tireoide)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nome do exame (ex: TGP/ALT)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("Valor") }, modifier = Modifier.weight(2f), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it },
                    label = { Text("Unidade") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = refMin, onValueChange = { refMin = it },
                    label = { Text("Ref. min") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(
                    value = refMax, onValueChange = { refMax = it },
                    label = { Text("Ref. max") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            }
            OutlinedTextField(
                value = refText, onValueChange = { refText = it },
                label = { Text("Texto de referência (ex: < 50 U/L)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            // Status
            Text("Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExamStatus.NORMAL to "Normal", ExamStatus.BORDERLINE to "Limítrofe",
                    ExamStatus.ALTERED to "Alterado").forEach { (s, lbl) ->
                    FilterChip(selected = status == s, onClick = { status = s },
                        label = { Text(lbl, fontSize = 12.sp) })
                }
            }
            Button(
                onClick = {
                    val v = value.toFloatOrNull() ?: return@Button
                    onSave(LocalDate.now(), group, name, v, unit, refMin.toFloatOrNull(), refMax.toFloatOrNull(), refText, status)
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar resultado") }
        }
    }
}

// ─── Exam Detail Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDetailSheet(
    date: java.time.LocalDate,
    exams: List<com.fitpro.data.local.entity.LabExamEntity>,
    onDismiss: () -> Unit
) {
    val grouped = exams.groupBy { it.examGroup }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    date.format(java.time.format.DateTimeFormatter.ofPattern(
                        "d 'de' MMMM 'de' yyyy", java.util.Locale("pt","BR"))),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("${exams.size} exames · ${exams.count { it.status != ExamStatus.NORMAL }} fora do intervalo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }
            grouped.entries.sortedBy { it.key }.forEach { (group, groupExams) ->
                item {
                    Text(group.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(groupExams.sortedBy { it.examName }) { exam ->
                    val statusColor = when (exam.status) {
                        ExamStatus.ALTERED    -> StatusAltered
                        ExamStatus.BORDERLINE -> StatusBorder
                        ExamStatus.NORMAL     -> StatusNormal
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusColor.copy(alpha = if (exam.status == ExamStatus.NORMAL) 0.04f else 0.08f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(exam.examName, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text(exam.referenceText, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${"%.1f".format(exam.value)} ${exam.unit}",
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = statusColor)
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = statusColor.copy(0.15f)) {
                                    Text(when (exam.status) {
                                            ExamStatus.NORMAL     -> "Normal"
                                            ExamStatus.BORDERLINE -> "Limítrofe"
                                            ExamStatus.ALTERED    -> "Alterado"
                                        },
                                        fontSize = 9.sp, color = statusColor,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Weight History Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightHistorySheet(
    metrics: List<com.fitpro.data.local.entity.BodyMetricEntity>,
    onDelete: (com.fitpro.data.local.entity.BodyMetricEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("Histórico Completo", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("${metrics.size} registros", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }
            items(metrics.sortedByDescending { it.date }) { m ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.date.format(java.time.format.DateTimeFormatter.ofPattern(
                                "d MMM yyyy", java.util.Locale("pt","BR"))),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${"%.1f".format(m.weightKg)} kg",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (m.bodyFatPercent != null)
                                    Text("${"%.1f".format(m.bodyFatPercent)}% gord.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (m.muscleMassKg != null)
                                    Text("${"%.1f".format(m.muscleMassKg)} kg musc.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (m.waistCm != null)
                                    Text("${"%.0f".format(m.waistCm)} cm cin.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDelete(m) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "Excluir", Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        }
                    }
                }
            }
        }
    }
}
