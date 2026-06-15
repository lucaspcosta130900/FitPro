package com.fitpro.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpro.data.local.dao.DailySummary
import com.fitpro.data.local.entity.BodyMetricEntity
import com.fitpro.data.local.entity.MealType
import com.fitpro.data.local.entity.TrainingSessionEntity
import com.fitpro.data.local.entity.UserGoalEntity
import com.fitpro.data.preferences.UserPreferences
import com.fitpro.data.preferences.UserPreferencesRepository
import com.fitpro.data.repository.FoodRepository
import com.fitpro.data.repository.HealthRepository
import com.fitpro.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val todaySummary: DailySummary? = null,
    val goal: UserGoalEntity = UserGoalEntity(),
    val latestMetric: BodyMetricEntity? = null,
    val recentSessions: List<TrainingSessionEntity> = emptyList(),
    val prefs: UserPreferences? = null,
    val streakDays: Int = 0,
    val weeklyTrainingDays: Int = 0,
    val bmi: Float? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val healthRepo: HealthRepository,
    private val trainingRepo: TrainingRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val today = LocalDate.now()

    val uiState: StateFlow<DashboardUiState> = combine(
        foodRepo.getDailySummaries(today, today),
        foodRepo.getGoal(),
        trainingRepo.getRecentSessions(),
        prefsRepo.preferences
    ) { summaries, goal, sessions, prefs ->
        val todaySum = summaries.firstOrNull()
        val metric   = healthRepo.getLatestMetric()
        val bmi      = metric?.let { m ->
            val hM = prefs.heightCm / 100f
            m.weightKg / (hM * hM)
        }

        DashboardUiState(
            todaySummary    = todaySum,
            goal            = goal ?: UserGoalEntity(),
            latestMetric    = metric,
            recentSessions  = sessions,
            prefs           = prefs,
            bmi             = bmi
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        viewModelScope.launch {
            // streak and weekly training
        }
    }
}
