package org.example.project.data.repository

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.ai.*
import org.example.project.core.auth.RealSupabaseAuthService
import org.example.project.core.config.SupabaseConfig
import org.example.project.core.onboarding.OnboardingRepository
import org.example.project.core.utils.ErrorLogger
import org.example.project.data.dto.ChatMessageDTO
import org.example.project.data.dto.ChatSessionDTO
import org.example.project.domain.model.ChatBot
import org.example.project.domain.model.ChatMessage
import org.example.project.domain.model.ChatSession
import org.example.project.domain.model.MessageSender
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val LOG_TAG = "AIChatRepositoryImpl.kt"

class AIChatRepositoryImpl(
    private val aiService: AIBackendService = AIBackendService(),
    private val authService: RealSupabaseAuthService = RealSupabaseAuthService(),
    private val onboardingRepository: OnboardingRepository = OnboardingRepository(),
    private val userDataService: UserDataService = UserDataService(),
) : AIChatRepository {
    private val supabase = SupabaseConfig.client

    private val sessions = mutableMapOf<String, ChatSession>()
    private val messages = mutableMapOf<String, MutableList<ChatMessage>>()
    
    private var cachedUserContext: AIUserContext? = null
    private var cachedUserId: String? = null
    private var contextCacheTime: Long = 0
    private val cacheValidityDuration = 5 * 60 * 1000L
    
    private data class CachedAIResponse(
        val response: String,
        val timestamp: Long,
        val provider: AIProvider,
        val tokensUsed: Int?
    )
    private val aiResponseCache = mutableMapOf<String, CachedAIResponse>()
    private val responseCacheValidityDuration = 2 * 60 * 1000L

    override suspend fun createChatSession(
        userId: String,
        botId: String,
    ): Result<ChatSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bot = getChatBot(botId).getOrNull() ?: throw Exception("Bot not found: $botId")

                val actualUserId = authService.getCurrentUser().getOrNull()?.id ?: userId

                println("Creating session with user: $actualUserId, bot: $botId")

                val session =
                    ChatSession(
                        id = UUID.randomUUID().toString(),
                        title = "Chat with ${bot.name}",
                        startTime = System.currentTimeMillis(),
                        endTime = null,
                        messageCount = 0,
                        topic = "General Conversation",
                        difficulty = bot.difficulty,
                    )

                try {
                    val sessionDTO = ChatSessionDTO.fromDomain(session, actualUserId, botId)
                    supabase.postgrest["chat_sessions"]
                        .insert(sessionDTO) {
                            select(Columns.ALL)
                        }
                    println("Session saved to Supabase: ${session.id}")
                } catch (e: Exception) {
                    ErrorLogger.logException(LOG_TAG, e, "Failed to save session to Supabase")
                }

                sessions[session.id] = session
                messages[session.id] = mutableListOf()

                session
            }
        }

    override suspend fun getChatSession(sessionId: String): Result<ChatSession?> =
        withContext(Dispatchers.IO) {
            runCatching {
                sessions[sessionId]?.let { return@runCatching it }

                try {
                    val sessionDTO =
                        supabase.postgrest["chat_sessions"]
                            .select {
                                filter {
                                    eq("id", sessionId)
                                }
                            }
                            .decodeSingleOrNull<ChatSessionDTO>()

                    sessionDTO?.let {
                        val session = it.toDomain()
                        sessions[sessionId] = session
                        session
                    }
                } catch (e: Exception) {
                    ErrorLogger.logException(LOG_TAG, e, "Failed to load session from Supabase")
                    null
                }
            }
        }

    override suspend fun getUserChatSessions(userId: String): Result<List<ChatSession>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val actualUserId = authService.getCurrentUser().getOrNull()?.id ?: userId

                try {
                    val sessionDTOs =
                        supabase.postgrest["chat_sessions"]
                            .select {
                                filter {
                                    eq("user_id", actualUserId)
                                    eq("status", "active")
                                }
                            }
                            .decodeList<ChatSessionDTO>()

                    val loadedSessions = sessionDTOs.map { it.toDomain() }

                    loadedSessions.forEach { session ->
                        sessions[session.id] = session
                    }

                    println("Loaded ${loadedSessions.size} sessions from Supabase for user: $actualUserId")
                    loadedSessions
                } catch (e: Exception) {
                    ErrorLogger.logException(LOG_TAG, e, "Failed to load sessions from Supabase")
                    sessions.values.toList()
                }
            }
        }

    override suspend fun updateChatSession(session: ChatSession): Result<ChatSession> =
        runCatching {
            sessions[session.id] = session
            session
        }

    override suspend fun deleteChatSession(sessionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                println("Deleting chat session: $sessionId")

                try {
                    supabase.postgrest["chat_sessions"]
                        .delete {
                            filter {
                                eq("id", sessionId)
                            }
                        }
                    println("Session deleted from Supabase: $sessionId")
                } catch (e: Exception) {
                    println("Failed to delete session from Supabase: ${e.message}")
                    e.printStackTrace()
                    throw Exception("Failed to delete session from database: ${e.message}")
                }

                try {
                    val deleteResult = aiService.deleteChatHistory(sessionId)
                    deleteResult.onSuccess {
                        println("Chat history deleted from backend: $sessionId")
                    }.onFailure { e ->
                        println("Failed to delete chat history from backend: ${e.message}")
                    }
                } catch (e: Exception) {
                    println("Backend deletion error (non-critical): ${e.message}")
                }

                sessions.remove(sessionId)
                messages.remove(sessionId)

                println("Session fully deleted: $sessionId")
            }
        }

    override suspend fun sendMessage(
        sessionId: String,
        messageText: String,
    ): Result<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val message =
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = messageText,
                        sender = MessageSender.USER,
                        timestamp = System.currentTimeMillis(),
                    )

                // Add to memory cache
                messages.getOrPut(sessionId) { mutableListOf() }.add(message)

                // Save to database
                try {
                    val messageDTO = ChatMessageDTO(
                        id = message.id,
                        sessionId = sessionId,
                        senderType = "user", // Use senderType instead of role
                        messageText = message.content, // Use messageText instead of content
                        metadata = message.metadata,
                        createdAt = java.time.Instant.ofEpochMilli(message.timestamp).toString(), // Use createdAt instead of timestamp
                    )

                    supabase.postgrest["chat_messages"]
                        .insert(messageDTO)
                    println("Saved message to database: ${message.id}")
                } catch (e: Exception) {
                    println("Failed to save message to database: ${e.message}")
                    // Continue even if database save fails
                }

                message
            }
        }

    override suspend fun getChatMessages(sessionId: String): Result<List<ChatMessage>> =
        runCatching {
            val memoryMessages = messages[sessionId]?.toList()
            if (memoryMessages.isNullOrEmpty()) {
                loadChatHistoryFromSupabase(sessionId)
            } else {
                memoryMessages
            }
        }

    override suspend fun getLatestMessages(
        sessionId: String,
        limit: Int,
    ): Result<List<ChatMessage>> =
        runCatching {
            messages[sessionId]?.takeLast(limit)?.toList() ?: emptyList()
        }

    override suspend fun deleteMessage(messageId: String): Result<Unit> =
        runCatching {
            messages.values.forEach { msgList ->
                msgList.removeIf { it.id == messageId }
            }
        }

    override suspend fun generateAIResponse(
        sessionId: String,
        userMessage: String,
        context: Map<String, Any>,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                println("Generating AI response for session: $sessionId")

                val userContext = buildUserContext()
                
                val cacheKey = generateCacheKey(userMessage, sessionId)
                val cachedResponse = aiResponseCache[cacheKey]
                val currentTime = System.currentTimeMillis()
                
                if (cachedResponse != null && (currentTime - cachedResponse.timestamp) < responseCacheValidityDuration) {
                    println("Using cached AI response for message")
                    
                    val formattedResponse = formatAIResponse(cachedResponse.response)
                    
                    val aiMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = formattedResponse,
                        sender = MessageSender.AI,
                        timestamp = System.currentTimeMillis(),
                        metadata = mapOf(
                            "provider" to cachedResponse.provider.name,
                            "tokens_used" to (cachedResponse.tokensUsed?.toString() ?: "0"),
                            "cached" to "true"
                        ),
                    )
                    
                    messages.getOrPut(sessionId) { mutableListOf() }.add(aiMessage)
                    
                    try {
                        val messageDTO = ChatMessageDTO(
                            id = aiMessage.id,
                            sessionId = sessionId,
                            senderType = "assistant",
                            messageText = aiMessage.content,
                            metadata = aiMessage.metadata,
                            createdAt = java.time.Instant.ofEpochMilli(aiMessage.timestamp).toString(),
                        )
                        supabase.postgrest["chat_messages"].insert(messageDTO)
                    } catch (e: Exception) {
                        println("Failed to save cached AI message: ${e.message}")
                    }
                    
                    return@runCatching cachedResponse.response
                }
                
                println("Fetching new AI response from backend")

                val history =
                    messages[sessionId]?.map { msg ->
                        AIChatMessage(
                            role = if (msg.sender == MessageSender.USER) MessageRole.USER else MessageRole.ASSISTANT,
                            content = msg.content,
                            timestamp = msg.timestamp,
                        )
                    } ?: emptyList()

                val botId = context["bot_id"] as? String

                val request =
                    AIChatRequest(
                        message = userMessage,
                        userContext = userContext,
                        conversationHistory = history,
                        provider = AIProvider.GEMINI,
                        botId = botId,
                        temperature = 0.7f,
                        maxTokens = 1000,
                    )

                val response =
                    aiService.sendChatMessage(request)
                        .getOrElse { error ->
                            throw Exception("AI backend request failed: ${error.message}")
                        }

                aiResponseCache[cacheKey] = CachedAIResponse(
                    response = response.message,
                    timestamp = System.currentTimeMillis(),
                    provider = response.provider,
                    tokensUsed = response.tokensUsed
                )
                println("AI response cached for future use")
                
                cleanupExpiredCache()
                
                val formattedResponse = formatAIResponse(response.message)

                val aiMessage =
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = formattedResponse,
                        sender = MessageSender.AI,
                        timestamp = System.currentTimeMillis(),
                        metadata =
                            mapOf(
                                "provider" to response.provider.name,
                                "tokens_used" to (response.tokensUsed?.toString() ?: "0"),
                                "cached" to "false"
                            ),
                    )

                messages.getOrPut(sessionId) { mutableListOf() }.add(aiMessage)

                // Save AI message to database
                try {
                    val messageDTO = ChatMessageDTO(
                        id = aiMessage.id,
                        sessionId = sessionId,
                        senderType = "assistant", // Use senderType instead of role
                        messageText = aiMessage.content, // Use messageText instead of content
                        metadata = aiMessage.metadata,
                        createdAt = java.time.Instant.ofEpochMilli(aiMessage.timestamp).toString(), // Use createdAt instead of timestamp
                    )

                    supabase.postgrest["chat_messages"]
                        .insert(messageDTO)
                    println("Saved AI message to database: ${aiMessage.id}")
                } catch (e: Exception) {
                    println("Failed to save AI message to database: ${e.message}")
                    // Continue even if database save fails
                }

                saveChatHistoryToSupabase(sessionId)

                formattedResponse
            }
        }

    override suspend fun getAvailableBots(): Result<List<ChatBot>> =
        runCatching {
            ChatBot.getAvailableBots()
        }

    override suspend fun getChatBot(botId: String): Result<ChatBot?> =
        runCatching {
            ChatBot.getAvailableBots().find { it.id == botId }
        }

    override suspend fun subscribeToChatMessages(
        sessionId: String,
        onMessage: (ChatMessage) -> Unit,
    ): Result<Unit> =
        runCatching {
            println("Chat message subscription not yet implemented")
        }

    override suspend fun unsubscribeFromChatMessages(sessionId: String): Result<Unit> =
        runCatching {
            println("Chat message unsubscribe not yet implemented")
        }

    private suspend fun buildUserContext(): AIUserContext {
        return try {
            val user =
                authService.getCurrentUser()
                    .getOrNull()
                    ?: return AIUserContext(userId = "anonymous")

            val currentTime = System.currentTimeMillis()
            val cacheValid = cachedUserContext != null && 
                            cachedUserId == user.id && 
                            (currentTime - contextCacheTime) < cacheValidityDuration
            
            if (cacheValid) {
                println("Using cached user context for: ${user.id}")
                return cachedUserContext!!
            }

            println("Building comprehensive context for user: ${user.id}")

            val profile =
                onboardingRepository.fetchOnboardingProfile(user.id)
                    .getOrNull()

            println("Onboarding profile loaded: ${profile != null}")

            val comprehensiveData =
                userDataService.fetchCompleteUserData(user.id)
                    .getOrNull()

            println("Comprehensive data loaded: ${comprehensiveData != null}")

            val nativeLanguage =
                user.firstName.takeIf { it.isNotBlank() }
                    ?: profile?.aiProfile?.get("native_language")?.toString()?.removeSurrounding("\"")

            val targetLanguages =
                profile?.aiProfile?.get("target_languages")?.toString()
                    ?.removeSurrounding("[", "]")
                    ?.split(",")
                    ?.map { it.trim().removeSurrounding("\"") }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

            val currentLevel =
                profile?.aiProfile?.get("current_level")?.toString()?.removeSurrounding("\"")

            val primaryGoal =
                profile?.aiProfile?.get("primary_goal")?.toString()?.removeSurrounding("\"")

            val learningStyle =
                profile?.aiProfile?.get("learning_style")?.toString()?.removeSurrounding("\"")

            val focusAreas =
                profile?.aiProfile?.get("focus_areas")?.toString()
                    ?.removeSurrounding("[", "]")
                    ?.split(",")
                    ?.map { it.trim().removeSurrounding("\"") }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

            val motivations =
                profile?.aiProfile?.get("motivations")?.toString()
                    ?.removeSurrounding("[", "]")
                    ?.split(",")
                    ?.map { it.trim().removeSurrounding("\"") }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

            val interests =
                profile?.aiProfile?.get("interests")?.toString()
                    ?.removeSurrounding("[", "]")
                    ?.split(",")
                    ?.map { it.trim().removeSurrounding("\"") }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

            val userContext = AIUserContext(
                userId = user.id,
                firstName = comprehensiveData?.firstName,
                lastName = comprehensiveData?.lastName,
                nativeLanguage = nativeLanguage,
                targetLanguages = targetLanguages,
                currentLevel = currentLevel,
                primaryGoal = primaryGoal,
                learningStyle = learningStyle,
                focusAreas = focusAreas,
                motivations = motivations,
                interests = interests,
                aiProfile = profile?.aiProfile,
                learningProgress =
                    comprehensiveData?.learningProgress?.let {
                        AILearningProgress(
                            overallLevel = it.overallLevel,
                            xpPoints = it.xpPoints,
                            weeklyXp = it.weeklyXp,
                            streakDays = it.streakDays,
                            longestStreak = it.longestStreak,
                            totalStudyTime = it.totalStudyTime,
                            weeklyStudyTime = it.weeklyStudyTime,
                        )
                    },
                skillProgress =
                    comprehensiveData?.skillProgress?.map {
                        AISkillProgress(
                            skillArea = it.skillArea,
                            level = it.level,
                            xpPoints = it.xpPoints,
                            accuracyPercentage = it.accuracyPercentage,
                            timeSpent = it.timeSpent,
                            exercisesCompleted = it.exercisesCompleted,
                        )
                    } ?: emptyList(),
                vocabularyStats =
                    comprehensiveData?.vocabularyStats?.let {
                        AIVocabularyStats(
                            totalWords = it.totalWords,
                            newWords = it.newWords,
                            learningWords = it.learningWords,
                            reviewingWords = it.reviewingWords,
                            masteredWords = it.masteredWords,
                            averageCorrectRate = it.averageCorrectRate,
                            words =
                                it.words.map { word ->
                                    AIVocabularyWord(
                                        word = word.word,
                                        definition = word.definition,
                                        pronunciation = word.pronunciation,
                                        exampleSentence = word.exampleSentence,
                                        difficultyLevel = word.difficultyLevel,
                                        category = word.category,
                                        status = word.status,
                                        reviewCount = word.reviewCount,
                                        correctCount = word.correctCount,
                                        lastReviewed = word.lastReviewed,
                                        nextReview = word.nextReview,
                                    )
                                },
                        )
                    },
                lessonProgress =
                    comprehensiveData?.lessonProgress?.let {
                        AILessonProgress(
                            totalLessons = it.totalLessons,
                            completedLessons = it.completedLessons,
                            inProgressLessons = it.inProgressLessons,
                            averageScore = it.averageScore,
                            totalTimeSpent = it.totalTimeSpent,
                        )
                    },
                chatHistory =
                    comprehensiveData?.chatHistory?.let {
                        AIChatHistory(
                            totalSessions = it.totalSessions,
                            totalMessages = it.totalMessages,
                            totalDuration = it.totalDuration,
                        )
                    },
                achievements = comprehensiveData?.achievements ?: emptyList(),
                userSettings =
                    comprehensiveData?.userSettings?.let {
                        AIUserSettings(
                            aiPreferences = it.aiPreferences,
                        )
                    },
            )
            
            cachedUserContext = userContext
            cachedUserId = user.id
            contextCacheTime = System.currentTimeMillis()
            println("User context cached successfully for: ${user.id}")
            
            userContext
        } catch (e: Exception) {
            println("Failed to build user context: ${e.message}")
            e.printStackTrace()
            AIUserContext(userId = "anonymous")
        }
    }

    private fun generateCacheKey(message: String, sessionId: String): String {
        val normalizedMessage = message.trim().lowercase()
        return "${sessionId}_${normalizedMessage.hashCode()}"
    }
    
    private fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = aiResponseCache.filter { (_, cached) ->
            (currentTime - cached.timestamp) >= responseCacheValidityDuration
        }.keys
        
        if (expiredKeys.isNotEmpty()) {
            expiredKeys.forEach { aiResponseCache.remove(it) }
            println("Cleaned up ${expiredKeys.size} expired AI response cache entries")
        }
    }
    
    private fun formatAIResponse(response: String): String {
        var formatted = response
        
        formatted = formatted.replace(Regex("""\*{3,}"""), "**")
        
        formatted = formatted.replace(Regex("""\*\*([^*]+)\*\*:"""), "$1:")
        
        formatted = formatted.replace(Regex("""\*\*"([^"]+)"\*\*"""), "\"$1\"")
        
        formatted = formatted.replace(Regex("""\*\s+\*\*([^*]+)\*\*:"""), "• $1:")
        
        formatted = formatted.replace(Regex("""^\*{2,3}([^*\n]+)\*{2,3}$""", RegexOption.MULTILINE), "**$1**")
        
        formatted = formatted.trim()
        
        return formatted
    }
    
    fun clearAIResponseCache() {
        val count = aiResponseCache.size
        aiResponseCache.clear()
        println("Cleared $count AI response cache entries")
    }

    private suspend fun saveChatHistoryToSupabase(sessionId: String) {
        try {
            val sessionMessages = messages[sessionId] ?: return

            if (sessionMessages.isEmpty()) {
                return
            }

            // Save messages to chat_messages table
            val messageDTOs = sessionMessages.map { msg ->
                ChatMessageDTO(
                    id = msg.id,
                    sessionId = sessionId,
                    senderType = when (msg.sender) { // Use senderType instead of role
                        MessageSender.USER -> "user"
                        MessageSender.AI -> "assistant"
                    },
                    messageText = msg.content, // Use messageText instead of content
                    metadata = msg.metadata,
                    createdAt = java.time.Instant.ofEpochMilli(msg.timestamp).toString(), // Use createdAt instead of timestamp
                )
            }

            try {
                supabase.postgrest["chat_messages"]
                    .upsert(messageDTOs)
                println("Saved ${messageDTOs.size} messages to chat_messages table")
            } catch (e: Exception) {
                println("Failed to save messages to database: ${e.message}")
                e.printStackTrace()
            }

            // Also save to AI backend as backup
            val aiMessages = sessionMessages.map { msg ->
                AIChatMessage(
                    role = if (msg.sender == MessageSender.USER) MessageRole.USER else MessageRole.ASSISTANT,
                    content = msg.content,
                    timestamp = msg.timestamp,
                )
            }

            val request = SaveHistoryRequest(
                sessionId = sessionId,
                messages = aiMessages,
                compress = true,
            )

            val result = aiService.saveChatHistory(request)
                .onSuccess { response ->
                    println("Saved ${response.messageCount} messages to AI backend")
                    println(
                        "   Compression: ${response.compressionRatio}% saved (${response.originalSize} -> ${response.compressedSize} bytes)",
                    )
                }
                .onFailure { error ->
                    println("Failed to save chat history to AI backend: ${error.message}")
                }
        } catch (e: Exception) {
            println("Error saving chat history: ${e.message}")
        }
    }

    private suspend fun loadChatHistoryFromSupabase(sessionId: String): List<ChatMessage> {
        return try {
            // Load messages from chat_messages table
            val messageDTOs = supabase.postgrest["chat_messages"]
                .select {
                    filter {
                        eq("session_id", sessionId)
                    }
                }
                .decodeList<ChatMessageDTO>()

            if (messageDTOs.isNotEmpty()) {
                println("Loaded ${messageDTOs.size} messages from Supabase for session: $sessionId")

                val chatMessages = messageDTOs.map { msgDTO ->
                    ChatMessage(
                        id = msgDTO.id,
                        content = msgDTO.messageText, // Use messageText instead of content
                        sender = when (msgDTO.senderType) { // Use senderType instead of role
                            "user" -> MessageSender.USER
                            "assistant" -> MessageSender.AI
                            else -> MessageSender.AI
                        },
                        timestamp = msgDTO.createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis(), // Use createdAt instead of timestamp
                        metadata = msgDTO.metadata ?: emptyMap(),
                    )
                }

                messages[sessionId] = chatMessages.toMutableList()
                chatMessages
            } else {
                // Fallback to AI backend if no messages in database
                println("No messages in database, trying AI backend for session: $sessionId")
                loadChatHistoryFromBackend(sessionId)
            }
        } catch (e: Exception) {
            println("Error loading chat history from database: ${e.message}")
            // Fallback to AI backend
            loadChatHistoryFromBackend(sessionId)
        }
    }

    private suspend fun loadChatHistoryFromBackend(sessionId: String): List<ChatMessage> {
        return try {
            val request = LoadHistoryRequest(sessionId = sessionId)
            val result = aiService.loadChatHistory(request).getOrNull()

            if (result != null && result.success) {
                println("Loaded ${result.messageCount} messages from AI backend")

                val chatMessages = result.messages.map { aiMsg ->
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = aiMsg.content,
                        sender = if (aiMsg.role == MessageRole.USER) MessageSender.USER else MessageSender.AI,
                        timestamp = aiMsg.timestamp ?: System.currentTimeMillis(),
                    )
                }

                messages[sessionId] = chatMessages.toMutableList()
                chatMessages
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error loading chat history from backend: ${e.message}")
            emptyList()
        }
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            val zonedDateTime = ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(timestamp).toEpochMilli()
            } catch (e2: Exception) {
                println("Failed to parse timestamp: $timestamp, using current time")
                System.currentTimeMillis()
            }
        }
    }
}
