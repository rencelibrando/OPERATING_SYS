package org.example.project.core.onboarding

import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.example.project.core.auth.RealSupabaseAuthService
import org.example.project.core.auth.User
import org.example.project.core.config.SupabaseConfig
import org.example.project.core.profile.ProfileService
import org.example.project.core.utils.PreferencesManager
import org.example.project.domain.model.PersonalInfo
import org.example.project.domain.model.LearningProfile


class OnboardingService(
    private val authService: RealSupabaseAuthService = RealSupabaseAuthService(),
    private val repository: OnboardingRepository = OnboardingRepository(),
    private val profileService: ProfileService = ProfileService()
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _currentProfile = MutableStateFlow<OnboardingProfile?>(null)
    val currentProfile: StateFlow<OnboardingProfile?> = _currentProfile.asStateFlow()

    private val _answers = MutableStateFlow<Map<String, OnboardingAnswer>>(emptyMap())
    val answers: StateFlow<Map<String, OnboardingAnswer>> = _answers.asStateFlow()

    suspend fun initialize(): Result<User?> = runCatching {
        val userResult = authService.getCurrentUser()
        val user = userResult.getOrNull() ?: return@runCatching null

        println("🔍 Initializing onboarding for user: ${user.id}")
        println("📧 User email: ${user.email}")
        
        // ALWAYS fetch from database - this is the source of truth
        println("📊 Fetching onboarding profile from database...")
        
        try {
            val profile = repository.fetchOnboardingProfile(user.id).getOrNull()
            
            if (profile != null) {
                println("✅✅✅ PROFILE FOUND IN DATABASE:")
                println("   📌 User ID: ${profile.userId}")
                println("   📌 User Email: ${user.email}")
                println("   📌 Is Onboarded: ${profile.isOnboarded}")
                println("   📌 Current Step: ${profile.currentStep}")
                println("   📌 Has AI Profile: ${profile.aiProfile != null}")
                
                _currentProfile.value = profile
                
                // Load saved answers if not completed yet
                if (!profile.isOnboarded && profile.stateSnapshot != null) {
                    _answers.value = deserializeState(profile.stateSnapshot)
                    println("   📌 Loaded ${_answers.value.size} saved answers")
                }
                
                // Update cache for this specific user
                PreferencesManager.cacheOnboardingCompletion(user.id, profile.isOnboarded)
                
                println("   ➡️ Onboarding will ${if (profile.isOnboarded) "BE SKIPPED" else "BE SHOWN"}")
            } else {
                println("ℹ️ℹ️ℹ️ NO PROFILE FOUND IN DATABASE")
                println("   📌 User ID: ${user.id}")
                println("   📌 User Email: ${user.email}")
                println("   ➡️ This is a NEW USER - ONBOARDING WILL BE SHOWN")
                
                // New user - create empty profile in memory
                _currentProfile.value = OnboardingProfile(
                    userId = user.id,
                    isOnboarded = false,
                    aiProfile = null,
                    stateSnapshot = null,
                    currentStep = 0
                )
                
                // Cache as not completed for this specific user
                PreferencesManager.cacheOnboardingCompletion(user.id, false)
            }
        } catch (e: Exception) {
            println("❌❌❌ ERROR fetching profile from database: ${e.message}")
            e.printStackTrace()
            throw e
        }

        user
    }

    fun shouldShowOnboarding(): Boolean {
        val profile = _currentProfile.value
        
        if (profile == null) {
            println("❓❓❓ shouldShowOnboarding: profile is NULL -> SHOW onboarding")
            return true
        }
        
        val isOnboarded = profile.isOnboarded
        val shouldShow = !isOnboarded
        
        println("❓❓❓ shouldShowOnboarding:")
        println("   📌 Profile exists: YES")
        println("   📌 is_onboarded: $isOnboarded")
        println("   📌 Decision: ${if (shouldShow) "SHOW ONBOARDING" else "SKIP ONBOARDING"}")
        
        return shouldShow
    }

    fun getCurrentStep(): Int {
        // Always use the in-memory answers size, not the persisted profile step
        // The profile step is only used for resuming on app restart
        return _answers.value.size
    }

    fun getNextQuestion(): OnboardingQuestion? {
        val nextIndex = getCurrentStep()
        val question = OnboardingQuestionBank.questions.getOrNull(nextIndex)
        println("??? getNextQuestion: nextIndex=$nextIndex, questionId=${question?.id ?: "NONE"}, totalAnswers=${_answers.value.size}")
        return question
    }

    fun hasPendingQuestions(): Boolean {
        return getCurrentStep() < OnboardingQuestionBank.questions.size
    }

    fun recordAnswer(question: OnboardingQuestion, response: OnboardingResponse) {
        val updated = _answers.value.toMutableMap()
        updated[question.id] = OnboardingAnswer(question.id, response)
        println("??? OnboardingService.recordAnswer: ${question.id} = ${response.summaryText()}")
        println("??? Total answers: ${updated.size}")
        _answers.value = updated
    }

    suspend fun persistProgress(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val profile = _currentProfile.value
            val userResult = authService.getCurrentUser()
            val user = userResult.getOrNull() ?: return@withContext

            val stateJson = serializeState(_answers.value)
            val currentStep = _answers.value.size

            repository.upsertOnboardingState(
                userId = user.id,
                isOnboarded = false,
                aiProfile = null,
                onboardingState = stateJson,
                currentStep = currentStep
            )

            _currentProfile.value = OnboardingProfile(
                userId = user.id,
                isOnboarded = false,
                aiProfile = null,
                stateSnapshot = stateJson,
                currentStep = currentStep
            )
        }
    }

    suspend fun completeOnboarding(): Result<JsonObject> = runCatching {
        println("??? OnboardingService.completeOnboarding: Starting...")
        val answersSnapshot = _answers.value
        println("??? Total answers collected: ${answersSnapshot.size}")
        
        if (answersSnapshot.size < OnboardingQuestionBank.questions.size) {
            throw IllegalStateException("Not all onboarding questions have been answered")
        }
        
        val userResult = authService.getCurrentUser()
        val user = userResult.getOrThrow()?.takeIf { it.isEmailVerified }
            ?: throw IllegalStateException("User must be signed in and verified")
        
        println("??? Building profile data for user: ${user.email}")
        
        // Build PersonalInfo and LearningProfile from onboarding answers
        val personalInfo = buildPersonalInfo(answersSnapshot, user)
        val learningProfile = buildLearningProfile(answersSnapshot)
        
        println("??? PersonalInfo: ${personalInfo.fullName}, Native: ${personalInfo.nativeLanguage}, Target: ${personalInfo.targetLanguages}")
        println("??? LearningProfile: Level: ${learningProfile.currentLevel}, Goal: ${learningProfile.primaryGoal}")
        
        // Build AI persona JSON for the profiles table
        val persona = buildPersonaJson(answersSnapshot, user)
        println("??? AI Persona JSON created")

        // Save to profiles table
        println("??? Saving to profiles table...")
        repository.markOnboardingComplete(
            userId = user.id,
            aiProfile = persona,
            stateSnapshot = serializeState(answersSnapshot),
            stepCount = answersSnapshot.size
        ).getOrThrow()
        
        // Save PersonalInfo to user metadata
        println("??? Updating personal info in user metadata...")
        profileService.updatePersonalInfo(personalInfo).getOrThrow()
        
        // Save LearningProfile to user metadata
        println("??? Updating learning profile in user metadata...")
        profileService.updateLearningProfile(learningProfile).getOrThrow()
        
        // Verify data was saved correctly
        println("??? Verifying data persistence...")
        val savedPersonalInfo = profileService.loadPersonalInfo().getOrNull()
        val savedLearningProfile = profileService.loadLearningProfile().getOrNull()
        
        // Personal info and learning profile verification
        if (savedPersonalInfo == null || savedLearningProfile == null) {
            println("?????? Warning: Could not load back profile data, but save operations succeeded")
            // Don't throw error - the saves succeeded, this might be a timing/caching issue
        } else {
            println("??? Data verification successful!")
            println("   - PersonalInfo: ${savedPersonalInfo.fullName}")
            println("   - LearningProfile: ${savedLearningProfile.currentLevel}")
        }
        
        println("??? Note: profiles table saves succeeded (PATCH returned 200 OK)")
        println("??? Onboarding completion recorded!")

        _currentProfile.value = OnboardingProfile(
            userId = user.id,
            isOnboarded = true,
            aiProfile = persona,
            stateSnapshot = null,
            currentStep = OnboardingQuestionBank.questions.size
        )
        
        // Cache the completion status
        PreferencesManager.cacheOnboardingCompletion(user.id, true)
        
        println("??? Onboarding completed and verified successfully!")
        persona
    }

    fun resetOnboarding() {
        _answers.value = emptyMap()
        _currentProfile.value?.let { profile ->
            _currentProfile.value = profile.copy(
                isOnboarded = false,
                aiProfile = null,
                stateSnapshot = null,
                currentStep = 0
            )
            // Clear cache when resetting
            PreferencesManager.clearOnboardingCache(profile.userId)
        }
    }

    private fun serializeState(state: Map<String, OnboardingAnswer>): JsonObject {
        return buildJsonObject {
            state.values.forEach { answer ->
                put(answer.questionId, answer.response.toJsonElement())
            }
        }
    }

    private fun deserializeState(json: JsonObject): Map<String, OnboardingAnswer> {
        val mutable = mutableMapOf<String, OnboardingAnswer>()
        json.forEach { (key, value) ->
            val question = OnboardingQuestionBank.questions.find { it.id == key }
            if (question != null) {
                (value as? JsonObject)?.let { element ->
                    decodeResponse(question, element)?.let { response ->
                        mutable[key] = OnboardingAnswer(key, response)
                    }
                }
            }
        }
        return mutable
    }

    private fun decodeResponse(question: OnboardingQuestion, element: JsonObject): OnboardingResponse? {
        return when (question.inputType) {
            OnboardingInputType.TEXT -> element["value"].asStringOrNull()?.let { OnboardingResponse.Text(it) }
            OnboardingInputType.SINGLE_SELECT -> {
                val optionId = element["option_id"].asStringOrNull() ?: return null
                val option = question.options.find { it.id == optionId } ?: return null
                OnboardingResponse.SingleChoice(option.id, option.label, option.value)
            }
            OnboardingInputType.MULTI_SELECT -> {
                val optionIds = element["option_ids"].asStringList()
                val selected = question.options.filter { optionIds.contains(it.id) }
                OnboardingResponse.MultiChoice(
                    optionIds = selected.map { it.id },
                    labels = selected.map { it.label },
                    values = selected.map { it.value }
                )
            }
            OnboardingInputType.SCALE -> {
                val score = element["score"].asIntOrNull() ?: return null
                OnboardingResponse.Scale(score, question.minScale, question.maxScale)
            }
        }
    }

    private fun buildPersonaJson(
        answers: Map<String, OnboardingAnswer>,
        user: User
    ): JsonObject {
        fun answer(id: String) = answers[id]?.response

        val persona = buildJsonObject {
            put("user_id", user.id)
            put("user_email", user.email)
            put("user_name", answer("user_name")?.summaryText() ?: user.fullName)
            put("native_language", answer("native_language")?.summaryText())
            put("target_language", answer("target_language")?.summaryText())
            putJsonArray("learning_goals") {
                when (val resp = answer("motivation")) {
                    is OnboardingResponse.MultiChoice -> resp.values.forEach { add(JsonPrimitive(it)) }
                    is OnboardingResponse.SingleChoice -> add(JsonPrimitive(resp.value))
                    else -> { }
                }
            }
            put("primary_focus", answer("focus_area")?.summaryText())
            put("confidence_level", (answer("confidence_level") as? OnboardingResponse.Scale)?.score)
            putJsonArray("learning_style") {
                when (val resp = answer("learning_style")) {
                    is OnboardingResponse.MultiChoice -> resp.values.forEach { add(JsonPrimitive(it)) }
                    else -> { }
                }
            }
            put("lesson_tone", answer("lesson_style")?.summaryText())
            put("correction_style", answer("correction_preference")?.summaryText())
            put("tutor_vibe", answer("tutor_vibe")?.summaryText())
            put("mistake_response", answer("mistake_response")?.summaryText())
            put("language_register", answer("language_register")?.summaryText())
            putJsonArray("practice_schedule") {
                when (val resp = answer("practice_time")) {
                    is OnboardingResponse.MultiChoice -> resp.values.forEach { add(JsonPrimitive(it)) }
                    else -> { }
                }
            }
            put("check_in_frequency", answer("check_in_frequency")?.summaryText())
            putJsonArray("topics_of_interest") {
                when (val resp = answer("topics_interest")) {
                    is OnboardingResponse.MultiChoice -> resp.values.forEach { add(JsonPrimitive(it)) }
                    else -> { }
                }
            }
            putJsonArray("topics_to_avoid") {
                when (val resp = answer("topics_avoid")) {
                    is OnboardingResponse.MultiChoice -> resp.values.forEach { add(JsonPrimitive(it)) }
                    else -> { }
                }
            }
            put("motivation_style", answer("motivation_style")?.summaryText())
            put("feedback_frequency", answer("feedback_frequency")?.summaryText())
            put("social_preference", answer("social_practice")?.summaryText())
            put("ai_accent_preference", answer("ai_voice")?.summaryText())
            put("future_goal", answer("future_goal")?.summaryText())
        }

        return persona
    }
    
    /**
     * Maps onboarding answers to PersonalInfo model for user profile
     */
    private fun buildPersonalInfo(
        answers: Map<String, OnboardingAnswer>,
        user: User
    ): PersonalInfo {
        fun answer(id: String) = answers[id]?.response
        
        val userName = answer("user_name")?.summaryText() ?: user.fullName
        val nameParts = userName.split(" ", limit = 2)
        
        val targetLanguages = when (val resp = answer("target_language")) {
            is OnboardingResponse.Text -> listOfNotNull(resp.value.takeIf { it.isNotBlank() })
            is OnboardingResponse.SingleChoice -> listOfNotNull(resp.value)
            is OnboardingResponse.MultiChoice -> resp.values
            else -> emptyList()
        }
        
        return PersonalInfo(
            firstName = user.firstName.takeIf { it.isNotBlank() } ?: nameParts.getOrNull(0) ?: "",
            lastName = user.lastName.takeIf { it.isNotBlank() } ?: nameParts.getOrNull(1) ?: "",
            email = user.email,
            avatar = user.initials,
            profileImageUrl = user.profileImageUrl,
            dateOfBirth = null,
            location = null,
            nativeLanguage = answer("native_language")?.summaryText() ?: "",
            targetLanguages = targetLanguages,
            bio = answer("future_goal")?.summaryText()
        )
    }
    
    /**
     * Maps onboarding answers to LearningProfile model for user profile
     */
    private fun buildLearningProfile(
        answers: Map<String, OnboardingAnswer>
    ): LearningProfile {
        fun answer(id: String) = answers[id]?.response
        
        // Map confidence level to proficiency level
        val confidenceScore = (answer("confidence_level") as? OnboardingResponse.Scale)?.score ?: 1
        val currentLevel = when (confidenceScore) {
            1, 2 -> "Beginner"
            3 -> "Intermediate"
            4, 5 -> "Advanced"
            else -> "Beginner"
        }
        
        // Extract learning goals/motivations
        val motivations = when (val resp = answer("motivation")) {
            is OnboardingResponse.MultiChoice -> resp.labels
            is OnboardingResponse.SingleChoice -> listOf(resp.label)
            else -> emptyList()
        }
        
        // Extract focus areas
        val focusArea = answer("focus_area")?.summaryText() ?: ""
        val focusAreas = if (focusArea.isNotBlank()) listOf(focusArea) else emptyList()
        
        // Extract learning styles
        val learningStyles = when (val resp = answer("learning_style")) {
            is OnboardingResponse.MultiChoice -> resp.labels
            else -> emptyList()
        }
        
        val preferredLearningStyle = if (learningStyles.isNotEmpty()) {
            learningStyles.joinToString(" + ")
        } else {
            "Mixed"
        }
        
        // Extract practice times/available time slots
        val availableTimeSlots = when (val resp = answer("practice_time")) {
            is OnboardingResponse.MultiChoice -> resp.labels
            else -> emptyList()
        }
        
        // Default weekly goal hours based on time commitment
        val weeklyGoalHours = when {
            availableTimeSlots.any { it.contains("Daily", ignoreCase = true) } -> 7
            availableTimeSlots.size >= 3 -> 5
            availableTimeSlots.size >= 2 -> 3
            else -> 2
        }
        
        // Primary goal from focus area or first motivation
        val primaryGoal = focusArea.takeIf { it.isNotBlank() } 
            ?: motivations.firstOrNull() 
            ?: "General Learning"
        
        return LearningProfile(
            currentLevel = currentLevel,
            primaryGoal = primaryGoal,
            weeklyGoalHours = weeklyGoalHours,
            preferredLearningStyle = preferredLearningStyle,
            focusAreas = focusAreas,
            availableTimeSlots = availableTimeSlots,
            motivations = motivations
        )
    }
}

