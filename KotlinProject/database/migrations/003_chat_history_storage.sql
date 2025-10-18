-- Chat History Storage with Compression (COMBINED VERSION)
-- This migration creates chat history tables with TEXT IDs (not UUID)
-- and TEXT-based compressed messages (base64-encoded) for JSON serialization
-- to support string-based session and bot identifiers

-- Drop existing tables if they exist
DROP TABLE IF EXISTS public.chat_session_history CASCADE;
DROP TABLE IF EXISTS public.chat_sessions CASCADE;

-- CHAT SESSIONS TABLE
-- Stores metadata about chat sessions between users and bots
CREATE TABLE public.chat_sessions (
    id TEXT PRIMARY KEY,                                    -- TEXT instead of UUID
    user_id UUID REFERENCES auth.users(id) NOT NULL,       -- User who owns the session
    bot_id TEXT NOT NULL,                                   -- TEXT instead of UUID (e.g., "emma", "james")
    title TEXT,                                             -- Session title
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived', 'deleted')),
    message_count INTEGER DEFAULT 0,                        -- Number of messages in session
    total_duration INTEGER DEFAULT 0,                       -- Total duration in minutes
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for fast queries
CREATE INDEX idx_chat_sessions_user_id ON public.chat_sessions(user_id);
CREATE INDEX idx_chat_sessions_status ON public.chat_sessions(status);
CREATE INDEX idx_chat_sessions_created_at ON public.chat_sessions(created_at DESC);

-- CHAT SESSION HISTORY TABLE
-- Stores compressed chat messages for efficient storage
CREATE TABLE public.chat_session_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id TEXT REFERENCES public.chat_sessions(id) ON DELETE CASCADE NOT NULL,  -- TEXT foreign key
    
    -- Compressed message storage (TEXT for JSON serialization)
    compressed_messages TEXT NOT NULL,                      -- Base64-encoded compressed data (gzip/zlib)
    compression_type TEXT NOT NULL DEFAULT 'gzip' CHECK (compression_type IN ('gzip', 'zlib', 'none')),
    
    -- Compression statistics
    original_size INTEGER NOT NULL DEFAULT 0,               -- Original size in bytes
    compressed_size INTEGER NOT NULL DEFAULT 0,             -- Compressed size in bytes
    compression_ratio DECIMAL(5,2) GENERATED ALWAYS AS (    -- Auto-calculated compression ratio
        CASE 
            WHEN original_size > 0 THEN ROUND((1 - (compressed_size::DECIMAL / original_size::DECIMAL)) * 100, 2)
            ELSE 0
        END
    ) STORED,
    
    -- Message metadata
    message_count INTEGER DEFAULT 0,                        -- Number of messages stored
    first_message_at TIMESTAMPTZ,                          -- Timestamp of first message
    last_message_at TIMESTAMPTZ,                           -- Timestamp of last message
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Ensure only one history record per session
    UNIQUE(session_id)
);

-- Create indexes for fast lookups
CREATE INDEX idx_chat_history_session_id ON public.chat_session_history(session_id);
CREATE INDEX idx_chat_history_updated_at ON public.chat_session_history(updated_at DESC);

-- FUNCTIONS

-- Function to update the updated_at column
CREATE OR REPLACE FUNCTION public.update_chat_updated_at() 
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Triggers for updated_at columns
CREATE TRIGGER update_chat_sessions_updated_at 
    BEFORE UPDATE ON public.chat_sessions 
    FOR EACH ROW 
    EXECUTE FUNCTION public.update_chat_updated_at();

CREATE TRIGGER update_chat_history_updated_at 
    BEFORE UPDATE ON public.chat_session_history 
    FOR EACH ROW 
    EXECUTE FUNCTION public.update_chat_updated_at();

-- ROW LEVEL SECURITY (RLS)

-- Enable RLS on both tables
ALTER TABLE public.chat_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_session_history ENABLE ROW LEVEL SECURITY;

-- Chat Sessions Policies
-- Users can select their own sessions
CREATE POLICY "Users can select own chat sessions" 
    ON public.chat_sessions 
    FOR SELECT 
    TO authenticated 
    USING ((SELECT auth.uid()) = user_id);

-- Users can insert their own sessions
CREATE POLICY "Users can insert own chat sessions" 
    ON public.chat_sessions 
    FOR INSERT 
    TO authenticated 
    WITH CHECK ((SELECT auth.uid()) = user_id);

-- Users can update their own sessions
CREATE POLICY "Users can update own chat sessions" 
    ON public.chat_sessions 
    FOR UPDATE 
    TO authenticated 
    USING ((SELECT auth.uid()) = user_id) 
    WITH CHECK ((SELECT auth.uid()) = user_id);

-- Users can delete their own sessions
CREATE POLICY "Users can delete own chat sessions" 
    ON public.chat_sessions 
    FOR DELETE 
    TO authenticated 
    USING ((SELECT auth.uid()) = user_id);

-- Chat Session History Policies
-- Users can select their own chat history
CREATE POLICY "Users can select own chat history" 
    ON public.chat_session_history 
    FOR SELECT 
    TO authenticated 
    USING (
        EXISTS (
            SELECT 1 FROM public.chat_sessions 
            WHERE public.chat_sessions.id = session_id 
            AND (SELECT auth.uid()) = public.chat_sessions.user_id
        )
    );

-- Users can insert their own chat history
CREATE POLICY "Users can insert own chat history" 
    ON public.chat_session_history 
    FOR INSERT 
    TO authenticated 
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.chat_sessions 
            WHERE public.chat_sessions.id = session_id 
            AND (SELECT auth.uid()) = public.chat_sessions.user_id
        )
    );

-- Users can update their own chat history
CREATE POLICY "Users can update own chat history" 
    ON public.chat_session_history 
    FOR UPDATE 
    TO authenticated 
    USING (
        EXISTS (
            SELECT 1 FROM public.chat_sessions 
            WHERE public.chat_sessions.id = session_id 
            AND (SELECT auth.uid()) = public.chat_sessions.user_id
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.chat_sessions 
            WHERE public.chat_sessions.id = session_id 
            AND (SELECT auth.uid()) = public.chat_sessions.user_id
        )
    );

-- Users can delete their own chat history
CREATE POLICY "Users can delete own chat history" 
    ON public.chat_session_history 
    FOR DELETE 
    TO authenticated 
    USING (
        EXISTS (
            SELECT 1 FROM public.chat_sessions 
            WHERE public.chat_sessions.id = session_id 
            AND (SELECT auth.uid()) = public.chat_sessions.user_id
        )
    );

-- COMMENTS FOR DOCUMENTATION
COMMENT ON TABLE public.chat_sessions IS 'Stores chat session metadata with TEXT-based IDs';
COMMENT ON TABLE public.chat_session_history IS 'Stores compressed chat conversation history for efficient storage and retrieval';
COMMENT ON COLUMN public.chat_sessions.id IS 'Session identifier (TEXT format, e.g., session_1234567890)';
COMMENT ON COLUMN public.chat_sessions.bot_id IS 'Bot identifier (TEXT format, e.g., emma, james, sophia)';
COMMENT ON COLUMN public.chat_session_history.compressed_messages IS 'Compressed chat messages in base64-encoded format (gzip or zlib)';
COMMENT ON COLUMN public.chat_session_history.compression_ratio IS 'Percentage of space saved through compression';

-- Success message
DO $$
BEGIN
  RAISE NOTICE '================================================';
  RAISE NOTICE '✅ Chat History Storage Created Successfully!';
  RAISE NOTICE '================================================';
  RAISE NOTICE '';
  RAISE NOTICE 'Tables created:';
  RAISE NOTICE '  - chat_sessions (with TEXT id and bot_id)';
  RAISE NOTICE '  - chat_session_history (with TEXT compression)';
  RAISE NOTICE '';
  RAISE NOTICE 'Features enabled:';
  RAISE NOTICE '  ✅ TEXT-based IDs (no UUID issues)';
  RAISE NOTICE '  ✅ TEXT-based compressed messages (JSON serializable)';
  RAISE NOTICE '  ✅ Message compression (gzip/zlib)';
  RAISE NOTICE '  ✅ Automatic compression ratio calculation';
  RAISE NOTICE '  ✅ Row Level Security (RLS)';
  RAISE NOTICE '  ✅ Automatic timestamps';
  RAISE NOTICE '  ✅ Foreign key constraints';
  RAISE NOTICE '  ✅ Indexes for performance';
  RAISE NOTICE '';
  RAISE NOTICE 'Ready to use! 🚀';
  RAISE NOTICE '================================================';
END $$;

-- Verify tables were created
SELECT 
    table_name,
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns 
WHERE table_name IN ('chat_sessions', 'chat_session_history')
    AND table_schema = 'public'
ORDER BY table_name, ordinal_position;
