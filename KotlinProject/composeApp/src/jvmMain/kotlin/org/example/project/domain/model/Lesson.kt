package org.example.project.domain.model

data class Lesson(
    val id: String,
    val title: String,
    val category: LessonCategory,
    val difficulty: LessonDifficulty,
    val duration: Int,
    val lessonsCount: Int,
    val completedCount: Int,
    val progressPercentage: Int,
    val icon: String,
    val isAvailable: Boolean = true,
) {
    companion object {
        fun getSampleLessons(): List<Lesson> = emptyList()

        fun getDemoLessons(): List<Lesson> =
            listOf(
                Lesson(
                    id = "grammar_mastery",
                    title = "Grammar Mastery",
                    category = LessonCategory.GRAMMAR,
                    difficulty = LessonDifficulty.INTERMEDIATE,
                    duration = 15,
                    lessonsCount = 12,
                    completedCount = 8,
                    progressPercentage = 67,
                    icon = "📚",
                ),
                Lesson(
                    id = "vocabulary_builder",
                    title = "Vocabulary Builder",
                    category = LessonCategory.VOCABULARY,
                    difficulty = LessonDifficulty.INTERMEDIATE,
                    duration = 20,
                    lessonsCount = 15,
                    completedCount = 10,
                    progressPercentage = 67,
                    icon = "📖",
                ),
                Lesson(
                    id = "conversation_skills",
                    title = "Conversation Skills",
                    category = LessonCategory.CONVERSATION,
                    difficulty = LessonDifficulty.INTERMEDIATE,
                    duration = 18,
                    lessonsCount = 10,
                    completedCount = 5,
                    progressPercentage = 50,
                    icon = "💬",
                ),
                Lesson(
                    id = "pronunciation_guide",
                    title = "Pronunciation Guide",
                    category = LessonCategory.PRONUNCIATION,
                    difficulty = LessonDifficulty.INTERMEDIATE,
                    duration = 12,
                    lessonsCount = 8,
                    completedCount = 3,
                    progressPercentage = 38,
                    icon = "🎙️",
                ),
            )
    }
}

enum class LessonCategory(val displayName: String) {
    GRAMMAR("Grammar"),
    VOCABULARY("Vocabulary"),
    CONVERSATION("Conversation"),
    PRONUNCIATION("Pronunciation"),
}

enum class LessonDifficulty(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

data class LevelProgress(
    val level: Int,
    val title: String,
    val completedLessons: Int,
    val remainingLessons: Int,
    val progressPercentage: Int,
) {
    companion object {
        fun getSampleProgress(): LevelProgress =
            LevelProgress(
                level = 1,
                title = "Beginner Level",
                completedLessons = 0,
                remainingLessons = 0,
                progressPercentage = 0,
            )
    }
}

data class RecentLesson(
    val id: String,
    val title: String,
    val category: String,
    val difficulty: String,
    val duration: Int,
    val progressPercentage: Int,
    val icon: String,
) {
    companion object {
        fun getSampleRecentLessons(): List<RecentLesson> = emptyList()

        fun getDemoRecentLessons(): List<RecentLesson> =
            listOf(
                RecentLesson(
                    id = "present_perfect",
                    title = "Present Perfect Tense",
                    category = "Grammar",
                    difficulty = "Intermediate",
                    duration = 15,
                    progressPercentage = 75,
                    icon = "📚",
                ),
                RecentLesson(
                    id = "business_vocab",
                    title = "Business Vocabulary",
                    category = "Vocabulary",
                    difficulty = "Intermediate",
                    duration = 20,
                    progressPercentage = 100,
                    icon = "📖",
                ),
                RecentLesson(
                    id = "restaurant_conv",
                    title = "Restaurant Conversations",
                    category = "Conversation",
                    difficulty = "Intermediate",
                    duration = 18,
                    progressPercentage = 0,
                    icon = "💬",
                ),
                RecentLesson(
                    id = "difficult_consonants",
                    title = "Difficult Consonants",
                    category = "Pronunciation",
                    difficulty = "Intermediate",
                    duration = 12,
                    progressPercentage = 40,
                    icon = "🎙️",
                ),
            )
    }
}

data class LessonCategoryInfo(
    val difficulty: LessonDifficulty,
    val title: String,
    val description: String,
    val totalLessons: Int,
    val completedLessons: Int,
    val isLocked: Boolean,
    val progressPercentage: Int,
) {
    companion object {
        fun getSampleCategories(userLevel: LessonDifficulty): List<LessonCategoryInfo> {
            val beginnerCompleted = 0
            val intermediateCompleted = 0
            
            // Get actual lesson counts from the lesson topics
            val beginnerLessonCount = LessonTopic.getBeginnerTopics().size
            val intermediateLessonCount = LessonTopic.getIntermediateTopics().size
            val advancedLessonCount = LessonTopic.getAdvancedTopics().size
            
            return listOf(
                LessonCategoryInfo(
                    difficulty = LessonDifficulty.BEGINNER,
                    title = "Beginner",
                    description = "Start your language learning journey with foundational lessons",
                    totalLessons = beginnerLessonCount,
                    completedLessons = beginnerCompleted,
                    isLocked = false,
                    progressPercentage = 0,
                ),
                LessonCategoryInfo(
                    difficulty = LessonDifficulty.INTERMEDIATE,
                    title = "Intermediate",
                    description = "Build on your basics with more complex concepts and conversations",
                    totalLessons = intermediateLessonCount,
                    completedLessons = intermediateCompleted,
                    isLocked = userLevel == LessonDifficulty.BEGINNER && beginnerCompleted < beginnerLessonCount,
                    progressPercentage = 0,
                ),
                LessonCategoryInfo(
                    difficulty = LessonDifficulty.ADVANCED,
                    title = "Advanced",
                    description = "Master advanced topics and achieve fluency in complex situations",
                    totalLessons = advancedLessonCount,
                    completedLessons = 0,
                    isLocked = userLevel != LessonDifficulty.ADVANCED && intermediateCompleted < intermediateLessonCount,
                    progressPercentage = 0,
                ),
            )
        }
    }
}

data class LessonTopic(
    val id: String,
    val title: String,
    val description: String,
    val lessonNumber: Int? = null,
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false,
    val durationMinutes: Int? = null,
) {
    companion object {
        fun getSampleTopics(): List<LessonTopic> = emptyList()
        
        /**
         * Get all Intermediate level lesson topics
         * TODO: Add intermediate topics here
         */
        fun getIntermediateTopics(): List<LessonTopic> = emptyList()
        
        /**
         * Get all Advanced level lesson topics
         * TODO: Add advanced topics here
         */
        fun getAdvancedTopics(): List<LessonTopic> = emptyList()
        
        fun getBeginnerTopics(): List<LessonTopic> = listOf(
            LessonTopic(
                id = "intro_chinese",
                title = "Intro to the Chinese Language",
                description = "Welcome to the jungle of Mandarin Chinese! Wait, Mandarin, Chinese, or Mandarin Chinese? Are they even the same thing? In the first chapter of this Chinese lesson series, you'll learn some basic concepts of the language of China.",
                lessonNumber = null,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 15
            ),
            LessonTopic(
                id = "lesson_1_greetings",
                title = "Chinese Greetings and Essentials",
                description = "Here's your basic Chinese survival kit – common Chinese greetings beyond \"ni hao\", how to say \"yes\" and \"no\", and how to say \"please\" and \"thank you\", etc.",
                lessonNumber = 1,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_2_alphabet",
                title = "Chinese Alphabet & Pronunciation",
                description = "Well, Chinese doesn't really have an alphabet, but there is this Romanization system called Pinyin you must know to learn the pronunciation of words. Don't let Pinyin get you – discover how to pronounce Q, X, Z sounds in Chinese.",
                lessonNumber = 2,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_3_tones",
                title = "Chinese Tones",
                description = "You probably already know that Chinese is a tonal language. Many words appear to have the same basic pronunciation with varied pitches. It seems like a small difference, but it's quite important. Here's an introduction to the four tones of Chinese.",
                lessonNumber = 3,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 30
            ),
            LessonTopic(
                id = "lesson_4_grammar",
                title = "Basic Chinese Grammar",
                description = "Not the most exciting topic, but let's get real – you'll need it. This quick Chinese grammar lesson will get you through the basic grammar rules in minutes that might otherwise take you weeks to figure out on your own.",
                lessonNumber = 4,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 35
            ),
            LessonTopic(
                id = "lesson_5_name",
                title = "Saying Your Name in Chinese",
                description = "You wouldn't think there would be much trouble stating your name, but it can actually be quite hard for an average Chinese person to remember or pronounce your name unless you say it the Chinese way.",
                lessonNumber = 5,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 15
            ),
            LessonTopic(
                id = "lesson_6_yourself",
                title = "Talking about Yourself in Chinese",
                description = "You'll learn some super useful Chinese conversation starters and how to talk about yourself and your family. Learn how to say \"my name is…\", how to say where you live, what you do, and how to talk about the things you like doing.",
                lessonNumber = 6,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_7_countries",
                title = "Countries in Chinese",
                description = "This lesson will show you how to let everybody around you know where you are from. We enlist all the country names in Chinese from Argentina to Madagascar. Plus you'll learn some little tricks to help you remember some of them.",
                lessonNumber = 7,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_8_professions",
                title = "Professions in Chinese",
                description = "One of the best conversation starters in China, guaranteed. Learn how to say \"I am an engineer at Siemens\", and talk about what you like or dislike about your job, and you'll always have something to contribute to a conversation!",
                lessonNumber = 8,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_9_have",
                title = "The Verb \"to Have\" in Chinese",
                description = "Probably the most important verb to have in your Chinese toolbox – \"yǒu\" is used in a lot of places where English speakers wouldn't expect it. This lesson shows you how to express possession and existence in Chinese.",
                lessonNumber = 9,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_10_verbs",
                title = "Connecting Verbs in Chinese",
                description = "In this lesson, you'll learn the rule for using two or more verbs in one Chinese sentence. You'll be able to say that you're going somewhere to do something. You'll also learn some incredibly useful verbs. Hoorah!",
                lessonNumber = 10,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_11_negating",
                title = "Negating Statements in Chinese",
                description = "You can turn a positive statement into a negative statement with two negation words in Chinese: \"bù\" and \"méi\". This lesson will show you how to say that you don't like sports, or that you don't have the time to play sports.",
                lessonNumber = 11,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_12_questions",
                title = "Forming Questions with \"ne\"",
                description = "Let us introduce you to the world of particles. Once you master this one, you'll be able to ask simple questions in Chinese. You'll also see how easy it'd be to bounce a question imposed on you back to your conversation partner. Fun!",
                lessonNumber = 12,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_13_commands",
                title = "Giving Commands in Chinese",
                description = "One of the easiest grammar points in Chinese, but we'll walk you through it anyway. Learn how to tell someone to do or not to do something in China and sound as polite or impolite as you need to be!",
                lessonNumber = 13,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 15
            ),
            LessonTopic(
                id = "lesson_14_understand",
                title = "\"I Don't Understand\" in Chinese",
                description = "Once you step into the Chinese-speaking territory, you're bound to encounter communication problems at one point or another. You can do so much better than a blunt \"ting bu dong\". Learn how to respond naturally when your Chinese deserts you.",
                lessonNumber = 14,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 15
            ),
            LessonTopic(
                id = "lesson_15_counting",
                title = "Counting in Chinese – 0 to 1,000,000,000",
                description = "Want to count in Chinese? Who doesn't? Chinese numbers are actually much easier than their English counterparts to make you go. This lesson will have you covered for everything between zero and one billion – that's probably all the numbers you need!",
                lessonNumber = 15,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 30
            ),
            LessonTopic(
                id = "lesson_16_measure",
                title = "Chinese Measure Words",
                description = "Measure words are used a lot more in Chinese than in English – so much so that there are 150+ of them. Never fear! In this lesson, we'll give you some tricks for using the most common Chinese measure words to help you get by in most conversations.",
                lessonNumber = 16,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 30
            ),
            LessonTopic(
                id = "lesson_17_age",
                title = "Talking about Age in Chinese",
                description = "In this lesson, you'll get a quick introduction to age in Chinese. You'll learn why it's important and how to ask specific people their age, using both polite and informal language. There are some interesting rules for how to raise the question correctly.",
                lessonNumber = 17,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_18_time",
                title = "Telling the Time in Chinese",
                description = "Once you've known your way around numbers, telling the time in Chinese becomes pretty easy – just watch out for a couple of curveballs coming in your way. This lesson will cover all the essential time expressions like \"just now\" and \"soon\".",
                lessonNumber = 18,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_19_days",
                title = "Days of the Week in Chinese",
                description = "Chinese is fairly logical when it comes to naming the days of the week. Well, there are three names for each day (it's nuts), but you'll find them pretty intuitive and predictable. So here's how to say Monday to Sunday in Chinese.",
                lessonNumber = 19,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 15
            ),
            LessonTopic(
                id = "lesson_20_dates",
                title = "Dates in Chinese",
                description = "Simple, but pretty essential. Learn how to say dates, months, and years in Chinese. Combine this with the time and days of the week lessons above, and you should feel very comfortable making appointments or holidays reservations.",
                lessonNumber = 20,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_21_comparing",
                title = "Comparing Things in Chinese",
                description = "How do you say when someone is smarter than another, or taller than another, or less beautiful than another? In this lesson, you'll learn how to compare two people or two things that aren't equal as well as superlatives (who is the smartest?)",
                lessonNumber = 21,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_22_directions",
                title = "Asking for Directions in Chinese",
                description = "Where's the famous dim sum restaurant? Where is a public restroom? Learn how to ask for directions in Chinese before you start packing. Simply learn these essential keywords and phrases because you'll always need to get somewhere!",
                lessonNumber = 22,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 20
            ),
            LessonTopic(
                id = "lesson_23_restaurant",
                title = "Ordering in a Restaurant",
                description = "Get a crash course in how to order in a Chinese restaurant using \"xiǎng\" and \"yào\"(want). You'll learn some useful food & drink vocabulary, and how to catch the waiter or waitress's attention when you need to.",
                lessonNumber = 23,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 25
            ),
            LessonTopic(
                id = "lesson_24_topic",
                title = "Topic-Comment Structure",
                description = "Topic-comment structure in Chinese can be a little tricky to wrap your head around since it sounds \"off\" to an English ear. Make sure you've got a firm grasp on the basic\"S-V-O\" before you dive in.",
                lessonNumber = 24,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 30
            ),
            LessonTopic(
                id = "lesson_25_filler",
                title = "Conversation Filler: \"Nèi Ge\"",
                description = "Every language has an array of filler words that help to grease the wheels of conversation. The most popular filler word in Chinese is \"nèi ge\". This final lesson will teach you how to slip \"nèi ge\" naturally into your conversation.",
                lessonNumber = 25,
                isCompleted = false,
                isLocked = false,
                durationMinutes = 15
            )
        )
    }
}