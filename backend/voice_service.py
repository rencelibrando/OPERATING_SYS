"""
Voice Tutor service using Deepgram API for speech-to-text and AI feedback.
"""
import os
import json
import uuid
from datetime import datetime
from typing import Dict, Any, List, Optional

# Try to import Deepgram, but handle gracefully if not available
from providers.deepseek import DeepSeekProvider
from providers.gemini import GeminiProvider
from supabase_client import SupabaseManager
from voice_models import (
    VoiceTranscribeRequest,
    VoiceTranscribeResponse,
    VoiceFeedbackRequest,
    VoiceFeedbackResponse,
    VoiceSessionSaveRequest,
    VoiceSessionSaveResponse,
    VoiceProgressRequest,
    VoiceProgressResponse,
    VoiceLanguage,
    VoiceLevel,
    VoiceScenario
)
from config import settings


class VoiceService:
    def __init__(self):
        
        # Initialize AI providers - DeepSeek as primary, Gemini as fallback
        self.deepseek_provider = DeepSeekProvider()
        self.gemini_provider = GeminiProvider()
        
        # Initialize Supabase manager
        self.supabase_manager = SupabaseManager()
        
        # Language mapping (for compatibility - not used with local Whisper)
        self.language_mapping = {
            VoiceLanguage.FRENCH: "fr",
            VoiceLanguage.GERMAN: "de",
            VoiceLanguage.KOREAN: "ko",
            VoiceLanguage.MANDARIN: "zh",
            VoiceLanguage.SPANISH: "es"
        }
        
        # Scenario prompts for different languages and levels
        self.scenario_prompts = self._load_scenario_prompts()
    
    def _load_scenario_prompts(self) -> Dict[str, Dict[str, List[str]]]:
        """Load scenario prompts for different languages and levels."""
        return {
            "travel": {
                "beginner": [
                    "Where is the nearest hotel?",
                    "How much does this cost?",
                    "I would like to order coffee",
                    "Can you help me find the train station?",
                    "Do you speak English?"
                ],
                "intermediate": [
                    "Could you recommend a good local restaurant?",
                    "I need to book a room for two nights",
                    "What time does the museum close?",
                    "Is there a pharmacy nearby?",
                    "I'd like to buy a ticket to the city center"
                ],
                "advanced": [
                    "What are the must-see attractions for a first-time visitor?",
                    "Could you explain the local customs I should be aware of?",
                    "I'm looking for authentic local experiences off the beaten path",
                    "What's the best way to navigate the public transportation system?",
                    "Can you suggest some day trips from the city?"
                ]
            },
            "food": {
                "beginner": [
                    "I would like water",
                    "This is delicious",
                    "The bill please",
                    "Do you have a menu in English?",
                    "I'm allergic to nuts"
                ],
                "intermediate": [
                    "What is today's special?",
                    "Can I have the recipe for this dish?",
                    "Is this dish traditionally spicy?",
                    "What wine would you recommend?",
                    "I'd like to make a reservation for dinner"
                ],
                "advanced": [
                    "Could you tell me about the regional cuisine?",
                    "What cooking techniques are used in this dish?",
                    "How has this dish evolved over time?",
                    "What are the key ingredients in traditional cooking?",
                    "Can you explain the cultural significance of this meal?"
                ]
            },
            "daily_conversation": {
                "beginner": [
                    "Hello, how are you?",
                    "My name is...",
                    "Nice to meet you",
                    "See you later",
                    "Thank you very much"
                ],
                "intermediate": [
                    "What did you do today?",
                    "How was your weekend?",
                    "I'm planning to visit the cinema",
                    "What's the weather like today?",
                    "I need to go to the post office"
                ],
                "advanced": [
                    "What's your opinion on current events?",
                    "I've been thinking about career development",
                    "Let's discuss the pros and cons of remote work",
                    "How do you stay motivated during challenging times?",
                    "What are your thoughts on work-life balance?"
                ]
            },
            "work": {
                "beginner": [
                    "I have a meeting",
                    "Please send me an email",
                    "What time is the appointment?",
                    "I need to make a phone call",
                    "Can we schedule a meeting?"
                ],
                "intermediate": [
                    "I'll prepare the presentation",
                    "Let's review the quarterly report",
                    "We need to discuss the project timeline",
                    "Could you forward me the documents?",
                    "I'd like to propose a new strategy"
                ],
                "advanced": [
                    "Let's analyze the market trends",
                    "We should optimize our workflow",
                    "I'd like to discuss the strategic direction",
                    "What are the key performance indicators?",
                    "How can we improve team collaboration?"
                ]
            },
            "culture": {
                "beginner": [
                    "This is beautiful",
                    "Tell me about this tradition",
                    "Happy holidays!",
                    "I enjoy learning about your culture",
                    "What is this celebration?"
                ],
                "intermediate": [
                    "Can you explain this historical event?",
                    "I'm interested in local art and music",
                    "What are some cultural etiquette tips?",
                    "How do people celebrate this festival?",
                    "I'd like to learn more about traditional crafts"
                ],
                "advanced": [
                    "How has globalization affected local traditions?",
                    "What role does religion play in daily life?",
                    "Can you discuss the influence of historical events?",
                    "How do modern and traditional values coexist?",
                    "What are the current cultural trends among youth?"
                ]
            }
        }
    
    async def transcribe_audio(self, request: VoiceTranscribeRequest) -> VoiceTranscribeResponse:
        """Transcribe audio using local Whisper (Deepgram removed)."""
        try:
            # Deepgram is no longer available - using local Whisper instead
            # This method is kept for compatibility but should not be used
            # Use /local-voice/transcribe endpoint instead
            return VoiceTranscribeResponse(
                success=False,
                transcript="",
                confidence=0.0,
                words=[],
                language_detected=None,
                duration=0.0,
                error="Deepgram transcription is no longer available. Please use local Whisper transcription at /local-voice/transcribe"
            )
            
        except Exception as e:
            error_msg = f"Deepgram transcription unavailable: {str(e)}"
            print(f"Error: {error_msg}")
            return VoiceTranscribeResponse(
                success=False,
                transcript="",
                confidence=0.0,
                words=[],
                language_detected=None,
                duration=0.0,
                error="Deepgram transcription is no longer available. Please use local Whisper transcription at /local-voice/transcribe"
            )
    
    async def generate_feedback(self, request: VoiceFeedbackRequest) -> VoiceFeedbackResponse:
        """Generate AI feedback for transcribed speech."""
        try:
            # Get expected text based on scenario and level if not provided
            expected_text = request.expected_text
            if not expected_text:
                expected_text = self._get_expected_text(request.scenario, request.level)
            
            # Prepare feedback prompt for AI
            prompt = self._create_feedback_prompt(
                transcript=request.transcript,
                expected_text=expected_text,
                language=request.language.value,
                level=request.level.value,
                scenario=request.scenario.value
            )
            
            # Try DeepSeek first, then fall back to Gemini
            ai_response = None
            provider_used = "unknown"
            
            try:
                print("Attempting to generate feedback with DeepSeek...")
                response = await self.deepseek_provider.generate_response(
                    message=request.transcript,
                    system_prompt=prompt,
                    conversation_history=[],
                    temperature=0.7,
                    max_tokens=1000
                )
                ai_response = response.message
                provider_used = "deepseek"
                print(f" DeepSeek feedback generated successfully")
            except Exception as deepseek_error:
                print(f" DeepSeek failed: {str(deepseek_error)}")
                print("Falling back to Gemini...")
                
                try:
                    response = await self.gemini_provider.generate_response(
                        message=request.transcript,
                        system_prompt=prompt,
                        conversation_history=[],
                        temperature=0.7,
                        max_tokens=1000
                    )
                    ai_response = response.message
                    provider_used = "gemini"
                    print(f" Gemini fallback successful")
                except Exception as gemini_error:
                    print(f"❌ Gemini fallback also failed: {str(gemini_error)}")
                    raise Exception("Both DeepSeek and Gemini providers failed")
            
            # Parse AI response to extract structured feedback
            feedback_data = self._parse_ai_feedback(ai_response)
            
            # Add provider info for debugging
            feedback_data["provider_used"] = provider_used
            
            return VoiceFeedbackResponse(
                success=True,
                scores=feedback_data.get("scores", {}),
                overall_score=feedback_data.get("overall_score", 0.0),
                feedback_messages=feedback_data.get("feedback_messages", []),
                suggestions=feedback_data.get("suggestions", []),
                corrected_text=feedback_data.get("corrected_text")
            )
            
        except Exception as e:
            print(f"Error generating feedback: {str(e)}")
            return VoiceFeedbackResponse(
                success=False,
                scores={"fluency": 0.0, "pronunciation": 0.0, "accuracy": 0.0},
                overall_score=0.0,
                feedback_messages=["Unable to generate feedback. Please try again."],
                suggestions=["Please check your audio and try recording again."],
                corrected_text=None
            )
    
    async def save_session(self, request: VoiceSessionSaveRequest) -> VoiceSessionSaveResponse:
        """Save voice session to Supabase."""
        try:
            session_id = str(uuid.uuid4())
            
            # Prepare session data
            session_data = {
                "id": session_id,
                "user_id": request.user_id,
                "language": request.language.value,
                "level": request.level.value,
                "scenario": request.scenario.value,
                "transcript": request.transcript,
                "audio_url": request.audio_url,
                "feedback": request.feedback,
                "scores": request.feedback.get("scores", {}),
                "session_duration": request.session_duration,
                "created_at": datetime.utcnow().isoformat()
            }
            
            # Save to Supabase
            client = self.supabase_manager.get_client()
            if client:
                client.table("voice_sessions").insert(session_data).execute()
            else:
                raise Exception("Failed to get Supabase client")
            
            # Update progress tracking
            await self._update_progress(request.user_id, request.language.value, request.feedback.get("scores", {}))
            
            return VoiceSessionSaveResponse(
                success=True,
                session_id=session_id,
                message="Session saved successfully"
            )
            
        except Exception as e:
            print(f"Error saving session: {str(e)}")
            return VoiceSessionSaveResponse(
                success=False,
                session_id="",
                message=f"Failed to save session: {str(e)}"
            )
    
    async def get_progress(self, request: VoiceProgressRequest) -> VoiceProgressResponse:
        """Get a user's voice learning progress."""
        try:
            # Query recent sessions
            client = self.supabase_manager.get_client()
            if not client:
                raise Exception("Failed to get Supabase client")
            query = client.table("voice_sessions").select("*").eq("user_id", request.user_id)
            
            if request.language:
                query = query.eq("language", request.language.value)
            
            # Filter by date range
            days_ago = datetime.utcnow().timestamp() - (request.days * 24 * 60 * 60)
            query = query.gte("created_at", datetime.fromtimestamp(days_ago).isoformat())
            
            response = query.order("created_at", desc=True).execute()
            sessions = response.data
            
            # Calculate statistics
            total_sessions = len(sessions)
            average_scores = self._calculate_average_scores(sessions)
            sessions_by_language = self._count_sessions_by_language(sessions)
            recent_sessions = sessions[:10]  # Last 10 sessions
            improvement_trends = self._calculate_improvement_trends(sessions)
            
            return VoiceProgressResponse(
                success=True,
                total_sessions=total_sessions,
                average_scores=average_scores,
                sessions_by_language=sessions_by_language,
                recent_sessions=recent_sessions,
                improvement_trends=improvement_trends
            )
            
        except Exception as e:
            print(f"Error getting progress: {str(e)}")
            return VoiceProgressResponse(
                success=False,
                total_sessions=0,
                average_scores={},
                sessions_by_language={},
                recent_sessions=[],
                improvement_trends={}
            )
    
    def _get_expected_text(self, scenario: VoiceScenario, level: VoiceLevel) -> str:
        """Get expected text for a scenario and level."""
        prompts = self.scenario_prompts.get(scenario.value, {}).get(level.value, [])
        import random
        return random.choice(prompts) if prompts else "Please speak clearly"
    
    def _create_feedback_prompt(self, transcript: str, expected_text: str, language: str, level: str, scenario: str) -> str:
        """Create an enhanced prompt for AI feedback generation with comprehensive analysis."""
        
        # Level-specific guidance for feedback calibration
        level_guidance = {
            "beginner": "Be very encouraging and focus on building confidence. Celebrate small wins. Focus on basic pronunciation and simple grammar. Avoid overwhelming with too many corrections.",
            "intermediate": "Provide balanced feedback with specific areas for improvement. Focus on fluency, natural phrasing, and common expressions. Point out patterns in mistakes.",
            "advanced": "Be more detailed and nuanced in feedback. Focus on subtle pronunciation differences, advanced grammar structures, idiomatic expressions, and native-like fluency."
        }
        
        # Language-specific pronunciation tips
        language_tips = {
            "french": "Pay attention to nasal vowels, liaison between words, silent letters, and the distinction between similar sounds like 'u' and 'ou'.",
            "german": "Focus on umlauts (ä, ö, ü), consonant clusters, word stress patterns, and the pronunciation of 'ch' and 'r' sounds.",
            "korean": "Check for proper vowel distinction, consonant aspiration levels (ㅂ vs ㅍ), syllable timing, and appropriate intonation patterns.",
            "mandarin": "Evaluate tone accuracy (1-4 tones), distinguish between similar initials (zh/j, ch/q, sh/x), and check for proper syllable structure.",
            "spanish": "Focus on rolling 'r' sounds, vowel clarity, proper stress placement, and distinction between 'b' and 'v' sounds."
        }
        
        current_level_guidance = level_guidance.get(level, level_guidance["intermediate"])
        current_language_tips = language_tips.get(language.lower(), "Focus on clear pronunciation and natural rhythm.")
        
        return f"""You are an expert language tutor specializing in {language} pronunciation and speaking skills. 
Analyze the following speech attempt with care and precision, providing actionable feedback.

## Context
- **Language**: {language}
- **Proficiency Level**: {level}
- **Scenario**: {scenario}
- **Expected Text**: "{expected_text}"
- **Student's Actual Speech**: "{transcript}"

## Level-Specific Guidance
{current_level_guidance}

## Language-Specific Focus Areas
{current_language_tips}

## Analysis Instructions
1. **Fluency Analysis**: Evaluate speech flow, hesitations, natural pacing, and rhythm
2. **Pronunciation Analysis**: Assess sound accuracy, stress patterns, and intonation
3. **Accuracy Analysis**: Compare against expected text for vocabulary and grammar correctness
4. **Vocabulary Usage**: Check for appropriate word choice in context
5. **Grammar Structure**: Identify any structural issues appropriate for the level

## Response Format
Provide your analysis in the following JSON format ONLY (no additional text):
{{
    "scores": {{
        "fluency": <score 0-100>,
        "pronunciation": <score 0-100>,
        "accuracy": <score 0-100>,
        "vocabulary": <score 0-100>,
        "grammar": <score 0-100>
    }},
    "overall_score": <weighted average score 0-100>,
    "feedback_messages": [
        "<specific positive feedback highlighting what was done well>",
        "<specific area that needs improvement with clear explanation>",
        "<contextual feedback related to the scenario>"
    ],
    "suggestions": [
        "<actionable tip for pronunciation improvement>",
        "<specific practice exercise suggestion>",
        "<resource or technique recommendation>"
    ],
    "pronunciation_notes": [
        "<specific sound or word that needs attention>",
        "<phonetic tip for improvement>"
    ],
    "strengths": [
        "<what the student did particularly well>"
    ],
    "corrected_text": "<corrected version of the transcript if needed, or null if perfect>"
}}

## Scoring Guidelines
- 90-100: Excellent, near-native quality
- 80-89: Very good, minor issues
- 70-79: Good, noticeable room for improvement
- 60-69: Fair, several areas need work
- 50-59: Developing, significant practice needed
- Below 50: Needs substantial improvement

Be encouraging while being honest. Focus on progress over perfection."""
    
    def _parse_ai_feedback(self, ai_response: str) -> Dict[str, Any]:
        """Parse AI feedback response into structured data."""
        try:
            # Try to extract JSON from the response
            start = ai_response.find("{")
            end = ai_response.rfind("}") + 1
            if start != -1 and end != -1:
                json_str = ai_response[start:end]
                return json.loads(json_str)
        except:
            pass
        
        # Fallback if JSON parsing fails
        return {
            "scores": {"fluency": 75.0, "pronunciation": 75.0, "accuracy": 75.0},
            "overall_score": 75.0,
            "feedback_messages": ["Keep practicing!"],
            "suggestions": ["Try speaking more clearly"],
            "corrected_text": None
        }
    
    def _calculate_average_scores(self, sessions: List[Dict]) -> Dict[str, float]:
        """Calculate average scores from sessions."""
        if not sessions:
            return {"fluency": 0.0, "pronunciation": 0.0, "accuracy": 0.0, "overall": 0.0}
        
        total_scores = {"fluency": 0.0, "pronunciation": 0.0, "accuracy": 0.0}
        count = 0
        
        for session in sessions:
            scores = session.get("scores", {})
            for key in total_scores:
                total_scores[key] += scores.get(key, 0.0)
            count += 1
        
        if count == 0:
            return {"fluency": 0.0, "pronunciation": 0.0, "accuracy": 0.0, "overall": 0.0}
        
        averages = {key: total / count for key, total in total_scores.items()}
        averages["overall"] = sum(averages.values()) / len(averages)
        
        return averages
    
    def _count_sessions_by_language(self, sessions: List[Dict]) -> Dict[str, int]:
        """Count sessions by language."""
        counts = {}
        for session in sessions:
            lang = session.get("language", "unknown")
            counts[lang] = counts.get(lang, 0) + 1
        return counts
    
    def _calculate_improvement_trends(self, sessions: List[Dict]) -> Dict[str, float]:
        """Calculate improvement trends over time."""
        if len(sessions) < 2:
            return {"fluency": 0.0, "pronunciation": 0.0, "accuracy": 0.0, "overall": 0.0}
        
        # Compare recent sessions with older sessions
        midpoint = len(sessions) // 2
        recent = sessions[:midpoint]
        older = sessions[midpoint:]
        
        recent_avg = self._calculate_average_scores(recent)
        older_avg = self._calculate_average_scores(older)
        
        trends = {}
        for key in recent_avg:
            trends[key] = recent_avg[key] - older_avg[key]
        
        return trends
    
    async def _update_progress(self, user_id: str, language: str, scores: Dict[str, float]):
        """Update user progress tracking."""
        try:
            # Check if a progress record exists
            client = self.supabase_manager.get_client()
            if not client:
                raise Exception("Failed to get Supabase client")
            response = client.table("voice_progress").select("*").eq("user_id", user_id).eq("language", language).execute()
            
            if response.data:
                # Update existing record
                current = response.data[0]
                total_sessions = current.get("total_sessions", 0) + 1
                
                # Calculate new averages
                current_avg = current.get("average_overall", 0.0)
                new_overall = (current_avg * (total_sessions - 1) + scores.get("overall", current_avg)) / total_sessions
                
                update_data = {
                    "total_sessions": total_sessions,
                    "average_overall": new_overall,
                    "last_session_date": datetime.utcnow().isoformat()
                }
                
                client = self.supabase_manager.get_client()
                if client:
                    client.table("voice_progress").update(update_data).eq("user_id", user_id).eq("language", language).execute()
            else:
                # Create a new record
                progress_data = {
                    "id": str(uuid.uuid4()),
                    "user_id": user_id,
                    "language": language,
                    "total_sessions": 1,
                    "average_fluency": scores.get("fluency", 0.0),
                    "average_pronunciation": scores.get("pronunciation", 0.0),
                    "average_accuracy": scores.get("accuracy", 0.0),
                    "average_overall": scores.get("overall", 0.0),
                    "last_session_date": datetime.utcnow().isoformat(),
                    "improvement_percentage": 0.0
                }
                
                client = self.supabase_manager.get_client()
                if client:
                    client.table("voice_progress").insert(progress_data).execute()
                
        except Exception as e:
            print(f"Error updating progress: {str(e)}")
