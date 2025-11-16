-- Lesson Topics Storage for WordBridge
-- This migration creates lesson_topics table to store individual lesson topics
-- and lesson_topic_progress to track user progress on each topic

-- LESSON TOPICS TABLE
-- Stores individual lesson topics within a difficulty level (Beginner, Intermediate, Advanced)
CREATE TABLE IF NOT EXISTS public.lesson_topics (
    id TEXT PRIMARY KEY,                                           -- Unique identifier (e.g., "intro_chinese", "lesson_1_greetings")
    difficulty_level TEXT NOT NULL CHECK (difficulty_level IN ('Beginner', 'Intermediate', 'Advanced')),
    title TEXT NOT NULL,
    description TEXT,
    lesson_number INTEGER,                                         -- Lesson number within the difficulty level (null for intro topics)
    duration_minutes INTEGER,                                      -- Estimated duration in minutes
    sort_order INTEGER NOT NULL DEFAULT 0,                        -- Order within difficulty level
    is_locked BOOLEAN DEFAULT false,
    is_published BOOLEAN DEFAULT true,
    metadata JSONB DEFAULT '{}'::jsonb,                           -- Additional metadata for future use
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for fast queries
CREATE INDEX idx_lesson_topics_difficulty ON public.lesson_topics(difficulty_level);
CREATE INDEX idx_lesson_topics_sort_order ON public.lesson_topics(difficulty_level, sort_order);
CREATE INDEX idx_lesson_topics_published ON public.lesson_topics(is_published);

-- LESSON TOPIC PROGRESS TABLE
-- Tracks user progress on individual lesson topics
CREATE TABLE IF NOT EXISTS public.lesson_topic_progress (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    topic_id TEXT REFERENCES public.lesson_topics(id) NOT NULL,
    is_completed BOOLEAN DEFAULT false,
    time_spent INTEGER DEFAULT 0,                                 -- Time spent in minutes
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, topic_id)
);

-- Create indexes for fast queries
CREATE INDEX idx_lesson_topic_progress_user ON public.lesson_topic_progress(user_id);
CREATE INDEX idx_lesson_topic_progress_topic ON public.lesson_topic_progress(topic_id);
CREATE INDEX idx_lesson_topic_progress_completed ON public.lesson_topic_progress(user_id, is_completed);

-- Enable Row Level Security
ALTER TABLE public.lesson_topics ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lesson_topic_progress ENABLE ROW LEVEL SECURITY;

-- RLS Policies for lesson_topics (public read, admin write)
CREATE POLICY "Anyone can read published lesson topics"
    ON public.lesson_topics FOR SELECT
    TO authenticated
    USING (is_published = true);

CREATE POLICY "Anyone can read published lesson topics (anon)"
    ON public.lesson_topics FOR SELECT
    TO anon
    USING (is_published = true);

-- RLS Policies for lesson_topic_progress (users can only see/modify their own progress)
CREATE POLICY "Users can view their own lesson topic progress"
    ON public.lesson_topic_progress FOR SELECT
    TO authenticated
    USING ((SELECT auth.uid()) = user_id);

CREATE POLICY "Users can insert their own lesson topic progress"
    ON public.lesson_topic_progress FOR INSERT
    TO authenticated
    WITH CHECK ((SELECT auth.uid()) = user_id);

CREATE POLICY "Users can update their own lesson topic progress"
    ON public.lesson_topic_progress FOR UPDATE
    TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION public.update_lesson_topics_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_lesson_topics_updated_at
    BEFORE UPDATE ON public.lesson_topics
    FOR EACH ROW
    EXECUTE FUNCTION public.update_lesson_topics_updated_at();

CREATE OR REPLACE FUNCTION public.update_lesson_topic_progress_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_lesson_topic_progress_updated_at
    BEFORE UPDATE ON public.lesson_topic_progress
    FOR EACH ROW
    EXECUTE FUNCTION public.update_lesson_topic_progress_updated_at();

