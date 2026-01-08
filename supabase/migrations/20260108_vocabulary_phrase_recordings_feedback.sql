-- Migration: Vocabulary and Phrase Practice Recordings with AI Feedback
-- This creates the complete data persistence layer for voice recordings and AI analysis
-- for both individual vocabulary words AND phrase scenarios

-- ============================================================================
-- 1. VOCABULARY PRACTICE RECORDINGS TABLE
-- Stores each individual recording attempt for vocabulary words
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.vocabulary_practice_recordings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    word_id UUID NOT NULL REFERENCES public.vocabulary_words(id) ON DELETE CASCADE,
    user_vocabulary_id UUID REFERENCES public.user_vocabulary(id) ON DELETE SET NULL,
    
    -- Recording details
    recording_url TEXT NOT NULL,
    duration_seconds FLOAT8 DEFAULT 0.0,
    file_size_bytes INTEGER DEFAULT 0,
    audio_format TEXT DEFAULT 'wav',
    
    -- Transcription from speech-to-text
    transcript TEXT,
    
    -- Context information
    language TEXT CHECK (language IN ('en', 'ko', 'zh', 'fr', 'de', 'es')),
    expected_text TEXT, -- The word/phrase the user was trying to pronounce
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 2. VOCABULARY AI FEEDBACK TABLE
-- Stores AI analysis results for each vocabulary practice recording
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.vocabulary_ai_feedback (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES public.vocabulary_practice_recordings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    word_id UUID NOT NULL REFERENCES public.vocabulary_words(id) ON DELETE CASCADE,
    
    -- Score metrics (0-100 scale)
    overall_score INTEGER CHECK (overall_score >= 0 AND overall_score <= 100),
    pronunciation_score INTEGER CHECK (pronunciation_score >= 0 AND pronunciation_score <= 100),
    accuracy_score INTEGER CHECK (accuracy_score >= 0 AND accuracy_score <= 100),
    fluency_score INTEGER CHECK (fluency_score >= 0 AND fluency_score <= 100),
    clarity_score INTEGER CHECK (clarity_score >= 0 AND clarity_score <= 100),
    
    -- Detailed feedback
    detailed_analysis TEXT,
    strengths TEXT[] DEFAULT '{}'::text[],
    areas_for_improvement TEXT[] DEFAULT '{}'::text[],
    suggestions TEXT[] DEFAULT '{}'::text[],
    
    -- Additional structured data
    specific_examples JSONB DEFAULT '[]'::jsonb,
    phonetic_breakdown JSONB DEFAULT '{}'::jsonb, -- For word-level pronunciation details
    
    -- Analysis metadata
    analysis_provider TEXT DEFAULT 'local', -- 'local', 'gemini', 'whisper', etc.
    analysis_duration_ms INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 3. PHRASE PRACTICE RECORDINGS TABLE
-- Stores recordings for phrase/scenario practice sessions
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.phrase_practice_recordings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    scenario_id UUID REFERENCES public.speaking_scenarios(id) ON DELETE SET NULL,
    
    -- Recording details
    recording_url TEXT NOT NULL,
    duration_seconds FLOAT8 DEFAULT 0.0,
    file_size_bytes INTEGER DEFAULT 0,
    audio_format TEXT DEFAULT 'wav',
    
    -- Transcription from speech-to-text
    transcript TEXT,
    
    -- Context information
    language TEXT CHECK (language IN ('en', 'ko', 'zh', 'fr', 'de', 'es')),
    expected_phrase TEXT, -- The phrase the user was trying to say
    scenario_type TEXT, -- 'travel', 'food', 'daily_conversation', etc.
    difficulty_level TEXT CHECK (difficulty_level IN ('beginner', 'intermediate', 'advanced')),
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 4. PHRASE AI FEEDBACK TABLE
-- Stores AI analysis results for phrase practice recordings
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.phrase_ai_feedback (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    recording_id UUID NOT NULL REFERENCES public.phrase_practice_recordings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    scenario_id UUID REFERENCES public.speaking_scenarios(id) ON DELETE SET NULL,
    
    -- Score metrics (0-100 scale)
    overall_score INTEGER CHECK (overall_score >= 0 AND overall_score <= 100),
    pronunciation_score INTEGER CHECK (pronunciation_score >= 0 AND pronunciation_score <= 100),
    grammar_score INTEGER CHECK (grammar_score >= 0 AND grammar_score <= 100),
    fluency_score INTEGER CHECK (fluency_score >= 0 AND fluency_score <= 100),
    accuracy_score INTEGER CHECK (accuracy_score >= 0 AND accuracy_score <= 100),
    contextual_appropriateness_score INTEGER CHECK (contextual_appropriateness_score >= 0 AND contextual_appropriateness_score <= 100),
    
    -- Detailed feedback
    detailed_analysis TEXT,
    strengths TEXT[] DEFAULT '{}'::text[],
    areas_for_improvement TEXT[] DEFAULT '{}'::text[],
    suggestions TEXT[] DEFAULT '{}'::text[],
    
    -- Additional structured data
    specific_examples JSONB DEFAULT '[]'::jsonb,
    word_by_word_feedback JSONB DEFAULT '[]'::jsonb, -- Per-word analysis
    
    -- Analysis metadata
    analysis_provider TEXT DEFAULT 'local',
    analysis_duration_ms INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 5. INDEXES FOR PERFORMANCE
-- ============================================================================

-- Vocabulary recordings indexes
CREATE INDEX IF NOT EXISTS idx_vocab_recordings_user_id ON public.vocabulary_practice_recordings(user_id);
CREATE INDEX IF NOT EXISTS idx_vocab_recordings_word_id ON public.vocabulary_practice_recordings(word_id);
CREATE INDEX IF NOT EXISTS idx_vocab_recordings_created_at ON public.vocabulary_practice_recordings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_vocab_recordings_user_word ON public.vocabulary_practice_recordings(user_id, word_id);

-- Vocabulary AI feedback indexes
CREATE INDEX IF NOT EXISTS idx_vocab_feedback_recording_id ON public.vocabulary_ai_feedback(recording_id);
CREATE INDEX IF NOT EXISTS idx_vocab_feedback_user_id ON public.vocabulary_ai_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_vocab_feedback_word_id ON public.vocabulary_ai_feedback(word_id);
CREATE INDEX IF NOT EXISTS idx_vocab_feedback_created_at ON public.vocabulary_ai_feedback(created_at DESC);

-- Phrase recordings indexes
CREATE INDEX IF NOT EXISTS idx_phrase_recordings_user_id ON public.phrase_practice_recordings(user_id);
CREATE INDEX IF NOT EXISTS idx_phrase_recordings_scenario_id ON public.phrase_practice_recordings(scenario_id);
CREATE INDEX IF NOT EXISTS idx_phrase_recordings_created_at ON public.phrase_practice_recordings(created_at DESC);

-- Phrase AI feedback indexes
CREATE INDEX IF NOT EXISTS idx_phrase_feedback_recording_id ON public.phrase_ai_feedback(recording_id);
CREATE INDEX IF NOT EXISTS idx_phrase_feedback_user_id ON public.phrase_ai_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_phrase_feedback_created_at ON public.phrase_ai_feedback(created_at DESC);

-- ============================================================================
-- 6. ROW LEVEL SECURITY
-- ============================================================================

-- NOTE: RLS is DISABLED to match other tables in the schema (like user_vocabulary)
-- The app handles user isolation at the application layer
ALTER TABLE public.vocabulary_practice_recordings DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.vocabulary_ai_feedback DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.phrase_practice_recordings DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.phrase_ai_feedback DISABLE ROW LEVEL SECURITY;

-- Vocabulary recordings policies
DROP POLICY IF EXISTS "Users can view own vocabulary recordings" ON public.vocabulary_practice_recordings;
DROP POLICY IF EXISTS "Users can insert own vocabulary recordings" ON public.vocabulary_practice_recordings;
DROP POLICY IF EXISTS "Users can update own vocabulary recordings" ON public.vocabulary_practice_recordings;
DROP POLICY IF EXISTS "Users can delete own vocabulary recordings" ON public.vocabulary_practice_recordings;

CREATE POLICY "Users can view own vocabulary recordings" ON public.vocabulary_practice_recordings 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own vocabulary recordings" ON public.vocabulary_practice_recordings 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own vocabulary recordings" ON public.vocabulary_practice_recordings 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own vocabulary recordings" ON public.vocabulary_practice_recordings 
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Vocabulary AI feedback policies
DROP POLICY IF EXISTS "Users can view own vocabulary feedback" ON public.vocabulary_ai_feedback;
DROP POLICY IF EXISTS "Users can insert own vocabulary feedback" ON public.vocabulary_ai_feedback;
DROP POLICY IF EXISTS "Users can update own vocabulary feedback" ON public.vocabulary_ai_feedback;
DROP POLICY IF EXISTS "Users can delete own vocabulary feedback" ON public.vocabulary_ai_feedback;

CREATE POLICY "Users can view own vocabulary feedback" ON public.vocabulary_ai_feedback 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own vocabulary feedback" ON public.vocabulary_ai_feedback 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own vocabulary feedback" ON public.vocabulary_ai_feedback 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own vocabulary feedback" ON public.vocabulary_ai_feedback 
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Phrase recordings policies
DROP POLICY IF EXISTS "Users can view own phrase recordings" ON public.phrase_practice_recordings;
DROP POLICY IF EXISTS "Users can insert own phrase recordings" ON public.phrase_practice_recordings;
DROP POLICY IF EXISTS "Users can update own phrase recordings" ON public.phrase_practice_recordings;
DROP POLICY IF EXISTS "Users can delete own phrase recordings" ON public.phrase_practice_recordings;

CREATE POLICY "Users can view own phrase recordings" ON public.phrase_practice_recordings 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own phrase recordings" ON public.phrase_practice_recordings 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own phrase recordings" ON public.phrase_practice_recordings 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own phrase recordings" ON public.phrase_practice_recordings 
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- Phrase AI feedback policies
DROP POLICY IF EXISTS "Users can view own phrase feedback" ON public.phrase_ai_feedback;
DROP POLICY IF EXISTS "Users can insert own phrase feedback" ON public.phrase_ai_feedback;
DROP POLICY IF EXISTS "Users can update own phrase feedback" ON public.phrase_ai_feedback;
DROP POLICY IF EXISTS "Users can delete own phrase feedback" ON public.phrase_ai_feedback;

CREATE POLICY "Users can view own phrase feedback" ON public.phrase_ai_feedback 
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own phrase feedback" ON public.phrase_ai_feedback 
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own phrase feedback" ON public.phrase_ai_feedback 
    FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own phrase feedback" ON public.phrase_ai_feedback 
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ============================================================================
-- 7. GRANT PERMISSIONS
-- ============================================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON public.vocabulary_practice_recordings TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.vocabulary_ai_feedback TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.phrase_practice_recordings TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.phrase_ai_feedback TO authenticated;

-- ============================================================================
-- 8. HELPER VIEWS FOR PRACTICE HISTORY
-- ============================================================================

-- View combining vocabulary recordings with their AI feedback
CREATE OR REPLACE VIEW vocabulary_practice_history AS
SELECT 
    vpr.id AS recording_id,
    vpr.user_id,
    vpr.word_id,
    vw.word,
    vw.definition,
    vw.pronunciation AS expected_pronunciation,
    vpr.recording_url,
    vpr.duration_seconds,
    vpr.transcript,
    vpr.language,
    vpr.expected_text,
    vpr.created_at AS practiced_at,
    vaf.id AS feedback_id,
    vaf.overall_score,
    vaf.pronunciation_score,
    vaf.accuracy_score,
    vaf.fluency_score,
    vaf.clarity_score,
    vaf.detailed_analysis,
    vaf.strengths,
    vaf.areas_for_improvement,
    vaf.suggestions
FROM public.vocabulary_practice_recordings vpr
LEFT JOIN public.vocabulary_ai_feedback vaf ON vaf.recording_id = vpr.id
LEFT JOIN public.vocabulary_words vw ON vw.id = vpr.word_id
ORDER BY vpr.created_at DESC;

-- View combining phrase recordings with their AI feedback
CREATE OR REPLACE VIEW phrase_practice_history AS
SELECT 
    ppr.id AS recording_id,
    ppr.user_id,
    ppr.scenario_id,
    ss.title AS scenario_title,
    ss.scenario_type,
    ppr.recording_url,
    ppr.duration_seconds,
    ppr.transcript,
    ppr.language,
    ppr.expected_phrase,
    ppr.difficulty_level,
    ppr.created_at AS practiced_at,
    paf.id AS feedback_id,
    paf.overall_score,
    paf.pronunciation_score,
    paf.grammar_score,
    paf.fluency_score,
    paf.accuracy_score,
    paf.contextual_appropriateness_score,
    paf.detailed_analysis,
    paf.strengths,
    paf.areas_for_improvement,
    paf.suggestions
FROM public.phrase_practice_recordings ppr
LEFT JOIN public.phrase_ai_feedback paf ON paf.recording_id = ppr.id
LEFT JOIN public.speaking_scenarios ss ON ss.id = ppr.scenario_id
ORDER BY ppr.created_at DESC;

GRANT SELECT ON vocabulary_practice_history TO authenticated;
GRANT SELECT ON phrase_practice_history TO authenticated;

-- ============================================================================
-- NOTES:
-- ============================================================================
--
-- Usage Examples:
--
-- 1. Get all practice history for a specific vocabulary word:
--    SELECT * FROM vocabulary_practice_history
--    WHERE user_id = $1 AND word_id = $2
--    ORDER BY practiced_at DESC;
--
-- 2. Get recent practice sessions for a user:
--    SELECT * FROM vocabulary_practice_history
--    WHERE user_id = $1
--    ORDER BY practiced_at DESC
--    LIMIT 20;
--
-- 3. Get phrase practice history for a scenario:
--    SELECT * FROM phrase_practice_history
--    WHERE user_id = $1 AND scenario_id = $2
--    ORDER BY practiced_at DESC;
--
-- 4. Get score improvements over time:
--    SELECT word_id, word, practiced_at, overall_score
--    FROM vocabulary_practice_history
--    WHERE user_id = $1 AND word_id = $2
--    ORDER BY practiced_at ASC;
--
-- ============================================================================
