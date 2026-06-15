package com.fitpro.data.local.entity

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it) }
    @TypeConverter fun toLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter fun fromLocalDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it) }
    @TypeConverter fun toLocalDateTime(dt: LocalDateTime?): String? = dt?.toString()

    @TypeConverter fun fromMealType(value: String?): MealType? =
        value?.let { MealType.valueOf(it) }
    @TypeConverter fun toMealType(type: MealType?): String? = type?.name

    @TypeConverter fun fromTrainingType(value: String?): TrainingType? =
        value?.let { TrainingType.valueOf(it) }
    @TypeConverter fun toTrainingType(type: TrainingType?): String? = type?.name

    @TypeConverter fun fromIntensity(value: String?): Intensity? =
        value?.let { Intensity.valueOf(it) }
    @TypeConverter fun toIntensity(i: Intensity?): String? = i?.name

    @TypeConverter fun fromExamStatus(value: String?): ExamStatus? =
        value?.let { ExamStatus.valueOf(it) }
    @TypeConverter fun toExamStatus(s: ExamStatus?): String? = s?.name

    @TypeConverter fun fromChatRole(value: String?): ChatRole? =
        value?.let { ChatRole.valueOf(it) }
    @TypeConverter fun toChatRole(r: ChatRole?): String? = r?.name
}

    @TypeConverter fun fromShoppingCategory(v: String?): ShoppingCategory? =
        v?.let { ShoppingCategory.valueOf(it) }
    @TypeConverter fun toShoppingCategory(c: ShoppingCategory?): String? = c?.name
