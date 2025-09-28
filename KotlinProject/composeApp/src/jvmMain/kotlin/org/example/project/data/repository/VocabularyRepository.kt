package org.example.project.data.repository

import org.example.project.domain.model.*

interface VocabularyRepository {
    
    suspend fun getAllVocabularyWords(): Result<List<VocabularyWord>>
    suspend fun getVocabularyWordsByCategory(category: String): Result<List<VocabularyWord>>
    suspend fun getVocabularyWordsByDifficulty(difficulty: String): Result<List<VocabularyWord>>
    suspend fun searchVocabularyWords(query: String): Result<List<VocabularyWord>>
    suspend fun getVocabularyWord(wordId: String): Result<VocabularyWord?>
    suspend fun addVocabularyWord(word: VocabularyWord): Result<VocabularyWord>
    suspend fun updateVocabularyWord(word: VocabularyWord): Result<VocabularyWord>
    suspend fun deleteVocabularyWord(wordId: String): Result<Unit>
    
    suspend fun getUserVocabulary(userId: String): Result<List<UserVocabularyWord>>
    suspend fun addWordToUserVocabulary(userId: String, wordId: String): Result<UserVocabularyWord>
    suspend fun updateUserWordStatus(userId: String, wordId: String, status: VocabularyStatus): Result<Unit>
    suspend fun getUserWordsByStatus(userId: String, status: VocabularyStatus): Result<List<UserVocabularyWord>>
    suspend fun removeWordFromUserVocabulary(userId: String, wordId: String): Result<Unit>
    
    suspend fun getUserVocabularyStats(userId: String): Result<VocabularyStats>
    suspend fun getWordsForReview(userId: String, limit: Int = 10): Result<List<UserVocabularyWord>>
}

data class UserVocabularyWord(
    val userId: String,
    val wordId: String,
    val word: VocabularyWord,
    val status: VocabularyStatus,
    val dateAdded: Long,
    val lastReviewed: Long?,
    val reviewCount: Int = 0,
    val masteryLevel: Int = 0 // 0-100
)

data class VocabularyStats(
    val totalWords: Int,
    val newWords: Int,
    val reviewingWords: Int,
    val masteredWords: Int,
    val averageMastery: Int,
    val wordsAddedToday: Int,
    val wordsReviewedToday: Int
)
