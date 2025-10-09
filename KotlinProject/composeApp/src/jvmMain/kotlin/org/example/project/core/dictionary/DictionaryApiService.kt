package org.example.project.core.dictionary

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DictionaryApiService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun lookupWord(word: String): Result<WordDefinition> = runCatching {
        val response = client.get("https://api.dictionaryapi.dev/api/v2/entries/en/${word.trim()}")

        if (response.status.value in 200..299) {
            val apiResponse = response.body<List<DictionaryApiResponse>>()

            if (apiResponse.isEmpty()) {
                throw WordNotFoundException("Word not found")
            }

            val firstEntry = apiResponse.first()
            val firstMeaning = firstEntry.meanings.firstOrNull()
            val firstDefinition = firstMeaning?.definitions?.firstOrNull()
            val firstPhonetics = firstEntry.phonetics.firstOrNull()

            WordDefinition(
                word = firstEntry.word,
                definition = firstDefinition?.definition ?: "No definition available",
                pronunciation = firstEntry.phonetic ?: "",
                audio = firstPhonetics?.audio ?: "No audio URL available",
                example = firstDefinition?.example,
                partOfSpeech = firstMeaning?.partOfSpeech ?: "Unknown"
            )
        } else {
            throw WordNotFoundException("Word not found")
        }
    }
}

data class WordDefinition(
    val word: String,
    val definition: String,
    val pronunciation: String,
    val audio: String,
    val example: String?,
    val partOfSpeech: String,
)

class WordNotFoundException(message: String) : Exception(message)

@Serializable
private data class DictionaryApiResponse(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<Phonetics> = emptyList(),
    val meanings: List<Meaning> = emptyList()
)

@Serializable
private data class Phonetics(
    val audio: String
)

@Serializable
private data class Meaning(
    @SerialName("partOfSpeech") val partOfSpeech: String,
    val definitions: List<Definition> = emptyList()
)

@Serializable
private data class Definition(
    val definition: String,
    val example: String? = null
)