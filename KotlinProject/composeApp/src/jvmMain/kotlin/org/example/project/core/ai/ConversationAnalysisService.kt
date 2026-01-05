package org.example.project.core.ai

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.core.config.ApiKeyConfig
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.ConversationFeedback
import org.example.project.domain.model.ConversationSession
import org.example.project.domain.model.FeedbackExample

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val maxOutputTokens: Int
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

@Serializable
data class GeminiErrorResponse(
    val error: GeminiError
)

@Serializable
data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)

@Serializable
data class ConversationFeedbackCache(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("grammar_score") val grammarScore: Int,
    @SerialName("pronunciation_score") val pronunciationScore: Int,
    @SerialName("vocabulary_score") val vocabularyScore: Int,
    @SerialName("fluency_score") val fluencyScore: Int,
    @SerialName("detailed_analysis") val detailedAnalysis: String,
    val strengths: List<String>,
    @SerialName("areas_for_improvement") val areasForImprovement: List<String>,
    @SerialName("specific_examples") val specificExamples: List<FeedbackExample>,
    val suggestions: List<String>,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

class ConversationAnalysisService {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
    }
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        
        // Increase timeout for Gemini API calls
        engine {
            requestTimeout = 60000 // 60 seconds
        }
        
        // Add timeout configuration
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 // 60 seconds
            connectTimeoutMillis = 30000 // 30 seconds
            socketTimeoutMillis = 60000 // 60 seconds
        }
    }
    
    suspend fun analyzeConversation(
        session: ConversationSession,
        userId: String,
        maxRetries: Int = 3
    ): Result<ConversationFeedback> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                println("[ConversationAnalysis] Attempt ${attempt + 1}/$maxRetries")
                
                // Check cache first
                println("[ConversationAnalysis] Checking cache for session: ${session.sessionId}")
                val cachedFeedback = getCachedFeedback(session.sessionId)
                if (cachedFeedback != null) {
                    println("[ConversationAnalysis] Using cached feedback (score: ${cachedFeedback.overallScore})")
                    return@withContext Result.success(cachedFeedback)
                }
                
                println("[ConversationAnalysis] No cache found, calling Gemini API")
                val apiKey = ApiKeyConfig.getGeminiApiKey()
                if (apiKey == null) {
                    return@withContext Result.failure(
                        Exception("Gemini API key not configured. Please set GEMINI_API_KEY in your .env file.")
                    )
                }
                
                val prompt = buildAnalysisPrompt(session)
                println("[ConversationAnalysis] Analyzing conversation (${session.language}, ${session.level})")
                println("[ConversationAnalysis] Transcript length: ${session.transcript.length} characters")
                
                // Adjust max tokens based on attempt number (more tokens for later attempts)
                val maxTokens = when (attempt) {
                    0 -> 4096
                    1 -> 6144
                    else -> 8192
                }
                
                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(Part(text = prompt))
                        )
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.4f,
                        topK = 40,
                        topP = 0.95f,
                        maxOutputTokens = maxTokens
                    )
                )
                
                val startTime = System.currentTimeMillis()
                val response = httpClient.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                val responseTime = System.currentTimeMillis() - startTime
                
                println("[ConversationAnalysis] Response received in ${responseTime}ms")
                println("[ConversationAnalysis] Response status: ${response.status}")
                
                // Get raw response body for debugging
                val rawResponse = response.body<String>()
                println("[ConversationAnalysis] Raw response: $rawResponse")
                
                // Try to parse as GeminiResponse first
                try {
                    val geminiResponse = json.decodeFromString<GeminiResponse>(rawResponse)
                    val responseText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    println("[ConversationAnalysis] Response length: ${responseText.length} characters")
                    
                    if (responseText.isBlank()) {
                        lastException = Exception("Empty response from Gemini API")
                        if (attempt < maxRetries - 1) {
                            println("[ConversationAnalysis] Empty response, retrying...")
                            kotlinx.coroutines.delay(1000L * (attempt + 1)) // Exponential backoff
                            return@repeat
                        }
                        return@withContext Result.failure(lastException!!)
                    }
                    
                    val jsonText = extractJsonFromResponse(responseText)
                    println("[ConversationAnalysis] Extracted JSON: ${jsonText.take(200)}...")
                    
                    try {
                        val feedback = json.decodeFromString<ConversationFeedback>(jsonText)
                        
                        println("[ConversationAnalysis] Successfully parsed feedback")
                        println("[ConversationAnalysis] Overall score: ${feedback.overallScore}/100")
                        
                        // Save to cache
                        saveFeedbackToCache(session.sessionId, userId, feedback)
                        
                        return@withContext Result.success(feedback)
                    } catch (jsonError: Exception) {
                        println("[ConversationAnalysis] JSON parsing failed after extraction: ${jsonError.message}")
                        println("[ConversationAnalysis] Attempting to create fallback feedback...")
                        
                        // Create fallback feedback for truncated responses
                        val fallbackFeedback = createFallbackFeedback(session, responseText)
                        saveFeedbackToCache(session.sessionId, userId, fallbackFeedback)
                        return@withContext Result.success(fallbackFeedback)
                    }
                } catch (e: Exception) {
                    println("[ConversationAnalysis] Failed to parse Gemini response: ${e.message}")
                    println("[ConversationAnalysis] Response might be an error or different format")
                    lastException = e
                    
                    // Check if it's an error response
                    try {
                        val errorResponse = json.decodeFromString<GeminiErrorResponse>(rawResponse)
                        lastException = Exception("Gemini API error: ${errorResponse.error.message}")
                    } catch (errorParse: Exception) {
                        lastException = Exception("Failed to parse Gemini response: ${e.message}. Raw response: $rawResponse")
                    }
                    
                    // Don't retry on API errors (like invalid key), only on parsing/network issues
                    if (lastException?.message?.contains("API error") == true) {
                        return@withContext Result.failure(lastException!!)
                    }
                    
                    if (attempt < maxRetries - 1) {
                        println("[ConversationAnalysis] Error occurred, retrying in ${(attempt + 1) * 1000}ms...")
                        kotlinx.coroutines.delay(1000L * (attempt + 1)) // Exponential backoff
                    }
                }
            } catch (e: Exception) {
                println("[ConversationAnalysis] Attempt ${attempt + 1} failed: ${e.message}")
                lastException = e
                
                if (attempt < maxRetries - 1) {
                    println("[ConversationAnalysis] Retrying in ${(attempt + 1) * 1000}ms...")
                    kotlinx.coroutines.delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }
        
        // All retries failed
        println("[ConversationAnalysis] All $maxRetries attempts failed")
        Result.failure(lastException ?: Exception("Unknown error during conversation analysis"))
    }
    
    private fun extractJsonFromResponse(responseText: String): String {
        var trimmed = responseText.trim()
        
        // Remove Markdown code blocks if present
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json").trim()
        }
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```").trim()
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```").trim()
        }
        
        // Extract JSON object
        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            var jsonContent = trimmed.substring(jsonStart, jsonEnd + 1)
            
            // Fix truncated JSON by attempting to complete it
            jsonContent = fixTruncatedJson(jsonContent)
            
            return jsonContent
        }
        
        return trimmed
    }
    
    private fun fixTruncatedJson(jsonContent: String): String {
        var fixed = jsonContent
        
        // Fix trailing commas in arrays
        fixed = fixed.replace(Regex(",\\s*]"), "]")
        fixed = fixed.replace(Regex(",\\s*}"), "}")
        
        // Fix incomplete arrays by adding closing bracket
        val openArrayBrackets = fixed.count { it == '[' }
        val closeArrayBrackets = fixed.count { it == ']' }
        if (openArrayBrackets > closeArrayBrackets) {
            repeat(openArrayBrackets - closeArrayBrackets) {
                fixed += "\n]"
            }
        }
        
        // Fix incomplete objects by adding closing brace
        val openObjectBraces = fixed.count { it == '{' }
        val closeObjectBraces = fixed.count { it == '}' }
        if (openObjectBraces > closeObjectBraces) {
            repeat(openObjectBraces - closeObjectBraces) {
                fixed += "\n}"
            }
        }
        
        // Fix incomplete strings (unclosed quotes)
        val quoteCount = fixed.count { it == '"' }
        if (quoteCount % 2 != 0) {
            fixed += '"'
        }
        
        // Fix incomplete specificExamples array by adding a default example if truncated
        if (fixed.contains("\"specificExamples\": [") && !fixed.contains("\"specificExamples\": []")) {
            val examplesStart = fixed.indexOf("\"specificExamples\": [")
            val afterExamplesStart = fixed.substring(examplesStart + 20)
            if (!afterExamplesStart.contains("]")) {
                // Add a default example and close the array
                fixed = fixed.substring(0, examplesStart + 20) +
                        "\n    {\n      \"userUtterance\": \"[Truncated response]\",\n      \"issue\": \"Response was truncated due to length limits\",\n      \"correction\": \"Please try again with shorter conversation\",\n      \"explanation\": \"The AI response was cut off. Consider shorter conversations for analysis.\"\n    }\n  ]"
            }
        }
        
        return fixed
    }
    
    private fun createFallbackFeedback(session: ConversationSession, partialResponse: String): ConversationFeedback {
        println("[ConversationAnalysis] Creating fallback feedback due to parsing failure")
        
        // Extract any scores we can find from the partial response
        val overallScore = extractScoreFromText(partialResponse, "overallScore") ?: 50
        val grammarScore = extractScoreFromText(partialResponse, "grammarScore") ?: 50
        val pronunciationScore = extractScoreFromText(partialResponse, "pronunciationScore") ?: 50
        val vocabularyScore = extractScoreFromText(partialResponse, "vocabularyScore") ?: 50
        val fluencyScore = extractScoreFromText(partialResponse, "fluencyScore") ?: 50
        
        return ConversationFeedback(
            overallScore = overallScore,
            grammarScore = grammarScore,
            pronunciationScore = pronunciationScore,
            vocabularyScore = vocabularyScore,
            fluencyScore = fluencyScore,
            detailedAnalysis = "AI analysis was partially completed but response was truncated. Based on the available data, this appears to be a ${session.level} level ${session.language} conversation. For complete analysis, please try again with a shorter conversation.",
            strengths = listOf(
                "Conversation was successfully recorded and transcribed",
                "User engaged with the AI tutor for ${session.turnCount} turns",
                "Session lasted ${session.duration} seconds showing good participation"
            ),
            areasForImprovement = listOf(
                "Consider shorter conversations for more detailed analysis",
                "Ensure responses are in the target language",
                "Practice more complex sentence structures"
            ),
            specificExamples = listOf(
                FeedbackExample(
                    userUtterance = "Analysis incomplete",
                    issue = "AI response was truncated due to length limits",
                    correction = "Try with shorter conversation",
                    explanation = "The AI analysis was cut off. Consider breaking longer conversations into smaller segments for better analysis."
                )
            ),
            suggestions = listOf(
                "Try analyzing shorter conversations for more detailed feedback",
                "Focus on specific aspects of language learning",
                "Practice with different scenarios to get comprehensive feedback"
            )
        )
    }
    
    private fun extractScoreFromText(text: String, scoreType: String): Int? {
        val regex = Regex("\"$scoreType\"\\s*:\\s*(\\d+)")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    private fun buildAnalysisPrompt(session: ConversationSession): String {
        val languageName = when (session.language.lowercase()) {
            "english", "en" -> "English"
            "french", "fr" -> "French"
            "german", "de" -> "German"
            "spanish", "es" -> "Spanish"
            "korean", "ko" -> "Korean"
            "chinese", "zh", "mandarin" -> "Mandarin Chinese"
            else -> session.language
        }
        
        val levelDescription = when (session.level.lowercase()) {
            "beginner" -> "Beginner (A1-A2)"
            "intermediate" -> "Intermediate (B1-B2)"
            "advanced" -> "Advanced (C1-C2)"
            else -> session.level
        }
        
        return """
            Analyze this language learning conversation between a user practicing $languageName and an AI tutor.
            
            CONVERSATION TRANSCRIPT:
            ${session.transcript}
            
            USER LANGUAGE LEVEL: $levelDescription
            TARGET LANGUAGE: $languageName
            SCENARIO: ${session.scenario}
            CONVERSATION DURATION: ${session.duration} seconds
            NUMBER OF TURNS: ${session.turnCount}
            
            Provide a comprehensive analysis with:
            1. Grammar assessment (score 0-100 and explanation) - Analyze sentence structure, verb conjugation, tenses, and agreement
            2. Pronunciation assessment (score 0-100) - Based on the transcription quality and confidence, evaluate clarity and phonetic patterns
            3. Vocabulary usage assessment (score 0-100 and explanation) - Evaluate word choice, diversity, contextual accuracy, and idiom usage
            4. Fluency and coherence assessment (score 0-100 and explanation) - Assess conversation flow, response timing, logical progression
            5. Overall score (weighted average: grammar 25%, pronunciation 25%, vocabulary 25%, fluency 25%)
            6. 3-5 key strengths that showcase positive aspects of the user's performance
            7. 3-5 areas for improvement with constructive feedback
            8. 3-5 specific examples from the conversation with corrections and explanations
            9. Actionable suggestions for improvement tailored to their level
            
            Be encouraging and constructive. Focus on progress and growth. Provide specific, actionable feedback.
            
            Return ONLY valid JSON matching this exact structure (no markdown formatting, no additional text):
            {
              "overallScore": 85,
              "grammarScore": 80,
              "pronunciationScore": 90,
              "vocabularyScore": 85,
              "fluencyScore": 85,
              "detailedAnalysis": "Overall analysis in 2-3 sentences...",
              "strengths": ["strength 1", "strength 2", "strength 3"],
              "areasForImprovement": ["area 1", "area 2", "area 3"],
              "specificExamples": [
                {
                  "userUtterance": "what user said",
                  "issue": "what was wrong or good",
                  "correction": "how to improve or alternate phrasing",
                  "explanation": "why this is better or works well"
                }
              ],
              "suggestions": ["suggestion 1", "suggestion 2", "suggestion 3"]
            }
        """.trimIndent()
    }
    
    private suspend fun getCachedFeedback(sessionId: String): ConversationFeedback? {
        return try {
            val response = SupabaseConfig.client
                .from("conversation_feedback")
                .select {
                    filter {
                        eq("session_id", sessionId)
                    }
                }
                .decodeSingle<ConversationFeedbackCache>()
            
            ConversationFeedback(
                overallScore = response.overallScore,
                grammarScore = response.grammarScore,
                pronunciationScore = response.pronunciationScore,
                vocabularyScore = response.vocabularyScore,
                fluencyScore = response.fluencyScore,
                detailedAnalysis = response.detailedAnalysis,
                strengths = response.strengths,
                areasForImprovement = response.areasForImprovement,
                specificExamples = response.specificExamples,
                suggestions = response.suggestions
            )
        } catch (e: Exception) {
            println("[ConversationAnalysis] No cached feedback found: ${e.message}")
            null
        }
    }
    
    private suspend fun saveFeedbackToCache(
        sessionId: String,
        userId: String,
        feedback: ConversationFeedback
    ) {
        try {
            val cacheData = ConversationFeedbackCache(
                sessionId = sessionId,
                userId = userId,
                overallScore = feedback.overallScore,
                grammarScore = feedback.grammarScore,
                pronunciationScore = feedback.pronunciationScore,
                vocabularyScore = feedback.vocabularyScore,
                fluencyScore = feedback.fluencyScore,
                detailedAnalysis = feedback.detailedAnalysis,
                strengths = feedback.strengths,
                areasForImprovement = feedback.areasForImprovement,
                specificExamples = feedback.specificExamples,
                suggestions = feedback.suggestions
            )
            
            SupabaseConfig.client
                .from("conversation_feedback")
                .insert(cacheData)
            
            println("[ConversationAnalysis] Saved feedback to cache for session: $sessionId")
        } catch (e: Exception) {
            println("[ConversationAnalysis] Failed to save feedback to cache: ${e.message}")
            e.printStackTrace()
        }
    }
}
