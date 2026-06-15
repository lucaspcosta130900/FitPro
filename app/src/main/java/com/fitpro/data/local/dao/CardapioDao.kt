package com.fitpro.data.local.dao

import androidx.room.*
import com.fitpro.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// ─── Template DAO ─────────────────────────────────────────────────────────────

data class MealTemplateWithItems(
    @Embedded val template: MealTemplateEntity,
    @Relation(
        entity = MealTemplateItemEntity::class,
        parentColumn = "id", entityColumn = "templateId"
    )
    val items: List<MealTemplateItemEntity>
)

data class TemplateItemWithFood(
    @Embedded val item: MealTemplateItemEntity,
    @Relation(parentColumn = "foodItemId", entityColumn = "id")
    val food: FoodItemEntity
)

data class FullMealTemplate(
    @Embedded val template: MealTemplateEntity,
    @Relation(
        entity = MealTemplateItemEntity::class,
        parentColumn = "id", entityColumn = "templateId"
    )
    val itemsWithFood: List<TemplateItemWithFood>
)

@Dao
interface CardapioDao {
    @Transaction
    @Query("SELECT * FROM meal_templates ORDER BY mealType, name")
    fun getAllTemplates(): Flow<List<FullMealTemplate>>

    @Transaction
    @Query("SELECT * FROM meal_templates WHERE mealType = :mealType ORDER BY name")
    fun getTemplatesByType(mealType: MealType): Flow<List<FullMealTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MealTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateItem(item: MealTemplateItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateItems(items: List<MealTemplateItemEntity>)

    @Delete
    suspend fun deleteTemplate(template: MealTemplateEntity)

    @Query("DELETE FROM meal_template_items WHERE templateId = :templateId")
    suspend fun deleteTemplateItems(templateId: Long)

    @Query("SELECT COUNT(*) FROM meal_templates")
    suspend fun count(): Int
}

// ─── Shopping DAO ─────────────────────────────────────────────────────────────

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items WHERE month = :month AND year = :year ORDER BY category, name")
    fun getItemsForMonth(month: Int, year: Int): Flow<List<ShoppingItemEntity>>

    @Query("SELECT SUM(estimatedPrice) FROM shopping_items WHERE month = :month AND year = :year AND isPurchased = 1")
    fun getTotalPurchased(month: Int, year: Int): Flow<Float?>

    @Query("SELECT SUM(estimatedPrice) FROM shopping_items WHERE month = :month AND year = :year")
    fun getTotalEstimated(month: Int, year: Int): Flow<Float?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Delete
    suspend fun deleteItem(item: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET isPurchased = :purchased WHERE id = :id")
    suspend fun setItemPurchased(id: Long, purchased: Boolean)

    @Query("UPDATE shopping_items SET isPurchased = 0 WHERE month = :month AND year = :year")
    suspend fun resetPurchased(month: Int, year: Int)

    @Query("SELECT COUNT(*) FROM shopping_items WHERE month = :month AND year = :year")
    suspend fun countForMonth(month: Int, year: Int): Int
}
