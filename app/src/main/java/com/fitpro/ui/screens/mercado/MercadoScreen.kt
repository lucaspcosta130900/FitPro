package com.fitpro.ui.screens.mercado

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.ShoppingDao
import com.fitpro.data.local.entity.ShoppingCategory
import com.fitpro.data.local.entity.ShoppingItemEntity
import com.fitpro.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MercadoViewModel @Inject constructor(private val shoppingDao: ShoppingDao) : ViewModel() {

    private val now  = LocalDate.now()
    val month        = now.monthValue
    val year         = now.year
    val budget       = 800f

    val items: StateFlow<List<ShoppingItemEntity>> =
        shoppingDao.getItemsForMonth(month, year)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalEstimated: StateFlow<Float> =
        shoppingDao.getTotalEstimated(month, year).map { it ?: 0f }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val totalPurchased: StateFlow<Float> =
        shoppingDao.getTotalPurchased(month, year).map { it ?: 0f }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun togglePurchased(item: ShoppingItemEntity) =
        viewModelScope.launch { shoppingDao.setItemPurchased(item.id, !item.isPurchased) }

    fun deleteItem(item: ShoppingItemEntity) =
        viewModelScope.launch { shoppingDao.deleteItem(item) }

    fun addItem(name: String, category: ShoppingCategory, quantity: String, price: Float, notes: String) =
        viewModelScope.launch {
            shoppingDao.insertItem(ShoppingItemEntity(
                name = name, category = category, quantity = quantity,
                estimatedPrice = price, month = month, year = year, notes = notes
            ))
        }

    fun editItem(item: ShoppingItemEntity, name: String, category: ShoppingCategory,
                 quantity: String, price: Float, notes: String) =
        viewModelScope.launch {
            shoppingDao.updateItem(item.copy(
                name = name, category = category, quantity = quantity,
                estimatedPrice = price, notes = notes
            ))
        }

    fun resetAll() = viewModelScope.launch { shoppingDao.resetPurchased(month, year) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercadoScreen(vm: MercadoViewModel = hiltViewModel()) {
    val items          by vm.items.collectAsStateWithLifecycle()
    val totalEst       by vm.totalEstimated.collectAsStateWithLifecycle()
    val totalPurchased by vm.totalPurchased.collectAsStateWithLifecycle()

    var showAddSheet   by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var showResetDlg   by remember { mutableStateOf(false) }

    val monthLabel = LocalDate.of(vm.year, vm.month, 1)
        .format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    val grouped        = items.groupBy { it.category }
    val purchasedCount = items.count { it.isPurchased }
    val progress       = (totalPurchased / vm.budget).coerceIn(0f, 1f)
    val progressColor  = when {
        progress > 0.9f -> StatusAltered
        progress > 0.7f -> StatusBorder
        else            -> StatusNormal
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, "Adicionar item")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)) {

            // ── Budget header
            item {
                Column(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Mercado — $monthLabel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("$purchasedCount de ${items.size} itens comprados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        }
                        IconButton(onClick = { showResetDlg = true }) {
                            Icon(Icons.Outlined.RestartAlt, "Resetar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gasto: R$ ${"%.2f".format(totalPurchased)}", fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Orçamento: R$ ${"%.0f".format(vm.budget)}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f))
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.15f))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimado: R$ ${"%.2f".format(totalEst)}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        val rem = vm.budget - totalPurchased
                        Text("Restante: R$ ${"%.2f".format(rem)}", fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (rem < 0) StatusAltered
                                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(0.9f))
                    }
                }
            }

            // ── Items by category
            ShoppingCategory.entries.forEach { cat ->
                val catItems = grouped[cat] ?: return@forEach
                if (catItems.isEmpty()) return@forEach
                val catTotal = catItems.sumOf { it.estimatedPrice.toDouble() }.toFloat()
                val catBought = catItems.count { it.isPurchased }

                item(key = cat.name + "_h") {
                    CategoryHeader(cat, catItems.size, catBought, catTotal)
                }
                items(catItems, key = { it.id }) { si ->
                    ShoppingItemRow(si,
                        onToggle = { vm.togglePurchased(si) },
                        onEdit   = { editTarget = si },
                        onDelete = { vm.deleteItem(si) })
                }
            }

            if (items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ShoppingCart, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
                            Text("Lista vazia", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ItemFormSheet(item = null,
            onDismiss = { showAddSheet = false },
            onSave = { n, c, q, p, nt -> vm.addItem(n, c, q, p, nt); showAddSheet = false })
    }
    editTarget?.let { si ->
        ItemFormSheet(item = si,
            onDismiss = { editTarget = null },
            onSave = { n, c, q, p, nt -> vm.editItem(si, n, c, q, p, nt); editTarget = null })
    }
    if (showResetDlg) {
        AlertDialog(
            onDismissRequest = { showResetDlg = false },
            title  = { Text("Resetar lista?") },
            text   = { Text("Todos os itens voltam para 'não comprado'.") },
            confirmButton = {
                TextButton(onClick = { vm.resetAll(); showResetDlg = false }) {
                    Text("Resetar", color = StatusAltered)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDlg = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CategoryHeader(cat: ShoppingCategory, total: Int, bought: Int, sum: Float) {
    val (icon, label, color) = when (cat) {
        ShoppingCategory.PROTEINAS        -> Triple(Icons.Outlined.Egg,         "Proteínas",          MaterialTheme.colorScheme.primary)
        ShoppingCategory.CARBOIDRATOS     -> Triple(Icons.Outlined.Grain,        "Carboidratos",       CarbAmber)
        ShoppingCategory.GORDURAS         -> Triple(Icons.Outlined.Opacity,      "Gorduras",           FatPink)
        ShoppingCategory.LEGUMES_TEMPEROS -> Triple(Icons.Outlined.Eco,          "Legumes e Temperos", StatusNormal)
        ShoppingCategory.OUTROS           -> Triple(Icons.Outlined.ShoppingBag,  "Outros",             MaterialTheme.colorScheme.outline)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color,
            letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text("$bought/$total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("R$ ${"%.2f".format(sum)}", fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val bg by animateColorAsState(
        if (item.isPurchased) MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
        else MaterialTheme.colorScheme.surface, label = "bg"
    )
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isPurchased, onCheckedChange = { onToggle() },
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = if (item.isPurchased) TextDecoration.LineThrough else null,
                color = if (item.isPurchased) MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.quantity, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.notes.isNotBlank())
                    Text("· ${item.notes}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
            }
        }
        Text("R$ ${"%.2f".format(item.estimatedPrice)}", fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (item.isPurchased) MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Edit, "Editar", Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.DeleteOutline, "Excluir", Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

// ─── Item Form Sheet (Add / Edit) ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFormSheet(
    item: ShoppingItemEntity?,
    onDismiss: () -> Unit,
    onSave: (String, ShoppingCategory, String, Float, String) -> Unit
) {
    var name     by remember { mutableStateOf(item?.name     ?: "") }
    var qty      by remember { mutableStateOf(item?.quantity  ?: "") }
    var price    by remember { mutableStateOf(item?.estimatedPrice?.toString() ?: "") }
    var notes    by remember { mutableStateOf(item?.notes    ?: "") }
    var category by remember { mutableStateOf(item?.category  ?: ShoppingCategory.PROTEINAS) }

    val catLabels = mapOf(
        ShoppingCategory.PROTEINAS        to "Proteínas",
        ShoppingCategory.CARBOIDRATOS     to "Carbos",
        ShoppingCategory.GORDURAS         to "Gorduras",
        ShoppingCategory.LEGUMES_TEMPEROS to "Legumes",
        ShoppingCategory.OUTROS           to "Outros"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (item != null) "Editar item" else "Novo item",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Nome *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            // Category chips
            Text("Categoria", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                catLabels.entries.forEach { (cat, label) ->
                    FilterChip(selected = category == cat, onClick = { category = cat },
                        label = { Text(label, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = qty, onValueChange = { qty = it },
                    label = { Text("Quantidade") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it },
                    label = { Text("Preço (R$)") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it },
                label = { Text("Observação") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, category, qty, price.toFloatOrNull() ?: 0f, notes) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank()
            ) { Text(if (item != null) "Salvar" else "Adicionar") }
        }
    }
}
