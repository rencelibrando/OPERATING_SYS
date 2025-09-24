-- WordBridge Database Schema - FIXED VERSION
-- Explicitly specifies public schema and organizes for better visibility

-- Drop existing objects if they exist to avoid conflicts
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();
DROP FUNCTION IF EXISTS public.update_updated_at_column();

-- USER PROFILES
-- User profiles (extends Supabase auth.users)
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID REFERENCES auth.users(id) PRIMARY KEY,
    personal_info JSONB DEFAULT '{}'::jsonb,
    learning_profile JSONB DEFAULT '{}'::jsonb,
    account_info JSONB DEFAULT '{}'::jsonb,
    profile_stats JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User settings
CREATE TABLE IF NOT EXISTS public.user_settings (
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
CREATE TABLE IF NOT EXISTS public.learning_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
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
CREATE TABLE IF NOT EXISTS public.skill_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    skill_area TEXT NOT NULL CHECK (skill_area IN ('vocabulary', 'grammar', 'speaking', 'listening', 'reading', 'writing')),
    level INTEGER DEFAULT 1,
    xp_points INTEGER DEFAULT 0,
    accuracy_percentage DECIMAL(5,2) DEFAULT 0.0,
    time_spent INTEGER DEFAULT 0, -- in minutes
    exercises_completed INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, skill_area)
);

-- VOCABULARY SYSTEM

-- Vocabulary words
CREATE TABLE IF NOT EXISTS public.vocabulary_words (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    word TEXT NOT NULL UNIQUE,
    definition TEXT NOT NULL,
    pronunciation TEXT,
    example_sentence TEXT,
    difficulty_level TEXT NOT NULL CHECK (difficulty_level IN ('Beginner', 'Intermediate', 'Advanced')),
    category TEXT NOT NULL,
    audio_url TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User vocabulary progress (spaced repetition system)
CREATE TABLE IF NOT EXISTS public.user_vocabulary (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    word_id UUID REFERENCES public.vocabulary_words(id) NOT NULL,
    status TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'learning', 'reviewing', 'mastered')),
    review_count INTEGER DEFAULT 0,
    correct_count INTEGER DEFAULT 0,
    last_reviewed TIMESTAMPTZ,
    next_review TIMESTAMPTZ DEFAULT NOW(),
    interval_days INTEGER DEFAULT 1,
    ease_factor DECIMAL(3,2) DEFAULT 2.50,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, word_id)
);

-- LESSONS SYSTEM

-- Lesson categories
CREATE TABLE IF NOT EXISTS public.lesson_categories (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    icon TEXT,
    color TEXT,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lessons
CREATE TABLE IF NOT EXISTS public.lessons (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    category_id UUID REFERENCES public.lesson_categories(id) NOT NULL,
    difficulty_level TEXT NOT NULL CHECK (difficulty_level IN ('Beginner', 'Intermediate', 'Advanced')),
    content JSONB NOT NULL DEFAULT '{}'::jsonb,
    estimated_duration INTEGER DEFAULT 10, -- in minutes
    xp_reward INTEGER DEFAULT 10,
    sort_order INTEGER DEFAULT 0,
    is_published BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User lesson progress
CREATE TABLE IF NOT EXISTS public.user_lesson_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    lesson_id UUID REFERENCES public.lessons(id) NOT NULL,
    status TEXT NOT NULL DEFAULT 'not_started' CHECK (status IN ('not_started', 'in_progress', 'completed')),
    progress_percentage INTEGER DEFAULT 0 CHECK (progress_percentage >= 0 AND progress_percentage <= 100),
    score INTEGER,
    time_spent INTEGER DEFAULT 0, -- in minutes
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, lesson_id)
);

-- AI CHAT SYSTEM

-- Chat bots/personalities
CREATE TABLE IF NOT EXISTS public.chat_bots (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    personality JSONB DEFAULT '{}'::jsonb,
    avatar_url TEXT,
    difficulty TEXT NOT NULL CHECK (difficulty IN ('Beginner', 'Intermediate', 'Advanced')),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Chat sessions
CREATE TABLE IF NOT EXISTS public.chat_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    bot_id UUID REFERENCES public.chat_bots(id) NOT NULL,
    title TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived', 'deleted')),
    message_count INTEGER DEFAULT 0,
    total_duration INTEGER DEFAULT 0, -- in minutes
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Chat messages
CREATE TABLE IF NOT EXISTS public.chat_messages (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id UUID REFERENCES public.chat_sessions(id) NOT NULL,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('user', 'bot')),
    message_text TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ACHIEVEMENTS SYSTEM

-- Achievements
CREATE TABLE IF NOT EXISTS public.achievements (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    icon TEXT,
    category TEXT NOT NULL,
    criteria JSONB NOT NULL DEFAULT '{}'::jsonb,
    xp_reward INTEGER DEFAULT 0,
    badge_color TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User achievements
CREATE TABLE IF NOT EXISTS public.user_achievements (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    achievement_id UUID REFERENCES public.achievements(id) NOT NULL,
    earned_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, achievement_id)
);

-- FUNCTIONS

-- Function to update the updated_at column
CREATE OR REPLACE FUNCTION public.update_updated_at_column() 
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Triggers for updated_at columns
CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON public.user_profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON public.user_settings FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_learning_progress_updated_at BEFORE UPDATE ON public.learning_progress FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_skill_progress_updated_at BEFORE UPDATE ON public.skill_progress FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_lessons_updated_at BEFORE UPDATE ON public.lessons FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_lesson_categories_updated_at BEFORE UPDATE ON public.lesson_categories FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_user_lesson_progress_updated_at BEFORE UPDATE ON public.user_lesson_progress FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_vocabulary_words_updated_at BEFORE UPDATE ON public.vocabulary_words FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_user_vocabulary_updated_at BEFORE UPDATE ON public.user_vocabulary FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_chat_sessions_updated_at BEFORE UPDATE ON public.chat_sessions FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE TRIGGER update_chat_bots_updated_at BEFORE UPDATE ON public.chat_bots FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Function to create user profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user() 
RETURNS TRIGGER AS $$
DECLARE
    temp_user_id UUID;
BEGIN
    temp_user_id := NEW.id;
    
    INSERT INTO public.user_profiles (id, personal_info, learning_profile, account_info, profile_stats)
    VALUES (
        temp_user_id,
        jsonb_build_object('firstName', '', 'lastName', '', 'email', NEW.email),
        jsonb_build_object('currentLevel', 'Beginner', 'weeklyGoalHours', 3),
        jsonb_build_object('subscriptionType', 'Free', 'subscriptionStatus', 'Active'),
        jsonb_build_object('totalStudyTime', 0, 'lessonsCompleted', 0, 'wordsLearned', 0)
    );
    
    INSERT INTO public.user_settings (user_id) VALUES (temp_user_id);
    INSERT INTO public.learning_progress (user_id) VALUES (temp_user_id);
    
    -- Initialize skill progress for all skill areas
    INSERT INTO public.skill_progress (user_id, skill_area)
    VALUES 
        (temp_user_id, 'vocabulary'),
        (temp_user_id, 'grammar'),
        (temp_user_id, 'speaking'),
        (temp_user_id, 'listening'),
        (temp_user_id, 'reading'),
        (temp_user_id, 'writing');
    
    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        RAISE LOG 'Error in handle_new_user(): %', SQLERRM;
        RAISE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to create user profile on signup
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Add sample data for immediate visibility
INSERT INTO public.lesson_categories (name, description, icon, color) VALUES 
('Vocabulary', 'Learn new words and phrases', '📚', '#3B82F6'),
('Grammar', 'Master grammar rules', '📝', '#10B981'),
('Pronunciation', 'Perfect your pronunciation', '🗣️', '#F59E0B'),
('Conversation', 'Practice real conversations', '💬', '#EF4444')
ON CONFLICT (name) DO NOTHING;

INSERT INTO public.vocabulary_words (word, definition, difficulty_level, category) VALUES 
('Hello', 'A greeting used when meeting someone', 'Beginner', 'Greetings'),
('Goodbye', 'A farewell expression', 'Beginner', 'Greetings'),
('Thank you', 'An expression of gratitude', 'Beginner', 'Politeness'),
('Please', 'Used to make a polite request', 'Beginner', 'Politeness')
ON CONFLICT (word) DO NOTHING;

INSERT INTO public.chat_bots (name, description, difficulty) VALUES 
('Emma', 'Friendly beginner tutor', 'Beginner'),
('James', 'Intermediate conversation partner', 'Intermediate'),
('Dr. Smith', 'Advanced academic discussions', 'Advanced')
ON CONFLICT (name) DO NOTHING;

INSERT INTO public.achievements (name, description, category, criteria) VALUES 
('First Steps', 'Complete your first lesson', 'Learning', '{"lessons_completed": 1}'),
('Word Master', 'Learn 100 vocabulary words', 'Vocabulary', '{"words_learned": 100}'),
('Streak Master', 'Maintain a 7-day learning streak', 'Consistency', '{"streak_days": 7}')
ON CONFLICT (name) DO NOTHING;

-- ROW LEVEL SECURITY (RLS) POLICIES
-- Note: RLS is enabled AFTER sample data insertion for better visibility

-- Enable RLS on user-specific tables
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.learning_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.skill_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_lesson_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_vocabulary ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;

-- User Profiles Policies
CREATE POLICY "Users can select own profile" ON public.user_profiles FOR SELECT TO authenticated USING ((SELECT auth.uid()) = id);
CREATE POLICY "Users can insert own profile" ON public.user_profiles FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = id);
CREATE POLICY "Users can update own profile" ON public.user_profiles FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = id) WITH CHECK ((SELECT auth.uid()) = id);
CREATE POLICY "Service role can insert any profile" ON public.user_profiles FOR INSERT TO service_role;

-- User Settings Policies
CREATE POLICY "Users can select own settings" ON public.user_settings FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own settings" ON public.user_settings FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can update own settings" ON public.user_settings FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Service role can insert any settings" ON public.user_settings FOR INSERT TO service_role;

-- Learning Progress Policies
CREATE POLICY "Users can select own learning progress" ON public.learning_progress FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own learning progress" ON public.learning_progress FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can update own learning progress" ON public.learning_progress FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Service role can insert any learning progress" ON public.learning_progress FOR INSERT TO service_role;

-- Skill Progress Policies
CREATE POLICY "Users can select own skill progress" ON public.skill_progress FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own skill progress" ON public.skill_progress FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can update own skill progress" ON public.skill_progress FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Service role can insert any skill progress" ON public.skill_progress FOR INSERT TO service_role;

-- User Lesson Progress Policies
CREATE POLICY "Users can select own lesson progress" ON public.user_lesson_progress FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own lesson progress" ON public.user_lesson_progress FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can update own lesson progress" ON public.user_lesson_progress FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);

-- User Vocabulary Policies
CREATE POLICY "Users can select own vocabulary" ON public.user_vocabulary FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own vocabulary" ON public.user_vocabulary FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can update own vocabulary" ON public.user_vocabulary FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);

-- Chat Sessions Policies
CREATE POLICY "Users can select own chat sessions" ON public.chat_sessions FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own chat sessions" ON public.chat_sessions FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can update own chat sessions" ON public.chat_sessions FOR UPDATE TO authenticated USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id);

-- Chat Messages Policies
CREATE POLICY "Users can select own chat messages" ON public.chat_messages FOR SELECT TO authenticated USING (
    EXISTS (SELECT 1 FROM public.chat_sessions WHERE public.chat_sessions.id = session_id AND (SELECT auth.uid()) = public.chat_sessions.user_id)
);
CREATE POLICY "Users can insert own chat messages" ON public.chat_messages FOR INSERT TO authenticated WITH CHECK (
    EXISTS (SELECT 1 FROM public.chat_sessions WHERE public.chat_sessions.id = session_id AND (SELECT auth.uid()) = public.chat_sessions.user_id)
);

-- User Achievements Policies
CREATE POLICY "Users can select own achievements" ON public.user_achievements FOR SELECT TO authenticated USING ((SELECT auth.uid()) = user_id);
CREATE POLICY "Users can insert own achievements" ON public.user_achievements FOR INSERT TO authenticated WITH CHECK ((SELECT auth.uid()) = user_id);

-- Public read access for shared content
CREATE POLICY "Public can read vocabulary words" ON public.vocabulary_words FOR SELECT TO authenticated;
CREATE POLICY "Public can read lessons" ON public.lessons FOR SELECT TO authenticated USING (is_published = true);
CREATE POLICY "Public can read lesson categories" ON public.lesson_categories FOR SELECT TO authenticated;
CREATE POLICY "Public can read chat bots" ON public.chat_bots FOR SELECT TO authenticated USING (is_active = true);
CREATE POLICY "Public can read achievements" ON public.achievements FOR SELECT TO authenticated USING (is_active = true);

-- Grant appropriate permissions
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.handle_new_user() TO postgres, service_role;
