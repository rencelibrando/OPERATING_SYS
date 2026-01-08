-- Migration: Voice Recordings Storage Bucket Policies
-- Created: 2026-01-09
-- 
-- NOTE: This migration CANNOT be applied automatically via supabase migrations.
-- Storage policies require owner privileges on storage.objects table.
-- 
-- TO APPLY: Run this SQL manually in the Supabase Dashboard SQL Editor
-- 
-- Issue: Storage bucket 'voice-recordings' has RLS enabled but no policies,
-- causing all uploads to fail with "new row violates row-level security policy"

-- ============================================================================
-- STORAGE POLICIES FOR voice-recordings BUCKET
-- ============================================================================

-- Policy: Allow authenticated users to upload recordings
CREATE POLICY "Users can upload voice recordings" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'voice-recordings');

-- Policy: Allow authenticated users to view recordings
CREATE POLICY "Users can view voice recordings" ON storage.objects
    FOR SELECT TO authenticated
    USING (bucket_id = 'voice-recordings');

-- Policy: Allow authenticated users to update their recordings
CREATE POLICY "Users can update voice recordings" ON storage.objects
    FOR UPDATE TO authenticated
    USING (bucket_id = 'voice-recordings');

-- Policy: Allow authenticated users to delete their recordings
CREATE POLICY "Users can delete voice recordings" ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'voice-recordings');

-- Policy: Allow public read access for playback URLs
CREATE POLICY "Public read voice recordings" ON storage.objects
    FOR SELECT TO public
    USING (bucket_id = 'voice-recordings');

-- ============================================================================
-- VERIFICATION QUERY (run after applying policies)
-- ============================================================================
-- SELECT policyname, cmd FROM pg_policies 
-- WHERE schemaname = 'storage' AND tablename = 'objects';
