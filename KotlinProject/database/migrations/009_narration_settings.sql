-- Narration Settings for Lesson Content
-- Adds enable_narration flags and language override fields to support
-- FastText auto-detection with Edge TTS narration

-- Add narration settings to lessons table
ALTER TABLE public.lessons 
ADD COLUMN IF NOT EXISTS enable_lesson_narration BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS narration_language TEXT,
ADD COLUMN IF NOT EXISTS narration_voice TEXT;

COMMENT ON COLUMN public.lessons.enable_lesson_narration IS 
'Enable/disable narration for the entire lesson. Affects all questions and answers.';

COMMENT ON COLUMN public.lessons.narration_language IS 
'Optional override for language detection. If null, FastText will auto-detect. Supported: ko (Korean), de (German), zh (Chinese), es (Spanish), fr (French), en (English)';

COMMENT ON COLUMN public.lessons.narration_voice IS 
'Optional override for Edge TTS voice. If null, uses default mapping for detected language.';

-- Add narration settings to questions table
ALTER TABLE public.questions
ADD COLUMN IF NOT EXISTS enable_question_narration BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS enable_answer_narration BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS narration_language TEXT,
ADD COLUMN IF NOT EXISTS narration_voice TEXT;

COMMENT ON COLUMN public.questions.enable_question_narration IS 
'Enable/disable narration for the question text. Inherits from lesson if lesson narration is disabled.';

COMMENT ON COLUMN public.questions.enable_answer_narration IS 
'Enable/disable narration for the answer text. Inherits from lesson if lesson narration is disabled.';

COMMENT ON COLUMN public.questions.narration_language IS 
'Optional language override for this specific question. Overrides lesson setting if provided.';

COMMENT ON COLUMN public.questions.narration_voice IS 
'Optional voice override for this specific question. Overrides lesson setting if provided.';

-- Create index for faster queries when filtering by narration settings
CREATE INDEX IF NOT EXISTS idx_lessons_narration ON public.lessons(enable_lesson_narration) WHERE enable_lesson_narration = true;
CREATE INDEX IF NOT EXISTS idx_questions_narration ON public.questions(enable_question_narration, enable_answer_narration);

-- Note: question_audio_url and answer_audio_url columns already exist in the questions table
-- They will be populated automatically by the narration service
