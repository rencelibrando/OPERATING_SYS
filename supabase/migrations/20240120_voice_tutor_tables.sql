-- Voice Tutor Tables for Language Learning App
-- Created: 2024-01-20
-- Purpose: Store voice sessions, feedback, and progress tracking

-- Create voice_sessions table
CREATE TABLE IF NOT EXISTS voice_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL CHECK (language IN ('fr', 'de', 'ko', 'zh', 'es')),
    level VARCHAR(20) NOT NULL CHECK (level IN ('beginner', 'intermediate', 'advanced')),
    scenario VARCHAR(30) NOT NULL CHECK (scenario IN ('travel', 'food', 'daily_conversation', 'work', 'culture')),
    transcript TEXT NOT NULL,
    audio_url TEXT,
    feedback JSONB NOT NULL DEFAULT '{}',
    scores JSONB NOT NULL DEFAULT '{}',
    session_duration FLOAT NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Indexes for performance
    CONSTRAINT voice_sessions_user_language_idx UNIQUE (user_id, language, created_at)
);

-- Create voice_progress table for tracking user progress per language
CREATE TABLE IF NOT EXISTS voice_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL CHECK (language IN ('fr', 'de', 'ko', 'zh', 'es')),
    total_sessions INTEGER NOT NULL DEFAULT 0,
    average_fluency FLOAT NOT NULL DEFAULT 0.0 CHECK (average_fluency >= 0 AND average_fluency <= 100),
    average_pronunciation FLOAT NOT NULL DEFAULT 0.0 CHECK (average_pronunciation >= 0 AND average_pronunciation <= 100),
    average_accuracy FLOAT NOT NULL DEFAULT 0.0 CHECK (average_accuracy >= 0 AND average_accuracy <= 100),
    average_overall FLOAT NOT NULL DEFAULT 0.0 CHECK (average_overall >= 0 AND average_overall <= 100),
    last_session_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    improvement_percentage FLOAT NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Unique constraint for one progress record per user per language
    CONSTRAINT voice_progress_user_lang UNIQUE (user_id, language)
);

-- Create voice_feedback_details table for detailed feedback analysis
CREATE TABLE IF NOT EXISTS voice_feedback_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES voice_sessions(id) ON DELETE CASCADE,
    feedback_type VARCHAR(20) NOT NULL CHECK (feedback_type IN ('fluency', 'pronunciation', 'accuracy')),
    score FLOAT NOT NULL CHECK (score >= 0 AND score <= 100),
    details JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_voice_sessions_user_id ON voice_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_language ON voice_sessions(language);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_created_at ON voice_sessions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_user_lang ON voice_sessions(user_id, language);

CREATE INDEX IF NOT EXISTS idx_voice_progress_user_id ON voice_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_voice_progress_language ON voice_progress(language);
CREATE INDEX IF NOT EXISTS idx_voice_progress_updated_at ON voice_progress(updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_voice_feedback_details_session_id ON voice_feedback_details(session_id);
CREATE INDEX IF NOT EXISTS idx_voice_feedback_details_type ON voice_feedback_details(feedback_type);

-- Create storage bucket for voice recordings
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'voice-recordings',
    'voice-recordings',
    false,
    10485760, -- 10MB limit
    ARRAY['audio/wav', 'audio/mp3', 'audio/mpeg', 'audio/ogg']
) ON CONFLICT (id) DO NOTHING;

-- Row Level Security (RLS) Policies

-- Enable RLS on all tables
ALTER TABLE voice_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE voice_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE voice_feedback_details ENABLE ROW LEVEL SECURITY;

-- Policy for voice_sessions: Users can only access their own sessions
CREATE POLICY "Users can view own voice sessions" ON voice_sessions
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own voice sessions" ON voice_sessions
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own voice sessions" ON voice_sessions
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own voice sessions" ON voice_sessions
    FOR DELETE USING (auth.uid() = user_id);

-- Policy for voice_progress: Users can only access their own progress
CREATE POLICY "Users can view own voice progress" ON voice_progress
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own voice progress" ON voice_progress
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own voice progress" ON voice_progress
    FOR UPDATE USING (auth.uid() = user_id);

-- Policy for voice_feedback_details: Users can only access feedback for their sessions
CREATE POLICY "Users can view own feedback details" ON voice_feedback_details
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM voice_sessions 
            WHERE voice_sessions.id = voice_feedback_details.session_id 
            AND voice_sessions.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can insert own feedback details" ON voice_feedback_details
    FOR INSERT WITH CHECK (
        EXISTS (
            SELECT 1 FROM voice_sessions 
            WHERE voice_sessions.id = voice_feedback_details.session_id 
            AND voice_sessions.user_id = auth.uid()
        )
    );

-- Storage policies for voice recordings bucket
CREATE POLICY "Users can upload own voice recordings" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'voice-recordings' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );

CREATE POLICY "Users can view own voice recordings" ON storage.objects
    FOR SELECT USING (
        bucket_id = 'voice-recordings' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );

CREATE POLICY "Users can update own voice recordings" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'voice-recordings' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );

CREATE POLICY "Users can delete own voice recordings" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'voice-recordings' AND
        auth.uid()::text = (storage.foldername(name))[1]
    );

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_voice_progress_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically update updated_at
CREATE TRIGGER voice_progress_updated_at_trigger
    BEFORE UPDATE ON voice_progress
    FOR EACH ROW
    EXECUTE FUNCTION update_voice_progress_updated_at();

-- Create view for voice analytics
CREATE OR REPLACE VIEW voice_user_analytics AS
SELECT 
    u.id as user_id,
    u.email,
    COUNT(vs.id) as total_sessions,
    AVG((vs.scores->>'overall')::float) as avg_overall_score,
    AVG((vs.scores->>'fluency')::float) as avg_fluency_score,
    AVG((vs.scores->>'pronunciation')::float) as avg_pronunciation_score,
    AVG((vs.scores->>'accuracy')::float) as avg_accuracy_score,
    MAX(vs.created_at) as last_session_date,
    ARRAY_AGG(DISTINCT vs.language) as practiced_languages
FROM auth.users u
LEFT JOIN voice_sessions vs ON u.id = vs.user_id
GROUP BY u.id, u.email;

-- Grant permissions
GRANT ALL ON voice_sessions TO authenticated;
GRANT ALL ON voice_progress TO authenticated;
GRANT ALL ON voice_feedback_details TO authenticated;
GRANT SELECT ON voice_user_analytics TO authenticated;

-- Grant usage on storage bucket
GRANT ALL ON storage.buckets TO authenticated;
GRANT ALL ON storage.objects TO authenticated;
