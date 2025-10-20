package org.example.project.presentation.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.core.ai.BackendManager
import org.example.project.data.repository.AIChatRepository
import org.example.project.data.repository.AIChatRepositoryImpl
import org.example.project.domain.model.ChatBot
import org.example.project.domain.model.ChatFeature
import org.example.project.domain.model.ChatMessage
import org.example.project.domain.model.ChatSession
import org.example.project.domain.model.MessageSender
import org.example.project.domain.model.MessageType

class AIChatViewModel(
    private val repository: AIChatRepository = AIChatRepositoryImpl(),
) : ViewModel() {
    // Backend is now started in App.kt when user authenticates
    // No need to start it here to prevent UI freeze
    
    private val _chatMessages = mutableStateOf(ChatMessage.getSampleMessages())
    private val _chatSessions = mutableStateOf(ChatSession.getSampleSessions())
    private val _availableBots = mutableStateOf(ChatBot.getAvailableBots())
    private val _chatFeatures = mutableStateOf(ChatFeature.getChatFeatures())
    private val _selectedBot = mutableStateOf<ChatBot?>(null)
    private val _currentMessage = mutableStateOf("")
    private val _isTyping = mutableStateOf(false)
    private val _isLoading = mutableStateOf(false)
    private val _currentSession = mutableStateOf<ChatSession?>(null)
    private val _error = mutableStateOf<String?>(null)

    val chatMessages: State<List<ChatMessage>> = _chatMessages
    val chatSessions: State<List<ChatSession>> = _chatSessions
    val availableBots: State<List<ChatBot>> = _availableBots
    val chatFeatures: State<List<ChatFeature>> = _chatFeatures
    val selectedBot: State<ChatBot?> = _selectedBot
    val currentMessage: State<String> = _currentMessage
    val isTyping: State<Boolean> = _isTyping
    val isLoading: State<Boolean> = _isLoading
    val currentSession: State<ChatSession?> = _currentSession
    val error: State<String?> = _error

    fun onMessageChanged(message: String) {
        _currentMessage.value = message
    }

    fun onSendMessage() {
        val messageText = _currentMessage.value.trim()
        if (messageText.isEmpty()) return

        // Check if we have an active session
        val session = _currentSession.value
        if (session == null) {
            println("No active session, cannot send message")
            _error.value = "Please select a bot to start chatting"
            return
        }

        // Clear input immediately
        _currentMessage.value = ""

        // Add user message to UI
        val userMessage =
            ChatMessage(
                id = "msg_${System.currentTimeMillis()}",
                content = messageText,
                sender = MessageSender.USER,
                timestamp = System.currentTimeMillis(),
                type = MessageType.TEXT,
            )

        _chatMessages.value = _chatMessages.value + userMessage
        _isTyping.value = true
        _error.value = null

        // Generate AI response
        viewModelScope.launch {
            try {
                // Quick health check (backend should already be running from app startup)
                // This is just a safety check and won't block UI
                println("Sending message to AI: $messageText")

                // Save user message to repository
                repository.sendMessage(session.id, messageText)
                    .onFailure { e ->
                        println("Failed to save user message: ${e.message}")
                    }

                // Generate AI response
                val botContext =
                    mapOf(
                        "bot_id" to (_selectedBot.value?.id ?: ""),
                    )

                val aiResponse =
                    repository.generateAIResponse(
                        sessionId = session.id,
                        userMessage = messageText,
                        context = botContext,
                    ).getOrThrow()

                println("Received AI response")

                // Add a small delay to simulate typing
                delay(500)

                // Add AI message to UI
                val aiMessage =
                    ChatMessage(
                        id = "ai_${System.currentTimeMillis()}",
                        content = aiResponse,
                        sender = MessageSender.AI,
                        timestamp = System.currentTimeMillis(),
                        type = MessageType.TEXT,
                    )

                _isTyping.value = false
                _chatMessages.value = _chatMessages.value + aiMessage
            } catch (e: Exception) {
                println("Failed to get AI response: ${e.message}")
                e.printStackTrace()

                _isTyping.value = false
                _error.value = "Failed to get response: ${e.message}"

                // Add error message to chat
                val errorMessage =
                    ChatMessage(
                        id = "error_${System.currentTimeMillis()}",
                        content = "Sorry, I'm having trouble connecting. Please check that the AI backend is running.",
                        sender = MessageSender.AI,
                        timestamp = System.currentTimeMillis(),
                        type = MessageType.SYSTEM,
                    )
                _chatMessages.value = _chatMessages.value + errorMessage
            }
        }
    }

    fun onBotSelected(bot: ChatBot) {
        _selectedBot.value = bot
        startNewSession(bot)
    }

    fun onStartFirstConversationClicked() {
        val defaultBot = _availableBots.value.firstOrNull()
        if (defaultBot != null) {
            onBotSelected(defaultBot)
        }
    }

    fun onExploreChatBotsClicked() {
        // TODO: Navigate to bot selection screen or show bot picker dialog
        println("Explore chat bots clicked")
    }

    fun onSessionSelected(sessionId: String) {
        viewModelScope.launch {
            try {
                println("Loading session: $sessionId")
                
                val session = repository.getChatSession(sessionId).getOrNull()
                if (session != null) {
                    _currentSession.value = session

                    // Load messages for this session (will load from Supabase if not in memory)
                    val messages = repository.getChatMessages(sessionId).getOrNull() ?: emptyList()
                    _chatMessages.value = messages
                    
                    println("Loaded session with ${messages.size} messages")
                    
                    // If no messages, show welcome message based on bot
                    if (messages.isEmpty()) {
                        // Try to get bot info
                        val bot = _availableBots.value.find { it.id == session.title } 
                            ?: _availableBots.value.firstOrNull()
                        
                        if (bot != null) {
                            val welcomeMessage = ChatMessage(
                                id = "welcome_${System.currentTimeMillis()}",
                                content = getWelcomeMessage(bot),
                                sender = MessageSender.AI,
                                timestamp = System.currentTimeMillis(),
                                type = MessageType.TEXT,
                            )
                            _chatMessages.value = listOf(welcomeMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Failed to load session: ${e.message}")
                e.printStackTrace()
                _error.value = "Failed to load session"
            }
        }
    }

    fun onNewSessionClicked() {
        _chatMessages.value = emptyList()
        _currentSession.value = null
        _selectedBot.value = null
        _error.value = null
    }

    fun onDeleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                println("Deleting session: $sessionId")
                
                // Delete from repository
                repository.deleteChatSession(sessionId).onSuccess {
                    println("Session deleted successfully")
                    
                    // Remove from local list
                    _chatSessions.value = _chatSessions.value.filter { it.id != sessionId }
                    
                    // If deleted session was current, clear it
                    if (_currentSession.value?.id == sessionId) {
                        onNewSessionClicked()
                    }
                }.onFailure { e ->
                    println("Failed to delete session: ${e.message}")
                    _error.value = "Failed to delete chat: ${e.message}"
                }
            } catch (e: Exception) {
                println("Error deleting session: ${e.message}")
                e.printStackTrace()
                _error.value = "Failed to delete chat"
            }
        }
    }

    fun onVoiceInputToggle() {
        // TODO: Implement voice input functionality
        println("Voice input toggled")
    }

    fun refreshChatData() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Load available bots
                val bots = repository.getAvailableBots().getOrNull()
                if (bots != null) {
                    _availableBots.value = bots
                }

                // Load user's chat sessions from repository
                val userId = "user" // Placeholder - actual ID fetched in repository
                val sessions = repository.getUserChatSessions(userId).getOrNull()
                if (sessions != null && sessions.isNotEmpty()) {
                    _chatSessions.value = sessions
                    println("Loaded ${sessions.size} previous chat sessions")
                }
                
                // If there's a current session, reload its messages
                _currentSession.value?.let { session ->
                    val messages = repository.getChatMessages(session.id).getOrNull()
                    if (messages != null) {
                        _chatMessages.value = messages
                        println("Reloaded ${messages.size} messages for current session")
                    }
                }
            } catch (e: Exception) {
                println("Failed to refresh chat data: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startNewSession(bot: ChatBot) {
        viewModelScope.launch {
            try {
                println("Starting new session with bot: ${bot.name}")

                // Get actual user ID (will be fetched inside repository)
                val session =
                    repository.createChatSession(
                        userId = "user", // Placeholder - actual ID fetched in repository
                        botId = bot.id,
                    ).getOrThrow()

                _currentSession.value = session
                _error.value = null

                println("Session created: ${session.id}")

                // Try to load existing messages from Supabase
                val existingMessages = repository.getChatMessages(session.id).getOrNull() ?: emptyList()
                
                if (existingMessages.isNotEmpty()) {
                    // Load existing conversation history
                    println("Loaded ${existingMessages.size} messages from history")
                    _chatMessages.value = existingMessages
                } else {
                    // No history - show welcome message
                    val welcomeMessage =
                        ChatMessage(
                            id = "welcome_${System.currentTimeMillis()}",
                            content = getWelcomeMessage(bot),
                            sender = MessageSender.AI,
                            timestamp = System.currentTimeMillis(),
                            type = MessageType.TEXT,
                        )

                    _chatMessages.value = listOf(welcomeMessage)
                }
            } catch (e: Exception) {
                println("Failed to create session: ${e.message}")
                _error.value = "Failed to start session: ${e.message}"
            }
        }
    }

    private fun getWelcomeMessage(bot: ChatBot): String {
        return when (bot.id) {
            "emma" -> "Hi! I'm Emma, your friendly conversation partner. What would you like to talk about today?"
            "james" -> "Hello! I'm James. I specialize in business English. How can I help you improve your professional communication?"
            "sophia" -> "Greetings! I'm Sophia. I love discussing cultural topics and advanced conversations. What interests you?"
            "alex" -> "Hey there! I'm Alex, your speaking practice specialist. Ready to work on your pronunciation?"
            else -> "Hello! I'm your AI tutor. How can I help you practice today?"
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources if needed
    }
}
