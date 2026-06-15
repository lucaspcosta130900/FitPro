package com.fitpro.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpro.data.local.dao.TrainingDayData
import com.fitpro.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

// ─── Macro Progress Ring ──────────────────────────────────────────────────────

@Composable
fun MacroProgressRing(
    label: String,
    current: Float,
    goal: Float,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 8.dp
) {
    val progress = if (goal > 0f) (current / goal).coerceIn(0f, 1.1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ring"
    )

    Column(
        modifier         = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)
                // Track
                drawArc(
                    color     = color.copy(alpha = 0.15f),
                    startAngle= -90f,
                    sweepAngle= 360f,
                    useCenter = false,
                    topLeft   = topLeft,
                    size      = arcSize,
                    style     = Stroke(stroke, cap = StrokeCap.Round)
                )
                // Progress
                val sweep = min(animatedProgress, 1f) * 360f
                val arcColor = if (animatedProgress > 1f) StatusAltered else color
                drawArc(
                    color     = arcColor,
                    startAngle= -90f,
                    sweepAngle= sweep,
                    useCenter = false,
                    topLeft   = topLeft,
                    size      = arcSize,
                    style     = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (current < 1000f) "%.0f".format(current) else "${"%.1f".format(current / 1000)}k",
                    fontSize    = (size.value * 0.2f).sp,
                    fontWeight  = FontWeight.SemiBold,
                    color       = MaterialTheme.colorScheme.onSurface,
                    textAlign   = TextAlign.Center
                )
                Text(
                    text      = unit,
                    fontSize  = (size.value * 0.11f).sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text      = label,
            fontSize  = 11.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text     = "/ ${goal.toInt()} $unit",
            fontSize = 10.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
            textAlign= TextAlign.Center
        )
    }
}

// ─── GitHub-style Training Heatmap ───────────────────────────────────────────

@Composable
fun TrainingHeatmap(
    data: Map<LocalDate, TrainingDayData>,
    modifier: Modifier = Modifier,
    weeks: Int = 26,
    onDayClick: (LocalDate) -> Unit = {}
) {
    val today = LocalDate.now()
    val cellSize = 12.dp
    val gap = 2.dp
    val cellTotal = cellSize + gap

    // Build week columns starting from `weeks` weeks ago (aligned to Monday)
    val startDate = today.minusWeeks(weeks.toLong())
        .let { it.minusDays(it.dayOfWeek.value.toLong() - 1) }

    val months = mutableListOf<Pair<String, Int>>()  // (month label, column index)
    var lastMonth = -1

    Column(modifier = modifier) {
        Row {
            // Day-of-week labels — all 7 days
            Column(modifier = Modifier.width(24.dp)) {
                listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach { lbl ->
                    Box(
                        modifier = Modifier
                            .height(cellTotal)
                            .width(24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(lbl, fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                }
            }
            Spacer(Modifier.width(3.dp))

            // Week columns
            Row {
                for (w in 0..weeks) {
                    Column {
                        for (d in 0..6) {
                            val date = startDate.plusDays((w * 7 + d).toLong())
                            if (date > today) {
                                Box(modifier = Modifier.size(cellSize).padding(gap / 2))
                            } else {
                                val dayData = data[date]
                                val level = when {
                                    dayData == null -> 0
                                    dayData.totalMinutes < 20 -> 1
                                    dayData.totalMinutes < 45 -> 2
                                    dayData.totalMinutes < 75 -> 3
                                    else -> 4
                                }
                                val bgColor = when (level) {
                                    0 -> HeatLevel0
                                    1 -> HeatLevel1
                                    2 -> HeatLevel2
                                    3 -> HeatLevel3
                                    else -> HeatLevel4
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(gap / 2)
                                        .size(cellSize)
                                        .background(bgColor, RoundedCornerShape(2.dp))
                                        .clickable { onDayClick(date) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Month labels
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.padding(start = 22.dp)) {
            for (w in 0..weeks) {
                val date = startDate.plusDays((w * 7).toLong())
                if (date.monthValue != lastMonth) {
                    lastMonth = date.monthValue
                    Text(
                        text  = date.month.getDisplayName(TextStyle.SHORT, Locale("pt", "BR")),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        modifier = Modifier.width(cellTotal * 4)
                    )
                } else {
                    Spacer(Modifier.width(cellTotal))
                }
            }
        }
    }
}

// ─── Macro Summary Bar ────────────────────────────────────────────────────────

@Composable
fun MacroSummaryBar(
    calories: Float,
    protein: Float,
    carbs: Float,
    fat: Float,
    goalCalories: Int,
    goalProtein: Int,
    goalCarbs: Int,
    goalFat: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%.0f / %d kcal".format(calories, goalCalories),
                style     = MaterialTheme.typography.titleMedium,
                color     = if (calories > goalCalories) StatusAltered else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Restam: %.0f".format((goalCalories - calories).coerceAtLeast(0f)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        // Stacked progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
        ) {
            val total = goalCalories.toFloat()
            val pPct = (protein * 4f / total).coerceIn(0f, 1f)
            val cPct = (carbs   * 4f / total).coerceIn(0f, 1f - pPct)
            val fPct = (fat     * 9f / total).coerceIn(0f, 1f - pPct - cPct)

            Row(modifier = Modifier.fillMaxSize()) {
                if (pPct > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(pPct).background(ProteinBlue, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                if (cPct > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(cPct / (1f - pPct)).background(CarbAmber))
                if (fPct > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fPct / (1f - pPct - cPct)).background(FatPink))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MacroLegendItem("P", "%.0fg/%.0fg".format(protein, goalProtein.toFloat()), ProteinBlue)
            MacroLegendItem("C", "%.0fg/%.0fg".format(carbs, goalCarbs.toFloat()), CarbAmber)
            MacroLegendItem("G", "%.0fg/%.0fg".format(fat, goalFat.toFloat()), FatPink)
        }
    }
}

@Composable
private fun MacroLegendItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Section card ─────────────────────────────────────────────────────────────

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
