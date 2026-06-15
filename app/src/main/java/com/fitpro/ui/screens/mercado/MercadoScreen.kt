package com.fitpro.ui.screens.mercado

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.ShoppingDao
import com.fitpro.data.local.entity.ShoppingCategory
import com.fitpro.data.local.entity.ShoppingItemEntity
import com.fitpro.ui.theme.StatusNormal
import com.fitpro.ui.theme.StatusAltered
import com.fitpro.ui.theme.StatusBorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MercadoViewModel @Inject constructor(
    private val shoppingDao: ShoppingDao
) : ViewModel() {

    private val now = LocalDate.now()
    val month = now.monthValue
    val year  = now.year

    val items: StateFlow<List<ShoppingItemEntity>> =
        shoppingDao.getItemsForMonth(month, year)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalEstimated: StateFlow<Float> =
        shoppingDao.getTotalEstimated(month, year)
            .map { it ?: 0f }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val totalPurchased: StateFlow<Float> =
        shoppingDao.getTotalPurchased(month, year)
            .map { it ?: 0f }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val budget = 800f

    fun togglePurchased(item: ShoppingItemEntity) {
        viewModelScope.launch {
            shoppingDao.setItemPurchased(item.id, !item.isPurchased)
        }
    }

    fun deleteItem(item: ShoppingItemEntity) {
        viewModelScope.launch { shoppingDao.deleteItem(item) }
    }

    fun addItem(
        name: String, category: ShoppingCategory,
        quantity: String, price: Float, notes: String
    ) {
        viewModelScope.launch {
            shoppingDao.insertItem(
                ShoppingItemEntity(
                    name = name, category = category, quantity = quantity,
                    estimatedPrice = price, month = month, year = year, notes = notes
                )
            )
        }
    }

    fun resetAll() {
        viewModelScope.launch { shoppingDao.resetPurchased(month, year) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercadoScreen(vm: MercadoViewModel = hiltViewModel()) {
    val items         by vm.items.collectAsStateWithLifecycle()
    val totalEst      by vm.totalEstimated.collectAsStateWithLifecycle()
    val totalPurchased by vm.totalPurchased.collectAsStateWithLifecycle()

    var showAddSheet  by remember { mutableStateOf(false) }
    var showResetDlg  by remember { mutableStateOf(false) }

    val monthLabel = LocalDate.of(vm.year, vm.month, 1)
        .format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt","BR")))
        .replaceFirstChar { it.uppercase() }

    val groupedItems = items.groupBy { it.category }
    val purchasedCount = items.count { it.isPurchased }
    val budgetProgress = (totalPurchased / vm.budget).coerceIn(0f, 1f)
    val budgetColor = when {
        budgetProgress > 0.9f -> StatusAltered
        budgetProgress > 0.7f -> StatusBorder
        else -> StatusNormal
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Outlined.Add, "Adicionar item") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {

            // ── Budget header ─────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Mercado — $monthLabel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("$purchasedCount de ${items.size} itens comprados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.75f))
                        }
                        IconButton(onClick = { showResetDlg = true }) {
                            Icon(Icons.Outlined.RestartAlt, "Resetar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Budget progress
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gasto: R$ ${"%.2f".format(totalPurchased)}",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Orçamento: R$ ${"%.0f".format(vm.budget)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f))
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { budgetProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color    = budgetColor,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.2f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimado: R$ ${"%.2f".format(totalEst)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        val remaining = vm.budget - totalPurchased
                        Text("Restante: R$ ${"%.2f".format(remaining)}",
                            fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = if (remaining < 0) StatusAltered
                                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(0.9f))
                    }
                }
            }

            // ── Items by category ─────────────────────────────────────────
            ShoppingCategory.entries.forEach { cat ->
                val catItems = groupedItems[cat] ?: return@forEach
                if (catItems.isEmpty()) return@forEach

                val catTotal = catItems.sumOf { it.estimatedPrice.toDouble() }.toFloat()
                val catBought = catItems.count { it.isPurchased }

                item(key = cat.name + "_header") {
                    CategoryHeader(
                        category   = cat,
                        itemCount  = catItems.size,
                        boughtCount= catBought,
                        total      = catTotal
                    )
                }

                items(catItems, key = { it.id }) { shopItem ->
                    ShoppingItemRow(
                        item       = shopItem,
                        onToggle   = { vm.togglePurchased(shopItem) },
                        onDelete   = { vm.deleteItem(shopItem) }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddShoppingItemSheet(
            onDismiss = { showAddSheet = false },
            onSave    = { name, cat, qty, price, notes ->
                vm.addItem(name, cat, qty, price, notes)
                showAddSheet = false
            }
        )
    }

    if (showResetDlg) {
        AlertDialog(
            onDismissRequest = { showResetDlg = false },
            title  = { Text("Resetar lista?") },
            text   = { Text("Todos os itens voltam a 'nao comprado'. Os itens em si nao sao apagados.") },
            confirmButton = {
                TextButton(onClick = { vm.resetAll(); showResetDlg = false }) {
                    Text("Resetar", color = StatusAltered)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDlg = false }) { Text("Cancelar") }
            }
        )
    }
}

// ─── Category Header ──────────────────────────────────────────────────────────

@Composable
private fun CategoryHeader(
    category: ShoppingCategory,
    itemCount: Int, boughtCount: Int, total: Float
) {
    val (icon, label, color) = when (category) {
        ShoppingCategory.PROTEINAS       -> Triple(Icons.Outlined.Egg,         "Proteinas",       MaterialTheme.colorScheme.primary)
        ShoppingCategory.CARBOIDRATOS    -> Triple(Icons.Outlined.Grain,        "Carboidratos",    com.fitpro.ui.theme.CarbAmber)
        ShoppingCategory.GORDURAS        -> Triple(Icons.Outlined.Opacity,      "Gorduras",        com.fitpro.ui.theme.FatPink)
        ShoppingCategory.LEGUMES_TEMPEROS-> Triple(Icons.Outlined.Eco,          "Legumes e Temp.", StatusNormal)
        ShoppingCategory.OUTROS          -> Triple(Icons.Outlined.ShoppingBag,  "Outros",          MaterialTheme.colorScheme.outline)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = color, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text("$boughtCount/$itemCount",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("R$ ${"%.2f".format(total)}",
            fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Shopping Item Row ────────────────────────────────────────────────────────

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (item.isPurchased)
            MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
        else MaterialTheme.colorScheme.surface,
        label = "bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked   = item.isPurchased,
            onCheckedChange = { onToggle() },
            modifier  = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = if (item.isPurchased) TextDecoration.LineThrough else null,
                color = if (item.isPurchased) MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        else MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.quantity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.notes.isNotBlank()) {
                    Text("· ${item.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        maxLines = 1)
                }
            }
        }
        Text("R$ ${"%.2f".format(item.estimatedPrice)}",
            fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (item.isPurchased)
                MaterialTheme.colorScheme.onSurface.copy(0.4f)
            else MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.DeleteOutline, "Excluir",
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

// ─── Add Item Sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShoppingItemSheet(
    onDismiss: () -> Unit,
    onSave: (String, ShoppingCategory, String, Float, String) -> Unit
) {
    var name     by remember { mutableStateOf("") }
    var qty      by remember { mutableStateOf("") }
    var price    by remember { mutableStateOf("") }
    var notes    by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ShoppingCategory.PROTEINAS) }

    val catLabels = mapOf(
        ShoppingCategory.PROTEINAS        to "Proteinas",
        ShoppingCategory.CARBOIDRATOS     to "Carboidratos",
        ShoppingCategory.GORDURAS         to "Gorduras",
        ShoppingCategory.LEGUMES_TEMPEROS to "Legumes/Temperos",
        ShoppingCategory.OUTROS           to "Outros"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Adicionar item", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium)

            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Nome do item *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Category chips
            Text("Categoria", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                catLabels.entries.forEach { (cat, label) ->
                    FilterChip(
                        selected = category == cat,
                        onClick  = { category = cat },
                        label    = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = qty, onValueChange = { qty = it },
                    label = { Text("Quantidade") },
                    modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it },
                    label = { Text("Preco (R$)") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            }

            OutlinedTextField(value = notes, onValueChange = { notes = it },
                label = { Text("Observacao (opcional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            Button(
                onClick  = {
                    if (name.isBlank()) return@Button
                    val p = price.toFloatOrNull() ?: 0f
                    onSave(name, category, qty, p, notes)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) { Text("Adicionar") }
        }
    }
}
