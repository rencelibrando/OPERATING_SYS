package org.example.project.data.repository

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.example.project.core.config.SupabaseConfig
import java.io.File
import java.util.UUID

interface PracticeRecordingRepository {
    suspend fun uploadVocabularyRecording(
        wordId: String,
        audioFile: File,
        transcript: String?,
        language: String,
        expectedText: String,
        durationSeconds: Float
    ): Result<VocabularyPracticeRecordingDTO>

    suspend fun saveVocabularyAIFeedback(
        recordingId: String,
        wordId: String,
        overallScore: Int,
        pronunciationScore: Int,
        accuracyScore: Int,
        fluencyScore: Int,
        clarityScore: Int,
        detailedAnalysis: String?,
        strengths: List<String>,
        areasForImprovement: List<String>,
        suggestions: List<String>,
        analysisProvider: String = "local",
        analysisDurationMs: Int = 0
    ): Result<VocabularyAIFeedbackDTO>

    suspend fun getVocabularyPracticeHistory(wordId: String): Result<List<VocabularyPracticeHistoryDTO>>

    suspend fun getUserVocabularyPracticeHistory(limit: Int = 50): Result<List<VocabularyPracticeHistoryDTO>>

    suspend fun uploadPhraseRecording(
        scenarioId: String?,
        audioFile: File,
        transcript: String?,
        language: String,
        expectedPhrase: String,
        scenarioType: String?,
        difficultyLevel: String,
        durationSeconds: Float
    ): Result<PhrasePracticeRecordingDTO>

    suspend fun savePhraseAIFeedback(
        recordingId: String,
        scenarioId: String?,
        overallScore: Int,
        pronunciationScore: Int,
        grammarScore: Int,
        fluencyScore: Int,
        accuracyScore: Int,
        contextualScore: Int,
        detailedAnalysis: String?,
        strengths: List<String>,
        areasForImprovement: List<String>,
        suggestions: List<String>,
        analysisProvider: String = "local",
        analysisDurationMs: Int = 0
    ): Result<PhraseAIFeedbackDTO>

    suspend fun getPhrasePracticeHistory(scenarioId: String): Result<List<PhrasePracticeHistoryDTO>>

    suspend fun getUserPhrasePracticeHistory(limit: Int = 50): Result<List<PhrasePracticeHistoryDTO>>
}

class PracticeRecordingRepositoryImpl : PracticeRecordingRepository {
    private val supabase = SupabaseConfig.client
    private val storageBucket = "voice-recordings"

    private suspend fun getCurrentUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not authenticated")
    }

    override suspend fun uploadVocabularyRecording(
        wordId: String,
        audioFile: File,
        transcript: String?,
        language: String,
        expectedText: String,
        durationSeconds: Float
    ): Result<VocabularyPracticeRecordingDTO> = runCatching {
        println("[PracticeRepo] uploadVocabularyRecording starting for word: $wordId")
        
        val userId = getCurrentUserId()
        println("[PracticeRepo] Got userId: $userId")
        
        val timestamp = System.currentTimeMillis()
        val uniqueId = UUID.randomUUID().toString().take(8)
        val fileName = "vocabulary/$userId/${wordId}_${timestamp}_$uniqueId.wav"
        println("[PracticeRepo] Uploading to: $fileName")

        val storage = supabase.storage.from(storageBucket)
        val bytes = audioFile.readBytes()
        println("[PracticeRepo] File size: ${bytes.size} bytes")
        
        storage.upload(fileName, bytes, upsert = true)
        val publicUrl = storage.publicUrl(fileName)
        println("[PracticeRepo] Uploaded successfully, URL: $publicUrl")

        val recording = VocabularyPracticeRecordingInsertDTO(
            userId = userId,
            wordId = wordId,
            recordingUrl = publicUrl,
            durationSeconds = durationSeconds.toDouble(),
            fileSizeBytes = bytes.size,
            audioFormat = "wav",
            transcript = transcript,
            language = language,
            expectedText = expectedText
        )

        println("[PracticeRepo] Inserting recording to database...")
        val result = supabase.postgrest["vocabulary_practice_recordings"]
            .insert(recording) {
                select(Columns.ALL)
            }
            .decodeSingle<VocabularyPracticeRecordingDTO>()
        println("[PracticeRepo] Recording inserted successfully: ${result.id}")
        result
    }

    override suspend fun saveVocabularyAIFeedback(
        recordingId: String,
        wordId: String,
        overallScore: Int,
        pronunciationScore: Int,
        accuracyScore: Int,
        fluencyScore: Int,
        clarityScore: Int,
        detailedAnalysis: String?,
        strengths: List<String>,
        areasForImprovement: List<String>,
        suggestions: List<String>,
        analysisProvider: String,
        analysisDurationMs: Int
    ): Result<VocabularyAIFeedbackDTO> = runCatching {
        val userId = getCurrentUserId()

        val feedback = VocabularyAIFeedbackInsertDTO(
            recordingId = recordingId,
            userId = userId,
            wordId = wordId,
            overallScore = overallScore,
            pronunciationScore = pronunciationScore,
            accuracyScore = accuracyScore,
            fluencyScore = fluencyScore,
            clarityScore = clarityScore,
            detailedAnalysis = detailedAnalysis,
            strengths = strengths,
            areasForImprovement = areasForImprovement,
            suggestions = suggestions,
            analysisProvider = analysisProvider,
            analysisDurationMs = analysisDurationMs
        )

        supabase.postgrest["vocabulary_ai_feedback"]
            .insert(feedback) {
                select(Columns.ALL)
            }
            .decodeSingle<VocabularyAIFeedbackDTO>()
    }

    override suspend fun getVocabularyPracticeHistory(wordId: String): Result<List<VocabularyPracticeHistoryDTO>> = runCatching {
        val userId = getCurrentUserId()

        supabase.postgrest["vocabulary_practice_history"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("word_id", wordId)
                }
            }
            .decodeList<VocabularyPracticeHistoryDTO>()
    }

    override suspend fun getUserVocabularyPracticeHistory(limit: Int): Result<List<VocabularyPracticeHistoryDTO>> = runCatching {
        val userId = getCurrentUserId()

        supabase.postgrest["vocabulary_practice_history"]
            .select {
                filter {
                    eq("user_id", userId)
                }
                limit(limit.toLong())
            }
            .decodeList<VocabularyPracticeHistoryDTO>()
    }

    override suspend fun uploadPhraseRecording(
        scenarioId: String?,
        audioFile: File,
        transcript: String?,
        language: String,
        expectedPhrase: String,
        scenarioType: String?,
        difficultyLevel: String,
        durationSeconds: Float
    ): Result<PhrasePracticeRecordingDTO> = runCatching {
        val userId = getCurrentUserId()
        val timestamp = System.currentTimeMillis()
        val uniqueId = UUID.randomUUID().toString().take(8)
        val scenarioPath = scenarioId ?: "general"
        val fileName = "phrases/$userId/${scenarioPath}_${timestamp}_$uniqueId.wav"

        val storage = supabase.storage.from(storageBucket)
        val bytes = audioFile.readBytes()
        storage.upload(fileName, bytes, upsert = true)
        val publicUrl = storage.publicUrl(fileName)

        val recording = PhrasePracticeRecordingInsertDTO(
            userId = userId,
            scenarioId = scenarioId,
            recordingUrl = publicUrl,
            durationSeconds = durationSeconds.toDouble(),
            fileSizeBytes = bytes.size,
            audioFormat = "wav",
            transcript = transcript,
            language = language,
            expectedPhrase = expectedPhrase,
            scenarioType = scenarioType,
            difficultyLevel = difficultyLevel
        )

        supabase.postgrest["phrase_practice_recordings"]
            .insert(recording) {
                select(Columns.ALL)
            }
            .decodeSingle<PhrasePracticeRecordingDTO>()
    }

    override suspend fun savePhraseAIFeedback(
        recordingId: String,
        scenarioId: String?,
        overallScore: Int,
        pronunciationScore: Int,
        grammarScore: Int,
        fluencyScore: Int,
        accuracyScore: Int,
        contextualScore: Int,
        detailedAnalysis: String?,
        strengths: List<String>,
        areasForImprovement: List<String>,
        suggestions: List<String>,
        analysisProvider: String,
        analysisDurationMs: Int
    ): Result<PhraseAIFeedbackDTO> = runCatching {
        val userId = getCurrentUserId()

        val feedback = PhraseAIFeedbackInsertDTO(
            recordingId = recordingId,
            userId = userId,
            scenarioId = scenarioId,
            overallScore = overallScore,
            pronunciationScore = pronunciationScore,
            grammarScore = grammarScore,
            fluencyScore = fluencyScore,
            accuracyScore = accuracyScore,
            contextualAppropriatenessScore = contextualScore,
            detailedAnalysis = detailedAnalysis,
            strengths = strengths,
            areasForImprovement = areasForImprovement,
            suggestions = suggestions,
            analysisProvider = analysisProvider,
            analysisDurationMs = analysisDurationMs
        )

        supabase.postgrest["phrase_ai_feedback"]
            .insert(feedback) {
                select(Columns.ALL)
            }
            .decodeSingle<PhraseAIFeedbackDTO>()
    }

    override suspend fun getPhrasePracticeHistory(scenarioId: String): Result<List<PhrasePracticeHistoryDTO>> = runCatching {
        val userId = getCurrentUserId()

        supabase.postgrest["phrase_practice_history"]
            .select {
                filter {
                    eq("user_id", userId)
                    eq("scenario_id", scenarioId)
                }
            }
            .decodeList<PhrasePracticeHistoryDTO>()
    }

    override suspend fun getUserPhrasePracticeHistory(limit: Int): Result<List<PhrasePracticeHistoryDTO>> = runCatching {
        val userId = getCurrentUserId()

        supabase.postgrest["phrase_practice_history"]
            .select {
                filter {
                    eq("user_id", userId)
                }
                limit(limit.toLong())
            }
            .decodeList<PhrasePracticeHistoryDTO>()
    }
}

// ============================================================================
// DTOs for Vocabulary Practice
// ============================================================================

@Serializable
data class VocabularyPracticeRecordingDTO(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    @SerialName("user_vocabulary_id") val userVocabularyId: String? = null,
    @SerialName("recording_url") val recordingUrl: String,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerialName("file_size_bytes") val fileSizeBytes: Int = 0,
    @SerialName("audio_format") val audioFormat: String = "wav",
    val transcript: String? = null,
    val language: String? = null,
    @SerialName("expected_text") val expectedText: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class VocabularyPracticeRecordingInsertDTO(
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    @SerialName("recording_url") val recordingUrl: String,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerialName("file_size_bytes") val fileSizeBytes: Int = 0,
    @SerialName("audio_format") val audioFormat: String = "wav",
    val transcript: String? = null,
    val language: String? = null,
    @SerialName("expected_text") val expectedText: String? = null
)

@Serializable
data class VocabularyAIFeedbackDTO(
    val id: String,
    @SerialName("recording_id") val recordingId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    @SerialName("overall_score") val overallScore: Int? = null,
    @SerialName("pronunciation_score") val pronunciationScore: Int? = null,
    @SerialName("accuracy_score") val accuracyScore: Int? = null,
    @SerialName("fluency_score") val fluencyScore: Int? = null,
    @SerialName("clarity_score") val clarityScore: Int? = null,
    @SerialName("detailed_analysis") val detailedAnalysis: String? = null,
    val strengths: List<String> = emptyList(),
    @SerialName("areas_for_improvement") val areasForImprovement: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    @SerialName("specific_examples") val specificExamples: JsonArray? = null,
    @SerialName("phonetic_breakdown") val phoneticBreakdown: JsonObject? = null,
    @SerialName("analysis_provider") val analysisProvider: String = "local",
    @SerialName("analysis_duration_ms") val analysisDurationMs: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class VocabularyAIFeedbackInsertDTO(
    @SerialName("recording_id") val recordingId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("pronunciation_score") val pronunciationScore: Int,
    @SerialName("accuracy_score") val accuracyScore: Int,
    @SerialName("fluency_score") val fluencyScore: Int,
    @SerialName("clarity_score") val clarityScore: Int,
    @SerialName("detailed_analysis") val detailedAnalysis: String? = null,
    val strengths: List<String> = emptyList(),
    @SerialName("areas_for_improvement") val areasForImprovement: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    @SerialName("analysis_provider") val analysisProvider: String = "local",
    @SerialName("analysis_duration_ms") val analysisDurationMs: Int = 0
)

@Serializable
data class VocabularyPracticeHistoryDTO(
    @SerialName("recording_id") val recordingId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    val word: String? = null,
    val definition: String? = null,
    @SerialName("expected_pronunciation") val expectedPronunciation: String? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val transcript: String? = null,
    val language: String? = null,
    @SerialName("expected_text") val expectedText: String? = null,
    @SerialName("practiced_at") val practicedAt: String? = null,
    @SerialName("feedback_id") val feedbackId: String? = null,
    @SerialName("overall_score") val overallScore: Int? = null,
    @SerialName("pronunciation_score") val pronunciationScore: Int? = null,
    @SerialName("accuracy_score") val accuracyScore: Int? = null,
    @SerialName("fluency_score") val fluencyScore: Int? = null,
    @SerialName("clarity_score") val clarityScore: Int? = null,
    @SerialName("detailed_analysis") val detailedAnalysis: String? = null,
    val strengths: List<String>? = null,
    @SerialName("areas_for_improvement") val areasForImprovement: List<String>? = null,
    val suggestions: List<String>? = null
)

// ============================================================================
// DTOs for Phrase Practice
// ============================================================================

@Serializable
data class PhrasePracticeRecordingDTO(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
    @SerialName("recording_url") val recordingUrl: String,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerialName("file_size_bytes") val fileSizeBytes: Int = 0,
    @SerialName("audio_format") val audioFormat: String = "wav",
    val transcript: String? = null,
    val language: String? = null,
    @SerialName("expected_phrase") val expectedPhrase: String? = null,
    @SerialName("scenario_type") val scenarioType: String? = null,
    @SerialName("difficulty_level") val difficultyLevel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PhrasePracticeRecordingInsertDTO(
    @SerialName("user_id") val userId: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
    @SerialName("recording_url") val recordingUrl: String,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerialName("file_size_bytes") val fileSizeBytes: Int = 0,
    @SerialName("audio_format") val audioFormat: String = "wav",
    val transcript: String? = null,
    val language: String? = null,
    @SerialName("expected_phrase") val expectedPhrase: String? = null,
    @SerialName("scenario_type") val scenarioType: String? = null,
    @SerialName("difficulty_level") val difficultyLevel: String? = null
)

@Serializable
data class PhraseAIFeedbackDTO(
    val id: String,
    @SerialName("recording_id") val recordingId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
    @SerialName("overall_score") val overallScore: Int? = null,
    @SerialName("pronunciation_score") val pronunciationScore: Int? = null,
    @SerialName("grammar_score") val grammarScore: Int? = null,
    @SerialName("fluency_score") val fluencyScore: Int? = null,
    @SerialName("accuracy_score") val accuracyScore: Int? = null,
    @SerialName("contextual_appropriateness_score") val contextualAppropriatenessScore: Int? = null,
    @SerialName("detailed_analysis") val detailedAnalysis: String? = null,
    val strengths: List<String> = emptyList(),
    @SerialName("areas_for_improvement") val areasForImprovement: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    @SerialName("specific_examples") val specificExamples: JsonArray? = null,
    @SerialName("word_by_word_feedback") val wordByWordFeedback: JsonArray? = null,
    @SerialName("analysis_provider") val analysisProvider: String = "local",
    @SerialName("analysis_duration_ms") val analysisDurationMs: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PhraseAIFeedbackInsertDTO(
    @SerialName("recording_id") val recordingId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("pronunciation_score") val pronunciationScore: Int,
    @SerialName("grammar_score") val grammarScore: Int,
    @SerialName("fluency_score") val fluencyScore: Int,
    @SerialName("accuracy_score") val accuracyScore: Int,
    @SerialName("contextual_appropriateness_score") val contextualAppropriatenessScore: Int,
    @SerialName("detailed_analysis") val detailedAnalysis: String? = null,
    val strengths: List<String> = emptyList(),
    @SerialName("areas_for_improvement") val areasForImprovement: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    @SerialName("analysis_provider") val analysisProvider: String = "local",
    @SerialName("analysis_duration_ms") val analysisDurationMs: Int = 0
)

@Serializable
data class PhrasePracticeHistoryDTO(
    @SerialName("recording_id") val recordingId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("scenario_id") val scenarioId: String? = null,
    @SerialName("scenario_title") val scenarioTitle: String? = null,
    @SerialName("scenario_type") val scenarioType: String? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val transcript: String? = null,
    val language: String? = null,
    @SerialName("expected_phrase") val expectedPhrase: String? = null,
    @SerialName("difficulty_level") val difficultyLevel: String? = null,
    @SerialName("practiced_at") val practicedAt: String? = null,
    @SerialName("feedback_id") val feedbackId: String? = null,
    @SerialName("overall_score") val overallScore: Int? = null,
    @SerialName("pronunciation_score") val pronunciationScore: Int? = null,
    @SerialName("grammar_score") val grammarScore: Int? = null,
    @SerialName("fluency_score") val fluencyScore: Int? = null,
    @SerialName("accuracy_score") val accuracyScore: Int? = null,
    @SerialName("contextual_appropriateness_score") val contextualAppropriatenessScore: Int? = null,
    @SerialName("detailed_analysis") val detailedAnalysis: String? = null,
    val strengths: List<String>? = null,
    @SerialName("areas_for_improvement") val areasForImprovement: List<String>? = null,
    val suggestions: List<String>? = null
)
