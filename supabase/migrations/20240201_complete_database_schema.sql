-- Complete Database Schema Migration
-- Matches DATABASE_STRUCTURE.md
-- This migration creates all tables, constraints, indexes, and RLS policies

-- ============================================================================
-- LESSON SYSTEM TABLES
-- ============================================================================

-- Lesson Topics (extended with new fields)
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

-- Lessons (with narration settings)
CREATE TABLE IF NOT EXISTS public.lessons (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    topic_id TEXT REFERENCES public.lesson_topics(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    lesson_order INTEGER DEFAULT 0,
    is_published BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    enable_lesson_narration BOOLEAN DEFAULT true,
    narration_language TEXT,
    narration_voice TEXT
);

-- Lesson Questions (new comprehensive version)
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

-- Lesson Spanish (legacy table)
CREATE TABLE IF NOT EXISTS public.lesson_spanish (
    id BIGINT PRIMARY KEY,
    level TEXT,
    lesson BIGINT,
    topic TEXT,
    type TEXT,
    component TEXT,
    content TEXT[],
    romanized TEXT[],
    english_translation TEXT[],
    choices TEXT[]
);

-- ============================================================================
-- QUIZ SYSTEM TABLES (New Structure)
-- ============================================================================

-- Questions (for lessons)
CREATE TABLE IF NOT EXISTS public.questions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE,
    question_type TEXT CHECK (question_type IN ('multiple_choice', 'text_entry', 'matching', 'paraphrasing', 'error_correction')),
    question_text TEXT NOT NULL,
    question_order INTEGER DEFAULT 0,
    answer_text TEXT,
    question_audio_url TEXT,
    answer_audio_url TEXT,
    error_text TEXT,
    explanation TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    wrong_answer_feedback TEXT,
    enable_question_narration BOOLEAN DEFAULT true,
    enable_answer_narration BOOLEAN DEFAULT true,
    narration_language TEXT,
    narration_voice TEXT
);

-- Question Choices
CREATE TABLE IF NOT EXISTS public.question_choices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    question_id UUID REFERENCES public.questions(id) ON DELETE CASCADE,
    choice_text TEXT NOT NULL,
    choice_order INTEGER DEFAULT 0,
    is_correct BOOLEAN DEFAULT false,
    image_url TEXT,
    audio_url TEXT,
    match_pair_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- USER PROGRESS TABLES
-- ============================================================================

-- User Lesson Progress (updated structure)
CREATE TABLE IF NOT EXISTS public.user_lesson_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    lesson_id UUID REFERENCES public.lessons(id) ON DELETE CASCADE,
    is_completed BOOLEAN DEFAULT false,
    score FLOAT8,
    time_spent_seconds INTEGER DEFAULT 0,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_lesson UNIQUE (user_id, lesson_id)
);

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

-- Add language column to vocabulary_words if not exists
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

-- Add user_audio_url to user_vocabulary if not exists
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
-- CHAT SYSTEM TABLES (Updated)
-- ============================================================================

-- Update chat_sessions to match current structure
DO $$
BEGIN
    -- Check if chat_sessions exists and has TEXT id
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_name = 'chat_sessions'
    ) THEN
        -- Check if status column allows 'deleted'
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.check_constraints 
            WHERE constraint_name LIKE '%chat_sessions%status%' 
            AND check_clause LIKE '%deleted%'
        ) THEN
            -- Drop and recreate constraint
            ALTER TABLE public.chat_sessions DROP CONSTRAINT IF EXISTS chat_sessions_status_check;
            ALTER TABLE public.chat_sessions ADD CONSTRAINT chat_sessions_status_check 
                CHECK (status IN ('active', 'archived', 'deleted'));
        END IF;
    END IF;
END $$;

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

-- ============================================================================
-- ONBOARDING & PROFILES
-- ============================================================================

-- Profiles (Onboarding)
CREATE TABLE IF NOT EXISTS public.profiles (
    user_id UUID REFERENCES auth.users(id) PRIMARY KEY,
    is_onboarded BOOLEAN DEFAULT false,
    ai_profile JSONB,
    onboarding_state JSONB,
    current_step INTEGER DEFAULT 0,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    active_learning_language TEXT CHECK (active_learning_language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish'))
);

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
CREATE INDEX IF NOT EXISTS idx_chat_session_history_session_id ON public.chat_session_history(session_id);

-- ============================================================================
-- ROW LEVEL SECURITY POLICIES
-- ============================================================================

-- Enable RLS on new tables
ALTER TABLE public.lesson_topics ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lessons ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_content_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_vocabulary ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_spanish ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.question_choices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.speaking_scenarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_session_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_topic_progress ENABLE ROW LEVEL SECURITY;

-- Lesson Topics Policies
DROP POLICY IF EXISTS "Anyone can read published lesson topics" ON public.lesson_topics;
DROP POLICY IF EXISTS "Anyone can read published lesson topics (anon)" ON public.lesson_topics;
DROP POLICY IF EXISTS "Authenticated users can insert lesson topics" ON public.lesson_topics;
DROP POLICY IF EXISTS "Authenticated users can update lesson topics" ON public.lesson_topics;
DROP POLICY IF EXISTS "Authenticated users can delete lesson topics" ON public.lesson_topics;

CREATE POLICY "Anyone can read published lesson topics" ON public.lesson_topics 
    FOR SELECT TO authenticated USING (is_published = true);
CREATE POLICY "Anyone can read published lesson topics (anon)" ON public.lesson_topics 
    FOR SELECT TO anon USING (is_published = true);
CREATE POLICY "Authenticated users can insert lesson topics" ON public.lesson_topics 
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Authenticated users can update lesson topics" ON public.lesson_topics 
    FOR UPDATE TO authenticated USING (true);
CREATE POLICY "Authenticated users can delete lesson topics" ON public.lesson_topics 
    FOR DELETE TO authenticated USING (true);

-- Lessons Policies
DROP POLICY IF EXISTS "Authenticated users can manage lessons" ON public.lessons;
CREATE POLICY "Authenticated users can manage lessons" ON public.lessons 
    FOR ALL TO public USING (auth.role() = 'authenticated');

-- Lesson Questions & Answers Policies
DROP POLICY IF EXISTS "Anyone can read active questions" ON public.lesson_questions;
DROP POLICY IF EXISTS "Authenticated users can insert questions" ON public.lesson_questions;
DROP POLICY IF EXISTS "Authenticated users can update questions" ON public.lesson_questions;
DROP POLICY IF EXISTS "Authenticated users can delete questions" ON public.lesson_questions;

CREATE POLICY "Anyone can read active questions" ON public.lesson_questions 
    FOR SELECT TO authenticated USING (is_active = true);
CREATE POLICY "Authenticated users can insert questions" ON public.lesson_questions 
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Authenticated users can update questions" ON public.lesson_questions 
    FOR UPDATE TO authenticated USING (true);
CREATE POLICY "Authenticated users can delete questions" ON public.lesson_questions 
    FOR DELETE TO authenticated USING (true);

DROP POLICY IF EXISTS "Anyone can read answers" ON public.lesson_answers;
DROP POLICY IF EXISTS "Authenticated users can insert answers" ON public.lesson_answers;
DROP POLICY IF EXISTS "Authenticated users can update answers" ON public.lesson_answers;
DROP POLICY IF EXISTS "Authenticated users can delete answers" ON public.lesson_answers;

CREATE POLICY "Anyone can read answers" ON public.lesson_answers 
    FOR SELECT TO authenticated USING (true);
CREATE POLICY "Authenticated users can insert answers" ON public.lesson_answers 
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Authenticated users can update answers" ON public.lesson_answers 
    FOR UPDATE TO authenticated USING (true);
CREATE POLICY "Authenticated users can delete answers" ON public.lesson_answers 
    FOR DELETE TO authenticated USING (true);

-- Lesson Content Sections Policies
DROP POLICY IF EXISTS "Anyone can read lesson content sections" ON public.lesson_content_sections;
DROP POLICY IF EXISTS "Authenticated users can manage lesson content sections" ON public.lesson_content_sections;

CREATE POLICY "Anyone can read lesson content sections" ON public.lesson_content_sections 
    FOR SELECT TO public USING (true);
CREATE POLICY "Authenticated users can manage lesson content sections" ON public.lesson_content_sections 
    FOR ALL TO authenticated USING (true);

-- Lesson Vocabulary Policies
DROP POLICY IF EXISTS "Anyone can read lesson vocabulary" ON public.lesson_vocabulary;
DROP POLICY IF EXISTS "Authenticated users can manage lesson vocabulary" ON public.lesson_vocabulary;

CREATE POLICY "Anyone can read lesson vocabulary" ON public.lesson_vocabulary 
    FOR SELECT TO public USING (true);
CREATE POLICY "Authenticated users can manage lesson vocabulary" ON public.lesson_vocabulary 
    FOR ALL TO authenticated USING (true);

-- Lesson Media Policies
DROP POLICY IF EXISTS "Anyone can read media" ON public.lesson_media;
DROP POLICY IF EXISTS "Authenticated users can insert media" ON public.lesson_media;
DROP POLICY IF EXISTS "Authenticated users can update media" ON public.lesson_media;
DROP POLICY IF EXISTS "Authenticated users can delete media" ON public.lesson_media;

CREATE POLICY "Anyone can read media" ON public.lesson_media 
    FOR SELECT TO authenticated USING (true);
CREATE POLICY "Authenticated users can insert media" ON public.lesson_media 
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Authenticated users can update media" ON public.lesson_media 
    FOR UPDATE TO authenticated USING (true);
CREATE POLICY "Authenticated users can delete media" ON public.lesson_media 
    FOR DELETE TO authenticated USING (true);

-- Lesson Spanish Policies
DROP POLICY IF EXISTS "Authenticated users can read lesson_spanish" ON public.lesson_spanish;
CREATE POLICY "Authenticated users can read lesson_spanish" ON public.lesson_spanish 
    FOR SELECT TO authenticated USING (true);

-- Questions & Question Choices Policies
DROP POLICY IF EXISTS "Authenticated users can manage questions" ON public.questions;
CREATE POLICY "Authenticated users can manage questions" ON public.questions 
    FOR ALL TO public USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "Authenticated users can manage choices" ON public.question_choices;
CREATE POLICY "Authenticated users can manage choices" ON public.question_choices 
    FOR ALL TO public USING (auth.role() = 'authenticated');

-- User Progress Policies
DROP POLICY IF EXISTS "Users manage own progress" ON public.user_lesson_progress;
CREATE POLICY "Users manage own progress" ON public.user_lesson_progress 
    FOR ALL TO public USING (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "Users manage own answers" ON public.user_question_answers;
CREATE POLICY "Users manage own answers" ON public.user_question_answers 
    FOR ALL TO public USING (auth.uid()::text = user_id::text);

DROP POLICY IF EXISTS "Users can insert their question progress" ON public.user_question_progress;
DROP POLICY IF EXISTS "Users can update their question progress" ON public.user_question_progress;
DROP POLICY IF EXISTS "Users can view their question progress" ON public.user_question_progress;

CREATE POLICY "Users can insert their question progress" ON public.user_question_progress 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update their question progress" ON public.user_question_progress 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can view their question progress" ON public.user_question_progress 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert their own lesson topic progress" ON public.lesson_topic_progress;
DROP POLICY IF EXISTS "Users can update their own lesson topic progress" ON public.lesson_topic_progress;
DROP POLICY IF EXISTS "Users can view their own lesson topic progress" ON public.lesson_topic_progress;

CREATE POLICY "Users can insert their own lesson topic progress" ON public.lesson_topic_progress 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update their own lesson topic progress" ON public.lesson_topic_progress 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can view their own lesson topic progress" ON public.lesson_topic_progress 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);

-- Speaking Scenarios Policies
DROP POLICY IF EXISTS "Public can read published speaking scenarios" ON public.speaking_scenarios;
CREATE POLICY "Public can read published speaking scenarios" ON public.speaking_scenarios 
    FOR SELECT TO authenticated USING (is_published = true);

-- Voice Sessions Policies
DROP POLICY IF EXISTS "Users can view own voice sessions" ON public.voice_sessions;
DROP POLICY IF EXISTS "Users can insert own voice sessions" ON public.voice_sessions;
DROP POLICY IF EXISTS "Users can update own voice sessions" ON public.voice_sessions;
DROP POLICY IF EXISTS "Users can delete own voice sessions" ON public.voice_sessions;

CREATE POLICY "Users can view own voice sessions" ON public.voice_sessions 
    FOR SELECT TO public USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own voice sessions" ON public.voice_sessions 
    FOR INSERT TO public WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own voice sessions" ON public.voice_sessions 
    FOR UPDATE TO public USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own voice sessions" ON public.voice_sessions 
    FOR DELETE TO public USING (auth.uid() = user_id);

-- Voice Feedback Details Policies
DROP POLICY IF EXISTS "Users can view own feedback details" ON public.voice_feedback_details;
DROP POLICY IF EXISTS "Users can insert own feedback details" ON public.voice_feedback_details;

CREATE POLICY "Users can view own feedback details" ON public.voice_feedback_details 
    FOR SELECT TO public USING (true);
CREATE POLICY "Users can insert own feedback details" ON public.voice_feedback_details 
    FOR INSERT TO public WITH CHECK (true);

-- Voice Progress Policies
DROP POLICY IF EXISTS "Users can view own voice progress" ON public.voice_progress;
DROP POLICY IF EXISTS "Users can insert own voice progress" ON public.voice_progress;
DROP POLICY IF EXISTS "Users can update own voice progress" ON public.voice_progress;

CREATE POLICY "Users can view own voice progress" ON public.voice_progress 
    FOR SELECT TO public USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own voice progress" ON public.voice_progress 
    FOR INSERT TO public WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own voice progress" ON public.voice_progress 
    FOR UPDATE TO public USING (auth.uid() = user_id);

-- User Speaking Progress Policies
DROP POLICY IF EXISTS "Users can view own speaking progress" ON public.user_speaking_progress;
DROP POLICY IF EXISTS "Users can insert own speaking progress" ON public.user_speaking_progress;
DROP POLICY IF EXISTS "Users can update own speaking progress" ON public.user_speaking_progress;

CREATE POLICY "Users can view own speaking progress" ON public.user_speaking_progress 
    FOR SELECT TO public USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own speaking progress" ON public.user_speaking_progress 
    FOR INSERT TO public WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own speaking progress" ON public.user_speaking_progress 
    FOR UPDATE TO public USING (auth.uid() = user_id);

-- Agent Sessions Policies
DROP POLICY IF EXISTS "Users can view own agent sessions" ON public.agent_sessions;
DROP POLICY IF EXISTS "Users can insert own agent sessions" ON public.agent_sessions;
DROP POLICY IF EXISTS "Users can update own agent sessions" ON public.agent_sessions;

CREATE POLICY "Users can view own agent sessions" ON public.agent_sessions 
    FOR SELECT TO public USING (auth.uid()::text = user_id);
CREATE POLICY "Users can insert own agent sessions" ON public.agent_sessions 
    FOR INSERT TO public WITH CHECK (auth.uid()::text = user_id);
CREATE POLICY "Users can update own agent sessions" ON public.agent_sessions 
    FOR UPDATE TO public USING (auth.uid()::text = user_id);

-- Agent Conversation Logs Policies
DROP POLICY IF EXISTS "Users can view own event logs" ON public.agent_conversation_logs;
DROP POLICY IF EXISTS "Users can insert own event logs" ON public.agent_conversation_logs;

CREATE POLICY "Users can view own event logs" ON public.agent_conversation_logs 
    FOR SELECT TO public USING (true);
CREATE POLICY "Users can insert own event logs" ON public.agent_conversation_logs 
    FOR INSERT TO public WITH CHECK (true);

-- Agent Event Logs Policies
DROP POLICY IF EXISTS "Users can view own event logs" ON public.agent_event_logs;
DROP POLICY IF EXISTS "Users can insert own event logs" ON public.agent_event_logs;

CREATE POLICY "Users can view own event logs" ON public.agent_event_logs 
    FOR SELECT TO public USING (true);
CREATE POLICY "Users can insert own event logs" ON public.agent_event_logs 
    FOR INSERT TO public WITH CHECK (true);

-- Agent User Stats Policies
DROP POLICY IF EXISTS "Users can view own stats" ON public.agent_user_stats;
DROP POLICY IF EXISTS "Users can update own stats" ON public.agent_user_stats;

CREATE POLICY "Users can view own stats" ON public.agent_user_stats 
    FOR SELECT TO public USING (auth.uid()::text = user_id);
CREATE POLICY "Users can update own stats" ON public.agent_user_stats 
    FOR UPDATE TO public USING (auth.uid()::text = user_id);

-- Conversation Recordings Policies
DROP POLICY IF EXISTS "Users can view own conversation recordings" ON public.conversation_recordings;
DROP POLICY IF EXISTS "Users can insert own conversation recordings" ON public.conversation_recordings;

CREATE POLICY "Users can view own conversation recordings" ON public.conversation_recordings 
    FOR SELECT TO public USING (auth.uid()::text = user_id);
CREATE POLICY "Users can insert own conversation recordings" ON public.conversation_recordings 
    FOR INSERT TO public WITH CHECK (auth.uid()::text = user_id);

-- Conversation Feedback Policies
DROP POLICY IF EXISTS "Users can view their own feedback" ON public.conversation_feedback;
DROP POLICY IF EXISTS "Users can insert their own feedback" ON public.conversation_feedback;
DROP POLICY IF EXISTS "Users can update their own feedback" ON public.conversation_feedback;
DROP POLICY IF EXISTS "Users can delete their own feedback" ON public.conversation_feedback;

CREATE POLICY "Users can view their own feedback" ON public.conversation_feedback 
    FOR SELECT TO public USING (auth.uid() = user_id);
CREATE POLICY "Users can insert their own feedback" ON public.conversation_feedback 
    FOR INSERT TO public WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update their own feedback" ON public.conversation_feedback 
    FOR UPDATE TO public USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own feedback" ON public.conversation_feedback 
    FOR DELETE TO public USING (auth.uid() = user_id);

-- Chat Session History Policies
DROP POLICY IF EXISTS "Users can select own chat history" ON public.chat_session_history;
DROP POLICY IF EXISTS "Users can insert own chat history" ON public.chat_session_history;
DROP POLICY IF EXISTS "Users can update own chat history" ON public.chat_session_history;
DROP POLICY IF EXISTS "Users can delete own chat history" ON public.chat_session_history;

CREATE POLICY "Users can select own chat history" ON public.chat_session_history 
    FOR SELECT TO authenticated USING (true);
CREATE POLICY "Users can insert own chat history" ON public.chat_session_history 
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Users can update own chat history" ON public.chat_session_history 
    FOR UPDATE TO authenticated USING (true);
CREATE POLICY "Users can delete own chat history" ON public.chat_session_history 
    FOR DELETE TO authenticated USING (true);

-- Chat Sessions - Update existing policies
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_sessions') THEN
        DROP POLICY IF EXISTS "Users can select own chat sessions" ON public.chat_sessions;
        DROP POLICY IF EXISTS "Users can insert own chat sessions" ON public.chat_sessions;
        DROP POLICY IF EXISTS "Users can update own chat sessions" ON public.chat_sessions;
        DROP POLICY IF EXISTS "Users can delete own chat sessions" ON public.chat_sessions;
        
        CREATE POLICY "Users can select own chat sessions" ON public.chat_sessions 
            FOR SELECT TO authenticated USING (auth.uid() = user_id);
        CREATE POLICY "Users can insert own chat sessions" ON public.chat_sessions 
            FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
        CREATE POLICY "Users can update own chat sessions" ON public.chat_sessions 
            FOR UPDATE TO authenticated USING (auth.uid() = user_id);
        CREATE POLICY "Users can delete own chat sessions" ON public.chat_sessions 
            FOR DELETE TO authenticated USING (auth.uid() = user_id);
    END IF;
END $$;

-- Word Audio Policies
DROP POLICY IF EXISTS "Anyone can read word audio" ON public.word_audio;
DROP POLICY IF EXISTS "Anon can read word audio" ON public.word_audio;
DROP POLICY IF EXISTS "Authenticated users can insert word audio" ON public.word_audio;
DROP POLICY IF EXISTS "Authenticated users can update word audio" ON public.word_audio;
DROP POLICY IF EXISTS "Authenticated users can delete word audio" ON public.word_audio;

CREATE POLICY "Anyone can read word audio" ON public.word_audio 
    FOR SELECT TO authenticated USING (true);
CREATE POLICY "Anon can read word audio" ON public.word_audio 
    FOR SELECT TO anon USING (true);
CREATE POLICY "Authenticated users can insert word audio" ON public.word_audio 
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "Authenticated users can update word audio" ON public.word_audio 
    FOR UPDATE TO authenticated USING (true);
CREATE POLICY "Authenticated users can delete word audio" ON public.word_audio 
    FOR DELETE TO authenticated USING (true);

-- Profiles (Onboarding) Policies
DROP POLICY IF EXISTS "Users can select their onboarding profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert their onboarding profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update their onboarding profile" ON public.profiles;
DROP POLICY IF EXISTS "Service role can manage onboarding profiles" ON public.profiles;

CREATE POLICY "Users can select their onboarding profile" ON public.profiles 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert their onboarding profile" ON public.profiles 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update their onboarding profile" ON public.profiles 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Service role can manage onboarding profiles" ON public.profiles 
    FOR ALL TO service_role USING (true);

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Updated_at triggers for new tables
CREATE TRIGGER update_lesson_topics_updated_at 
    BEFORE UPDATE ON public.lesson_topics 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_lessons_updated_at 
    BEFORE UPDATE ON public.lessons 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_lesson_questions_updated_at 
    BEFORE UPDATE ON public.lesson_questions 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_lesson_answers_updated_at 
    BEFORE UPDATE ON public.lesson_answers 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_questions_updated_at 
    BEFORE UPDATE ON public.questions 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_speaking_scenarios_updated_at 
    BEFORE UPDATE ON public.speaking_scenarios 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_voice_progress_updated_at 
    BEFORE UPDATE ON public.voice_progress 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_user_speaking_progress_updated_at 
    BEFORE UPDATE ON public.user_speaking_progress 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_agent_user_stats_updated_at 
    BEFORE UPDATE ON public.agent_user_stats 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_conversation_feedback_updated_at 
    BEFORE UPDATE ON public.conversation_feedback 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_chat_session_history_updated_at 
    BEFORE UPDATE ON public.chat_session_history 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_word_audio_updated_at 
    BEFORE UPDATE ON public.word_audio 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_profiles_updated_at 
    BEFORE UPDATE ON public.profiles 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_lesson_topic_progress_updated_at 
    BEFORE UPDATE ON public.lesson_topic_progress 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_lesson_content_sections_updated_at 
    BEFORE UPDATE ON public.lesson_content_sections 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_lesson_vocabulary_updated_at 
    BEFORE UPDATE ON public.lesson_vocabulary 
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
