package org.example.project.core.realtime

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.LessonLanguage

/**
 * Real-time progress update service using Supabase Realtime.
 * 
 * Listens to database changes and emits events when user progress is updated.
 * No polling required - instant updates via WebSocket.
 * 
 * Usage:
 * ```
 * progressRealtimeService.start(userId)
 * progressRealtimeService.progressUpdates.collect { event ->
 *     // Refresh progress for updated language
 *     viewModel.loadLanguageProgress(event.language, forceRefresh = true)
 * }
 * ```
 */
class ProgressRealtimeService {
    private val supabase = SupabaseConfig.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _progressUpdates = MutableSharedFlow<ProgressUpdateEvent>(replay = 0)
    val progressUpdates: SharedFlow<ProgressUpdateEvent> = _progressUpdates.asSharedFlow()
    
    private var currentUserId: String? = null
    private val activeChannels = mutableMapOf<String, io.github.jan.supabase.realtime.RealtimeChannel>()

    /**
     * Start listening to real-time updates for a user.
     */
    suspend fun start(userId: String) {
        if (currentUserId == userId && activeChannels.isNotEmpty()) {
            println("[ProgressRealtime] Already listening for user: $userId")
            return
        }

        stop() // Stop previous subscriptions
        currentUserId = userId

        println("[ProgressRealtime] 🔌 Starting real-time subscriptions for user: $userId")

        // Subscribe to lesson completions
        subscribeLessonProgress(userId)
        
        // Subscribe to conversation sessions
        subscribeConversationSessions(userId)
        
        // Subscribe to vocabulary additions
        subscribeVocabulary(userId)
        
        // Subscribe to voice feedback
        subscribeVoiceFeedback(userId)

        println("[ProgressRealtime] ✅ Real-time subscriptions active (${activeChannels.size} channels)")
    }

    /**
     * Stop all real-time subscriptions.
     */
    suspend fun stop() {
        if (activeChannels.isEmpty()) return

        println("[ProgressRealtime] 🔌 Stopping ${activeChannels.size} real-time subscriptions")
        
        activeChannels.forEach { (channelName, channel) ->
            try {
                channel.unsubscribe()
                supabase.realtime.removeChannel(channel)
            } catch (e: Exception) {
                println("[ProgressRealtime] ⚠️ Failed to remove channel $channelName: ${e.message}")
            }
        }
        
        activeChannels.clear()
        currentUserId = null
        
        println("[ProgressRealtime] ✅ All subscriptions stopped")
    }

    /**
     * Subscribe to lesson progress updates.
     */
    private suspend fun subscribeLessonProgress(userId: String) {
        val channelName = "lesson-progress-$userId"
        
        try {
            val channel = supabase.realtime.channel(channelName)
            
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "user_lesson_progress"
                filter = "user_id=eq.$userId"
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        val record = action.decodeRecord<LessonProgressRecord>()
                        handleLessonUpdate(userId, record.lessonId)
                    }
                    is PostgresAction.Update -> {
                        val record = action.decodeRecord<LessonProgressRecord>()
                        if (record.isCompleted) {
                            handleLessonUpdate(userId, record.lessonId)
                        }
                    }
                    else -> {} // Ignore delete
                }
            }.launchIn(scope)
            
            channel.subscribe()
            activeChannels[channelName] = channel
            
            println("[ProgressRealtime] ✅ Subscribed to lesson progress updates")
        } catch (e: Exception) {
            println("[ProgressRealtime] ⚠️ Failed to subscribe to lesson progress: ${e.message}")
        }
    }

    /**
     * Subscribe to conversation session updates.
     */
    private suspend fun subscribeConversationSessions(userId: String) {
        // Subscribe to agent_sessions
        subscribeAgentSessions(userId)
        
        // Subscribe to voice_sessions
        subscribeVoiceSessions(userId)
        
        // Subscribe to conversation_recordings
        subscribeConversationRecordings(userId)
    }

    private suspend fun subscribeAgentSessions(userId: String) {
        val channelName = "agent-sessions-$userId"
        
        try {
            val channel = supabase.realtime.channel(channelName)
            
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "agent_sessions"
                filter = "user_id=eq.$userId"
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert, is PostgresAction.Update -> {
                        val record = action.decodeRecord<AgentSessionRecord>()
                        val language = parseLanguage(record.language)
                        if (language != null) {
                            emitUpdate(ProgressUpdateEvent.ConversationSessionAdded(userId, language))
                        }
                    }
                    else -> {}
                }
            }.launchIn(scope)
            
            channel.subscribe()
            activeChannels[channelName] = channel
            
            println("[ProgressRealtime] ✅ Subscribed to agent sessions")
        } catch (e: Exception) {
            println("[ProgressRealtime] ⚠️ Failed to subscribe to agent sessions: ${e.message}")
        }
    }

    private suspend fun subscribeVoiceSessions(userId: String) {
        val channelName = "voice-sessions-$userId"
        
        try {
            val channel = supabase.realtime.channel(channelName)
            
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "voice_sessions"
                filter = "user_id=eq.$userId"
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert, is PostgresAction.Update -> {
                        val record = action.decodeRecord<VoiceSessionRecord>()
                        val language = parseLanguageFromCode(record.language)
                        if (language != null) {
                            emitUpdate(ProgressUpdateEvent.ConversationSessionAdded(userId, language))
                        }
                    }
                    else -> {}
                }
            }.launchIn(scope)
            
            channel.subscribe()
            activeChannels[channelName] = channel
            
            println("[ProgressRealtime] ✅ Subscribed to voice sessions")
        } catch (e: Exception) {
            println("[ProgressRealtime] ⚠️ Failed to subscribe to voice sessions: ${e.message}")
        }
    }

    private suspend fun subscribeConversationRecordings(userId: String) {
        val channelName = "conversation-recordings-$userId"
        
        try {
            val channel = supabase.realtime.channel(channelName)
            
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "conversation_recordings"
                filter = "user_id=eq.$userId"
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        val record = action.decodeRecord<ConversationRecordingRecord>()
                        val language = parseLanguage(record.language)
                        if (language != null) {
                            emitUpdate(ProgressUpdateEvent.ConversationSessionAdded(userId, language))
                        }
                    }
                    else -> {}
                }
            }.launchIn(scope)
            
            channel.subscribe()
            activeChannels[channelName] = channel
            
            println("[ProgressRealtime] ✅ Subscribed to conversation recordings")
        } catch (e: Exception) {
            println("[ProgressRealtime] ⚠️ Failed to subscribe to conversation recordings: ${e.message}")
        }
    }

    /**
     * Subscribe to vocabulary updates.
     */
    private suspend fun subscribeVocabulary(userId: String) {
        val channelName = "vocabulary-$userId"
        
        try {
            val channel = supabase.realtime.channel(channelName)
            
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "user_vocabulary"
                filter = "user_id=eq.$userId"
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        val record = action.decodeRecord<UserVocabularyRecord>()
                        handleVocabularyUpdate(userId, record.wordId)
                    }
                    else -> {}
                }
            }.launchIn(scope)
            
            channel.subscribe()
            activeChannels[channelName] = channel
            
            println("[ProgressRealtime] ✅ Subscribed to vocabulary updates")
        } catch (e: Exception) {
            println("[ProgressRealtime] ⚠️ Failed to subscribe to vocabulary: ${e.message}")
        }
    }

    /**
     * Subscribe to voice feedback updates.
     */
    private suspend fun subscribeVoiceFeedback(userId: String) {
        val channelName = "voice-feedback-$userId"
        
        try {
            val channel = supabase.realtime.channel(channelName)
            
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "conversation_feedback"
                filter = "user_id=eq.$userId"
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert, is PostgresAction.Update -> {
                        val record = action.decodeRecord<ConversationFeedbackRecord>()
                        handleFeedbackUpdate(userId, record.sessionId)
                    }
                    else -> {}
                }
            }.launchIn(scope)
            
            channel.subscribe()
            activeChannels[channelName] = channel
            
            println("[ProgressRealtime] ✅ Subscribed to voice feedback")
        } catch (e: Exception) {
            println("[ProgressRealtime] ⚠️ Failed to subscribe to voice feedback: ${e.message}")
        }
    }

    /**
     * Handle lesson progress update.
     */
    private suspend fun handleLessonUpdate(userId: String, lessonId: String) {
        // Need to look up lesson's language
        // For simplicity, emit a generic update that refreshes all languages
        // In production, you'd query the lesson to get its language
        println("[ProgressRealtime] 📚 Lesson completed: $lessonId")
        emitUpdate(ProgressUpdateEvent.LessonCompleted(userId, null))
    }

    /**
     * Handle vocabulary update.
     */
    private suspend fun handleVocabularyUpdate(userId: String, wordId: String) {
        println("[ProgressRealtime] 📖 Vocabulary added: $wordId")
        emitUpdate(ProgressUpdateEvent.VocabularyAdded(userId, null))
    }

    /**
     * Handle feedback update.
     */
    private suspend fun handleFeedbackUpdate(userId: String, sessionId: String) {
        println("[ProgressRealtime] 🎤 Voice feedback recorded: $sessionId")
        emitUpdate(ProgressUpdateEvent.VoiceFeedbackAdded(userId, null))
    }

    private suspend fun emitUpdate(event: ProgressUpdateEvent) {
        _progressUpdates.emit(event)
    }

    private fun parseLanguage(languageName: String?): LessonLanguage? {
        return LessonLanguage.entries.firstOrNull { 
            it.displayName.equals(languageName, ignoreCase = true) 
        }
    }

    private fun parseLanguageFromCode(code: String?): LessonLanguage? {
        return LessonLanguage.entries.firstOrNull { 
            it.code.equals(code, ignoreCase = true) 
        }
    }
}

/**
 * Real-time progress update events.
 */
sealed class ProgressUpdateEvent(
    open val userId: String,
    open val language: LessonLanguage?
) {
    data class LessonCompleted(
        override val userId: String,
        override val language: LessonLanguage?
    ) : ProgressUpdateEvent(userId, language)

    data class ConversationSessionAdded(
        override val userId: String,
        override val language: LessonLanguage?
    ) : ProgressUpdateEvent(userId, language)

    data class VocabularyAdded(
        override val userId: String,
        override val language: LessonLanguage?
    ) : ProgressUpdateEvent(userId, language)

    data class VoiceFeedbackAdded(
        override val userId: String,
        override val language: LessonLanguage?
    ) : ProgressUpdateEvent(userId, language)
}

// Realtime record DTOs
@Serializable
private data class LessonProgressRecord(
    @SerialName("lesson_id") val lessonId: String,
    @SerialName("is_completed") val isCompleted: Boolean = false
)

@Serializable
private data class AgentSessionRecord(
    @SerialName("language") val language: String
)

@Serializable
private data class VoiceSessionRecord(
    @SerialName("language") val language: String
)

@Serializable
private data class ConversationRecordingRecord(
    @SerialName("language") val language: String
)

@Serializable
private data class UserVocabularyRecord(
    @SerialName("word_id") val wordId: String
)

@Serializable
private data class ConversationFeedbackRecord(
    @SerialName("session_id") val sessionId: String
)
