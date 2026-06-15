package com.fitpro.data.repository

import com.fitpro.data.local.dao.*
import com.fitpro.data.local.entity.*
import com.fitpro.data.remote.AnthropicApiService
import com.fitpro.data.remote.ChatMessage
import com.fitpro.data.remote.ChatRequest
import com.fitpro.data.remote.DocumentContent
import com.fitpro.data.remote.DocumentSource
import com.fitpro.data.remote.TextContent
import com.fitpro.data.remote.MultiMessage
import com.fitpro.data.remote.DocAnalysisRequest
import com.fitpro.data.remote.FitProSystemPrompt
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// ─── Food Repository ──────────────────────────────────────────────────────────

@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodItemDao,
    private val mealDao: MealEntryDao,
    private val goalDao: UserGoalDao
) {
    fun searchFoods(query: String): Flow<List<FoodItemEntity>> =
        foodDao.searchFoods(query)

    fun getAllFoods(): Flow<List<FoodItemEntity>> = foodDao.getAllFoods()

    suspend fun getFoodById(id: Long): FoodItemEntity? = foodDao.getFoodById(id)

    suspend fun insertFood(food: FoodItemEntity): Long = foodDao.insertFood(food)

    fun getMealsForDate(date: LocalDate): Flow<List<MealEntryWithFood>> =
        mealDao.getMealsForDate(date)

    fun getDailySummaries(from: LocalDate, to: LocalDate): Flow<List<DailySummary>> =
        mealDao.getDailySummariesBetween(from, to)

    suspend fun addMealEntry(entry: MealEntryEntity): Long = mealDao.insertMeal(entry)

    suspend fun deleteMealEntry(id: Long) = mealDao.deleteMealById(id)

    fun getGoal(): Flow<UserGoalEntity?> = goalDao.getGoal()

    suspend fun setGoal(goal: UserGoalEntity) = goalDao.setGoal(goal)
}

// ─── Training Repository ──────────────────────────────────────────────────────

@Singleton
class TrainingRepository @Inject constructor(
    private val trainingDao: TrainingSessionDao
) {
    fun getSessionsForDate(date: LocalDate): Flow<List<TrainingSessionEntity>> =
        trainingDao.getSessionsForDate(date)

    fun getRecentSessions(): Flow<List<TrainingSessionEntity>> =
        trainingDao.getRecentSessions()

    fun getHeatmapData(from: LocalDate, to: LocalDate): Flow<List<TrainingDayData>> =
        trainingDao.getHeatmapData(from, to)

    fun getSessionsBetween(from: LocalDate, to: LocalDate): Flow<List<TrainingSessionEntity>> =
        trainingDao.getSessionsBetween(from, to)

    suspend fun addSession(session: TrainingSessionEntity): Long =
        trainingDao.insertSession(session)

    suspend fun updateSession(session: TrainingSessionEntity) =
        trainingDao.updateSession(session)

    suspend fun deleteSession(session: TrainingSessionEntity) =
        trainingDao.deleteSession(session)

    suspend fun getStreakDays(): Int {
        var streak = 0
        var checkDate = LocalDate.now()
        while (true) {
            val count = trainingDao.countSessionsBetween(checkDate, checkDate)
            if (count == 0) break
            streak++
            checkDate = checkDate.minusDays(1)
        }
        return streak
    }
}

// ─── Health Repository ────────────────────────────────────────────────────────

@Singleton
class HealthRepository @Inject constructor(
    private val bodyMetricDao: BodyMetricDao,
    private val labExamDao: LabExamDao
) {
    fun getAllMetrics(): Flow<List<BodyMetricEntity>> = bodyMetricDao.getAllMetrics()

    suspend fun getLatestMetric(): BodyMetricEntity? = bodyMetricDao.getLatestMetric()

    fun getMetricsBetween(from: LocalDate, to: LocalDate): Flow<List<BodyMetricEntity>> =
        bodyMetricDao.getMetricsBetween(from, to)

    suspend fun insertMetric(metric: BodyMetricEntity): Long =
        bodyMetricDao.insertMetric(metric)

    suspend fun deleteMetric(metric: BodyMetricEntity) =
        bodyMetricDao.deleteMetric(metric)

    fun getAllExams(): Flow<List<LabExamEntity>> = labExamDao.getAllExams()

    fun getExamDates(): Flow<List<LocalDate>> = labExamDao.getExamDates()

    fun getExamsByDate(date: LocalDate): Flow<List<LabExamEntity>> =
        labExamDao.getExamsByDate(date)

    fun getAlteredExams(): Flow<List<LabExamEntity>> = labExamDao.getAlteredExams()

    suspend fun insertExam(exam: LabExamEntity): Long = labExamDao.insertExam(exam)

    suspend fun insertExams(exams: List<LabExamEntity>) = labExamDao.insertExams(exams)

    suspend fun deleteExam(exam: LabExamEntity) = labExamDao.deleteExam(exam)
}

// ─── AI Repository ────────────────────────────────────────────────────────────

@Singleton
class AiRepository @Inject constructor(
    private val apiService: AnthropicApiService,
    private val chatDao: ChatMessageDao
) {
    fun getMessages(): Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()

    suspend fun sendMessage(
        userMessage: String,
        apiKey: String,
        systemPrompt: String,
        conversationHistory: List<ChatMessageEntity>
    ): Result<String> {
        chatDao.insertMessage(ChatMessageEntity(role = ChatRole.USER, content = userMessage))
        return try {
            val history = (conversationHistory.takeLast(20).map {
                ChatMessage(
                    role    = if (it.role == ChatRole.USER) "user" else "assistant",
                    content = it.content
                )
            } + listOf(ChatMessage(role = "user", content = userMessage)))

            val response = apiService.chat(
                apiKey  = apiKey,
                request = ChatRequest(system = systemPrompt, messages = history)
            )
            val reply = response.content?.firstOrNull { it.type == "text" }?.text
                ?: "Sem resposta da API"
            chatDao.insertMessage(ChatMessageEntity(role = ChatRole.ASSISTANT, content = reply))
            Result.success(reply)
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string() ?: ""
            val msg  = "Erro ${e.code()}: ${e.message()} — $body"
            chatDao.insertMessage(ChatMessageEntity(role = ChatRole.ASSISTANT, content = msg, isError = true))
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            val msg = "Erro: ${e.message ?: "Falha na comunicacao com a API"}"
            chatDao.insertMessage(ChatMessageEntity(role = ChatRole.ASSISTANT, content = msg, isError = true))
            Result.failure(e)
        }
    }

    suspend fun analyzePdf(
        base64Pdf: String,
        apiKey: String
    ): List<com.fitpro.data.remote.ExamResultDto> {
        val message = MultiMessage(
            role = "user",
            content = listOf(
                DocumentContent(source = DocumentSource(
                    mediaType = "application/pdf", data = base64Pdf)),
                TextContent(text = FitProSystemPrompt.PDF_EXAM_PROMPT)
            )
        )
        val response = apiService.analyzeDocument(
            apiKey  = apiKey,
            request = DocAnalysisRequest(
                system   = "Voce e um assistente medico especializado em interpretar laudos laboratoriais brasileiros. Extraia dados com precisao.",
                messages = listOf(message)
            )
        )
        val raw = response.content?.firstOrNull { it.type == "text" }?.text
            ?: throw Exception("Resposta vazia da API")

        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        return parseExamJson(clean)
    }

    private fun parseExamJson(json: String): List<com.fitpro.data.remote.ExamResultDto> {
        val gson = com.google.gson.Gson()
        val type = Array<com.fitpro.data.remote.ExamResultDto>::class.java

        // 1. Try full parse first
        try { return gson.fromJson(json, type).toList() } catch (_: Exception) {}

        // 2. Truncated response — recover complete items before the cut
        val lastBrace = json.lastIndexOf('}')
        if (lastBrace > 0) {
            val partial = json.substring(0, lastBrace + 1)
                .trimEnd().trimEnd(',')
            val fixed = (if (partial.trimStart().startsWith("[")) partial else "[$partial") + "]"
            try {
                val results = gson.fromJson(fixed, type).toList()
                android.util.Log.w("FitPro",
                    "PDF truncado — recuperados ${results.size} exames parciais")
                return results
            } catch (_: Exception) {}
        }

        throw Exception(
            "Nao foi possivel interpretar a resposta (JSON invalido). " +
            "Tente um PDF com menos paginas ou aguarde e tente novamente."
        )
    }

    suspend fun analyzeNutritionalLabel(
        base64Image: String,
        mediaType: String,   // "image/jpeg", "image/png", etc.
        apiKey: String
    ): com.fitpro.data.remote.NutritionDto {
        val message = MultiMessage(
            role = "user",
            content = listOf(
                com.fitpro.data.remote.ImageContent(
                    source = com.fitpro.data.remote.ImageSource(
                        mediaType = mediaType, data = base64Image)),
                TextContent(text = FitProSystemPrompt.NUTRITION_LABEL_PROMPT)
            )
        )
        val response = apiService.analyzeDocument(
            apiKey  = apiKey,
            request = DocAnalysisRequest(
                system   = "Voce e um especialista em nutricao que extrai dados de tabelas nutricionais com precisao.",
                messages = listOf(message)
            )
        )
        val raw = response.content?.firstOrNull { it.type == "text" }?.text
            ?: throw Exception("Resposta vazia da API")
        val clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        return com.google.gson.Gson().fromJson(clean, com.fitpro.data.remote.NutritionDto::class.java)
    }

    suspend fun clearHistory() = chatDao.clearAll()
}
