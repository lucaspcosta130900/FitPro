package com.fitpro.ui.screens.alimentos

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.FoodItemDao
import com.fitpro.data.local.entity.FoodItemEntity
import com.fitpro.data.remote.NutritionDto
import com.fitpro.data.repository.AiRepository
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class)
@HiltViewModel
class AlimentosViewModel @Inject constructor(
    private val foodDao: FoodItemDao,
    private val aiRepo: AiRepository,
    private val prefsRepo: com.fitpro.data.preferences.UserPreferencesRepository
) : ViewModel() {

    val apiKey: StateFlow<String> = prefsRepo.userPreferences
        .map { it?.anthropicApiKey ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")


    val searchQuery = MutableStateFlow("")
    val showCustomOnly = MutableStateFlow(false)

    val foods: StateFlow<List<FoodItemEntity>> = combine(
        searchQuery.debounce(200),
        showCustomOnly,
        foodDao.getAllFoods()
    ) { query, customOnly, all ->
        all.filter { food ->
            val matchesSearch = query.isBlank() || food.name.contains(query, ignoreCase = true) ||
                                food.brand.contains(query, ignoreCase = true)
            val matchesFilter = !customOnly || food.isCustom
            matchesSearch && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI label analysis state
    val isAnalyzing  = MutableStateFlow(false)
    val analysisError = MutableStateFlow<String?>(null)
    val analysisResult = MutableStateFlow<NutritionDto?>(null)

    fun saveFood(existing: FoodItemEntity?, form: FoodForm) {
        viewModelScope.launch {
            val entity = FoodItemEntity(
                id             = existing?.id ?: 0,
                name           = form.name.trim(),
                brand          = form.brand.trim(),
                caloriesPer100g = form.calories,
                proteinPer100g  = form.protein,
                carbsPer100g    = form.carbs,
                fatPer100g      = form.fat,
                fiberPer100g    = form.fiber,
                servingSizeG    = form.servingSize.takeIf { it > 0f },
                isCustom        = true
            )
            if (existing != null) foodDao.updateFood(entity) else foodDao.insertFood(entity)
        }
    }

    fun deleteFood(food: FoodItemEntity) {
        viewModelScope.launch { foodDao.deleteFood(food) }
    }

    fun analyzeLabel(context: Context, uri: Uri, apiKey: String) {
        if (apiKey.isBlank()) { analysisError.value = "Configure sua chave API primeiro"; return }
        viewModelScope.launch {
            isAnalyzing.value = true
            analysisError.value = null
            analysisResult.value = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Não foi possível ler a imagem")
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                analysisResult.value = aiRepo.analyzeNutritionalLabel(b64, mimeType, apiKey)
            } catch (e: Exception) {
                analysisError.value = "Erro: ${e.message}"
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    fun clearAnalysis() { analysisResult.value = null; analysisError.value = null }
    fun setQuery(q: String) { searchQuery.value = q }
    fun toggleCustomOnly() { showCustomOnly.value = !showCustomOnly.value }
}

data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val calories: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
    val servingSize: Float = 0f
)

fun FoodItemEntity.toForm() = FoodForm(
    name = name, brand = brand,
    calories = caloriesPer100g, protein = proteinPer100g,
    carbs = carbsPer100g, fat = fatPer100g,
    fiber = fiberPer100g, servingSize = servingSizeG ?: 0f
)

fun NutritionDto.toForm() = FoodForm(
    name = name, brand = brand,
    calories = calories, protein = protein,
    carbs = carbs, fat = fat,
    fiber = fiber, servingSize = servingSizeG ?: 0f
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlimentosScreen(vm: AlimentosViewModel = hiltViewModel()) {
    val context       = LocalContext.current
    val apiKey        by vm.apiKey.collectAsStateWithLifecycle()
    val foods         by vm.foods.collectAsStateWithLifecycle()
    val query         by vm.searchQuery.collectAsStateWithLifecycle()
    val customOnly    by vm.showCustomOnly.collectAsStateWithLifecycle()
    val isAnalyzing   by vm.isAnalyzing.collectAsStateWithLifecycle()
    val analysisResult by vm.analysisResult.collectAsStateWithLifecycle()
    val analysisError  by vm.analysisError.collectAsStateWithLifecycle()

    var showForm      by remember { mutableStateOf(false) }
    var editTarget    by remember { mutableStateOf<FoodItemEntity?>(null) }
    var deleteTarget  by remember { mutableStateOf<FoodItemEntity?>(null) }
    var prefillForm   by remember { mutableStateOf<FoodForm?>(null) }
    var showAiSheet   by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(analysisResult) {
        analysisResult?.let {
            prefillForm = it.toForm()
            vm.clearAnalysis()
            showAiSheet = false
            showForm = true
        }
    }
    LaunchedEffect(analysisError) {
        analysisError?.let { snackbar.showSnackbar(it) }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.analyzeLabel(context, uri, apiKey)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(
                    onClick = { showAiSheet = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    if (isAnalyzing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.DocumentScanner, "Escanear tabela nutricional")
                }
                FloatingActionButton(onClick = {
                    editTarget = null; prefillForm = null; showForm = true
                }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Outlined.Add, "Novo alimento")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Header
            Column(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp)) {
                Text("Alimentos", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${foods.size} itens · Toque em 📷 para escanear tabela nutricional",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
            }

            // Search + filter
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.setQuery(it) },
                    placeholder = { Text("Buscar alimento…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotBlank())
                            IconButton(onClick = { vm.setQuery("") }, Modifier.size(20.dp)) {
                                Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                            }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = customOnly,
                    onClick  = { vm.toggleCustomOnly() },
                    label    = { Text("Meus", fontSize = 11.sp) }
                )
            }

            if (foods.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.NoFood, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                        Text(if (query.isBlank()) "Nenhum alimento encontrado"
                             else "Nenhum resultado para \"$query\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(
                    horizontal = 12.dp, bottom = 100.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(foods, key = { it.id }) { food ->
                        FoodCard(
                            food     = food,
                            onEdit   = { editTarget = food; prefillForm = food.toForm(); showForm = true },
                            onDelete = { if (food.isCustom) deleteTarget = food }
                        )
                    }
                }
            }
        }
    }

    // Form sheet
    if (showForm) {
        FoodFormSheet(
            initial   = prefillForm ?: editTarget?.toForm() ?: FoodForm(),
            isEdit    = editTarget != null,
            onDismiss = { showForm = false; editTarget = null; prefillForm = null },
            onSave    = { form ->
                vm.saveFood(editTarget, form)
                showForm = false; editTarget = null; prefillForm = null
            }
        )
    }

    // AI label sheet
    if (showAiSheet) {
        AiLabelSheet(
            isAnalyzing  = isAnalyzing,
            onPickImage  = { imagePicker.launch("image/*") },
            onDismiss    = { showAiSheet = false }
        )
    }

    // Delete confirmation
    deleteTarget?.let { food ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title  = { Text("Excluir alimento?") },
            text   = { Text("\"${food.name}\" será removido do seu banco de dados.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFood(food); deleteTarget = null }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

// ─── Food Card ────────────────────────────────────────────────────────────────

@Composable
private fun FoodCard(
    food: FoodItemEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(food.name, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (food.isCustom) Surface(shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Meu", fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                if (food.brand.isNotBlank())
                    Text(food.brand, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroTag("${food.caloriesPer100g.toInt()} kcal", CalorieOrange)
                    MacroTag("P ${"%.1f".format(food.proteinPer100g)}g", ProteinBlue)
                    MacroTag("C ${"%.1f".format(food.carbsPer100g)}g", CarbAmber)
                    MacroTag("G ${"%.1f".format(food.fatPer100g)}g", FatPink)
                }
                Text("por 100g", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit, Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, "Editar", Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
                if (food.isCustom) {
                    IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, "Excluir", Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
}

// ─── Food Form Sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodFormSheet(
    initial: FoodForm,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (FoodForm) -> Unit
) {
    var name        by remember(initial) { mutableStateOf(initial.name) }
    var brand       by remember(initial) { mutableStateOf(initial.brand) }
    var calories    by remember(initial) { mutableStateOf(initial.calories.let { if (it > 0) "%.1f".format(it) else "" }) }
    var protein     by remember(initial) { mutableStateOf(initial.protein.let { if (it > 0) "%.1f".format(it) else "" }) }
    var carbs       by remember(initial) { mutableStateOf(initial.carbs.let { if (it > 0) "%.1f".format(it) else "" }) }
    var fat         by remember(initial) { mutableStateOf(initial.fat.let { if (it > 0) "%.1f".format(it) else "" }) }
    var fiber       by remember(initial) { mutableStateOf(initial.fiber.let { if (it > 0) "%.1f".format(it) else "" }) }
    var servingSize by remember(initial) { mutableStateOf(initial.servingSize.let { if (it > 0) "%.0f".format(it) else "" }) }

    val isValid = name.isNotBlank() && calories.toFloatOrNull() != null

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(if (isEdit) "Editar Alimento" else "Novo Alimento",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                if (!isEdit)
                    Text("Valores por 100g", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nome *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                OutlinedTextField(value = brand, onValueChange = { brand = it },
                    label = { Text("Marca (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Text("Informação Nutricional por 100g *",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutriField("Calorias (kcal)", calories, Modifier.weight(1f)) { calories = it }
                    NutriField("Proteína (g)", protein, Modifier.weight(1f)) { protein = it }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutriField("Carboidratos (g)", carbs, Modifier.weight(1f)) { carbs = it }
                    NutriField("Gorduras (g)", fat, Modifier.weight(1f)) { fat = it }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutriField("Fibras (g)", fiber, Modifier.weight(1f)) { fiber = it }
                    NutriField("Porção padrão (g)", servingSize, Modifier.weight(1f)) { servingSize = it }
                }
            }
            // Live preview
            if (calories.toFloatOrNull() != null) {
                item {
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            PreviewMacro("Kcal", calories, CalorieOrange)
                            PreviewMacro("Prot", protein, ProteinBlue)
                            PreviewMacro("Carb", carbs, CarbAmber)
                            PreviewMacro("Gord", fat, FatPink)
                            if (fiber.toFloatOrNull() != null && (fiber.toFloatOrNull() ?: 0f) > 0)
                                PreviewMacro("Fibra", fiber, StatusNormal)
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        onSave(FoodForm(
                            name = name, brand = brand,
                            calories    = calories.toFloatOrNull() ?: 0f,
                            protein     = protein.toFloatOrNull() ?: 0f,
                            carbs       = carbs.toFloatOrNull() ?: 0f,
                            fat         = fat.toFloatOrNull() ?: 0f,
                            fiber       = fiber.toFloatOrNull() ?: 0f,
                            servingSize = servingSize.toFloatOrNull() ?: 0f
                        ))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isValid
                ) { Text(if (isEdit) "Salvar alterações" else "Cadastrar alimento") }
            }
        }
    }
}

@Composable
private fun NutriField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun PreviewMacro(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${"%.1f".format(value.toFloatOrNull() ?: 0f)}", fontWeight = FontWeight.Bold,
            color = color, fontSize = 13.sp)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ─── AI Label Analysis Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiLabelSheet(
    isAnalyzing: Boolean,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Escanear Tabela Nutricional",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text("Fotografe ou selecione uma imagem da tabela nutricional do produto. " +
                 "O Claude AI vai extrair automaticamente as calorias e macros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (isAnalyzing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Analisando com Claude AI…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Selecionar imagem da galeria")
                }
                Text("Formatos suportados: JPG, PNG, WebP · Máx. 5MB",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
