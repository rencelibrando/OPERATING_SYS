-- Migration: Progress history tracking for historical trends
-- Stores daily snapshots of user progress per language

-- ============================================================================
-- 1. Create progress history table
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_progress_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    language TEXT NOT NULL CHECK (language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish')),
    snapshot_date DATE NOT NULL,
    
    -- Metrics snapshot
    lessons_completed INT DEFAULT 0,
    total_lessons INT DEFAULT 0,
    conversation_sessions INT DEFAULT 0,
    vocabulary_words INT DEFAULT 0,
    total_time_seconds FLOAT8 DEFAULT 0.0,
    
    -- Voice scores snapshot
    score_overall FLOAT8 DEFAULT 0.0,
    score_grammar FLOAT8 DEFAULT 0.0,
    score_pronunciation FLOAT8 DEFAULT 0.0,
    score_vocabulary FLOAT8 DEFAULT 0.0,
    score_fluency FLOAT8 DEFAULT 0.0,
    score_accuracy FLOAT8 DEFAULT 0.0,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Ensure one snapshot per user per language per day
    UNIQUE(user_id, language, snapshot_date)
);

-- Create indexes for fast queries
CREATE INDEX idx_progress_history_user_lang ON user_progress_history(user_id, language);
CREATE INDEX idx_progress_history_date ON user_progress_history(snapshot_date DESC);

-- ============================================================================
-- 2. Create function to capture daily snapshot
-- ============================================================================

CREATE OR REPLACE FUNCTION capture_progress_snapshot(
    p_user_id UUID,
    p_language TEXT,
    p_snapshot_date DATE DEFAULT CURRENT_DATE
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_progress RECORD;
BEGIN
    -- Get current progress from materialized view
    SELECT * INTO v_progress
    FROM user_language_progress
    WHERE user_id = p_user_id
      AND language = p_language
    LIMIT 1;
    
    IF v_progress IS NULL THEN
        -- No progress yet, create empty snapshot
        INSERT INTO user_progress_history (
            user_id, language, snapshot_date,
            lessons_completed, total_lessons, conversation_sessions,
            vocabulary_words, total_time_seconds,
            score_overall, score_grammar, score_pronunciation,
            score_vocabulary, score_fluency, score_accuracy
        ) VALUES (
            p_user_id, p_language, p_snapshot_date,
            0, 0, 0, 0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        )
        ON CONFLICT (user_id, language, snapshot_date) 
        DO NOTHING;
    ELSE
        -- Insert or update snapshot
        INSERT INTO user_progress_history (
            user_id, language, snapshot_date,
            lessons_completed, total_lessons, conversation_sessions,
            vocabulary_words, total_time_seconds,
            score_overall, score_grammar, score_pronunciation,
            score_vocabulary, score_fluency, score_accuracy
        ) VALUES (
            p_user_id, p_language, p_snapshot_date,
            v_progress.lessons_completed, v_progress.total_lessons,
            v_progress.conversation_sessions, v_progress.vocabulary_words,
            v_progress.total_time_seconds,
            v_progress.score_overall, v_progress.score_grammar,
            v_progress.score_pronunciation, v_progress.score_vocabulary,
            v_progress.score_fluency, v_progress.score_accuracy
        )
        ON CONFLICT (user_id, language, snapshot_date) 
        DO UPDATE SET
            lessons_completed = EXCLUDED.lessons_completed,
            total_lessons = EXCLUDED.total_lessons,
            conversation_sessions = EXCLUDED.conversation_sessions,
            vocabulary_words = EXCLUDED.vocabulary_words,
            total_time_seconds = EXCLUDED.total_time_seconds,
            score_overall = EXCLUDED.score_overall,
            score_grammar = EXCLUDED.score_grammar,
            score_pronunciation = EXCLUDED.score_pronunciation,
            score_vocabulary = EXCLUDED.score_vocabulary,
            score_fluency = EXCLUDED.score_fluency,
            score_accuracy = EXCLUDED.score_accuracy;
    END IF;
END;
$$;

-- ============================================================================
-- 3. Create function to capture all users' snapshots
-- ============================================================================

CREATE OR REPLACE FUNCTION capture_all_progress_snapshots()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_lang RECORD;
BEGIN
    -- Loop through all unique user-language combinations
    FOR v_user_lang IN 
        SELECT DISTINCT user_id, language 
        FROM user_language_progress
    LOOP
        PERFORM capture_progress_snapshot(
            v_user_lang.user_id,
            v_user_lang.language,
            CURRENT_DATE
        );
    END LOOP;
END;
$$;

-- ============================================================================
-- 4. Schedule daily snapshot capture (requires pg_cron)
-- ============================================================================

-- Uncomment to enable automatic daily snapshots
-- SELECT cron.schedule(
--     'daily-progress-snapshot',
--     '0 0 * * *', -- Every day at midnight UTC
--     $$ SELECT capture_all_progress_snapshots(); $$
-- );

-- ============================================================================
-- 5. Enable RLS
-- ============================================================================

ALTER TABLE user_progress_history ENABLE ROW LEVEL SECURITY;

-- Users can only view their own history
CREATE POLICY "Users can view own progress history"
    ON user_progress_history FOR SELECT
    USING (auth.uid() = user_id);

-- Allow authenticated users to insert their own snapshots
CREATE POLICY "Users can insert own progress history"
    ON user_progress_history FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- ============================================================================
-- 6. Grant permissions
-- ============================================================================

GRANT SELECT, INSERT ON user_progress_history TO authenticated;

-- ============================================================================
-- 7. Create helper views for common time ranges
-- ============================================================================

-- Last 7 days view
CREATE OR REPLACE VIEW user_progress_last_7_days AS
SELECT *
FROM user_progress_history
WHERE snapshot_date >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY snapshot_date DESC;

-- Last 30 days view
CREATE OR REPLACE VIEW user_progress_last_30_days AS
SELECT *
FROM user_progress_history
WHERE snapshot_date >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY snapshot_date DESC;

-- Last 90 days view
CREATE OR REPLACE VIEW user_progress_last_90_days AS
SELECT *
FROM user_progress_history
WHERE snapshot_date >= CURRENT_DATE - INTERVAL '90 days'
ORDER BY snapshot_date DESC;

GRANT SELECT ON user_progress_last_7_days TO authenticated;
GRANT SELECT ON user_progress_last_30_days TO authenticated;
GRANT SELECT ON user_progress_last_90_days TO authenticated;

-- ============================================================================
-- NOTES:
-- ============================================================================
--
-- Usage:
-- 
-- 1. Capture snapshot for specific user/language:
--    SELECT capture_progress_snapshot('user-uuid', 'Korean');
--
-- 2. Capture all snapshots (run daily):
--    SELECT capture_all_progress_snapshots();
--
-- 3. Query history:
--    SELECT * FROM user_progress_history
--    WHERE user_id = 'user-uuid' AND language = 'Korean'
--    ORDER BY snapshot_date DESC
--    LIMIT 30;
--
-- 4. Get last week's data:
--    SELECT * FROM user_progress_last_7_days
--    WHERE user_id = 'user-uuid' AND language = 'Korean';
--
-- ============================================================================
