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
import org.example.project.data.dto.ChatSessionDTO
import org.example.project.domain.model.ChatBot
import org.example.project.domain.model.ChatMessage
import org.example.project.domain.model.ChatSession
import org.example.project.domain.model.MessageSender

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
                        id = "session_${System.currentTimeMillis()}",
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
        runCatching {
            val message =
                ChatMessage(
                    id = "msg_${System.currentTimeMillis()}",
                    content = messageText,
                    sender = MessageSender.USER,
                    timestamp = System.currentTimeMillis(),
                )

            messages.getOrPut(sessionId) { mutableListOf() }.add(message)
            message
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

                
                val aiMessage =
                    ChatMessage(
                        id = "msg_${System.currentTimeMillis()}",
                        content = response.message,
                        sender = MessageSender.AI,
                        timestamp = System.currentTimeMillis(),
                        metadata =
                            mapOf(
                                "provider" to response.provider.name,
                                "tokens_used" to (response.tokensUsed?.toString() ?: "0"),
                            ),
                    )

                messages.getOrPut(sessionId) { mutableListOf() }.add(aiMessage)

                
                saveChatHistoryToSupabase(sessionId)

                response.message
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

            
            AIUserContext(
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
        } catch (e: Exception) {
            println("Failed to build user context: ${e.message}")
            e.printStackTrace()
            AIUserContext(userId = "anonymous")
        }
    }


    private suspend fun saveChatHistoryToSupabase(sessionId: String) {
        try {
            val sessionMessages = messages[sessionId] ?: return

            if (sessionMessages.isEmpty()) {
                return
            }

            val aiMessages =
                sessionMessages.map { msg ->
                    AIChatMessage(
                        role = if (msg.sender == MessageSender.USER) MessageRole.USER else MessageRole.ASSISTANT,
                        content = msg.content,
                        timestamp = msg.timestamp,
                    )
                }

            val request =
                SaveHistoryRequest(
                    sessionId = sessionId,
                    messages = aiMessages,
                    compress = true,
                )

            val result =
                aiService.saveChatHistory(request)
                    .onSuccess { response ->
                        println("Saved ${response.messageCount} messages to Supabase")
                        println("   Compression: ${response.compressionRatio}% saved (${response.originalSize} -> ${response.compressedSize} bytes)")
                    }
                    .onFailure { error ->
                        println("Failed to save chat history: ${error.message}")
                    }
        } catch (e: Exception) {
            println("Error saving chat history: ${e.message}")
            
        }
    }
    private suspend fun loadChatHistoryFromSupabase(sessionId: String): List<ChatMessage> {
        return try {
            val request = LoadHistoryRequest(sessionId = sessionId)

            val result =
                aiService.loadChatHistory(request)
                    .getOrNull()

            if (result != null && result.success) {
                println(" Loaded ${result.messageCount} messages from Supabase")

                
                val chatMessages =
                    result.messages.map { aiMsg ->
                        ChatMessage(
                            id = "msg_${aiMsg.timestamp ?: System.currentTimeMillis()}",
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
            println(" Error loading chat history: ${e.message}")
            emptyList()
        }
    }
}

