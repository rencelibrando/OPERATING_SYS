-- Add RLS policies for lesson_spanish table
-- This table contains public lesson content, so authenticated users should be able to read it

-- Enable RLS on lesson_spanish table
ALTER TABLE public.lesson_spanish ENABLE ROW LEVEL SECURITY;

-- Allow authenticated users to read all lesson_spanish content
CREATE POLICY "Authenticated users can read lesson_spanish"
    ON public.lesson_spanish
    FOR SELECT
    TO authenticated
    USING (true);

-- Allow anonymous users to read lesson_spanish content (optional, if you want unauthenticated access)
-- Uncomment if needed:
-- CREATE POLICY "Anonymous users can read lesson_spanish"
--     ON public.lesson_spanish
--     FOR SELECT
--     TO anon
--     USING (true);







