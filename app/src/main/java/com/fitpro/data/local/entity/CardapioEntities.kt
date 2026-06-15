package com.fitpro.data.local.entity

import androidx.room.*
import java.time.LocalDateTime

// ─── Meal Template ────────────────────────────────────────────────────────────

@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: MealType,
    val description: String = "",
    val isDefault: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// ─── Meal Template Item ───────────────────────────────────────────────────────

@Entity(
    tableName = "meal_template_items",
    foreignKeys = [
        ForeignKey(
            entity = MealTemplateEntity::class,
            parentColumns = ["id"], childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"], childColumns = ["foodItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId"), Index("foodItemId")]
)
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val foodItemId: Long,
    val quantityG: Float
)

// ─── Shopping Item ────────────────────────────────────────────────────────────

enum class ShoppingCategory {
    PROTEINAS, CARBOIDRATOS, GORDURAS, LEGUMES_TEMPEROS, OUTROS
}

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ShoppingCategory,
    val quantity: String,          // "5 kg", "3 caixas"
    val estimatedPrice: Float,
    val isPurchased: Boolean = false,
    val month: Int,
    val year: Int,
    val notes: String = ""
)
