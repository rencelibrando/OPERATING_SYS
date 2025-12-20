-- Populate lesson_topics table from lesson_spanish table
-- This migration extracts unique lesson topics from the lesson_spanish table
-- and creates corresponding entries in the lesson_topics table for Spanish language

-- Function to format topic names (capitalize and replace underscores)
CREATE OR REPLACE FUNCTION format_topic_name(topic_text TEXT)
RETURNS TEXT AS $$
BEGIN
    -- Replace underscores with spaces and capitalize each word
    RETURN INITCAP(REPLACE(topic_text, '_', ' '));
END;
$$ LANGUAGE plpgsql;

-- Insert Spanish lesson topics from lesson_spanish table
INSERT INTO public.lesson_topics (
    id,
    difficulty_level,
    language,
    title,
    description,
    lesson_number,
    duration_minutes,
    sort_order,
    is_locked,
    is_published
)
SELECT 
    -- Create unique ID: spanish_{level}_{lesson}_{topic}
    'spanish_' || LOWER(TRIM(level)) || '_' || lesson::TEXT || '_' || topic AS id,
    
    -- Map level to proper case (Beginner, Intermediate, Advanced)
    -- Handle both 'advanced' (lowercase) and 'Advanced' (capitalized) from CSV
    CASE 
        WHEN LOWER(TRIM(level)) = 'beginner' THEN 'Beginner'
        WHEN LOWER(TRIM(level)) = 'intermediate' THEN 'Intermediate'
        WHEN LOWER(TRIM(level)) = 'advanced' THEN 'Advanced'
        ELSE INITCAP(TRIM(level))
    END AS difficulty_level,
    
    -- Set language to Spanish
    'Spanish' AS language,
    
    -- Format topic name (capitalize and replace underscores)
    format_topic_name(topic) AS title,
    
    -- Create description based on topic
    'Learn ' || format_topic_name(topic) || ' in Spanish' AS description,
    
    -- Lesson number
    lesson AS lesson_number,
    
    -- Estimated duration (default 15 minutes per lesson)
    15 AS duration_minutes,
    
    -- Sort order: (level_order * 1000) + lesson_number
    -- This ensures Beginner lessons come before Intermediate, etc.
    CASE 
        WHEN LOWER(TRIM(level)) = 'beginner' THEN (0 * 1000) + lesson
        WHEN LOWER(TRIM(level)) = 'intermediate' THEN (1 * 1000) + lesson
        WHEN LOWER(TRIM(level)) = 'advanced' THEN (2 * 1000) + lesson
        ELSE lesson
    END AS sort_order,
    
    -- Not locked by default
    false AS is_locked,
    
    -- Published by default
    true AS is_published
    
FROM public.lesson_spanish
WHERE level IS NOT NULL 
  AND lesson IS NOT NULL 
  AND topic IS NOT NULL
  -- Avoid duplicates (in case migration is run multiple times)
  AND NOT EXISTS (
      SELECT 1 
      FROM public.lesson_topics lt 
      WHERE lt.id = 'spanish_' || LOWER(TRIM(lesson_spanish.level)) || '_' || lesson_spanish.lesson::TEXT || '_' || lesson_spanish.topic
        AND lt.language = 'Spanish'
  )
GROUP BY level, lesson, topic
ORDER BY 
    CASE 
        WHEN LOWER(TRIM(level)) = 'beginner' THEN 0
        WHEN LOWER(TRIM(level)) = 'intermediate' THEN 1
        WHEN LOWER(TRIM(level)) = 'advanced' THEN 2
        ELSE 3
    END,
    lesson,
    topic;

-- Drop the helper function (optional, can keep it for future use)
-- DROP FUNCTION IF EXISTS format_topic_name(TEXT);

-- Verify the insert
DO $$
DECLARE
    inserted_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO inserted_count
    FROM public.lesson_topics
    WHERE language = 'Spanish';
    
    RAISE NOTICE 'Successfully inserted % Spanish lesson topics', inserted_count;
END $$;

