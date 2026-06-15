package com.fitpro.ui.screens.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.fitpro.data.local.dao.FullMealTemplate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.collectAsState
import com.fitpro.data.local.dao.MealEntryWithFood
import com.fitpro.data.local.dao.CardapioDao
import com.fitpro.data.local.entity.*
import com.fitpro.data.repository.FoodRepository
import com.fitpro.ui.components.MacroSummaryBar
import com.fitpro.ui.components.SectionCard
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val cardapioDao: CardapioDao
) : ViewModel() {

    val templatesForType: (MealType) -> Flow<List<FullMealTemplate>> = { mealType ->
        cardapioDao.getTemplatesByType(mealType)
    }

    fun applyTemplate(template: FullMealTemplate, targetDate: LocalDate = selectedDate.value) {
        viewModelScope.launch {
            template.itemsWithFood.forEach { twf ->
                foodRepo.addMealEntry(
                    MealEntryEntity(
                        date = targetDate,
                        mealType = template.template.mealType,
                        foodItemId = twf.food.id,
                        quantityG = twf.item.quantityG
                    )
                )
            }
        }
    }

    val selectedDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val mealsForDate: StateFlow<List<MealEntryWithFood>> = selectedDate
        .flatMapLatest { foodRepo.getMealsForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goal: StateFlow<UserGoalEntity?> = foodRepo.getGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todaySummary: StateFlow<Triple<Float, Float, Float>> = mealsForDate.map { meals ->
        Triple(
            meals.sumOf { (it.entry.quantityG / 100f * it.food.caloriesPer100g).toDouble() }.toFloat(),
            meals.sumOf { (it.entry.quantityG / 100f * it.food.proteinPer100g).toDouble() }.toFloat(),
            meals.sumOf { (it.entry.quantityG / 100f * it.food.carbsPer100g).toDouble() }.toFloat()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0f, 0f, 0f))

    val searchQuery = MutableStateFlow("")

    // Show all foods when query blank, search results otherwise
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<FoodItemEntity>> = searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            if (query.isBlank()) foodRepo.getAllFoods().map { it.take(40) }
            else foodRepo.searchFoods(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMealEntry(id: Long) {
        viewModelScope.launch { foodRepo.deleteMealEntry(id) }
    }

    fun addMealEntry(food: FoodItemEntity, quantityG: Float, mealType: MealType) {
        viewModelScope.launch {
            foodRepo.addMealEntry(
                MealEntryEntity(
                    date = selectedDate.value,
                    mealType = mealType,
                    foodItemId = food.id,
                    quantityG = quantityG
                )
            )
        }
    }

    fun changeDate(delta: Long) {
        selectedDate.value = selectedDate.value.plusDays(delta)
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onNavigateToCardapio: () -> Unit = {},
    vm: DiaryViewModel = hiltViewModel()
) {
    val date    by vm.selectedDate.collectAsStateWithLifecycle()
    val meals   by vm.mealsForDate.collectAsStateWithLifecycle()
    val goal    by vm.goal.collectAsStateWithLifecycle()
    val summary by vm.todaySummary.collectAsStateWithLifecycle()
    val (totalCalories, totalProtein, totalCarbs) = summary

    var showAddSheet      by remember { mutableStateOf(false) }
    var showCardapioSheet by remember { mutableStateOf(false) }
    var addForMealType by remember { mutableStateOf(MealType.LUNCH) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date selector
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.changeDate(-1) }) {
                    Icon(Icons.Outlined.ChevronLeft, "Dia anterior")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (date == LocalDate.now()) "Hoje" else
                            date.format(DateTimeFormatter.ofPattern("d MMM", Locale("pt","BR"))),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE", Locale("pt","BR"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.changeDate(1) }, enabled = date < LocalDate.now()) {
                    Icon(Icons.Outlined.ChevronRight, "Próximo dia")
                }
            }
        }

        // Macro summary
        goal?.let { g ->
            item {
                SectionCard(title = "Resumo do dia") {
                    MacroSummaryBar(
                        calories     = totalCalories,
                        protein      = totalProtein,
                        carbs        = totalCarbs,
                        fat          = meals.sumOf { (it.entry.quantityG / 100f * it.food.fatPer100g).toDouble() }.toFloat(),
                        goalCalories = g.caloriesKcal,
                        goalProtein  = g.proteinG,
                        goalCarbs    = g.carbsG,
                        goalFat      = g.fatG
                    )
                }
            }
        }

        // Meals — clicking anywhere on the card opens the add sheet
        MealType.entries.forEach { mealType ->
            val mealsOfType = meals.filter { it.entry.mealType == mealType }
            val mealLabel   = mealType.displayName()
            val mealCalories = mealsOfType.sumOf {
                (it.entry.quantityG / 100f * it.food.caloriesPer100g).toDouble()
            }.toFloat()

            item(key = mealType.name) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Clicking anywhere on the card opens the add sheet
                        .clickable {
                            addForMealType = mealType
                            showAddSheet   = true
                        },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Card header
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    mealType.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    mealLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (mealCalories > 0f) {
                                    Text(
                                        "%.0f kcal".format(mealCalories),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Quick-add from cardápio
                                IconButton(
                                    onClick = {
                                        addForMealType = mealType
                                        showCardapioSheet = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.RestaurantMenu,
                                        contentDescription = "Usar cardapio",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.secondary)
                                }
                                Icon(
                                    Icons.Outlined.AddCircleOutline,
                                    contentDescription = "Adicionar",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        if (mealsOfType.isEmpty()) {
                            Text(
                                "Toque para adicionar alimentos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                            )
                        } else {
                            mealsOfType.forEachIndexed { index, entry ->
                                MealRow(entry = entry, onDelete = { vm.deleteMealEntry(entry.entry.id) })
                                if (index < mealsOfType.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showCardapioSheet) {
        CardapioQuickSheet(
            mealType    = addForMealType,
            getTemplates = { vm.templatesForType(it) },
            onDismiss   = { showCardapioSheet = false },
            onApply     = { template ->
                vm.applyTemplate(template)
                showCardapioSheet = false
            },
            onGoToCardapio = onNavigateToCardapio
        )
    }

    if (showAddSheet) {
        AddFoodSheet(
            mealType      = addForMealType,
            searchResults = vm.searchResults.collectAsStateWithLifecycle().value,
            onQueryChange = { vm.searchQuery.value = it },
            onDismiss     = {
                showAddSheet = false
                vm.searchQuery.value = ""
            },
            onAddFood = { food, qty ->
                vm.addMealEntry(food, qty, addForMealType)
                showAddSheet = false
                vm.searchQuery.value = ""
            }
        )
    }
}

// ─── Meal row ─────────────────────────────────────────────────────────────────

@Composable
private fun MealRow(entry: MealEntryWithFood, onDelete: () -> Unit) {
    val cals    = entry.entry.quantityG / 100f * entry.food.caloriesPer100g
    val protein = entry.entry.quantityG / 100f * entry.food.proteinPer100g
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.food.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("%.0fg".format(entry.entry.quantityG), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.0f kcal".format(cals), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("P: %.1fg".format(protein), style = MaterialTheme.typography.bodySmall,
                    color = ProteinBlue.copy(0.8f))
            }
        }
        // Stop click propagation on delete button so card click doesn't also fire
        IconButton(
            onClick  = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Outlined.Close, "Remover", Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}

// ─── Add Food Bottom Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodSheet(
    mealType: MealType,
    searchResults: List<FoodItemEntity>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAddFood: (FoodItemEntity, Float) -> Unit
) {
    var query        by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var quantityText by remember { mutableStateOf("100") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Adicionar em ${mealType.displayName()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            // Search field
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it; onQueryChange(it) },
                label         = { Text("Buscar alimento") },
                leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                trailingIcon  = if (query.isNotEmpty()) {{
                    IconButton(onClick = { query = ""; onQueryChange("") }) {
                        Icon(Icons.Outlined.Clear, "Limpar")
                    }
                }} else null,
                modifier  = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (selectedFood != null) {
                // ── Food selected: show macro preview + quantity input ──────────
                val food = selectedFood!!
                val qty  = quantityText.toFloatOrNull() ?: 100f

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(food.name, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f))
                            IconButton(
                                onClick  = { selectedFood = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.Close, "Remover seleção",
                                    Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MacroChip("Cal",  "%.0f".format(qty / 100 * food.caloriesPer100g), CalorieOrange)
                            MacroChip("Prot", "%.1fg".format(qty / 100 * food.proteinPer100g), ProteinBlue)
                            MacroChip("Carb", "%.1fg".format(qty / 100 * food.carbsPer100g),   CarbAmber)
                            MacroChip("Gord", "%.1fg".format(qty / 100 * food.fatPer100g),     FatPink)
                        }
                    }
                }

                OutlinedTextField(
                    value         = quantityText,
                    onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) quantityText = it },
                    label         = { Text("Quantidade (g)") },
                    suffix        = { Text("g") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                Button(
                    onClick  = { onAddFood(food, quantityText.toFloatOrNull() ?: 100f) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Adicionar à refeição")
                }

            } else {
                // ── Food list ──────────────────────────────────────────────────
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    Text(
                        if (query.isBlank()) "Alimentos cadastrados" else "${searchResults.size} resultados",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    searchResults.take(12).forEachIndexed { index, food ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFood = food
                                    quantityText = food.servingSizeG.toInt().toString()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment  = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(food.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text(
                                    "%.0f kcal · P %.0fg · C %.0fg · G %.0fg — por 100g".format(
                                        food.caloriesPer100g, food.proteinPer100g,
                                        food.carbsPer100g, food.fatPer100g),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Outlined.Add, "Selecionar",
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        if (index < searchResults.take(12).lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MacroChip(label: String, value: String,
                      color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun MealType.displayName() = when (this) {
    MealType.BREAKFAST -> "Café da manhã"
    MealType.LUNCH     -> "Almoço"
    MealType.SNACK     -> "Lanche"
    MealType.DINNER    -> "Jantar"
    MealType.SUPPER    -> "Ceia"
}

private fun MealType.icon() = when (this) {
    MealType.BREAKFAST -> Icons.Outlined.WbSunny
    MealType.LUNCH     -> Icons.Outlined.LunchDining
    MealType.SNACK     -> Icons.Outlined.Coffee
    MealType.DINNER    -> Icons.Outlined.DinnerDining
    MealType.SUPPER    -> Icons.Outlined.Nightlight
}

// ─── Cardápio Quick-Apply Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardapioQuickSheet(
    mealType: MealType,
    getTemplates: (MealType) -> Flow<List<FullMealTemplate>>,
    onDismiss: () -> Unit,
    onApply: (FullMealTemplate) -> Unit,
    onGoToCardapio: () -> Unit
) {
    val templates by getTemplates(mealType).collectAsState(initial = emptyList())
    val mealLabel = mealType.displayName()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Meu Cardapio — $mealLabel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium)
                TextButton(onClick = { onDismiss(); onGoToCardapio() }) {
                    Text("Ver todos")
                }
            }

            if (templates.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                    Text("Nenhum template para $mealLabel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { onDismiss(); onGoToCardapio() },
                        shape   = RoundedCornerShape(12.dp)
                    ) { Text("Criar template no Cardapio") }
                }
            } else {
                templates.forEach { template ->
                    val totalKcal = template.itemsWithFood.sumOf {
                        (it.item.quantityG / 100f * it.food.caloriesPer100g).toDouble()
                    }.toFloat()
                    val totalP = template.itemsWithFood.sumOf {
                        (it.item.quantityG / 100f * it.food.proteinPer100g).toDouble()
                    }.toFloat()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(template.template.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text("${template.itemsWithFood.size} itens · " +
                                     "${"%.0f".format(totalKcal)} kcal · " +
                                     "P ${"%.0f".format(totalP)}g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // Mini food list
                                Text(
                                    template.itemsWithFood.joinToString(" · ") {
                                        "${it.food.name.split(" ").first()} ${"%.0f".format(it.item.quantityG)}g"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onApply(template) },
                                shape   = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Usar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
