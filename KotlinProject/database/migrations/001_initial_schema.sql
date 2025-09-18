-- WordBridge Database Schema

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";


-- USER PROFILES
-- User profiles (extends Supabase auth.users)
CREATE TABLE user_profiles (
    id UUID REFERENCES auth.users(id) PRIMARY KEY,
    personal_info JSONB DEFAULT '{}'::jsonb,
    learning_profile JSONB DEFAULT '{}'::jsonb,
    account_info JSONB DEFAULT '{}'::jsonb,
    profile_stats JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User settings
CREATE TABLE user_settings (
    user_id UUID REFERENCES auth.users(id) PRIMARY KEY,
    theme_preferences JSONB DEFAULT '{}'::jsonb,
    notification_settings JSONB DEFAULT '{}'::jsonb,
    ai_preferences JSONB DEFAULT '{}'::jsonb,
    privacy_settings JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);


-- LEARNING PROGRESS


-- Overall learning progress
CREATE TABLE learning_progress (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    overall_level INTEGER DEFAULT 1,
    xp_points INTEGER DEFAULT 0,
    weekly_xp INTEGER DEFAULT 0,
    monthly_xp INTEGER DEFAULT 0,
    streak_days INTEGER DEFAULT 0,
    longest_streak INTEGER DEFAULT 0,
    total_study_time INTEGER DEFAULT 0, -- in minutes
    weekly_study_time INTEGER DEFAULT 0, -- in minutes
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id)
);

-- Skill-specific progress
CREATE TABLE skill_progress (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    skill_area TEXT NOT NULL CHECK (skill_area IN ('vocabulary', 'grammar', 'speaking', 'listening', 'reading', 'writing')),
    level INTEGER DEFAULT 1,
    xp_points INTEGER DEFAULT 0,
    max_xp INTEGER DEFAULT 100,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, skill_area)
);


-- LESSONS


-- Lesson definitions
CREATE TABLE lessons (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('grammar', 'vocabulary', 'conversation', 'pronunciation')),
    difficulty TEXT NOT NULL CHECK (difficulty IN ('beginner', 'intermediate', 'advanced')),
    duration INTEGER NOT NULL, -- in minutes
    lessons_count INTEGER DEFAULT 1,
    icon TEXT,
    content JSONB DEFAULT '{}'::jsonb,
    ai_generated BOOLEAN DEFAULT FALSE,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User lesson progress
CREATE TABLE user_lesson_progress (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    lesson_id UUID REFERENCES lessons(id) NOT NULL,
    completed_count INTEGER DEFAULT 0,
    total_count INTEGER DEFAULT 1,
    progress_percentage INTEGER DEFAULT 0 CHECK (progress_percentage >= 0 AND progress_percentage <= 100),
    last_accessed TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, lesson_id)
);


-- VOCABULARY


-- Vocabulary words
CREATE TABLE vocabulary_words (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    word TEXT NOT NULL,
    definition TEXT NOT NULL,
    pronunciation TEXT,
    category TEXT NOT NULL,
    difficulty TEXT NOT NULL CHECK (difficulty IN ('beginner', 'intermediate', 'advanced')),
    examples JSONB DEFAULT '[]'::jsonb,
    ai_explanation JSONB DEFAULT '{}'::jsonb,
    embedding VECTOR(1536), -- For OpenAI embeddings
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User vocabulary tracking
CREATE TABLE user_vocabulary (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    word_id UUID REFERENCES vocabulary_words(id) NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('new', 'learning', 'reviewing', 'mastered')),
    date_added TIMESTAMPTZ DEFAULT NOW(),
    last_reviewed TIMESTAMPTZ,
    review_count INTEGER DEFAULT 0,
    mastery_level INTEGER DEFAULT 0 CHECK (mastery_level >= 0 AND mastery_level <= 100),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, word_id)
);


-- AI CHAT


-- Chat sessions
CREATE TABLE chat_sessions (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    bot_id TEXT NOT NULL,
    title TEXT,
    conversation_context JSONB DEFAULT '{}'::jsonb,
    openai_thread_id TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Chat messages
CREATE TABLE chat_messages (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    session_id UUID REFERENCES chat_sessions(id) ON DELETE CASCADE NOT NULL,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('user', 'ai')),
    message_text TEXT NOT NULL,
    ai_metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Chat bots
CREATE TABLE chat_bots (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    avatar TEXT,
    personality TEXT,
    specialties JSONB DEFAULT '[]'::jsonb,
    difficulty TEXT NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);


-- ACHIEVEMENTS


-- Achievement definitions
CREATE TABLE achievements (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    icon TEXT,
    category TEXT NOT NULL,
    xp_reward INTEGER DEFAULT 0,
    is_rare BOOLEAN DEFAULT FALSE,
    requirements JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- User achievements
CREATE TABLE user_achievements (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    achievement_id UUID REFERENCES achievements(id) NOT NULL,
    unlocked_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, achievement_id)
);


-- INDEXES


-- User profiles indexes
CREATE INDEX idx_user_profiles_created_at ON user_profiles(created_at);

-- Learning progress indexes
CREATE INDEX idx_learning_progress_user_id ON learning_progress(user_id);
CREATE INDEX idx_skill_progress_user_skill ON skill_progress(user_id, skill_area);

-- Lesson indexes
CREATE INDEX idx_lessons_category ON lessons(category);
CREATE INDEX idx_lessons_difficulty ON lessons(difficulty);
CREATE INDEX idx_user_lesson_progress_user_id ON user_lesson_progress(user_id);
CREATE INDEX idx_user_lesson_progress_last_accessed ON user_lesson_progress(last_accessed DESC);

-- Vocabulary indexes
CREATE INDEX idx_vocabulary_words_category ON vocabulary_words(category);
CREATE INDEX idx_vocabulary_words_difficulty ON vocabulary_words(difficulty);
CREATE INDEX idx_vocabulary_words_word ON vocabulary_words(word);
CREATE INDEX idx_user_vocabulary_user_id ON user_vocabulary(user_id);
CREATE INDEX idx_user_vocabulary_status ON user_vocabulary(status);

-- Chat indexes
CREATE INDEX idx_chat_sessions_user_id ON chat_sessions(user_id);
CREATE INDEX idx_chat_messages_session_id ON chat_messages(session_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at DESC);

-- Achievement indexes
CREATE INDEX idx_user_achievements_user_id ON user_achievements(user_id);


-- ROW LEVEL SECURITY (RLS)


-- Enable RLS on all tables
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE learning_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE skill_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_lesson_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_vocabulary ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_achievements ENABLE ROW LEVEL SECURITY;

-- User can only access their own data
CREATE POLICY "Users can access own profile" ON user_profiles FOR ALL USING (auth.uid() = id);
CREATE POLICY "Users can access own settings" ON user_settings FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users can access own learning progress" ON learning_progress FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users can access own skill progress" ON skill_progress FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users can access own lesson progress" ON user_lesson_progress FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users can access own vocabulary" ON user_vocabulary FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users can access own chat sessions" ON chat_sessions FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Users can access own achievements" ON user_achievements FOR ALL USING (auth.uid() = user_id);

-- Chat messages policy (user can access messages from their sessions)
CREATE POLICY "Users can access messages from own sessions" ON chat_messages 
FOR ALL USING (
    session_id IN (
        SELECT id FROM chat_sessions WHERE user_id = auth.uid()
    )
);

-- Public read access for reference tables
CREATE POLICY "Anyone can read lessons" ON lessons FOR SELECT USING (is_available = true);
CREATE POLICY "Anyone can read vocabulary words" ON vocabulary_words FOR SELECT USING (true);
CREATE POLICY "Anyone can read chat bots" ON chat_bots FOR SELECT USING (is_available = true);
CREATE POLICY "Anyone can read achievements" ON achievements FOR SELECT USING (true);


-- FUNCTIONS AND TRIGGERS


-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add updated_at triggers
CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON user_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_learning_progress_updated_at BEFORE UPDATE ON learning_progress FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_skill_progress_updated_at BEFORE UPDATE ON skill_progress FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_lessons_updated_at BEFORE UPDATE ON lessons FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_lesson_progress_updated_at BEFORE UPDATE ON user_lesson_progress FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vocabulary_words_updated_at BEFORE UPDATE ON vocabulary_words FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_vocabulary_updated_at BEFORE UPDATE ON user_vocabulary FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_chat_sessions_updated_at BEFORE UPDATE ON chat_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_chat_bots_updated_at BEFORE UPDATE ON chat_bots FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to create user profile on signup
CREATE OR REPLACE FUNCTION handle_new_user() 
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO user_profiles (id, personal_info, learning_profile, account_info, profile_stats)
    VALUES (
        NEW.id,
        '{"firstName": "", "lastName": "", "email": "' || NEW.email || '"}'::jsonb,
        '{"currentLevel": "Beginner", "weeklyGoalHours": 3}'::jsonb,
        '{"subscriptionType": "Free", "subscriptionStatus": "Active"}'::jsonb,
        '{"totalStudyTime": 0, "lessonsCompleted": 0, "wordsLearned": 0}'::jsonb
    );
    
    INSERT INTO user_settings (user_id)
    VALUES (NEW.id);
    
    INSERT INTO learning_progress (user_id)
    VALUES (NEW.id);
    
    -- Initialize skill progress for all skill areas
    INSERT INTO skill_progress (user_id, skill_area)
    VALUES 
        (NEW.id, 'vocabulary'),
        (NEW.id, 'grammar'),
        (NEW.id, 'speaking'),
        (NEW.id, 'listening'),
        (NEW.id, 'reading'),
        (NEW.id, 'writing');
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to create user profile on signup
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION handle_new_user();
