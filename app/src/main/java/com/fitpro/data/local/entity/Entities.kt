package com.fitpro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

// ─── Food Item ────────────────────────────────────────────────────────────────

@Entity(tableName = "food_items")
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String = "",
    val servingSizeG: Float = 100f,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float = 0f,
    val sodiumMgPer100g: Float = 0f,
    val isCustom: Boolean = true
)

// ─── Meal Entry ───────────────────────────────────────────────────────────────

enum class MealType { BREAKFAST, LUNCH, SNACK, DINNER, SUPPER }

@Entity(
    tableName = "meal_entries",
    foreignKeys = [ForeignKey(
        entity = FoodItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("foodItemId"), Index("date")]
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val foodItemId: Long,
    val quantityG: Float,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// ─── Training Session ─────────────────────────────────────────────────────────

enum class TrainingType { CARDIO, STRENGTH, HIIT, RUN, SWIM, YOGA, OTHER }
enum class Intensity { LOW, MODERATE, HIGH, MAX }

@Entity(tableName = "training_sessions", indices = [Index("date")])
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val type: TrainingType,
    val durationMinutes: Int,
    val intensity: Intensity = Intensity.MODERATE,
    val caloriesBurned: Int = 0,
    val distanceKm: Float? = null,
    val notes: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// ─── Body Metric ──────────────────────────────────────────────────────────────

@Entity(tableName = "body_metrics", indices = [Index("date")])
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Float,
    val bodyFatPercent: Float? = null,
    val muscleMassKg: Float? = null,
    val waistCm: Float? = null,
    val hipCm: Float? = null,
    val chestCm: Float? = null,
    val notes: String = ""
)

// ─── Lab Exam ─────────────────────────────────────────────────────────────────

enum class ExamStatus { NORMAL, BORDERLINE, ALTERED }

@Entity(tableName = "lab_exams", indices = [Index("date"), Index("examName")])
data class LabExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val examGroup: String,
    val examName: String,
    val value: Float,
    val unit: String,
    val referenceMin: Float? = null,
    val referenceMax: Float? = null,
    val referenceText: String = "",
    val status: ExamStatus,
    val notes: String = ""
)

// ─── Chat Message ─────────────────────────────────────────────────────────────

enum class ChatRole { USER, ASSISTANT }

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: ChatRole,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isError: Boolean = false
)

// ─── User Goal ────────────────────────────────────────────────────────────────

@Entity(tableName = "user_goals")
data class UserGoalEntity(
    @PrimaryKey val id: Int = 1,
    val caloriesKcal: Int = 2052,
    val proteinG: Int = 160,
    val carbsG: Int = 207,
    val fatG: Int = 65,
    val fiberG: Int = 35,
    val waterMl: Int = 4000,
    val targetWeightKg: Float? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
