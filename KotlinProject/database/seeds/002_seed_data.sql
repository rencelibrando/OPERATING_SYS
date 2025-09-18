-- WordBridge Seed Data
-- Initial data for lessons, vocabulary, chat bots, and achievements


-- CHAT BOTS


INSERT INTO chat_bots (id, name, description, avatar, personality, specialties, difficulty) VALUES
('emma', 'Emma', 'Friendly conversation partner for everyday topics', '👩‍🏫', 'Encouraging and patient', '["Daily Conversation", "Grammar Basics", "Pronunciation"]'::jsonb, 'beginner'),
('james', 'James', 'Business English specialist for professional communication', '👨‍💼', 'Professional and structured', '["Business English", "Presentations", "Email Writing"]'::jsonb, 'intermediate'),
('sophia', 'Sophia', 'Advanced conversation partner for cultural topics', '👩‍🎓', 'Intellectual and engaging', '["Advanced Conversation", "Cultural Topics", "Idioms"]'::jsonb, 'advanced'),
('alex', 'Alex', 'Speaking practice specialist with pronunciation focus', '🗣️', 'Supportive and detailed', '["Pronunciation", "Speaking Practice", "Accent Training"]'::jsonb, 'beginner');


-- LESSONS


INSERT INTO lessons (id, title, category, difficulty, duration, lessons_count, icon, content) VALUES
(uuid_generate_v4(), 'Grammar Mastery', 'grammar', 'intermediate', 15, 12, '📚', '{"description": "Master essential grammar rules", "topics": ["Present Perfect", "Past Continuous", "Future Tense"]}'::jsonb),
(uuid_generate_v4(), 'Vocabulary Builder', 'vocabulary', 'intermediate', 20, 15, '📖', '{"description": "Expand your vocabulary", "topics": ["Business Terms", "Daily Life", "Academic Words"]}'::jsonb),
(uuid_generate_v4(), 'Conversation Skills', 'conversation', 'intermediate', 18, 10, '💬', '{"description": "Improve speaking abilities", "topics": ["Small Talk", "Formal Conversations", "Negotiations"]}'::jsonb),
(uuid_generate_v4(), 'Pronunciation Guide', 'pronunciation', 'intermediate', 12, 8, '🎙️', '{"description": "Perfect your pronunciation", "topics": ["Vowel Sounds", "Consonants", "Intonation"]}'::jsonb),
(uuid_generate_v4(), 'Basic Grammar', 'grammar', 'beginner', 10, 8, '📝', '{"description": "Learn grammar fundamentals", "topics": ["Present Simple", "Articles", "Plurals"]}'::jsonb),
(uuid_generate_v4(), 'Essential Vocabulary', 'vocabulary', 'beginner', 15, 10, '📚', '{"description": "Learn essential words", "topics": ["Numbers", "Colors", "Family"]}'::jsonb),
(uuid_generate_v4(), 'Advanced Grammar', 'grammar', 'advanced', 25, 15, '🎓', '{"description": "Master complex grammar", "topics": ["Subjunctive Mood", "Complex Conditionals", "Advanced Tenses"]}'::jsonb),
(uuid_generate_v4(), 'Professional Vocabulary', 'vocabulary', 'advanced', 22, 12, '💼', '{"description": "Business and professional terms", "topics": ["Finance", "Technology", "Management"]}'::jsonb);


-- VOCABULARY WORDS


INSERT INTO vocabulary_words (word, definition, pronunciation, category, difficulty, examples) VALUES
('Hello', 'A greeting used when meeting someone', 'həˈləʊ', 'Greetings', 'beginner', '["Hello, how are you?", "Hello there!", "Say hello to your family"]'::jsonb),
('Goodbye', 'A farewell expression', 'ɡʊdˈbaɪ', 'Greetings', 'beginner', '["Goodbye, see you later", "It''s time to say goodbye", "Goodbye for now"]'::jsonb),
('Thank you', 'An expression of gratitude', 'θæŋk juː', 'Politeness', 'beginner', '["Thank you for your help", "Thank you very much", "I want to thank you"]'::jsonb),
('Please', 'Used to make a polite request', 'pliːz', 'Politeness', 'beginner', '["Please help me", "Could you please come here?", "Please be quiet"]'::jsonb),
('Beautiful', 'Pleasing to the senses or mind', 'ˈbjuːtɪf(ə)l', 'Adjectives', 'intermediate', '["What a beautiful day!", "She is beautiful", "The sunset is beautiful"]'::jsonb),
('Important', 'Of great significance or value', 'ɪmˈpɔːt(ə)nt', 'Adjectives', 'intermediate', '["This is very important", "An important meeting", "It''s important to study"]'::jsonb),
('Opportunity', 'A set of circumstances making it possible to do something', 'ˌɒpəˈtjuːnɪti', 'Business', 'advanced', '["A great opportunity", "Business opportunity", "Don''t miss this opportunity"]'::jsonb),
('Entrepreneur', 'A person who starts a business', 'ˌɒntrəprəˈnɜː', 'Business', 'advanced', '["She is a successful entrepreneur", "Young entrepreneur", "Entrepreneur mindset"]'::jsonb),
('Innovation', 'The introduction of new ideas or methods', 'ˌɪnəˈveɪʃ(ə)n', 'Business', 'advanced', '["Technological innovation", "Innovation in education", "Culture of innovation"]'::jsonb),
('Collaborate', 'Work jointly on an activity', 'kəˈlabəreɪt', 'Business', 'intermediate', '["Let''s collaborate on this project", "Collaborate with colleagues", "International collaboration"]'::jsonb);


-- ACHIEVEMENTS


INSERT INTO achievements (title, description, icon, category, xp_reward, requirements) VALUES
('First Steps', 'Complete your first lesson', '🎯', 'Learning', 50, '{"type": "lessons_completed", "count": 1}'::jsonb),
('Vocabulary Explorer', 'Learn 10 new words', '📚', 'Vocabulary', 100, '{"type": "words_learned", "count": 10}'::jsonb),
('Chat Enthusiast', 'Have 5 conversations with AI tutors', '💬', 'AI Chat', 75, '{"type": "chat_sessions", "count": 5}'::jsonb),
('Streak Master', 'Maintain a 7-day learning streak', '🔥', 'Consistency', 200, '{"type": "streak_days", "count": 7}'::jsonb),
('Grammar Guru', 'Complete all grammar lessons', '📝', 'Learning', 300, '{"type": "category_completed", "category": "grammar"}'::jsonb),
('Pronunciation Pro', 'Master pronunciation lessons', '🎙️', 'Learning', 250, '{"type": "category_completed", "category": "pronunciation"}'::jsonb),
('Conversation Master', 'Complete 50 chat sessions', '🗣️', 'AI Chat', 500, '{"type": "chat_sessions", "count": 50}'::jsonb),
('Vocabulary Virtuoso', 'Learn 100 vocabulary words', '🎓', 'Vocabulary', 400, '{"type": "words_learned", "count": 100}'::jsonb),
('Dedication Award', 'Study for 30 days straight', '⭐', 'Consistency', 1000, '{"type": "streak_days", "count": 30}'::jsonb),
('Speed Learner', 'Complete 3 lessons in one day', '⚡', 'Learning', 150, '{"type": "lessons_per_day", "count": 3}'::jsonb);


-- SAMPLE LESSON CONTENT


-- Update lessons with more detailed content
UPDATE lessons SET content = '{"description": "Master essential grammar rules for intermediate learners", "topics": ["Present Perfect Tense", "Past Continuous", "Future Tense", "Conditional Sentences"], "exercises": [{"type": "multiple_choice", "question": "Choose the correct form: I ___ lived here for 5 years.", "options": ["have", "has", "had", "am"], "answer": "have"}]}'::jsonb WHERE title = 'Grammar Mastery';

UPDATE lessons SET content = '{"description": "Expand your vocabulary with essential words", "topics": ["Business Terminology", "Daily Life Vocabulary", "Academic Words", "Phrasal Verbs"], "word_count": 150, "difficulty_level": "intermediate"}'::jsonb WHERE title = 'Vocabulary Builder';

UPDATE lessons SET content = '{"description": "Improve your speaking and conversation skills", "topics": ["Small Talk Strategies", "Formal Conversations", "Negotiations", "Presentations"], "practice_dialogues": 10, "speaking_exercises": 15}'::jsonb WHERE title = 'Conversation Skills';

UPDATE lessons SET content = '{"description": "Perfect your English pronunciation", "topics": ["Vowel Sounds", "Consonant Clusters", "Word Stress", "Intonation Patterns"], "audio_exercises": 20, "phonetic_practice": true}'::jsonb WHERE title = 'Pronunciation Guide';


-- FUNCTION TO GET RANDOM VOCABULARY FOR PRACTICE


CREATE OR REPLACE FUNCTION get_vocabulary_for_practice(
    user_id_param UUID,
    difficulty_level TEXT DEFAULT NULL,
    word_count INTEGER DEFAULT 10
)
RETURNS TABLE (
    word_id UUID,
    word TEXT,
    definition TEXT,
    pronunciation TEXT,
    category TEXT,
    examples JSONB,
    user_status TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        vw.id,
        vw.word,
        vw.definition,
        vw.pronunciation,
        vw.category,
        vw.examples,
        COALESCE(uv.status, 'new') as user_status
    FROM vocabulary_words vw
    LEFT JOIN user_vocabulary uv ON vw.id = uv.word_id AND uv.user_id = user_id_param
    WHERE (difficulty_level IS NULL OR vw.difficulty = difficulty_level)
    ORDER BY RANDOM()
    LIMIT word_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- FUNCTION TO UPDATE USER PROGRESS


CREATE OR REPLACE FUNCTION update_user_progress(
    user_id_param UUID,
    xp_gained INTEGER,
    skill_area_param TEXT DEFAULT NULL,
    lesson_completed BOOLEAN DEFAULT FALSE
)
RETURNS VOID AS $$
BEGIN
    -- Update overall learning progress
    UPDATE learning_progress 
    SET 
        xp_points = xp_points + xp_gained,
        weekly_xp = weekly_xp + xp_gained,
        monthly_xp = monthly_xp + xp_gained,
        updated_at = NOW()
    WHERE user_id = user_id_param;
    
    -- Update skill-specific progress if specified
    IF skill_area_param IS NOT NULL THEN
        UPDATE skill_progress 
        SET 
            xp_points = xp_points + xp_gained,
            updated_at = NOW()
        WHERE user_id = user_id_param AND skill_area = skill_area_param;
        
        -- Level up logic (every 100 XP = 1 level)
        UPDATE skill_progress 
        SET 
            level = (xp_points / 100) + 1,
            max_xp = ((xp_points / 100) + 1) * 100
        WHERE user_id = user_id_param AND skill_area = skill_area_param;
    END IF;
    
    -- Update profile stats if lesson completed
    IF lesson_completed THEN
        UPDATE user_profiles 
        SET 
            profile_stats = jsonb_set(
                profile_stats, 
                '{lessonsCompleted}', 
                ((profile_stats->>'lessonsCompleted')::int + 1)::text::jsonb
            ),
            updated_at = NOW()
        WHERE id = user_id_param;
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
