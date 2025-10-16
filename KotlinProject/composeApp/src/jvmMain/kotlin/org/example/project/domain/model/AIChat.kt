package org.example.project.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val sender: MessageSender,
    val timestamp: Long,
    val type: MessageType = MessageType.TEXT,
    val isTyping: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        fun getSampleMessages(): List<ChatMessage> = emptyList()

        fun getDemoMessages(): List<ChatMessage> =
            listOf(
                ChatMessage(
                    id = "msg_1",
                    content = "Hello! I'm your AI language tutor. How can I help you today?",
                    sender = MessageSender.AI,
                    timestamp = System.currentTimeMillis() - 300000,
                ),
                ChatMessage(
                    id = "msg_2",
                    content = "Hi! I'd like to practice my conversation skills.",
                    sender = MessageSender.USER,
                    timestamp = System.currentTimeMillis() - 240000,
                ),
                ChatMessage(
                    id = "msg_3",
                    content = "Perfect! Let's start with a casual conversation. What did you do this weekend?",
                    sender = MessageSender.AI,
                    timestamp = System.currentTimeMillis() - 180000,
                ),
            )
    }
}

enum class MessageSender {
    USER,
    AI,
}

enum class MessageType {
    TEXT,
    VOICE,
    SYSTEM,
}

data class ChatSession(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long?,
    val messageCount: Int,
    val topic: String,
    val language: String = "English",
    val difficulty: String = "Intermediate",
) {
    companion object {
        fun getSampleSessions(): List<ChatSession> = emptyList()

        fun getDemoSessions(): List<ChatSession> =
            listOf(
                ChatSession(
                    id = "session_1",
                    title = "Weekend Activities",
                    startTime = System.currentTimeMillis() - 86400000,
                    endTime = System.currentTimeMillis() - 86400000 + 1800000,
                    messageCount = 15,
                    topic = "Daily Life",
                    difficulty = "Beginner",
                ),
                ChatSession(
                    id = "session_2",
                    title = "Travel Planning",
                    startTime = System.currentTimeMillis() - 172800000,
                    endTime = System.currentTimeMillis() - 172800000 + 2700000,
                    messageCount = 23,
                    topic = "Travel",
                    difficulty = "Intermediate",
                ),
            )
    }
}

data class ChatBot(
    val id: String,
    val name: String,
    val description: String,
    val avatar: String,
    val personality: String,
    val specialties: List<String>,
    val difficulty: String,
    val isAvailable: Boolean = true,
) {
    companion object {
        fun getAvailableBots(): List<ChatBot> =
            listOf(
                ChatBot(
                    id = "emma",
                    name = "Emma",
                    description = "Friendly conversation partner for everyday topics",
                    avatar = "👩‍🏫",
                    personality = "Encouraging and patient",
                    specialties = listOf("Daily Conversation", "Grammar Basics", "Pronunciation"),
                    difficulty = "Beginner",
                ),
                ChatBot(
                    id = "james",
                    name = "James",
                    description = "Business English specialist for professional communication",
                    avatar = "👨‍💼",
                    personality = "Professional and structured",
                    specialties = listOf("Business English", "Presentations", "Email Writing"),
                    difficulty = "Intermediate",
                ),
                ChatBot(
                    id = "sophia",
                    name = "Sophia",
                    description = "Advanced conversation partner for cultural topics",
                    avatar = "👩‍🎓",
                    personality = "Intellectual and engaging",
                    specialties = listOf("Advanced Conversation", "Cultural Topics", "Idioms"),
                    difficulty = "Advanced",
                ),
                ChatBot(
                    id = "alex",
                    name = "Alex",
                    description = "Speaking practice specialist with pronunciation focus",
                    avatar = "🗣️",
                    personality = "Supportive and detailed",
                    specialties = listOf("Pronunciation", "Speaking Practice", "Accent Training"),
                    difficulty = "All Levels",
                ),
            )
    }
}

data class ChatFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val color: String,
) {
    companion object {
        fun getChatFeatures(): List<ChatFeature> =
            listOf(
                ChatFeature(
                    id = "natural_conversation",
                    title = "Natural Conversations",
                    description = "Chat with AI tutors that understand context and provide meaningful responses.",
                    icon = "💬",
                    color = "#8B5CF6",
                ),
                ChatFeature(
                    id = "instant_feedback",
                    title = "Instant Feedback",
                    description = "Get immediate corrections and suggestions to improve your language skills.",
                    icon = "⚡",
                    color = "#10B981",
                ),
                ChatFeature(
                    id = "personalized_topics",
                    title = "Personalized Topics",
                    description = "Discuss topics that interest you and match your learning goals.",
                    icon = "🎯",
                    color = "#F59E0B",
                ),
                ChatFeature(
                    id = "available_24_7",
                    title = "Available 24/7",
                    description = "Practice anytime with AI tutors that never sleep or get tired.",
                    icon = "🌙",
                    color = "#3B82F6",
                ),
                ChatFeature(
                    id = "multiple_personalities",
                    title = "Multiple Personalities",
                    description = "Choose from different AI tutors with unique teaching styles and expertise.",
                    icon = "👥",
                    color = "#EF4444",
                ),
                ChatFeature(
                    id = "progress_tracking",
                    title = "Progress Tracking",
                    description = "Monitor your conversation skills improvement over time with detailed analytics.",
                    icon = "📈",
                    color = "#8B5CF6",
                ),
            )
    }
}
