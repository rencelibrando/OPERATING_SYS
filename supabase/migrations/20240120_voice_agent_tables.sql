-- Voice Agent Sessions Table
CREATE TABLE IF NOT EXISTS agent_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    language TEXT NOT NULL,
    scenario TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE,
    duration FLOAT DEFAULT 0, -- in seconds
    conversation_count INTEGER DEFAULT 0,
    audio_duration FLOAT DEFAULT 0.0, -- total audio duration in seconds
    voice_model TEXT,
    temperature FLOAT DEFAULT 0.7,
    custom_prompt TEXT,
    
    -- Indexes for performance
    INDEX idx_agent_sessions_user_id (user_id),
    INDEX idx_agent_sessions_status (status),
    INDEX idx_agent_sessions_created_at (created_at),
    INDEX idx_agent_sessions_language (language),
    INDEX idx_agent_sessions_scenario (scenario)
);

-- Agent Conversation Logs Table
CREATE TABLE IF NOT EXISTS agent_conversation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES agent_sessions(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    audio_available BOOLEAN DEFAULT FALSE,
    audio_url TEXT,
    
    -- Indexes for performance
    INDEX idx_agent_conversation_logs_session_id (session_id),
    INDEX idx_agent_conversation_logs_timestamp (timestamp),
    INDEX idx_agent_conversation_logs_role (role)
);

-- Agent Event Logs Table
CREATE TABLE IF NOT EXISTS agent_event_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES agent_sessions(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    event_data JSONB,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Indexes for performance
    INDEX idx_agent_event_logs_session_id (session_id),
    INDEX idx_agent_event_logs_timestamp (timestamp),
    INDEX idx_agent_event_logs_event_type (event_type)
);

-- Agent User Statistics Table
CREATE TABLE IF NOT EXISTS agent_user_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL UNIQUE,
    total_sessions INTEGER DEFAULT 0,
    total_duration FLOAT DEFAULT 0, -- in seconds
    favorite_language TEXT,
    favorite_scenario TEXT,
    last_session TIMESTAMP WITH TIME ZONE,
    improvement_trend FLOAT DEFAULT 0, -- positive = improving
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Indexes for performance
    INDEX idx_agent_user_stats_user_id (user_id),
    INDEX idx_agent_user_stats_updated_at (updated_at)
);

-- Function to update user statistics
CREATE OR REPLACE FUNCTION update_agent_user_stats()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO agent_user_stats (
        user_id, 
        total_sessions, 
        total_duration, 
        favorite_language, 
        favorite_scenario, 
        last_session,
        updated_at
    )
    VALUES (
        NEW.user_id,
        1,
        COALESCE(NEW.duration, 0),
        NEW.language,
        NEW.scenario,
        NEW.created_at,
        NOW()
    )
    ON CONFLICT (user_id) 
    DO UPDATE SET
        total_sessions = agent_user_stats.total_sessions + 1,
        total_duration = agent_user_stats.total_duration + COALESCE(NEW.duration, 0),
        favorite_language = (
            SELECT language 
            FROM agent_sessions 
            WHERE user_id = NEW.user_id 
            GROUP BY language 
            ORDER BY COUNT(*) DESC, created_at DESC 
            LIMIT 1
        ),
        favorite_scenario = (
            SELECT scenario 
            FROM agent_sessions 
            WHERE user_id = NEW.user_id 
            GROUP BY scenario 
            ORDER BY COUNT(*) DESC, created_at DESC 
            LIMIT 1
        ),
        last_session = NEW.created_at,
        updated_at = NOW();
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update user statistics
CREATE TRIGGER trigger_update_agent_user_stats
    AFTER INSERT ON agent_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_agent_user_stats();

-- Function to update session duration on end
CREATE OR REPLACE FUNCTION update_session_duration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ended' AND OLD.status != 'ended' THEN
        NEW.duration = EXTRACT(EPOCH FROM (NEW.ended_at - NEW.created_at));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update duration when session ends
CREATE TRIGGER trigger_update_session_duration
    BEFORE UPDATE ON agent_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_session_duration();

-- Row Level Security (RLS) Policies
ALTER TABLE agent_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_conversation_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_event_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_user_stats ENABLE ROW LEVEL SECURITY;

-- Policy for agent_sessions - users can only access their own sessions
CREATE POLICY "Users can view own agent sessions" ON agent_sessions
    FOR SELECT USING (auth.uid()::text = user_id);

CREATE POLICY "Users can insert own agent sessions" ON agent_sessions
    FOR INSERT WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY "Users can update own agent sessions" ON agent_sessions
    FOR UPDATE USING (auth.uid()::text = user_id);

-- Policy for agent_conversation_logs - users can only access logs from their own sessions
CREATE POLICY "Users can view own conversation logs" ON agent_conversation_logs
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM agent_sessions 
            WHERE id = session_id AND user_id = auth.uid()::text
        )
    );

CREATE POLICY "Users can insert own conversation logs" ON agent_conversation_logs
    FOR INSERT WITH CHECK (
        EXISTS (
            SELECT 1 FROM agent_sessions 
            WHERE id = session_id AND user_id = auth.uid()::text
        )
    );

-- Policy for agent_event_logs - users can only access logs from their own sessions
CREATE POLICY "Users can view own event logs" ON agent_event_logs
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM agent_sessions 
            WHERE id = session_id AND user_id = auth.uid()::text
        )
    );

CREATE POLICY "Users can insert own event logs" ON agent_event_logs
    FOR INSERT WITH CHECK (
        EXISTS (
            SELECT 1 FROM agent_sessions 
            WHERE id = session_id AND user_id = auth.uid()::text
        )
    );

-- Policy for agent_user_stats - users can only access their own statistics
CREATE POLICY "Users can view own stats" ON agent_user_stats
    FOR SELECT USING (auth.uid()::text = user_id);

CREATE POLICY "Users can update own stats" ON agent_user_stats
    FOR UPDATE USING (auth.uid()::text = user_id);

-- Grant permissions to authenticated users
GRANT SELECT, INSERT, UPDATE ON agent_sessions TO authenticated;
GRANT SELECT, INSERT, UPDATE ON agent_conversation_logs TO authenticated;
GRANT SELECT, INSERT, UPDATE ON agent_event_logs TO authenticated;
GRANT SELECT, UPDATE ON agent_user_stats TO authenticated;

-- Grant usage on sequences
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO authenticated;
