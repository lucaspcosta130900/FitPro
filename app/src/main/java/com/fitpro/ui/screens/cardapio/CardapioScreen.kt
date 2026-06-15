package com.fitpro.ui.screens.cardapio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.FullMealTemplate
import com.fitpro.data.local.dao.MealEntryDao
import com.fitpro.data.local.dao.CardapioDao
import com.fitpro.data.local.entity.*
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class CardapioViewModel @Inject constructor(
    private val cardapioDao: CardapioDao,
    private val mealEntryDao: MealEntryDao
) : ViewModel() {

    val templates: StateFlow<List<FullMealTemplate>> = cardapioDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val successMessage = MutableStateFlow<String?>(null)

    fun applyTemplateToday(template: FullMealTemplate) {
        viewModelScope.launch {
            val today = LocalDate.now()
            template.itemsWithFood.forEach { twf ->
                mealEntryDao.insertMeal(
                    MealEntryEntity(
                        date = today,
                        mealType = template.template.mealType,
                        foodItemId = twf.food.id,
                        quantityG = twf.item.quantityG
                    )
                )
            }
            val kcal = template.itemsWithFood.sumOf {
                (it.item.quantityG / 100f * it.food.caloriesPer100g).toDouble()
            }.toFloat()
            successMessage.value = "${template.template.name} adicionado ao diario! (${kcal.toInt()} kcal)"
        }
    }

    fun deleteTemplate(template: FullMealTemplate) {
        viewModelScope.launch { cardapioDao.deleteTemplate(template.template) }
    }

    fun dismissMessage() { successMessage.value = null }

    fun createTemplate(
        name: String, mealType: MealType, description: String,
        items: List<Pair<Long, Float>>  // foodId to quantityG
    ) {
        viewModelScope.launch {
            val id = cardapioDao.insertTemplate(
                MealTemplateEntity(name = name, mealType = mealType, description = description)
            )
            cardapioDao.insertTemplateItems(
                items.map { (foodId, qty) ->
                    MealTemplateItemEntity(templateId = id, foodItemId = foodId, quantityG = qty)
                }
            )
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardapioScreen(vm: CardapioViewModel = hiltViewModel()) {
    val templates  by vm.templates.collectAsStateWithLifecycle()
    val successMsg by vm.successMessage.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    val mealTypes = MealType.entries.toList()
    val mealLabels = listOf("Cafe", "Almoco", "Lanche", "Jantar", "Ceia")

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(successMsg) {
        successMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.dismissMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Header
            Column(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp)
            ) {
                Text("Meu Cardapio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Toque em 'Usar Hoje' para adicionar ao diario de uma vez",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.75f))
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                mealLabels.forEachIndexed { i, label ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Template list for selected meal type
            val currentType = mealTypes[selectedTab]
            val filtered = templates.filter { it.template.mealType == currentType }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Text("Nenhum template para ${mealLabels[selectedTab]}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Crie um template para adicionar refeicoes rapido",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.template.id }) { template ->
                        TemplateCard(
                            template   = template,
                            onUseToday = { vm.applyTemplateToday(template) },
                            onDelete   = { vm.deleteTemplate(template) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ─── Template Card ────────────────────────────────────────────────────────────

@Composable
fun TemplateCard(
    template: FullMealTemplate,
    onUseToday: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val totalKcal = template.itemsWithFood.sumOf {
        (it.item.quantityG / 100f * it.food.caloriesPer100g).toDouble()
    }.toFloat()
    val totalP = template.itemsWithFood.sumOf {
        (it.item.quantityG / 100f * it.food.proteinPer100g).toDouble()
    }.toFloat()
    val totalC = template.itemsWithFood.sumOf {
        (it.item.quantityG / 100f * it.food.carbsPer100g).toDouble()
    }.toFloat()
    val totalG = template.itemsWithFood.sumOf {
        (it.item.quantityG / 100f * it.food.fatPer100g).toDouble()
    }.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.RestaurantMenu, null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.template.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (template.template.description.isNotBlank()) {
                        Text(template.template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Macro summary chips
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroChip("${totalKcal.toInt()} kcal", CalorieOrange)
                MacroChip("P ${"%.0f".format(totalP)}g", ProteinBlue)
                MacroChip("C ${"%.0f".format(totalC)}g", CarbAmber)
                MacroChip("G ${"%.0f".format(totalG)}g", FatPink)
            }

            // Expanded: food list
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    template.itemsWithFood.forEach { twf ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(twf.food.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            Text("${"%.0f".format(twf.item.quantityG)}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Action buttons
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Use today — PRIMARY action
                Button(
                    onClick  = onUseToday,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.AddCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Usar Hoje", fontSize = 13.sp)
                }

                if (!template.template.isDefault) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, "Excluir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(label, fontSize = 10.sp, color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}
