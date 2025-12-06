-- Add language column to lesson_topics table
-- This migration adds language support for multiple languages

-- Add language column
ALTER TABLE public.lesson_topics 
ADD COLUMN IF NOT EXISTS language TEXT CHECK (language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish'));

-- Update existing records to default to Chinese (for backward compatibility)
UPDATE public.lesson_topics 
SET language = 'Chinese' 
WHERE language IS NULL;

-- Make language NOT NULL after setting defaults
ALTER TABLE public.lesson_topics 
ALTER COLUMN language SET NOT NULL;

-- Create index for language + difficulty queries
CREATE INDEX IF NOT EXISTS idx_lesson_topics_language_difficulty 
ON public.lesson_topics(language, difficulty_level);

-- Create index for language + difficulty + sort_order
CREATE INDEX IF NOT EXISTS idx_lesson_topics_language_difficulty_sort 
ON public.lesson_topics(language, difficulty_level, sort_order);

