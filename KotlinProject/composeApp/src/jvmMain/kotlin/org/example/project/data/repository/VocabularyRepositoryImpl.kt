package org.example.project.data.repository

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.project.core.config.SupabaseConfig
import org.example.project.domain.model.VocabularyStatus
import org.example.project.domain.model.VocabularyWord

class VocabularyRepositoryImpl : VocabularyRepository {

    private val supabase = SupabaseConfig.client

    
    override suspend fun getAllVocabularyWords(): Result<List<VocabularyWord>> = runCatching {
        
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not authenticated")

        getUserVocabularyInternal(userId)
    }

    override suspend fun searchVocabularyWords(query: String): Result<List<VocabularyWord>> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not authenticated")

        
        val userVocabRows = supabase.postgrest["user_vocabulary"]
            .select(columns = Columns.raw("*, vocabulary_words(*)")) {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeAs<List<UserVocabularyJoinDTO>>()

        
        userVocabRows
            .filter { join ->
                join.vocabularyWord?.word?.contains(query, ignoreCase = true) == true ||
                        join.vocabularyWord?.definition?.contains(query, ignoreCase = true) == true
            }
            .mapNotNull { it.toDomain() }
    }

    override suspend fun addVocabularyWord(word: VocabularyWord): Result<VocabularyWord> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not authenticated")

        
        val existing = supabase.postgrest["vocabulary_words"]
            .select {
                filter {
                    eq("word", word.word)
                }
            }
            .decodeSingleOrNull<VocabularyWordDTO>()

        val wordId: String = if (existing != null) {
            
            existing.id!!
        } else {
            
            val inserted = supabase.postgrest["vocabulary_words"]
                .insert(
                    value = VocabularyWordDTO.fromDomain(word)
                ) {
                    select(Columns.ALL)
                }
                .decodeSingle<VocabularyWordDTO>()
            inserted.id!!
        }

        
        val userVocabEntry = UserVocabularyDTO(
            userId = userId,
            wordId = wordId,
            status = "new",
            reviewCount = 0,
            correctCount = 0,
            lastReviewed = null,
            nextReview = null,
            intervalDays = 1,
            easeFactor = 2.50
        )

        supabase.postgrest["user_vocabulary"]
            .insert(userVocabEntry) {
                select(Columns.ALL)
            }
            .decodeSingle<UserVocabularyDTO>()

        
        val finalWord = supabase.postgrest["vocabulary_words"]
            .select {
                filter {
                    eq("id", wordId)
                }
            }
            .decodeSingle<VocabularyWordDTO>()

        finalWord.toDomain()
    }

    
    private suspend fun getUserVocabularyInternal(userId: String): List<VocabularyWord> {
        val userVocabRows = supabase.postgrest["user_vocabulary"]
            .select(columns = Columns.raw("*, vocabulary_words(*)")) {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeAs<List<UserVocabularyJoinDTO>>()

        return userVocabRows.mapNotNull { it.toDomain() }
    }

    
    override suspend fun getVocabularyWordsByCategory(category: String) = Result.success(emptyList<VocabularyWord>())
    override suspend fun getVocabularyWordsByDifficulty(difficulty: String) = Result.success(emptyList<VocabularyWord>())
    override suspend fun getVocabularyWord(wordId: String) = Result.success<VocabularyWord?>(null)
    override suspend fun updateVocabularyWord(word: VocabularyWord) = Result.success(word)
    override suspend fun deleteVocabularyWord(wordId: String) = Result.success(Unit)
    override suspend fun getUserVocabulary(userId: String) = Result.success(emptyList<UserVocabularyWord>())
    override suspend fun addWordToUserVocabulary(userId: String, wordId: String) = Result.failure<UserVocabularyWord>(NotImplementedError())
    override suspend fun updateUserWordStatus(userId: String, wordId: String, status: VocabularyStatus) = Result.success(Unit)
    override suspend fun getUserWordsByStatus(userId: String, status: VocabularyStatus) = Result.success(emptyList<UserVocabularyWord>())
    override suspend fun removeWordFromUserVocabulary(userId: String, wordId: String) = Result.success(Unit)
    override suspend fun getUserVocabularyStats(userId: String) = Result.success(VocabularyStats(0, 0, 0, 0, 0, 0, 0))
    override suspend fun getWordsForReview(userId: String, limit: Int) = Result.success(emptyList<UserVocabularyWord>())
}


@Serializable
private data class VocabularyWordDTO(
    val id: String? = null,
    val word: String,
    val definition: String,
    val pronunciation: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("difficulty_level") val difficultyLevel: String,
    val category: String,
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain(): VocabularyWord {
        return VocabularyWord(
            id = id ?: "",
            word = word,
            definition = definition,
            pronunciation = pronunciation ?: "",
            category = category,
            audioUrl = audioUrl,
            difficulty = difficultyLevel,
            examples = exampleSentence?.let { listOf(it) } ?: emptyList(),
            status = VocabularyStatus.NEW,
            dateAdded = System.currentTimeMillis(),
            lastReviewed = null
        )
    }

    companion object {
        fun fromDomain(w: VocabularyWord): VocabularyWordDTO {
            return VocabularyWordDTO(
                word = w.word,
                definition = w.definition,
                pronunciation = w.pronunciation.ifBlank { null },
                exampleSentence = w.examples.firstOrNull(),
                difficultyLevel = w.difficulty.ifBlank { "Beginner" },
                category = w.category.ifBlank { "General" },
                audioUrl = w.audioUrl,
                imageUrl = null
            )
        }
    }
}


@Serializable
private data class UserVocabularyDTO(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    val status: String,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("correct_count") val correctCount: Int = 0,
    @SerialName("last_reviewed") val lastReviewed: String? = null,
    @SerialName("next_review") val nextReview: String? = null,
    @SerialName("interval_days") val intervalDays: Int = 1,
    @SerialName("ease_factor") val easeFactor: Double = 2.50,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)


@Serializable
private data class UserVocabularyJoinDTO(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("word_id") val wordId: String,
    val status: String,
    @SerialName("review_count") val reviewCount: Int,
    @SerialName("correct_count") val correctCount: Int,
    @SerialName("last_reviewed") val lastReviewed: String?,
    @SerialName("vocabulary_words") val vocabularyWord: VocabularyWordDTO?
) {
    fun toDomain(): VocabularyWord? {
        return vocabularyWord?.let { word ->
            VocabularyWord(
                id = word.id ?: wordId,
                word = word.word,
                definition = word.definition,
                pronunciation = word.pronunciation ?: "",
                category = word.category,
                audioUrl = word.audioUrl,
                difficulty = word.difficultyLevel,
                examples = word.exampleSentence?.let { listOf(it) } ?: emptyList(),
                status = when (status.lowercase()) {
                    "learning" -> VocabularyStatus.LEARNING
                    "reviewing" -> VocabularyStatus.NEED_REVIEW
                    "mastered" -> VocabularyStatus.MASTERED
                    else -> VocabularyStatus.NEW
                },
                dateAdded = System.currentTimeMillis(),
                lastReviewed = lastReviewed?.let { System.currentTimeMillis() }
            )
        }
    }
}