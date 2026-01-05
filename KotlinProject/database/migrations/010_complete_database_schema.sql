-- Complete Database Schema Migration for Kotlin Project
-- Matches DATABASE_STRUCTURE.md
-- This migration ensures all tables, columns, constraints, and indexes exist

-- ============================================================================
-- LESSON SYSTEM TABLES
-- ============================================================================

-- Lesson Topics (comprehensive structure)
CREATE TABLE IF NOT EXISTS public.lesson_topics (
    id TEXT PRIMARY KEY,
    difficulty_level TEXT NOT NULL CHECK (difficulty_level IN ('Beginner', 'Intermediate', 'Advanced')),
    title TEXT NOT NULL,
    description TEXT,
    lesson_number INTEGER,
    duration_minutes INTEGER,
    sort_order INTEGER DEFAULT 0,
    is_locked BOOLEAN DEFAULT false,
    is_published BOOLEAN DEFAULT true,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    language TEXT CHECK (language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish')),
    cover_image_url TEXT,
    icon_emoji TEXT DEFAULT '📚',
    content_text TEXT,
    learning_objectives TEXT[],
    tags TEXT[],
    estimated_time_minutes INTEGER DEFAULT 15,
    prerequisites TEXT[],
    resources_links TEXT[]
);

-- Lessons table (extended with narration)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'lessons' AND column_name = 'enable_lesson_narration') THEN
        ALTER TABLE public.lessons ADD COLUMN enable_lesson_narration BOOLEAN DEFAULT true;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'lessons' AND column_name = 'narration_language') THEN
        ALTER TABLE public.lessons ADD COLUMN narration_language TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'lessons' AND column_name = 'narration_voice') THEN
        ALTER TABLE public.lessons ADD COLUMN narration_voice TEXT;
    END IF;
END $$;

-- Lesson Questions (new comprehensive structure)
CREATE TABLE IF NOT EXISTS public.lesson_questions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    topic_id TEXT REFERENCES public.lesson_topics(id) ON DELETE CASCADE,
    question_type TEXT CHECK (question_type IN ('multiple_choice', 'fill_blank', 'true_false', 'matching', 'audio_recognition', 'word_order', 'translation')),
    question_text TEXT NOT NULL,
    question_text_native TEXT,
    audio_url TEXT,
    image_url TEXT,
    hint TEXT,
    explanation TEXT,
    points INTEGER DEFAULT 10,
    time_limit_seconds INTEGER,
    sort_order INTEGER DEFAULT 0,
    difficulty_modifier NUMERIC DEFAULT 1.0,
    is_active BOOLEAN DEFAULT true,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lesson Answers
CREATE TABLE IF NOT EXISTS public.lesson_answers (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    question_id UUID REFERENCES public.lesson_questions(id) ON DELETE CASCADE,
    answer_text TEXT NOT NULL,
    answer_text_native TEXT,
    is_correct BOOLEAN DEFAULT false,
    audio_url TEXT,
    image_url TEXT,
    feedback TEXT,
    sort_order INTEGER DEFAULT 0,
    match_pair_id TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lesson Content Sections
CREATE TABLE IF NOT EXISTS public.lesson_content_sections (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_topic_id TEXT REFERENCES public.lesson_topics(id) ON DELETE CASCADE,
    section_type TEXT CHECK (section_type IN ('text', 'image', 'audio', 'video', 'interactive', 'code_snippet')),
    content TEXT,
    title TEXT,
    description TEXT,
    order_index INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lesson Vocabulary
CREATE TABLE IF NOT EXISTS public.lesson_vocabulary (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_topic_id TEXT REFERENCES public.lesson_topics(id) ON DELETE CASCADE,
    word TEXT NOT NULL,
    translation TEXT NOT NULL,
    pronunciation TEXT,
    audio_url TEXT,
    example_sentence TEXT,
    example_translation TEXT,
    part_of_speech TEXT,
    difficulty_level TEXT DEFAULT 'medium' CHECK (difficulty_level IN ('easy', 'medium', 'hard')),
    order_index INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lesson Media
CREATE TABLE IF NOT EXISTS public.lesson_media (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_type TEXT CHECK (file_type IN ('image', 'audio')),
    mime_type TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    public_url TEXT NOT NULL,
    file_size_bytes INTEGER,
    duration_seconds INTEGER,
    language TEXT,
    category TEXT,
    associated_word TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    uploaded_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Questions (for lessons) - extended
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'wrong_answer_feedback') THEN
        ALTER TABLE public.questions ADD COLUMN wrong_answer_feedback TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'enable_question_narration') THEN
        ALTER TABLE public.questions ADD COLUMN enable_question_narration BOOLEAN DEFAULT true;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'enable_answer_narration') THEN
        ALTER TABLE public.questions ADD COLUMN enable_answer_narration BOOLEAN DEFAULT true;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'narration_language') THEN
        ALTER TABLE public.questions ADD COLUMN narration_language TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'narration_voice') THEN
        ALTER TABLE public.questions ADD COLUMN narration_voice TEXT;
    END IF;
END $$;

-- ============================================================================
-- USER PROGRESS TABLES
-- ============================================================================

-- User Lesson Progress - update structure
DO $$
BEGIN
    -- Check if we need to modify user_lesson_progress
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_lesson_progress') THEN
        -- Drop old status/progress_percentage columns if they exist
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_lesson_progress' AND column_name = 'status') THEN
            ALTER TABLE public.user_lesson_progress DROP COLUMN IF EXISTS status;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_lesson_progress' AND column_name = 'progress_percentage') THEN
            ALTER TABLE public.user_lesson_progress DROP COLUMN IF EXISTS progress_percentage;
        END IF;
        
        -- Add new columns if they don't exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_lesson_progress' AND column_name = 'is_completed') THEN
            ALTER TABLE public.user_lesson_progress ADD COLUMN is_completed BOOLEAN DEFAULT false;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_lesson_progress' AND column_name = 'time_spent_seconds') THEN
            ALTER TABLE public.user_lesson_progress ADD COLUMN time_spent_seconds INTEGER DEFAULT 0;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_lesson_progress' AND column_name = 'last_accessed_at') THEN
            ALTER TABLE public.user_lesson_progress ADD COLUMN last_accessed_at TIMESTAMPTZ DEFAULT NOW();
        END IF;
        
        -- Drop time_spent if it exists (old version)
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_lesson_progress' AND column_name = 'time_spent' AND data_type != 'integer') THEN
            ALTER TABLE public.user_lesson_progress DROP COLUMN IF EXISTS time_spent;
        END IF;
    END IF;
END $$;

-- User Question Answers
CREATE TABLE IF NOT EXISTS public.user_question_answers (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID,
    question_id UUID REFERENCES public.questions(id) ON DELETE CASCADE,
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE,
    selected_choice_id UUID REFERENCES public.question_choices(id),
    answer_text TEXT,
    voice_recording_url TEXT,
    is_correct BOOLEAN,
    attempted_at TIMESTAMPTZ DEFAULT NOW()
);

-- User Question Progress (for lesson_questions)
CREATE TABLE IF NOT EXISTS public.user_question_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    question_id UUID REFERENCES public.lesson_questions(id) ON DELETE CASCADE,
    selected_answer_id UUID REFERENCES public.lesson_answers(id),
    is_correct BOOLEAN NOT NULL,
    time_taken_seconds INTEGER,
    attempts INTEGER DEFAULT 1,
    points_earned INTEGER DEFAULT 0,
    answered_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lesson Topic Progress
CREATE TABLE IF NOT EXISTS public.lesson_topic_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    topic_id TEXT REFERENCES public.lesson_topics(id) ON DELETE CASCADE,
    is_completed BOOLEAN DEFAULT false,
    time_spent INTEGER DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- VOCABULARY SYSTEM
-- ============================================================================

-- Update vocabulary_words with language column
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'vocabulary_words' 
        AND column_name = 'language'
    ) THEN
        ALTER TABLE public.vocabulary_words ADD COLUMN language TEXT DEFAULT 'en';
    END IF;
END $$;

-- Update user_vocabulary with user_audio_url
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'user_vocabulary' 
        AND column_name = 'user_audio_url'
    ) THEN
        ALTER TABLE public.user_vocabulary ADD COLUMN user_audio_url TEXT;
    END IF;
END $$;

-- Word Audio
CREATE TABLE IF NOT EXISTS public.word_audio (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    word TEXT NOT NULL,
    language TEXT CHECK (language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish')),
    pinyin TEXT,
    audio_url TEXT NOT NULL,
    slow_audio_url TEXT,
    speaker_gender TEXT CHECK (speaker_gender IN ('male', 'female', 'neutral')),
    is_primary BOOLEAN DEFAULT true,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- SPEAKING & VOICE PRACTICE TABLES
-- ============================================================================

-- Speaking Scenarios
CREATE TABLE IF NOT EXISTS public.speaking_scenarios (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_topic_id UUID REFERENCES public.lessons(id),
    language VARCHAR CHECK (language IN ('fr', 'de', 'ko', 'zh', 'es', 'en')),
    difficulty_level VARCHAR CHECK (difficulty_level IN ('Beginner', 'Intermediate', 'Advanced')),
    scenario_type VARCHAR CHECK (scenario_type IN ('travel', 'food', 'daily_conversation', 'work', 'culture')),
    title TEXT NOT NULL,
    description TEXT,
    prompts JSONB DEFAULT '[]'::jsonb,
    expected_vocabulary JSONB DEFAULT '[]'::jsonb,
    learning_objectives JSONB DEFAULT '[]'::jsonb,
    sort_order INTEGER DEFAULT 0,
    is_published BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Voice Sessions
CREATE TABLE IF NOT EXISTS public.voice_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    language VARCHAR CHECK (language IN ('fr', 'de', 'ko', 'zh', 'es')),
    level VARCHAR CHECK (level IN ('beginner', 'intermediate', 'advanced')),
    scenario VARCHAR CHECK (scenario IN ('travel', 'food', 'daily_conversation', 'work', 'culture')),
    transcript TEXT NOT NULL,
    audio_url TEXT,
    feedback JSONB DEFAULT '{}'::jsonb,
    scores JSONB DEFAULT '{}'::jsonb,
    session_duration FLOAT8 DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Voice Feedback Details
CREATE TABLE IF NOT EXISTS public.voice_feedback_details (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID REFERENCES public.voice_sessions(id) ON DELETE CASCADE,
    feedback_type VARCHAR CHECK (feedback_type IN ('fluency', 'pronunciation', 'accuracy')),
    score FLOAT8 CHECK (score >= 0 AND score <= 100),
    details JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Voice Progress
CREATE TABLE IF NOT EXISTS public.voice_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    language VARCHAR CHECK (language IN ('fr', 'de', 'ko', 'zh', 'es')),
    total_sessions INTEGER DEFAULT 0,
    average_fluency FLOAT8 DEFAULT 0.0 CHECK (average_fluency >= 0 AND average_fluency <= 100),
    average_pronunciation FLOAT8 DEFAULT 0.0 CHECK (average_pronunciation >= 0 AND average_pronunciation <= 100),
    average_accuracy FLOAT8 DEFAULT 0.0 CHECK (average_accuracy >= 0 AND average_accuracy <= 100),
    average_overall FLOAT8 DEFAULT 0.0 CHECK (average_overall >= 0 AND average_overall <= 100),
    last_session_date TIMESTAMPTZ DEFAULT NOW(),
    improvement_percentage FLOAT8 DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User Speaking Progress
CREATE TABLE IF NOT EXISTS public.user_speaking_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    scenario_id UUID REFERENCES public.speaking_scenarios(id),
    language VARCHAR,
    level VARCHAR,
    total_sessions INTEGER DEFAULT 0,
    total_practice_time INTEGER DEFAULT 0,
    average_pronunciation FLOAT8 DEFAULT 0.0 CHECK (average_pronunciation >= 0 AND average_pronunciation <= 100),
    average_fluency FLOAT8 DEFAULT 0.0 CHECK (average_fluency >= 0 AND average_fluency <= 100),
    average_pace FLOAT8 DEFAULT 0.0 CHECK (average_pace >= 0 AND average_pace <= 100),
    average_accuracy FLOAT8 DEFAULT 0.0 CHECK (average_accuracy >= 0 AND average_accuracy <= 100),
    average_overall FLOAT8 DEFAULT 0.0 CHECK (average_overall >= 0 AND average_overall <= 100),
    best_score FLOAT8 DEFAULT 0.0,
    last_session_date TIMESTAMPTZ DEFAULT NOW(),
    improvement_percentage FLOAT8 DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- AI AGENT & CONVERSATION TABLES
-- ============================================================================

-- Agent Sessions
CREATE TABLE IF NOT EXISTS public.agent_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id TEXT NOT NULL,
    language TEXT NOT NULL,
    scenario TEXT NOT NULL,
    status TEXT DEFAULT 'active',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    duration FLOAT8 DEFAULT 0,
    conversation_count INTEGER DEFAULT 0,
    audio_duration FLOAT8 DEFAULT 0.0,
    voice_model TEXT,
    temperature FLOAT8 DEFAULT 0.7,
    custom_prompt TEXT
);

-- Agent Conversation Logs
CREATE TABLE IF NOT EXISTS public.agent_conversation_logs (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID REFERENCES public.agent_sessions(id) ON DELETE CASCADE,
    role TEXT CHECK (role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    audio_available BOOLEAN DEFAULT false,
    audio_url TEXT
);

-- Agent Event Logs
CREATE TABLE IF NOT EXISTS public.agent_event_logs (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID REFERENCES public.agent_sessions(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    event_data JSONB DEFAULT '{}'::jsonb,
    timestamp TIMESTAMPTZ DEFAULT NOW()
);

-- Agent User Stats
CREATE TABLE IF NOT EXISTS public.agent_user_stats (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id TEXT UNIQUE NOT NULL,
    total_sessions INTEGER DEFAULT 0,
    total_duration FLOAT8 DEFAULT 0,
    favorite_language TEXT,
    favorite_scenario TEXT,
    last_session TIMESTAMPTZ,
    improvement_trend FLOAT8 DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Conversation Recordings
CREATE TABLE IF NOT EXISTS public.conversation_recordings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID,
    user_id TEXT NOT NULL,
    language TEXT NOT NULL,
    audio_url TEXT,
    audio_format TEXT DEFAULT 'wav',
    duration FLOAT8 DEFAULT 0.0,
    transcript TEXT,
    turn_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Conversation Feedback
CREATE TABLE IF NOT EXISTS public.conversation_feedback (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    overall_score INTEGER CHECK (overall_score >= 0 AND overall_score <= 100),
    grammar_score INTEGER CHECK (grammar_score >= 0 AND grammar_score <= 100),
    pronunciation_score INTEGER CHECK (pronunciation_score >= 0 AND pronunciation_score <= 100),
    vocabulary_score INTEGER CHECK (vocabulary_score >= 0 AND vocabulary_score <= 100),
    fluency_score INTEGER CHECK (fluency_score >= 0 AND fluency_score <= 100),
    detailed_analysis TEXT,
    strengths TEXT[] DEFAULT '{}'::text[],
    areas_for_improvement TEXT[] DEFAULT '{}'::text[],
    specific_examples JSONB DEFAULT '[]'::jsonb,
    suggestions TEXT[] DEFAULT '{}'::text[],
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- CHAT SYSTEM UPDATES
-- ============================================================================

-- Chat Session History (compression)
CREATE TABLE IF NOT EXISTS public.chat_session_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id TEXT UNIQUE,
    compressed_messages TEXT NOT NULL,
    compression_type TEXT DEFAULT 'gzip' CHECK (compression_type IN ('gzip', 'zlib', 'none')),
    original_size INTEGER DEFAULT 0,
    compressed_size INTEGER DEFAULT 0,
    compression_ratio NUMERIC GENERATED ALWAYS AS (
        CASE 
            WHEN original_size > 0 THEN 
                ROUND(((original_size - compressed_size)::NUMERIC / original_size * 100), 2)
            ELSE 0 
        END
    ) STORED,
    message_count INTEGER DEFAULT 0,
    first_message_at TIMESTAMPTZ,
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Update chat_sessions to support UUID user_id
DO $$
BEGIN
    -- Check if user_id is TEXT and needs updating
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'chat_sessions' 
        AND column_name = 'user_id' 
        AND data_type = 'text'
    ) THEN
        -- Already correct type
        NULL;
    END IF;
END $$;

-- ============================================================================
-- INDEXES FOR PERFORMANCE
-- ============================================================================

-- Lesson system indexes
CREATE INDEX IF NOT EXISTS idx_lesson_topics_language ON public.lesson_topics(language);
CREATE INDEX IF NOT EXISTS idx_lesson_topics_difficulty ON public.lesson_topics(difficulty_level);
CREATE INDEX IF NOT EXISTS idx_lesson_topics_published ON public.lesson_topics(is_published);
CREATE INDEX IF NOT EXISTS idx_lessons_topic_id ON public.lessons(topic_id);
CREATE INDEX IF NOT EXISTS idx_lesson_questions_topic_id ON public.lesson_questions(topic_id);
CREATE INDEX IF NOT EXISTS idx_lesson_answers_question_id ON public.lesson_answers(question_id);
CREATE INDEX IF NOT EXISTS idx_questions_lesson_id ON public.questions(lesson_id);
CREATE INDEX IF NOT EXISTS idx_question_choices_question_id ON public.question_choices(question_id);

-- Progress indexes
CREATE INDEX IF NOT EXISTS idx_user_lesson_progress_user_id ON public.user_lesson_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_user_lesson_progress_lesson_id ON public.user_lesson_progress(lesson_id);
CREATE INDEX IF NOT EXISTS idx_user_question_answers_user_id ON public.user_question_answers(user_id);
CREATE INDEX IF NOT EXISTS idx_user_question_progress_user_id ON public.user_question_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_lesson_topic_progress_user_id ON public.lesson_topic_progress(user_id);

-- Voice and speaking indexes
CREATE INDEX IF NOT EXISTS idx_voice_sessions_user_id ON public.voice_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_created_at ON public.voice_sessions(created_at);
CREATE INDEX IF NOT EXISTS idx_user_speaking_progress_user_id ON public.user_speaking_progress(user_id);

-- Agent indexes
CREATE INDEX IF NOT EXISTS idx_agent_sessions_user_id ON public.agent_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_agent_conversation_logs_session_id ON public.agent_conversation_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_conversation_recordings_user_id ON public.conversation_recordings(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_feedback_user_id ON public.conversation_feedback(user_id);

-- Chat indexes
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id ON public.chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON public.chat_sessions(status);
CREATE INDEX IF NOT EXISTS idx_chat_session_history_session_id ON public.chat_session_history(session_id);

-- Word audio indexes
CREATE INDEX IF NOT EXISTS idx_word_audio_word ON public.word_audio(word);
CREATE INDEX IF NOT EXISTS idx_word_audio_language ON public.word_audio(language);

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE public.lesson_topics IS 'Main lesson topics with comprehensive metadata and content';
COMMENT ON TABLE public.lesson_questions IS 'Questions for lesson topics (new structure)';
COMMENT ON TABLE public.lesson_answers IS 'Answers for lesson questions';
COMMENT ON TABLE public.questions IS 'Questions for lessons (legacy structure)';
COMMENT ON TABLE public.user_lesson_progress IS 'User progress tracking for lessons';
COMMENT ON TABLE public.user_question_progress IS 'User progress for lesson questions';
COMMENT ON TABLE public.conversation_feedback IS 'AI-powered conversation feedback with scoring';
COMMENT ON TABLE public.chat_session_history IS 'Compressed storage for chat message history';
COMMENT ON TABLE public.agent_sessions IS 'Voice agent conversation sessions';
COMMENT ON TABLE public.speaking_scenarios IS 'Speaking practice scenarios by language and difficulty';
COMMENT ON TABLE public.word_audio IS 'Audio pronunciations for vocabulary words';
