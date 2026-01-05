"""
Data models for Voice Tutor feature using Deepgram API.
"""
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any, Literal
from datetime import datetime
from enum import Enum


class VoiceLanguage(str, Enum):
    ENGLISH = "en"
    FRENCH = "fr"
    GERMAN = "de"
    KOREAN = "ko"
    MANDARIN = "zh"
    SPANISH = "es"


class VoiceLevel(str, Enum):
    BEGINNER = "beginner"
    INTERMEDIATE = "intermediate"
    ADVANCED = "advanced"


class VoiceScenario(str, Enum):
    TRAVEL = "travel"
    FOOD = "food"
    DAILY_CONVERSATION = "daily_conversation"
    WORK = "work"
    CULTURE = "culture"


# Request Models
class VoiceTranscribeRequest(BaseModel):
    audio_data: bytes = Field(..., description="Raw audio data in WAV format")
    language: VoiceLanguage = Field(..., description="Target language for transcription")
    model: str = Field(default="nova-3", description="Deepgram model to use")
    
    class Config:
        json_encoders = {
            bytes: lambda v: v.decode('latin1') if isinstance(v, bytes) else v
        }


class VoiceFeedbackRequest(BaseModel):
    transcript: str = Field(..., description="Transcribed text from Deepgram")
    expected_text: Optional[str] = Field(None, description="Expected phrase or prompt")
    language: VoiceLanguage = Field(..., description="Target language")
    level: VoiceLevel = Field(..., description="User proficiency level")
    scenario: VoiceScenario = Field(..., description="Practice scenario")
    user_id: str = Field(..., description="User identifier")


class VoiceSessionSaveRequest(BaseModel):
    user_id: str = Field(..., description="User identifier")
    language: VoiceLanguage = Field(..., description="Practice language")
    level: VoiceLevel = Field(..., description="Proficiency level")
    scenario: VoiceScenario = Field(..., description="Practice scenario")
    transcript: str = Field(..., description="Transcribed speech")
    audio_url: Optional[str] = Field(None, description="URL to stored audio file")
    feedback: Dict[str, Any] = Field(..., description="AI feedback results")
    session_duration: float = Field(..., description="Session duration in seconds")


class VoiceProgressRequest(BaseModel):
    user_id: str = Field(..., description="User identifier")
    language: Optional[VoiceLanguage] = Field(None, description="Filter by language")
    days: int = Field(default=30, description="Number of days to look back")


# Response Models
class VoiceTranscribeResponse(BaseModel):
    success: bool
    transcript: str
    confidence: float
    words: List[Dict[str, Any]] = Field(default_factory=list)
    language_detected: Optional[str] = None
    duration: Optional[float] = None


class VoiceFeedbackResponse(BaseModel):
    success: bool
    scores: Dict[str, float] = Field(..., description="Scores for fluency, pronunciation, accuracy")
    overall_score: float = Field(..., description="Overall score (0-100)")
    feedback_messages: List[str] = Field(..., description="Detailed feedback messages")
    suggestions: List[str] = Field(..., description="Improvement suggestions")
    corrected_text: Optional[str] = Field(None, description="Corrected version of transcript")


class VoiceSessionSaveResponse(BaseModel):
    success: bool
    session_id: str
    message: str


class VoiceProgressResponse(BaseModel):
    success: bool
    total_sessions: int
    average_scores: Dict[str, float]
    sessions_by_language: Dict[str, int]
    recent_sessions: List[Dict[str, Any]]
    improvement_trends: Dict[str, float]


# Database Models
class VoiceSession(BaseModel):
    id: str
    user_id: str
    language: VoiceLanguage
    level: VoiceLevel
    scenario: VoiceScenario
    transcript: str
    audio_url: Optional[str] = None
    feedback: Dict[str, Any]
    scores: Dict[str, float]
    session_duration: float
    created_at: datetime
    
    class Config:
        from_attributes = True


class VoiceProgress(BaseModel):
    id: str
    user_id: str
    language: VoiceLanguage
    total_sessions: int
    average_fluency: float
    average_pronunciation: float
    average_accuracy: float
    average_overall: float
    last_session_date: datetime
    improvement_percentage: float
    
    class Config:
        from_attributes = True
