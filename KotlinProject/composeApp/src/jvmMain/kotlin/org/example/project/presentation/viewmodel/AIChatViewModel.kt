package org.example.project.presentation.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.example.project.domain.model.ChatMessage
import org.example.project.domain.model.ChatSession
import org.example.project.domain.model.ChatBot
import org.example.project.domain.model.ChatFeature
import org.example.project.domain.model.MessageSender
import org.example.project.domain.model.MessageType

/**
 * ViewModel for the AI Chat screen
 * 
 * Manages the state and business logic for the AI chat screen,
 * including messages, sessions, bots, and user interactions
 */
class AIChatViewModel : ViewModel() {
    
    // Private mutable state
    private val _chatMessages = mutableStateOf(ChatMessage.getSampleMessages())
    private val _chatSessions = mutableStateOf(ChatSession.getSampleSessions())
    private val _availableBots = mutableStateOf(ChatBot.getAvailableBots())
    private val _chatFeatures = mutableStateOf(ChatFeature.getChatFeatures())
    private val _selectedBot = mutableStateOf<ChatBot?>(null)
    private val _currentMessage = mutableStateOf("")
    private val _isTyping = mutableStateOf(false)
    private val _isLoading = mutableStateOf(false)
    private val _currentSession = mutableStateOf<ChatSession?>(null)
    
    // Public read-only state
    val chatMessages: State<List<ChatMessage>> = _chatMessages
    val chatSessions: State<List<ChatSession>> = _chatSessions
    val availableBots: State<List<ChatBot>> = _availableBots
    val chatFeatures: State<List<ChatFeature>> = _chatFeatures
    val selectedBot: State<ChatBot?> = _selectedBot
    val currentMessage: State<String> = _currentMessage
    val isTyping: State<Boolean> = _isTyping
    val isLoading: State<Boolean> = _isLoading
    val currentSession: State<ChatSession?> = _currentSession
    
    /**
     * Handles message input changes
     * @param message The new message text
     */
    fun onMessageChanged(message: String) {
        _currentMessage.value = message
    }
    
    /**
     * Handles sending a message
     */
    fun onSendMessage() {
        val messageText = _currentMessage.value.trim()
        if (messageText.isEmpty()) return
        
        // Add user message
        val userMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            content = messageText,
            sender = MessageSender.USER,
            timestamp = System.currentTimeMillis(),
            type = MessageType.TEXT
        )
        
        _chatMessages.value = _chatMessages.value + userMessage
        _currentMessage.value = ""
        
        // Show typing indicator
        _isTyping.value = true
        
        // Simulate AI response
        simulateAIResponse(messageText)
    }
    
    /**
     * Handles bot selection
     * @param bot The selected chat bot
     */
    fun onBotSelected(bot: ChatBot) {
        _selectedBot.value = bot
        
        // Start new session with selected bot
        startNewSession(bot)
    }
    
    /**
     * Handles starting first conversation (from empty state)
     */
    fun onStartFirstConversationClicked() {
        // Select default bot or show bot selection
        val defaultBot = _availableBots.value.firstOrNull()
        if (defaultBot != null) {
            onBotSelected(defaultBot)
        }
    }
    
    /**
     * Handles exploring chat bots (from empty state)
     */
    fun onExploreChatBotsClicked() {
        // TODO: Navigate to bot selection screen or show bot picker dialog
        println("Explore chat bots clicked")
    }
    
    /**
     * Handles session selection
     * @param sessionId The ID of the session to load
     */
    fun onSessionSelected(sessionId: String) {
        // TODO: Load messages from selected session
        println("Session selected: $sessionId")
    }
    
    /**
     * Handles creating a new session
     */
    fun onNewSessionClicked() {
        _chatMessages.value = emptyList()
        _currentSession.value = null
        _selectedBot.value = null
    }
    
    /**
     * Handles voice input toggle
     */
    fun onVoiceInputToggle() {
        // TODO: Implement voice input functionality
        println("Voice input toggled")
    }
    
    /**
     * Refreshes chat data
     */
    fun refreshChatData() {
        _isLoading.value = true
        
        // TODO: Implement actual data refresh from repository
        // For now, simulate refresh
        _chatSessions.value = ChatSession.getSampleSessions()
        _availableBots.value = ChatBot.getAvailableBots()
        
        _isLoading.value = false
    }
    
    /**
     * Starts a new chat session with the given bot
     */
    private fun startNewSession(bot: ChatBot) {
        val newSession = ChatSession(
            id = "session_${System.currentTimeMillis()}",
            title = "Chat with ${bot.name}",
            startTime = System.currentTimeMillis(),
            endTime = null,
            messageCount = 0,
            topic = "General Conversation",
            difficulty = bot.difficulty
        )
        
        _currentSession.value = newSession
        
        // Clear previous messages
        _chatMessages.value = emptyList()
        
        // Add welcome message from bot
        val welcomeMessage = ChatMessage(
            id = "welcome_${System.currentTimeMillis()}",
            content = getWelcomeMessage(bot),
            sender = MessageSender.AI,
            timestamp = System.currentTimeMillis(),
            type = MessageType.TEXT
        )
        
        _chatMessages.value = listOf(welcomeMessage)
    }
    
    /**
     * Simulates AI response to user message
     */
    private fun simulateAIResponse(userMessage: String) {
        // Simulate network delay (in real app, this would be properly managed)
        // TODO: Replace with proper coroutine scope and actual AI service call
        println("Simulating AI response to: $userMessage")
        
        // For now, immediate response without coroutines to avoid import issues
        val aiResponse = generateAIResponse(userMessage)
        val aiMessage = ChatMessage(
            id = "ai_${System.currentTimeMillis()}",
            content = aiResponse,
            sender = MessageSender.AI,
            timestamp = System.currentTimeMillis(),
            type = MessageType.TEXT
        )
        
        _isTyping.value = false
        _chatMessages.value = _chatMessages.value + aiMessage
    }
    
    /**
     * Generates a welcome message for the selected bot
     */
    private fun getWelcomeMessage(bot: ChatBot): String {
        return when (bot.id) {
            "emma" -> "Hi! I'm Emma, your friendly conversation partner. What would you like to talk about today?"
            "james" -> "Hello! I'm James. I specialize in business English. How can I help you improve your professional communication?"
            "sophia" -> "Greetings! I'm Sophia. I love discussing cultural topics and advanced conversations. What interests you?"
            "alex" -> "Hey there! I'm Alex, your speaking practice specialist. Ready to work on your pronunciation?"
            else -> "Hello! I'm your AI tutor. How can I help you practice English today?"
        }
    }
    
    /**
     * Generates AI response based on user input
     */
    private fun generateAIResponse(userMessage: String): String {
        // Simple response generation (in real app, this would call AI service)
        val responses = listOf(
            "That's interesting! Can you tell me more about that?",
            "I understand. How do you feel about that situation?",
            "Great point! What made you think of that?",
            "That sounds challenging. How did you handle it?",
            "Excellent! Can you give me an example?",
            "I see. What would you do differently next time?",
            "That's a good question. Let me think about that...",
            "Wonderful! How long have you been interested in that?",
            "That makes sense. What's your opinion on this topic?",
            "Very good! Can you explain that in more detail?"
        )
        
        return responses.random()
    }
}
