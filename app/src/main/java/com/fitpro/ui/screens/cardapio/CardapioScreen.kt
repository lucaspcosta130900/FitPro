package com.fitpro.ui.screens.cardapio

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.CardapioDao
import com.fitpro.data.local.dao.FoodItemDao
import com.fitpro.data.local.dao.FullMealTemplate
import com.fitpro.data.local.dao.MealEntryDao
import com.fitpro.data.local.dao.ShoppingDao
import com.fitpro.data.local.entity.*
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class)
@HiltViewModel
class CardapioViewModel @Inject constructor(
    private val cardapioDao: CardapioDao,
    private val mealEntryDao: MealEntryDao,
    private val foodItemDao: FoodItemDao,
    private val shoppingDao: ShoppingDao
) : ViewModel() {

    val templates: StateFlow<List<FullMealTemplate>> = cardapioDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val successMessage = MutableStateFlow<String?>(null)

    // Food search for template editor
    val foodQuery = MutableStateFlow("")
    val foodResults: StateFlow<List<FoodItemEntity>> = foodQuery
        .debounce(200)
        .flatMapLatest { q ->
            if (q.isBlank()) foodItemDao.getAllFoods().map { it.take(20) }
            else foodItemDao.searchFoods("%$q%")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun applyTemplateToday(template: FullMealTemplate) {
        viewModelScope.launch {
            val today = LocalDate.now()
            template.itemsWithFood.forEach { twf ->
                mealEntryDao.insertMeal(MealEntryEntity(
                    date = today, mealType = template.template.mealType,
                    foodItemId = twf.food.id, quantityG = twf.item.quantityG
                ))
            }
            val kcal = template.itemsWithFood.sumOf {
                (it.item.quantityG / 100f * it.food.caloriesPer100g).toDouble()
            }.toInt()
            successMessage.value = "${template.template.name} adicionado! ($kcal kcal)"
        }
    }

    fun saveTemplate(
        existingId: Long?,
        name: String,
        mealType: MealType,
        description: String,
        items: List<Pair<FoodItemEntity, Float>>
    ) {
        if (name.isBlank() || items.isEmpty()) return
        viewModelScope.launch {
            val id = if (existingId != null) {
                cardapioDao.deleteTemplateItems(existingId)
                cardapioDao.insertTemplate(MealTemplateEntity(
                    id = existingId, name = name, mealType = mealType,
                    description = description, isDefault = false))
            } else {
                cardapioDao.insertTemplate(MealTemplateEntity(
                    name = name, mealType = mealType,
                    description = description, isDefault = false))
            }
            cardapioDao.insertTemplateItems(items.map { (food, qty) ->
                MealTemplateItemEntity(templateId = id, foodItemId = food.id, quantityG = qty)
            })
            successMessage.value = if (existingId != null) "Template atualizado!" else "Template criado!"
        }
    }

    fun deleteTemplate(template: FullMealTemplate) {
        viewModelScope.launch { cardapioDao.deleteTemplate(template.template) }
    }

    fun addTemplateToMercado(template: FullMealTemplate, days: Int) {
        viewModelScope.launch {
            val now = LocalDate.now()
            template.itemsWithFood.forEach { twf ->
                val totalG = twf.item.quantityG * days
                val qtyStr = if (totalG >= 1000) "${"%.1f".format(totalG / 1000)} kg"
                             else "${"%.0f".format(totalG)} g"
                val cat = when {
                    twf.food.proteinPer100g > 15f -> ShoppingCategory.PROTEINAS
                    twf.food.carbsPer100g   > 20f -> ShoppingCategory.CARBOIDRATOS
                    twf.food.fatPer100g     > 20f -> ShoppingCategory.GORDURAS
                    else -> ShoppingCategory.LEGUMES_TEMPEROS
                }
                shoppingDao.insertItem(ShoppingItemEntity(
                    name = twf.food.name, category = cat, quantity = qtyStr,
                    estimatedPrice = estimatePrice(twf.food, totalG),
                    month = now.monthValue, year = now.year,
                    notes = "Do cardapio: ${template.template.name} ($days dias)"
                ))
            }
            successMessage.value = "Ingredientes de ${template.template.name} adicionados ao mercado ($days dias)!"
        }
    }

    private fun estimatePrice(food: FoodItemEntity, totalG: Float): Float {
        val pricePerKg = when {
            food.name.contains("frango", true) && food.name.contains("peito", true) -> 25f
            food.name.contains("frango", true) -> 17f
            food.name.contains("patinho", true) -> 50f
            food.name.contains("ovo", true) -> 18f
            food.name.contains("leite", true) -> 3.5f
            food.name.contains("arroz", true) -> 5f
            food.name.contains("aveia", true) -> 11f
            food.name.contains("macarrao", true) || food.name.contains("macarrão", true) -> 8f
            food.name.contains("banana", true) -> 4.5f
            food.name.contains("azeite", true) -> 35f
            food.name.contains("amendoim", true) -> 16f
            else -> 10f
        }
        return (totalG / 1000f) * pricePerKg
    }

    fun dismissMessage() { successMessage.value = null }
    fun setFoodQuery(q: String) { foodQuery.value = q }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardapioScreen(vm: CardapioViewModel = hiltViewModel()) {
    val templates   by vm.templates.collectAsStateWithLifecycle()
    val successMsg  by vm.successMessage.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showCreateSheet  by remember { mutableStateOf(false) }
    var editingTemplate  by remember { mutableStateOf<FullMealTemplate?>(null) }
    var deleteTarget     by remember { mutableStateOf<FullMealTemplate?>(null) }
    var mercadoTarget    by remember { mutableStateOf<FullMealTemplate?>(null) }

    val mealTypes  = MealType.entries.toList()
    val mealLabels = listOf("Café", "Almoço", "Lanche", "Jantar", "Ceia")
    val snackbar   = remember { SnackbarHostState() }

    LaunchedEffect(successMsg) {
        successMsg?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short); vm.dismissMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, "Criar template")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Meu Cardápio", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Toque em 'Usar Hoje' para lançar no diário de uma vez",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                mealLabels.forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(label, fontSize = 12.sp) })
                }
            }

            val filtered = templates.filter { it.template.mealType == mealTypes[selectedTab] }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                        Text("Nenhum template para ${mealLabels[selectedTab]}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = { showCreateSheet = true }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Criar template")
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.template.id }) { tmpl ->
                        TemplateCard(
                            template    = tmpl,
                            onUseToday  = { vm.applyTemplateToday(tmpl) },
                            onEdit      = { editingTemplate = tmpl },
                            onDelete    = { deleteTarget = tmpl },
                            onMercado   = { mercadoTarget = tmpl }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateEditTemplateSheet(
            existing    = null,
            initialType = mealTypes[selectedTab],
            vm          = vm,
            onDismiss   = { showCreateSheet = false }
        )
    }
    editingTemplate?.let { tmpl ->
        CreateEditTemplateSheet(
            existing    = tmpl,
            initialType = tmpl.template.mealType,
            vm          = vm,
            onDismiss   = { editingTemplate = null }
        )
    }
    deleteTarget?.let { tmpl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title  = { Text("Excluir template?") },
            text   = { Text("\"${tmpl.template.name}\" será removido permanentemente.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTemplate(tmpl); deleteTarget = null }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
    mercadoTarget?.let { tmpl ->
        MercadoDaysDialog(template = tmpl, vm = vm, onDismiss = { mercadoTarget = null })
    }
}

// ─── Template Card ────────────────────────────────────────────────────────────

@Composable
fun TemplateCard(
    template: FullMealTemplate,
    onUseToday: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMercado: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val totalKcal = template.itemsWithFood.sumOf { (it.item.quantityG / 100f * it.food.caloriesPer100g).toDouble() }.toFloat()
    val totalP    = template.itemsWithFood.sumOf { (it.item.quantityG / 100f * it.food.proteinPer100g).toDouble() }.toFloat()
    val totalC    = template.itemsWithFood.sumOf { (it.item.quantityG / 100f * it.food.carbsPer100g).toDouble() }.toFloat()
    val totalG    = template.itemsWithFood.sumOf { (it.item.quantityG / 100f * it.food.fatPer100g).toDouble() }.toFloat()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(template.template.name, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (template.template.description.isNotBlank())
                        Text(template.template.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                MacroChip("${totalKcal.toInt()} kcal", CalorieOrange)
                MacroChip("P ${"%.0f".format(totalP)}g", ProteinBlue)
                MacroChip("C ${"%.0f".format(totalC)}g", CarbAmber)
                MacroChip("G ${"%.0f".format(totalG)}g", FatPink)
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 6.dp))
                    template.itemsWithFood.forEach { twf ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(twf.food.name, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${"%.0f".format(twf.item.quantityG)}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onUseToday, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                    Icon(Icons.Outlined.AddCircle, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Usar Hoje", fontSize = 12.sp)
                }
                FilledTonalIconButton(onClick = onMercado, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ShoppingCart, "Adicionar ao mercado", Modifier.size(17.dp))
                }
                FilledTonalIconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Edit, "Editar", Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, "Excluir",
                        Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

// ─── Create / Edit Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEditTemplateSheet(
    existing: FullMealTemplate?,
    initialType: MealType,
    vm: CardapioViewModel,
    onDismiss: () -> Unit
) {
    val foodResults by vm.foodResults.collectAsStateWithLifecycle()
    val isEdit = existing != null

    var name        by remember { mutableStateOf(existing?.template?.name ?: "") }
    var description by remember { mutableStateOf(existing?.template?.description ?: "") }
    var mealType    by remember { mutableStateOf(existing?.template?.mealType ?: initialType) }
    var items       by remember {
        mutableStateOf(existing?.itemsWithFood?.map { Pair(it.food, it.item.quantityG) } ?: emptyList())
    }
    var foodSearch  by remember { mutableStateOf("") }
    var qtyInput    by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var showFoodSearch by remember { mutableStateOf(false) }

    LaunchedEffect(foodSearch) { vm.setFoodQuery(foodSearch) }

    val mealLabels = listOf("Café", "Almoço", "Lanche", "Jantar", "Ceia")

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(if (isEdit) "Editar Template" else "Novo Template",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
            item {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nome *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Descrição (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            // Meal type selector
            item {
                Text("Tipo de Refeição", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MealType.entries.forEachIndexed { i, mt ->
                        FilterChip(selected = mealType == mt, onClick = { mealType = mt },
                            label = { Text(mealLabels.getOrElse(i) { mt.name }, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f))
                    }
                }
            }
            // Items list
            item {
                Text("Alimentos (${items.size})", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(items) { (food, qty) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(food.name, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${"%.0f".format(qty)}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { items = items.filter { it.first.id != food.id } },
                        modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, "Remover", Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                }
            }
            // Add food section
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Adicionar alimento", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = foodSearch, onValueChange = { foodSearch = it; showFoodSearch = true },
                    label = { Text("Buscar alimento…") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (foodSearch.isNotBlank())
                            IconButton(onClick = { foodSearch = ""; selectedFood = null; showFoodSearch = false }) {
                                Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                            }
                    }
                )
            }
            if (showFoodSearch && foodResults.isNotEmpty()) {
                items(foodResults.take(6)) { food ->
                    val isSelected = selectedFood?.id == food.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedFood = food; foodSearch = food.name; showFoodSearch = false
                        }
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(food.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${food.caloriesPer100g.toInt()} kcal/100g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = qtyInput, onValueChange = { qtyInput = it },
                        label = { Text("Qtd (g)") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Button(
                        onClick = {
                            val food = selectedFood
                            val qty  = qtyInput.toFloatOrNull()
                            if (food != null && qty != null && qty > 0) {
                                items = items.filter { it.first.id != food.id } + Pair(food, qty)
                                selectedFood = null; foodSearch = ""; qtyInput = ""; showFoodSearch = false
                            }
                        },
                        enabled = selectedFood != null && qtyInput.toFloatOrNull() != null
                    ) { Text("Adicionar") }
                }
            }
            item {
                Button(
                    onClick = {
                        vm.saveTemplate(existing?.template?.id, name, mealType, description, items)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotBlank() && items.isNotEmpty()
                ) { Text(if (isEdit) "Salvar alterações" else "Criar template") }
            }
        }
    }
}

// ─── Mercado Days Dialog ──────────────────────────────────────────────────────

@Composable
private fun MercadoDaysDialog(
    template: FullMealTemplate,
    vm: CardapioViewModel,
    onDismiss: () -> Unit
) {
    var days by remember { mutableStateOf("7") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Outlined.ShoppingCart, null) },
        title = { Text("Adicionar ao Mercado") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calcular ingredientes de \"${template.template.name}\" para quantos dias?",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Itens: ${template.itemsWithFood.joinToString(", ") {
                    "${it.food.name.split(" ").first()} ${it.item.quantityG.toInt()}g"
                }}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = days, onValueChange = { days = it },
                    label = { Text("Número de dias") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = days.toIntOrNull() ?: 7
                vm.addTemplateToMercado(template, d)
                onDismiss()
            }) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
