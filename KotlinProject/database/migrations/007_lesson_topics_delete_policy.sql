-- Add DELETE, INSERT, and UPDATE policies for lesson_topics table
-- This allows authenticated users to manage lesson topics (for admin app)

-- Allow authenticated users to insert lesson topics (for seeding/admin)
DROP POLICY IF EXISTS "Authenticated users can insert lesson topics" ON public.lesson_topics;
CREATE POLICY "Authenticated users can insert lesson topics"
    ON public.lesson_topics FOR INSERT
    TO authenticated
    WITH CHECK (true);

-- Allow authenticated users to update lesson topics (for admin)
DROP POLICY IF EXISTS "Authenticated users can update lesson topics" ON public.lesson_topics;
CREATE POLICY "Authenticated users can update lesson topics"
    ON public.lesson_topics FOR UPDATE
    TO authenticated
    USING (true)
    WITH CHECK (true);

-- Allow authenticated users to delete lesson topics (for admin)
DROP POLICY IF EXISTS "Authenticated users can delete lesson topics" ON public.lesson_topics;
CREATE POLICY "Authenticated users can delete lesson topics"
    ON public.lesson_topics FOR DELETE
    TO authenticated
    USING (true);

