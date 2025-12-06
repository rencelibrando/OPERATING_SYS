-- Migration: Add active learning language to profiles table
-- This migration adds explicit tracking of the user's currently active learning language
-- This is OPTIONAL - the app already persists language preference via user metadata (targetLanguages)
-- This column provides direct database access for analytics, admin queries, and future features

-- Add active_learning_language column to profiles table
ALTER TABLE public.profiles 
ADD COLUMN IF NOT EXISTS active_learning_language TEXT 
CHECK (active_learning_language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish'));

-- Add default value for existing users (uses Chinese as fallback)
-- New users will have this set when they change language or complete onboarding

-- Create an index for efficient language-based queries
CREATE INDEX IF NOT EXISTS idx_profiles_active_language 
ON public.profiles(active_learning_language);

-- Add a comment for documentation
COMMENT ON COLUMN public.profiles.active_learning_language IS 
'The user''s currently active learning language for lessons. Updated when user switches language in the Lessons tab.';

-- Create a function to sync active_learning_language from user metadata (optional trigger)
-- This can be used if you want to keep the column in sync with user metadata
CREATE OR REPLACE FUNCTION public.sync_active_learning_language()
RETURNS TRIGGER AS $$
DECLARE
    target_languages TEXT;
    first_language TEXT;
BEGIN
    -- Get target_languages from user metadata
    SELECT raw_user_meta_data->>'target_languages' INTO target_languages
    FROM auth.users
    WHERE id = NEW.user_id;
    
    -- Extract first language from comma-separated list
    IF target_languages IS NOT NULL AND target_languages != '' THEN
        first_language := SPLIT_PART(target_languages, ',', 1);
        first_language := TRIM(first_language);
        
        -- Update if it's a valid language
        IF first_language IN ('Korean', 'Chinese', 'French', 'German', 'Spanish') THEN
            NEW.active_learning_language := first_language;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Note: The trigger below is commented out by default
-- Uncomment if you want automatic sync from user metadata

-- CREATE TRIGGER trg_sync_active_language
--     BEFORE INSERT OR UPDATE ON public.profiles
--     FOR EACH ROW
--     EXECUTE FUNCTION public.sync_active_learning_language();

-- Helpful view for admin dashboard to see language distribution
CREATE OR REPLACE VIEW public.language_distribution AS
SELECT 
    COALESCE(active_learning_language, 'Not Set') as language,
    COUNT(*) as user_count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) as percentage
FROM public.profiles
WHERE is_onboarded = true
GROUP BY active_learning_language
ORDER BY user_count DESC;

-- Grant access to authenticated users (they can only see aggregate, not individual data)
GRANT SELECT ON public.language_distribution TO authenticated;

