-- Onboarding profile metadata for WordBridge
-- Adds lightweight profile table for conversational onboarding and AI persona data

CREATE TABLE IF NOT EXISTS public.profiles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    is_onboarded BOOLEAN DEFAULT FALSE,
    ai_profile JSONB,
    onboarding_state JSONB,
    current_step INTEGER DEFAULT 0,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Maintain updated_at column automatically
CREATE OR REPLACE FUNCTION public.update_profiles_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_profiles_updated_at
BEFORE UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.update_profiles_updated_at();

-- Allow authenticated users to manage their own onboarding profile
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can select their onboarding profile"
ON public.profiles FOR SELECT
TO authenticated
USING ((SELECT auth.uid()) = user_id);

CREATE POLICY "Users can insert their onboarding profile"
ON public.profiles FOR INSERT
TO authenticated
WITH CHECK ((SELECT auth.uid()) = user_id);

CREATE POLICY "Users can update their onboarding profile"
ON public.profiles FOR UPDATE
TO authenticated
USING ((SELECT auth.uid()) = user_id)
WITH CHECK ((SELECT auth.uid()) = user_id);

-- Service role access for server-side orchestration (optional)
CREATE POLICY "Service role can manage onboarding profiles"
ON public.profiles
FOR ALL
TO service_role
USING (TRUE)
WITH CHECK (TRUE);

