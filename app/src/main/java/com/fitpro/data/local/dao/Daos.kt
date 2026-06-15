package com.fitpro.data.local.dao

import androidx.room.*
import com.fitpro.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// ─── Food Item DAO ────────────────────────────────────────────────────────────

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    fun searchFoods(query: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items ORDER BY name")
    fun getAllFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodById(id: Long): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoods(foods: List<FoodItemEntity>)

    @Update
    suspend fun updateFood(food: FoodItemEntity)

    @Delete
    suspend fun deleteFood(food: FoodItemEntity)

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int
}

// ─── Meal Entry DAO ───────────────────────────────────────────────────────────

data class MealEntryWithFood(
    @Embedded val entry: MealEntryEntity,
    @Relation(parentColumn = "foodItemId", entityColumn = "id")
    val food: FoodItemEntity
)

@Dao
interface MealEntryDao {
    @Transaction
    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY mealType, createdAt")
    fun getMealsForDate(date: LocalDate): Flow<List<MealEntryWithFood>>

    @Query("""
        SELECT date, 
               SUM((m.quantityG / 100.0) * f.caloriesPer100g) as calories,
               SUM((m.quantityG / 100.0) * f.proteinPer100g) as protein,
               SUM((m.quantityG / 100.0) * f.carbsPer100g) as carbs,
               SUM((m.quantityG / 100.0) * f.fatPer100g) as fat
        FROM meal_entries m JOIN food_items f ON m.foodItemId = f.id
        WHERE date BETWEEN :from AND :to
        GROUP BY date
    """)
    fun getDailySummariesBetween(from: LocalDate, to: LocalDate): Flow<List<DailySummary>>

    @Insert
    suspend fun insertMeal(entry: MealEntryEntity): Long

    @Delete
    suspend fun deleteMeal(entry: MealEntryEntity)

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteMealById(id: Long)
}

data class DailySummary(
    val date: LocalDate,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

// ─── Training Session DAO ─────────────────────────────────────────────────────

@Dao
interface TrainingSessionDao {
    @Query("SELECT * FROM training_sessions WHERE date = :date ORDER BY createdAt")
    fun getSessionsForDate(date: LocalDate): Flow<List<TrainingSessionEntity>>

    @Query("SELECT * FROM training_sessions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getSessionsBetween(from: LocalDate, to: LocalDate): Flow<List<TrainingSessionEntity>>

    @Query("SELECT date FROM training_sessions WHERE date BETWEEN :from AND :to GROUP BY date")
    fun getActiveDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    @Query("""
        SELECT date, SUM(durationMinutes) as totalMinutes, COUNT(*) as sessionCount
        FROM training_sessions 
        WHERE date BETWEEN :from AND :to
        GROUP BY date
    """)
    fun getHeatmapData(from: LocalDate, to: LocalDate): Flow<List<TrainingDayData>>

    @Query("SELECT COUNT(*) FROM training_sessions WHERE date BETWEEN :from AND :to")
    suspend fun countSessionsBetween(from: LocalDate, to: LocalDate): Int

    @Insert
    suspend fun insertSession(session: TrainingSessionEntity): Long

    @Update
    suspend fun updateSession(session: TrainingSessionEntity)

    @Delete
    suspend fun deleteSession(session: TrainingSessionEntity)

    @Query("SELECT * FROM training_sessions ORDER BY date DESC LIMIT 10")
    fun getRecentSessions(): Flow<List<TrainingSessionEntity>>
}

data class TrainingDayData(
    val date: LocalDate,
    val totalMinutes: Int,
    val sessionCount: Int
)

// ─── Body Metric DAO ──────────────────────────────────────────────────────────

@Dao
interface BodyMetricDao {
    @Query("SELECT * FROM body_metrics ORDER BY date DESC")
    fun getAllMetrics(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY date DESC LIMIT 1")
    suspend fun getLatestMetric(): BodyMetricEntity?

    @Query("SELECT * FROM body_metrics WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun getMetricsBetween(from: LocalDate, to: LocalDate): Flow<List<BodyMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: BodyMetricEntity): Long

    @Delete
    suspend fun deleteMetric(metric: BodyMetricEntity)
}

// ─── Lab Exam DAO ─────────────────────────────────────────────────────────────

@Dao
interface LabExamDao {
    @Query("SELECT * FROM lab_exams ORDER BY date DESC, examGroup, examName")
    fun getAllExams(): Flow<List<LabExamEntity>>

    @Query("SELECT DISTINCT date FROM lab_exams ORDER BY date DESC")
    fun getExamDates(): Flow<List<LocalDate>>

    @Query("SELECT * FROM lab_exams WHERE date = :date ORDER BY examGroup, examName")
    fun getExamsByDate(date: LocalDate): Flow<List<LabExamEntity>>

    @Query("SELECT * FROM lab_exams WHERE status != 'NORMAL' ORDER BY date DESC")
    fun getAlteredExams(): Flow<List<LabExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: LabExamEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<LabExamEntity>)

    @Delete
    suspend fun deleteExam(exam: LabExamEntity)
}

// ─── Chat Message DAO ─────────────────────────────────────────────────────────

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT 50")
    fun getRecentMessages(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

// ─── User Goal DAO ────────────────────────────────────────────────────────────

@Dao
interface UserGoalDao {
    @Query("SELECT * FROM user_goals WHERE id = 1")
    fun getGoal(): Flow<UserGoalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setGoal(goal: UserGoalEntity)
}
