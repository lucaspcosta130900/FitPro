package com.fitpro.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// ─── Content blocks ───────────────────────────────────────────────────────────

data class TextContent(
    val type: String = "text",
    val text: String
)

data class DocumentSource(
    val type: String = "base64",
    @SerializedName("media_type") val mediaType: String,
    val data: String
)

data class DocumentContent(
    val type: String = "document",
    val source: DocumentSource
)

// ─── Messages ─────────────────────────────────────────────────────────────────

/** Text-only chat message (content = plain String) */
data class ChatMessage(val role: String, val content: String)

/** Message with mixed content blocks (text + document) */
data class MultiMessage(val role: String, val content: List<Any>)

// ─── Requests ─────────────────────────────────────────────────────────────────

data class ChatRequest(
    val model: String = "claude-sonnet-4-6",
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val system: String,
    val messages: List<ChatMessage>
)

data class DocAnalysisRequest(
    val model: String = "claude-sonnet-4-6",
    @SerializedName("max_tokens") val maxTokens: Int = 8096,
    val system: String,
    val messages: List<MultiMessage>
)

// ─── Response ─────────────────────────────────────────────────────────────────

data class ResponseBlock(val type: String, val text: String?)

data class AnthropicResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<ResponseBlock>?,
    val model: String?,
    @SerializedName("stop_reason") val stopReason: String?,
    val usage: UsageInfo?
)

data class UsageInfo(
    @SerializedName("input_tokens")  val inputTokens: Int,
    @SerializedName("output_tokens") val outputTokens: Int
)

// ─── API interface ────────────────────────────────────────────────────────────
// NOTE: Content-Type is set automatically by GsonConverterFactory — DO NOT add
//       it via @Headers or it will duplicate and cause 400.
// NOTE: anthropic-version is added by AnthropicHeaderInterceptor in NetworkModule.

interface AnthropicApiService {

    /** Standard chat endpoint (text only). */
    @POST("v1/messages")
    suspend fun chat(
        @Header("x-api-key") apiKey: String,
        @Body request: ChatRequest
    ): AnthropicResponse

    /** Document analysis endpoint (PDF + text prompt). */
    @POST("v1/messages")
    suspend fun analyzeDocument(
        @Header("x-api-key") apiKey: String,
        @Body request: DocAnalysisRequest
    ): AnthropicResponse
}

// ─── OkHttp interceptor (adds required fixed headers) ────────────────────────
// Registered in NetworkModule — keeps interface clean.
class AnthropicHeaderInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val req = chain.request().newBuilder()
            .addHeader("anthropic-version", "2023-06-01")
            .build()
        return chain.proceed(req)
    }
}

// ─── Exam result model (parsed from Claude JSON response) ────────────────────

data class ExamResultDto(
    val group: String,
    val name: String,
    val value: Float,
    val unit: String,
    @SerializedName("refMin")  val refMin: Float?,
    @SerializedName("refMax")  val refMax: Float?,
    @SerializedName("refText") val refText: String,
    val status: String        // "NORMAL" | "BORDERLINE" | "ALTERED"
)

// ─── System prompt builder ────────────────────────────────────────────────────

object FitProSystemPrompt {
    fun build(
        weightKg: Float?, heightCm: Float, ageYears: Int,
        dailyCalorieGoal: Int, proteinGoalG: Int, carbsGoalG: Int, fatGoalG: Int,
        todayCalories: Float, todayProtein: Float, todayCarbs: Float, todayFat: Float,
        lastTraining: String?, examNotes: String?
    ): String = buildString {
        appendLine("Voce e o FitPro AI, assistente pessoal de saude e nutricao do Lucas.")
        appendLine()
        appendLine("## Perfil atual:")
        appendLine("- Peso: ${weightKg?.let { "${"%.1f".format(it)} kg" } ?: "nao informado"}")
        appendLine("- Altura: ${"%.0f".format(heightCm)} cm | Idade: $ageYears anos")
        appendLine()
        appendLine("## Metas diarias:")
        appendLine("- Calorias: $dailyCalorieGoal kcal | Proteina: ${proteinGoalG}g | Carbs: ${carbsGoalG}g | Gordura: ${fatGoalG}g")
        appendLine()
        appendLine("## Consumo de hoje:")
        appendLine("- Calorias: ${"%.0f".format(todayCalories)} / $dailyCalorieGoal kcal")
        appendLine("- Proteina: ${"%.1f".format(todayProtein)}g / ${proteinGoalG}g")
        appendLine("- Carbs: ${"%.1f".format(todayCarbs)}g / ${carbsGoalG}g | Gordura: ${"%.1f".format(todayFat)}g / ${fatGoalG}g")
        appendLine()
        lastTraining?.let { appendLine("## Ultimo treino: $it\n") }
        examNotes?.let { appendLine("## Exames alterados:\n$it\n") }
        appendLine("## Instrucoes:")
        appendLine("- Responda SEMPRE em portugues do Brasil")
        appendLine("- Seja objetivo, pratico e motivador")
        appendLine("- Use os dados acima para personalizar as respostas")
        appendLine("- Foque em nutricao, treino, interpretacao de exames e motivacao")
    }

    const val PDF_EXAM_PROMPT = """Analise o PDF de resultado de exame laboratorial e extraia TODOS os resultados encontrados.

Responda APENAS com um JSON array puro — sem texto adicional, sem markdown, sem explicacao.

Formato de cada item do array:
{
  "group": "categoria do exame em portugues (ex: Lipidios, Hemograma, Tiroide, Glicemia, Funcao Hepatica, Funcao Renal, Hormonios, Vitaminas)",
  "name": "nome exato do exame como aparece no laudo",
  "value": 0.0,
  "unit": "unidade (ex: mg/dL, U/L, g/dL, %)",
  "refMin": null ou numero minimo de referencia para o sexo masculino,
  "refMax": null ou numero maximo de referencia para o sexo masculino,
  "refText": "texto de referencia completo como aparece no laudo",
  "status": "NORMAL" ou "BORDERLINE" ou "ALTERED"
}

Regras para status:
- ALTERED: resultado fora do intervalo de referencia
- BORDERLINE: dentro do intervalo mas nos 10% proximos ao limite
- NORMAL: claramente dentro do intervalo

Inclua TODOS os exames do laudo, mesmo os normais.
Para exames sem valor numerico (ex: tipo sanguineo), omita.
Nao inclua subcomponentes duplicados se o total ja esta incluido."""
}
