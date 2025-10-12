package org.example.project.core.onboarding

object OnboardingQuestionBank {

    val questions: List<OnboardingQuestion> = listOf(
        OnboardingQuestion(
            id = "user_name",
            category = OnboardingCategory.BASIC_INFO,
            prompt = "What should I call you?",
            helperText = "I'll use this name in our practice sessions.",
            inputType = OnboardingInputType.TEXT,
            placeholder = "Type your preferred name",
            allowsVoiceInput = true
        ),
        OnboardingQuestion(
            id = "native_language",
            category = OnboardingCategory.BASIC_INFO,
            prompt = "What's your native language?",
            helperText = "Knowing this helps me tailor pronunciation tips.",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = defaultLanguageOptions(),
            allowsVoiceInput = true
        ),
        OnboardingQuestion(
            id = "target_language",
            category = OnboardingCategory.BASIC_INFO,
            prompt = "What language would you love to master?",
            helperText = "Choose the language you want to focus on first.",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = defaultLanguageOptions(),
            allowsVoiceInput = true
        ),
        OnboardingQuestion(
            id = "motivation",
            category = OnboardingCategory.GOALS,
            prompt = "Why do you want to improve your speaking skills?",
            helperText = "Pick what resonates with your current motivation.",
            inputType = OnboardingInputType.MULTI_SELECT,
            options = listOf(
                option("motivation_work", "Work opportunities", "work", "💼"),
                option("motivation_travel", "Travel adventures", "travel", "✈️"),
                option("motivation_confidence", "Build confidence", "confidence", "💪"),
                option("motivation_study", "Academic goals", "study", "🎓"),
                option("motivation_social", "Social connections", "social", "🤝")
            ),
            allowsVoiceInput = true
        ),
        OnboardingQuestion(
            id = "focus_area",
            category = OnboardingCategory.GOALS,
            prompt = "What should we focus on first?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("focus_fluency", "Fluency", "fluency", "💬"),
                option("focus_pronunciation", "Pronunciation", "pronunciation", "🎙️"),
                option("focus_vocabulary", "Vocabulary", "vocabulary", "🧠")
            )
        ),
        OnboardingQuestion(
            id = "confidence_level",
            category = OnboardingCategory.GOALS,
            prompt = "How confident do you feel speaking in your target language?",
            helperText = "1 = very shy, 5 = I speak without hesitation",
            inputType = OnboardingInputType.SCALE,
            minScale = 1,
            maxScale = 5
        ),
        OnboardingQuestion(
            id = "lesson_style",
            category = OnboardingCategory.LEARNING_PREFERENCES,
            prompt = "Would you like our lessons to feel more like a friendly chat or structured coaching?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("lesson_chat", "Friendly chat", "friendly_chat", "😊"),
                option("lesson_coach", "Structured coaching", "structured_coaching", "📋")
            ),
            followUpPrompt = "Great! I'll adapt my tone to match that vibe."
        ),
        OnboardingQuestion(
            id = "learning_style",
            category = OnboardingCategory.LEARNING_PREFERENCES,
            prompt = "Do you prefer learning by listening, reading, or speaking more?",
            inputType = OnboardingInputType.MULTI_SELECT,
            options = listOf(
                option("style_speaking", "Speaking", "speaking", "🗣️"),
                option("style_listening", "Listening", "listening", "🎧"),
                option("style_reading", "Reading", "reading", "📚")
            )
        ),
        OnboardingQuestion(
            id = "correction_preference",
            category = OnboardingCategory.LEARNING_PREFERENCES,
            prompt = "Would you like grammar corrections as you speak, or at the end of each session?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("correction_live", "Real-time corrections", "real_time", "⚡"),
                option("correction_summary", "End-of-session recap", "summary", "📝")
            )
        ),
        OnboardingQuestion(
            id = "tutor_vibe",
            category = OnboardingCategory.TONE_PERSONALITY,
            prompt = "What kind of tutor vibe do you prefer?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("tone_friendly", "Friendly", "friendly", "🤗"),
                option("tone_funny", "Funny", "funny", "😂"),
                option("tone_calm", "Calm", "calm", "😌"),
                option("tone_motivational", "Motivational", "motivational", "🚀"),
                option("tone_strict", "Strict", "strict", "🧭")
            )
        ),
        OnboardingQuestion(
            id = "mistake_response",
            category = OnboardingCategory.TONE_PERSONALITY,
            prompt = "How should I respond when you make mistakes?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("mistake_gentle", "Gently correct me", "gentle", "🌿"),
                option("mistake_encourage", "Encourage me to keep going", "encourage", "👏"),
                option("mistake_direct", "Be direct so I remember", "direct", "🎯")
            )
        ),
        OnboardingQuestion(
            id = "language_register",
            category = OnboardingCategory.TONE_PERSONALITY,
            prompt = "Would you like me to use slang or keep things formal?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("register_slang", "Slang is welcome", "slang", "🆒"),
                option("register_formal", "Keep it formal", "formal", "🤵")
            )
        ),
        OnboardingQuestion(
            id = "practice_time",
            category = OnboardingCategory.LIFESTYLE,
            prompt = "When do you usually have time to practice?",
            inputType = OnboardingInputType.MULTI_SELECT,
            options = listOf(
                option("schedule_morning", "Morning", "morning", "🌅"),
                option("schedule_afternoon", "Afternoon", "afternoon", "🌤️"),
                option("schedule_evening", "Evening", "evening", "🌆"),
                option("schedule_night", "Night", "night", "🌙")
            )
        ),
        OnboardingQuestion(
            id = "check_in_frequency",
            category = OnboardingCategory.LIFESTYLE,
            prompt = "How often should I check in with you?",
            helperText = "I'll send encouragement and reminders based on this.",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("frequency_daily", "Daily", "daily", "📆"),
                option("frequency_every_other_day", "Every other day", "every_other_day", "🔁"),
                option("frequency_weekly", "Weekly", "weekly", "🗓️")
            )
        ),
        OnboardingQuestion(
            id = "topics_interest",
            category = OnboardingCategory.INTERESTS,
            prompt = "What topics do you enjoy talking about?",
            helperText = "This helps me bring in stories you'll love.",
            inputType = OnboardingInputType.MULTI_SELECT,
            options = listOf(
                option("topic_tech", "Tech & innovation", "tech", "💻"),
                option("topic_music", "Music", "music", "🎵"),
                option("topic_movies", "Movies & shows", "movies", "🎬"),
                option("topic_travel", "Travel & culture", "travel", "🌍"),
                option("topic_food", "Food & cooking", "food", "🍜"),
                option("topic_business", "Business", "business", "📈"),
                option("topic_gaming", "Gaming", "gaming", "🎮")
            ),
            allowsVoiceInput = true
        ),
        OnboardingQuestion(
            id = "topics_avoid",
            category = OnboardingCategory.INTERESTS,
            prompt = "Is there anything you'd rather avoid discussing?",
            inputType = OnboardingInputType.MULTI_SELECT,
            options = listOf(
                option("avoid_politics", "Politics", "politics", "🏛️"),
                option("avoid_religion", "Religion", "religion", "⛪"),
                option("avoid_finance", "Personal finance", "finance", "💰"),
                option("avoid_none", "I'm open to anything", "none", "🌟")
            ),
            isOptional = true
        ),
        OnboardingQuestion(
            id = "motivation_style",
            category = OnboardingCategory.EMOTIONAL_SUPPORT,
            prompt = "How should I motivate you when things feel tough?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("motivation_encourage", "Encourage me gently", "encourage", "💖"),
                option("motivation_challenge", "Challenge me to push harder", "challenge", "🔥"),
                option("motivation_humor", "Make me laugh and keep it light", "humor", "😄")
            )
        ),
        OnboardingQuestion(
            id = "feedback_frequency",
            category = OnboardingCategory.EMOTIONAL_SUPPORT,
            prompt = "Do you like a lot of feedback or just the essentials?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("feedback_frequent", "Frequent feedback", "frequent", "📣"),
                option("feedback_occasional", "Occasional feedback", "occasional", "🔔"),
                option("feedback_minimal", "Only when necessary", "minimal", "🤫")
            )
        ),
        OnboardingQuestion(
            id = "social_practice",
            category = OnboardingCategory.SOCIAL,
            prompt = "Would you like to occasionally practice with other learners?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("social_yes", "Yes, that sounds exciting", "yes", "🎉"),
                option("social_no", "No, I'd prefer to keep it private", "no", "🛋️")
            ),
            isOptional = true
        ),
        OnboardingQuestion(
            id = "ai_voice",
            category = OnboardingCategory.VOICE,
            prompt = "What accent would you like your AI tutor to use?",
            inputType = OnboardingInputType.SINGLE_SELECT,
            options = listOf(
                option("voice_american", "American", "american", "🇺🇸"),
                option("voice_british", "British", "british", "🇬🇧"),
                option("voice_australian", "Australian", "australian", "🇦🇺"),
                option("voice_neutral", "Neutral", "neutral", "🌐")
            ),
            isOptional = true
        ),
        OnboardingQuestion(
            id = "future_goal",
            category = OnboardingCategory.FUTURE,
            prompt = "In 6 months, what would make you feel successful?",
            helperText = "Tell me your vision so we can celebrate when you get there!",
            inputType = OnboardingInputType.TEXT,
            placeholder = "Example: Speak confidently in meetings",
            allowsVoiceInput = true
        )
    )

    private fun option(
        id: String,
        label: String,
        value: String,
        emoji: String? = null,
        description: String? = null
    ) = OnboardingOption(
        id = id,
        label = label,
        value = value,
        emoji = emoji,
        description = description
    )

    private fun defaultLanguageOptions(): List<OnboardingOption> = listOf(
        option("lang_english", "English", "english", "🇬🇧"),
        option("lang_spanish", "Spanish", "spanish", "🇪🇸"),
        option("lang_french", "French", "french", "🇫🇷"),
        option("lang_german", "German", "german", "🇩🇪"),
        option("lang_korean", "Korean", "korean", "🇰🇷"),
        option("lang_chinese", "Chinese", "chinese", "🇨🇳"),
        option("lang_tagalog", "Filipino / Tagalog", "tagalog", "🇵🇭")
    )
}

