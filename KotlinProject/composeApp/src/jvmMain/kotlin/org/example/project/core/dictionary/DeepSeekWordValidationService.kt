package org.example.project.core.dictionary

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.github.cdimascio.dotenv.dotenv
import java.io.File

/**
 * Service to validate words using DeepSeek API
 * Checks if a word exists in the specified language
 */
class DeepSeekWordValidationService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // Find .env file using similar logic to SupabaseConfig
    private fun findEnvFile(): File? {
        val currentDir = File(System.getProperty("user.dir"))
        
        // List of possible .env locations
        val possibleLocations = listOf(
            File(currentDir, ".env"),
            File(currentDir, "env.config"),
            File(currentDir, "KotlinProject/composeApp/.env"),
            File(currentDir, "composeApp/.env"),
            File(System.getProperty("user.home"), "AppData\\Local\\WordBridge\\.env")
        )
        
        return possibleLocations.firstOrNull { it.exists() && it.isFile }
    }

    // Initialize dotenv with proper file finding
    private val dotenv = try {
        val envFile = findEnvFile()
        if (envFile != null && envFile.exists()) {
            println("DeepSeek: Loading .env from: ${envFile.absolutePath}")
            dotenv {
                ignoreIfMissing = false
                directory = envFile.parentFile.absolutePath
                filename = envFile.name
            }
        } else {
            println("DeepSeek: .env file not found, trying default locations")
            dotenv {
                ignoreIfMissing = true
                directory = "KotlinProject/composeApp"
            }
        }
    } catch (e: Exception) {
        println("DeepSeek: Failed to load .env from primary location: ${e.message}")
        try {
            dotenv {
                ignoreIfMissing = true
                directory = "composeApp"
            }
        } catch (e2: Exception) {
            println("DeepSeek: Failed to load .env from composeApp, trying current directory")
            dotenv {
                ignoreIfMissing = true
            }
        }
    }

    private val apiKey: String by lazy {
        val key = dotenv["DEEPSEEK_API_KEY"] ?: System.getenv("DEEPSEEK_API_KEY") ?: ""
        if (key.isBlank()) {
            println("DeepSeek: DEEPSEEK_API_KEY not found in .env or environment variables")
            println("DeepSeek: Checked .env file: ${findEnvFile()?.absolutePath ?: "not found"}")
            println("DeepSeek: Current working directory: ${System.getProperty("user.dir")}")
        } else {
            println("DeepSeek: API key loaded successfully (length: ${key.length})")
        }
        key
    }

    /**
     * Map LessonLanguage codes to DeepSeek language codes
     */
    private fun getLanguageCode(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "fr" -> "French"
            "de" -> "German"
            "es" -> "Spanish"
            "en" -> "English"
            else -> languageCode
        }
    }

    /**
     * Validate if a word exists in the specified language
     * Returns a WordDefinition if the word is valid, or null if not found
     */
    suspend fun validateWord(
        word: String,
        languageCode: String
    ): Result<WordDefinition> = runCatching {
        val key = apiKey
        if (key.isBlank()) {
            val envFile = findEnvFile()
            val errorMsg = buildString {
                append("DeepSeek API key is not configured. ")
                append("Please set DEEPSEEK_API_KEY in your .env file. ")
                if (envFile != null) {
                    append("Expected location: ${envFile.absolutePath}")
                } else {
                    append("Current working directory: ${System.getProperty("user.dir")}")
                }
            }
            println("DeepSeek Error: $errorMsg")
            throw Exception(errorMsg)
        }

        val languageName = getLanguageCode(languageCode)
        val trimmedWord = word.trim()
        
        // Create a prompt to check if the word exists and get its definition
        val prompt = """
            You are a $languageName language validator. I need you to validate the word: "$trimmedWord"
            
            IMPORTANT: Validate the exact word "$trimmedWord" that I provided. Do NOT use example words or default words.
            
            Your task:
            1. Check if "$trimmedWord" is a valid word in $languageName language
            2. If it's an English word, check if it has a valid translation in $languageName
            3. If "$trimmedWord" is already in $languageName, verify it's a real word
            4. Respond with ONLY a JSON object in this exact format with no additional text, comments, or markdown:
            
            {
                "exists": true or false,
                "word": "the validated $languageName word (use the original word if it's already in $languageName, or the translation if it was English)",
                "definition": "definition in English",
                "pronunciation": "romanized pronunciation or phonetic spelling",
                "partOfSpeech": "noun/verb/adjective/etc",
                "example": "an example sentence using the word in $languageName"
            }
            
            CRITICAL: 
            - If "$trimmedWord" does not exist or cannot be translated to a valid $languageName word, set "exists" to false
            - The "word" field must be the actual validated word, NOT a default example word
            - Only respond with valid JSON, no markdown code blocks, no explanations
        """.trimIndent()
        
        println("DeepSeek: Validating word '$trimmedWord' in $languageName")

        val requestBody = DeepSeekRequest(
            model = "deepseek-chat",
            messages = listOf(
                DeepSeekMessage(
                    role = "system",
                    content = "You are a helpful language validator. Always respond with valid JSON only, no markdown, no explanations."
                ),
                DeepSeekMessage(
                    role = "user",
                    content = prompt
                )
            ),
            temperature = 0.1, // Lower temperature for more consistent responses
            maxTokens = 800
        )

        val response = client.post("https://api.deepseek.com/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(requestBody)
        }

        if (response.status.value in 200..299) {
            val apiResponse = response.body<DeepSeekResponse>()
            var content = apiResponse.choices.firstOrNull()?.message?.content ?: ""
            
            println("DeepSeek: Raw response: $content")
            
            // Remove markdown code blocks if present
            content = content.trim()
            if (content.startsWith("```json")) {
                content = content.removePrefix("```json").trim()
            }
            if (content.startsWith("```")) {
                content = content.removePrefix("```").trim()
            }
            if (content.endsWith("```")) {
                content = content.removeSuffix("```").trim()
            }
            
            // Parse the JSON response from DeepSeek
            val jsonResponse = try {
                Json.parseToJsonElement(content).jsonObject
            } catch (e: Exception) {
                println("DeepSeek: JSON parsing failed: ${e.message}")
                println("DeepSeek: Attempting to parse text response")
                // If not JSON, try to extract information from text
                return parseTextResponse(content, trimmedWord, languageName)
            }

            val exists = jsonResponse["exists"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val returnedWord = jsonResponse["word"]?.jsonPrimitive?.content?.trim() ?: trimmedWord
            
            println("DeepSeek: Word exists: $exists, Returned word: '$returnedWord'")
            
            // Validate that the returned word matches what we're looking for (or is a valid translation)
            // If the word is completely different and not a translation, it might be a default word
            if (returnedWord.lowercase() == "사과" && trimmedWord.lowercase() != "apple" && trimmedWord.lowercase() != "사과") {
                println("DeepSeek: Warning - returned default Korean word '사과' for input '$trimmedWord'")
            }
            if (returnedWord.lowercase() == "casa" && trimmedWord.lowercase() != "house" && trimmedWord.lowercase() != "casa") {
                println("DeepSeek: Warning - returned default Spanish word 'casa' for input '$trimmedWord'")
            }
            
            if (!exists) {
                throw WordNotFoundException("Word '$trimmedWord' does not exist in $languageName")
            }

            WordDefinition(
                word = returnedWord,
                definition = jsonResponse["definition"]?.jsonPrimitive?.content 
                    ?: "Definition not available",
                pronunciation = jsonResponse["pronunciation"]?.jsonPrimitive?.content ?: "",
                audio = "", // DeepSeek doesn't provide audio URLs
                example = jsonResponse["example"]?.jsonPrimitive?.content,
                partOfSpeech = jsonResponse["partOfSpeech"]?.jsonPrimitive?.content ?: "Unknown"
            )
        } else {
            throw Exception("DeepSeek API request failed with status: ${response.status.value}")
        }
    }

    /**
     * Parse text response if JSON parsing fails
     */
    private fun parseTextResponse(
        content: String,
        word: String,
        languageName: String
    ): Result<WordDefinition> {
        println("DeepSeek: Parsing text response for word '$word'")
        println("DeepSeek: Content: $content")
        
        // Try to extract information from text response
        val exists = content.contains("\"exists\":true", ignoreCase = true) || 
                    content.contains("exists\": true", ignoreCase = true) ||
                    content.contains("does exist", ignoreCase = true) ||
                    (!content.contains("does not exist", ignoreCase = true) && 
                     !content.contains("\"exists\":false", ignoreCase = true) &&
                     !content.contains("exists\": false", ignoreCase = true))
        
        if (!exists) {
            return Result.failure(WordNotFoundException("Word '$word' does not exist in $languageName"))
        }
        
        // Extract word (look for "word": "value" pattern)
        val wordMatch = Regex("\"word\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(content)
        val extractedWord = wordMatch?.groupValues?.get(1)?.trim() ?: word
        
        // Extract definition (look for common patterns)
        val definitionMatch = Regex("\"definition\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(content) ?: Regex("definition[:\"']\\s*([^\"]+)", RegexOption.IGNORE_CASE)
            .find(content)
        val definition = definitionMatch?.groupValues?.get(1)?.trim() ?: "Definition not available"
        
        // Extract example
        val exampleMatch = Regex("\"example\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(content) ?: Regex("example[:\"']\\s*([^\"]+)", RegexOption.IGNORE_CASE)
            .find(content)
        val example = exampleMatch?.groupValues?.get(1)?.trim()
        
        // Extract part of speech
        val posMatch = Regex("\"partOfSpeech\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(content) ?: Regex("partOfSpeech[:\"']\\s*([^\"]+)", RegexOption.IGNORE_CASE)
            .find(content)
        val partOfSpeech = posMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
        
        // Extract pronunciation
        val pronunciationMatch = Regex("\"pronunciation\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(content) ?: Regex("pronunciation[:\"']\\s*([^\"]+)", RegexOption.IGNORE_CASE)
            .find(content)
        val pronunciation = pronunciationMatch?.groupValues?.get(1)?.trim() ?: ""
        
        println("DeepSeek: Extracted word: '$extractedWord' (original: '$word')")
        
        return Result.success(
            WordDefinition(
                word = extractedWord,
                definition = definition,
                pronunciation = pronunciation,
                audio = "",
                example = example,
                partOfSpeech = partOfSpeech
            )
        )
    }
}

@Serializable
private data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
)

@Serializable
private data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
private data class DeepSeekResponse(
    val id: String,
    val choices: List<DeepSeekChoice>
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekMessage,
    val finishReason: String? = null
)

