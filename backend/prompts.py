from models import UserContext, MessageRole
from typing import List, Dict, Any


def build_system_prompt(user_context: UserContext, bot_id: str = None) -> str:
    prompt_parts = []
    
    # Base role definition with student name
    bot_personality = _get_bot_personality(bot_id)

    student_name = None
    if user_context.first_name:
        student_name = user_context.first_name
        if user_context.last_name:
            student_name = f"{user_context.first_name} {user_context.last_name}"
    
    if student_name:
        bot_personality += f"\n\nYou are currently tutoring **{student_name}**. Address them by their first name to create a warm, personal learning environment."
    
    prompt_parts.append(bot_personality)

    profile_parts = []
    
    if student_name:
        profile_parts.append(f"Student name: {student_name}")
    
    if user_context.native_language:
        profile_parts.append(f"Native language: {user_context.native_language}")
    
    if user_context.target_languages:
        target_langs = ", ".join(user_context.target_languages)
        profile_parts.append(f"Learning: {target_langs}")
    
    if user_context.current_level:
        profile_parts.append(f"Current level: {user_context.current_level}")
    
    if user_context.primary_goal:
        profile_parts.append(f"Primary goal: {user_context.primary_goal}")
    
    if profile_parts:
        prompt_parts.append("**Student Profile:**\n" + "\n".join(f"- {p}" for p in profile_parts))
    
    # === LEARNING PROGRESS ===
    if user_context.learning_progress:
        lp = user_context.learning_progress
        progress_insights = []
        progress_insights.append(f"Overall Level {lp.get('overall_level', 1)}")
        progress_insights.append(f"{lp.get('xp_points', 0)} total XP")
        
        if lp.get('streak_days', 0) > 0:
            progress_insights.append(f"Current streak: {lp['streak_days']} days 🔥")
            if lp.get('longest_streak', 0) > lp['streak_days']:
                progress_insights.append(f"(best: {lp['longest_streak']} days)")
        
        if lp.get('total_study_time', 0) > 0:
            hours = lp['total_study_time'] // 60
            progress_insights.append(f"Total practice: {hours} hours")
        
        prompt_parts.append("**Progress:**\n" + "\n".join(f"- {p}" for p in progress_insights))
    
    # === SKILL BREAKDOWN ===
    if user_context.skill_progress:
        skill_insights = []
        for skill in user_context.skill_progress:
            skill_name = skill.get('skill_area', '').title()
            skill_level = skill.get('level', 1)
            accuracy = skill.get('accuracy_percentage', 0)
            
            if accuracy > 0:
                skill_insights.append(f"{skill_name}: Level {skill_level} ({accuracy:.0f}% accuracy)")
            else:
                skill_insights.append(f"{skill_name}: Level {skill_level}")
        
        if skill_insights:
            prompt_parts.append("**Skill Levels:**\n" + "\n".join(f"- {s}" for s in skill_insights))
    
    # === VOCABULARY STATUS ===
    if user_context.vocabulary_stats:
        vocab = user_context.vocabulary_stats
        total = vocab.get('total_words', 0)
        mastered = vocab.get('mastered_words', 0)
        learning = vocab.get('learning_words', 0)
        
        if total > 0:
            vocab_insight = f"Vocabulary: {total} words ({mastered} mastered, {learning} learning)"
            if vocab.get('average_correct_rate', 0) > 0:
                acc = vocab['average_correct_rate'] * 100
                vocab_insight += f" - {acc:.0f}% retention rate"
            prompt_parts.append(vocab_insight)
            
            # Include actual vocabulary words
            words = vocab.get('words', [])
            if words:
                vocab_list = ["**Current Vocabulary Words:**"]
                for word_data in words[:20]:  # Limit to the first 20 words to avoid too long prompts
                    word = word_data.get('word', '')
                    definition = word_data.get('definition', '')
                    status = word_data.get('status', 'new')
                    category = word_data.get('category', '')
                    
                    vocab_list.append(f"- {word} ({status}) - {definition} [{category}]")
                
                if len(words) > 20:
                    vocab_list.append(f"... and {len(words) - 20} more words")
                
                prompt_parts.append("\n".join(vocab_list))
    
    # === LESSON PROGRESS ===
    if user_context.lesson_progress:
        lessons = user_context.lesson_progress
        completed = lessons.get('completed_lessons', 0)
        in_progress = lessons.get('in_progress_lessons', 0)
        avg_score = lessons.get('average_score', 0)
        
        if completed > 0 or in_progress > 0:
            lesson_insight = f"Lessons: {completed} completed"
            if in_progress > 0:
                lesson_insight += f", {in_progress} in progress"
            if avg_score > 0:
                lesson_insight += f" (avg score: {avg_score:.0f}%)"
            prompt_parts.append(lesson_insight)
    
    # === PREVIOUS CHAT EXPERIENCE ===
    if user_context.chat_history:
        chat = user_context.chat_history
        sessions = chat.get('total_sessions', 0)
        messages = chat.get('total_messages', 0)
        
        if sessions > 0:
            chat_insight = f"Chat Experience: {sessions} previous sessions, {messages} messages"
            prompt_parts.append(chat_insight)
    
    # === LEARNING PREFERENCES ===
    pref_parts = []
    if user_context.learning_style:
        pref_parts.append(f"Preferred style: {user_context.learning_style}")
    
    if user_context.focus_areas:
        focus = ", ".join(user_context.focus_areas)
        pref_parts.append(f"Focus areas: {focus}")
    
    if user_context.motivations:
        motivations = ", ".join(user_context.motivations)
        pref_parts.append(f"Motivations: {motivations}")
    
    if pref_parts:
        prompt_parts.append("**Learning Preferences:**\n" + "\n".join(f"- {p}" for p in pref_parts))
    
    # === INTERESTS FOR ENGAGEMENT ===
    if user_context.interests:
        interests = ", ".join(user_context.interests)
        prompt_parts.append(
            f"**Topics of Interest:** {interests}\n"
            f"Use these topics to make conversations engaging and relatable."
        )
    
    # === AI PROFILE INSIGHTS ===
    if user_context.ai_profile:
        ai_insights = _extract_ai_profile_insights(user_context.ai_profile)
        if ai_insights:
            prompt_parts.append(f"**Additional Context:** {ai_insights}")
    
    # === PERSONALITY PREFERENCES ===
    if user_context.personality_preferences:
        personality = _extract_personality_preferences(user_context.personality_preferences)
        if personality:
            prompt_parts.append(f"**Communication Style:** {personality}")
    
    # === TEACHING APPROACH ===
    teaching_approach = """
**Your Teaching Approach:**
- Adapt to their specific level and progress
- Reference their strengths and areas for improvement
- Connect lessons to their interests and goals
- Acknowledge their streak and progress when appropriate
- Provide corrections gently and constructively
- Celebrate milestones and achievements
- Keep them motivated and engaged
- Ask follow-up questions to deepen understanding
"""
    
    # Add specific recommendations based on data
    recommendations = []
    
    if user_context.learning_progress:
        lp = user_context.learning_progress
        if lp.get('streak_days', 0) >= 3:
            recommendations.append("Encourage their consistency streak")
        if lp.get('weekly_xp', 0) > lp.get('xp_points', 0) * 0.3:
            recommendations.append("Praise their recent activity")
    
    if user_context.vocabulary_stats:
        vocab = user_context.vocabulary_stats
        if vocab.get('mastered_words', 0) >= 50:
            recommendations.append("Acknowledge their vocabulary progress")
        if vocab.get('learning_words', 0) > vocab.get('reviewing_words', 0):
            recommendations.append("Help reinforce newer vocabulary")
    
    if recommendations:
        teaching_approach += "\n**Today's Focus:**\n" + "\n".join(f"- {r}" for r in recommendations)
    
    prompt_parts.append(teaching_approach)
    
    return "\n\n".join(prompt_parts)


def _get_bot_personality(bot_id: str = None) -> str:
    """Returns the personality description for the specified bot."""
    personalities = {
        "emma": (
            "You are Emma, a friendly and encouraging language tutor who specializes in daily conversation, "
            "grammar basics, and pronunciation. Your personality is warm, patient, and supportive. "
            "You focus on making learning fun and accessible for beginners."
        ),
        "james": (
            "You are James, a professional business English specialist. You focus on business communication, "
            "presentations, and professional writing. Your approach is structured and formal, "
            "helping intermediate learners develop workplace language skills."
        ),
        "sophia": (
            "You are Sophia, an intellectual and engaging tutor for advanced learners. "
            "You love discussing cultural topics, advanced conversation, and idiomatic expressions. "
            "Your teaching style is thought-provoking and culturally rich."
        ),
        "alex": (
            "You are Alex, a speaking practice specialist focused on pronunciation and accent training. "
            "You are supportive, detail-oriented, and provide specific feedback on pronunciation. "
            "You work with all levels and adapt to each student's needs."
        ),
    }
    
    if bot_id and bot_id.lower() in personalities:
        return personalities[bot_id.lower()]
    
    # Default personality
    return (
        "You are an AI language tutor designed to help students improve their language skills. "
        "You are knowledgeable, patient, and adaptive to each student's needs."
    )


def _extract_ai_profile_insights(ai_profile: Dict[str, Any]) -> str:
    insights = []

    if "learning_pace" in ai_profile:
        pace = ai_profile["learning_pace"]
        if isinstance(pace, dict) and "value" in pace:
            insights.append(f"They prefer a {pace['value']} learning pace.")
    
    if "interaction_style" in ai_profile:
        style = ai_profile["interaction_style"]
        if isinstance(style, dict) and "value" in style:
            insights.append(f"They prefer an {style['value']} interaction style.")
    
    if "feedback_preference" in ai_profile:
        feedback = ai_profile["feedback_preference"]
        if isinstance(feedback, dict) and "value" in feedback:
            insights.append(f"They prefer {feedback['value']} feedback.")
    
    if "conversation_topics" in ai_profile:
        topics = ai_profile.get("conversation_topics", {})
        if isinstance(topics, dict) and "values" in topics:
            topic_list = ", ".join(topics["values"])
            insights.append(f"They enjoy discussing: {topic_list}.")
    
    return " ".join(insights) if insights else ""


def _extract_personality_preferences(personality: Dict[str, Any]) -> str:
    prefs = []
    
    if "tone" in personality:
        tone = personality["tone"]
        if isinstance(tone, str):
            prefs.append(f"Use a {tone} tone in your responses.")
    
    if "formality" in personality:
        formality = personality["formality"]
        if isinstance(formality, str):
            prefs.append(f"Maintain a {formality} level of formality.")
    
    if "humor" in personality:
        humor = personality["humor"]
        if isinstance(humor, bool) and humor:
            prefs.append("Feel free to use appropriate humor.")
    
    return " ".join(prefs) if prefs else ""


def format_conversation_history(
    history: List[Dict[str, Any]], 
    max_messages: int = 10
) -> List[Dict[str, str]]:
    recent_history = history[-max_messages:] if len(history) > max_messages else history
    
    formatted = []
    for msg in recent_history:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        
        # Skip empty messages
        if not content or not content.strip():
            continue
            
        # Map system messages to user
        if role == "system":
            role = "user"
            
        formatted.append({
            "role": role,
            "content": content
        })
    
    return formatted

