"""
Data models for Voice Agent API using Deepgram's Voice Agent.
"""
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any, Literal
from datetime import datetime
from enum import Enum


class AgentScenario(str, Enum):
    LANGUAGE_TUTOR = "language_tutor"
    CONVERSATION_PARTNER = "conversation_partner"
    INTERVIEW_PRACTICE = "interview_practice"
    TRAVEL_COMPANION = "travel_companion"


class AgentLanguage(str, Enum):
    ENGLISH = "english"
    FRENCH = "french"
    GERMAN = "german"
    KOREAN = "korean"
    MANDARIN = "mandarin"
    SPANISH = "spanish"


class AgentVoice(str, Enum):
    THALIA = "aura-2-thalia-en"
    ANDROMEDA = "aura-2-andromeda-en"
    APOLLO = "aura-2-apollo-en"
    ARIES = "aura-2-aries-en"
    ARCAS = "aura-2-arcas-en"
    HELENA = "aura-2-helena-en"


class AgentStatus(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"
    ERROR = "error"
    CONNECTING = "connecting"


# Request Models
class AgentStartRequest(BaseModel):
    user_id: str = Field(..., description="User identifier")
    language: AgentLanguage = Field(default=AgentLanguage.ENGLISH, description="Conversation language")
    scenario: AgentScenario = Field(default=AgentScenario.LANGUAGE_TUTOR, description="Conversation scenario")
    voice_model: AgentVoice = Field(default=AgentVoice.THALIA, description="Voice model for the agent")
    temperature: float = Field(default=0.7, ge=0.0, le=2.0, description="AI response randomness (0-2)")
    custom_prompt: Optional[str] = Field(None, description="Custom prompt for the agent")


class AgentAudioRequest(BaseModel):
    session_id: str = Field(..., description="Session identifier")
    audio_data: bytes = Field(..., description="Raw audio data in WAV format")


class AgentTextMessageRequest(BaseModel):
    session_id: str = Field(..., description="Session identifier")
    content: str = Field(..., description="Text message content")


class AgentUpdateRequest(BaseModel):
    session_id: str = Field(..., description="Session identifier")


class AgentPromptUpdateRequest(AgentUpdateRequest):
    new_prompt: str = Field(..., description="New prompt for the agent")


class AgentVoiceUpdateRequest(AgentUpdateRequest):
    voice_model: AgentVoice = Field(..., description="New voice model for the agent")


class AgentEndRequest(BaseModel):
    session_id: str = Field(..., description="Session identifier")


class AgentHistoryRequest(BaseModel):
    session_id: str = Field(..., description="Session identifier")


# Response Models
class AgentStartResponse(BaseModel):
    success: bool
    session_id: str
    message: str
    settings: Dict[str, Any]


class AgentAudioResponse(BaseModel):
    success: bool
    message: str


class AgentTextMessageResponse(BaseModel):
    success: bool
    message: str


class AgentUpdateResponse(BaseModel):
    success: bool
    message: str


class AgentEndResponse(BaseModel):
    success: bool
    message: str


class AgentHistoryResponse(BaseModel):
    success: bool
    conversation_history: List[Dict[str, Any]]
    session_info: Optional[Dict[str, Any]] = None


class AgentSessionInfo(BaseModel):
    session_id: str
    user_id: str
    language: str
    scenario: str
    status: AgentStatus
    start_time: datetime
    duration: float  # in seconds


class AgentActiveSessionsResponse(BaseModel):
    success: bool
    active_sessions: List[AgentSessionInfo]
    total_count: int


# Event Models
class AgentEvent(BaseModel):
    type: str
    event: str
    session_id: str
    timestamp: Optional[datetime] = None
    data: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class AgentAudioEvent(BaseModel):
    session_id: str
    audio_data: bytes
    timestamp: datetime


class AgentConversationEvent(BaseModel):
    session_id: str
    role: Literal["user", "assistant"]
    content: str
    timestamp: datetime


class AgentStatusEvent(BaseModel):
    session_id: str
    status: AgentStatus
    message: Optional[str] = None
    timestamp: datetime


# Database Models
class AgentSession(BaseModel):
    id: str
    user_id: str
    language: str
    scenario: str
    status: str
    created_at: datetime
    ended_at: Optional[datetime] = None
    duration: Optional[float] = None  # in seconds
    conversation_count: int = 0
    audio_duration: float = 0.0  # total audio duration in seconds
    
    class Config:
        from_attributes = True


class AgentConversationLog(BaseModel):
    id: str
    session_id: str
    role: str  # "user" or "assistant"
    content: str
    timestamp: datetime
    audio_available: bool = False
    audio_url: Optional[str] = None
    
    class Config:
        from_attributes = True


class AgentEventLog(BaseModel):
    id: str
    session_id: str
    event_type: str
    event_data: Dict[str, Any]
    timestamp: datetime
    
    class Config:
        from_attributes = True


# Configuration Models
class AgentConfiguration(BaseModel):
    language: AgentLanguage = Field(default=AgentLanguage.ENGLISH)
    scenario: AgentScenario = Field(default=AgentScenario.LANGUAGE_TUTOR)
    voice_model: AgentVoice = Field(default=AgentVoice.THALIA)
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    custom_prompt: Optional[str] = None
    
    # Audio settings
    input_sample_rate: int = Field(default=24000)
    output_sample_rate: int = Field(default=24000)
    audio_encoding: str = Field(default="linear16")
    
    # Agent behavior settings
    enable_history: bool = Field(default=True)
    enable_function_calls: bool = Field(default=False)
    max_context_length: Optional[int] = Field(default=None)
    
    class Config:
        json_encoders = {
            bytes: lambda v: v.decode('latin1') if isinstance(v, bytes) else v
        }


# Analytics Models
class AgentSessionStats(BaseModel):
    total_sessions: int
    active_sessions: int
    average_duration: float
    total_conversation_turns: int
    languages_used: Dict[str, int]
    scenarios_used: Dict[str, int]


class AgentUserStats(BaseModel):
    user_id: str
    total_sessions: int
    total_duration: float
    favorite_language: str
    favorite_scenario: str
    last_session: Optional[datetime] = None
    improvement_trend: Optional[float] = None  # positive = improving


# Error Models
class AgentError(BaseModel):
    error_code: str
    error_message: str
    session_id: Optional[str] = None
    timestamp: datetime
    details: Optional[Dict[str, Any]] = None


class AgentWarning(BaseModel):
    warning_code: str
    warning_message: str
    session_id: Optional[str] = None
    timestamp: datetime
    suggestions: Optional[List[str]] = None


# WebSocket Message Models
class WebSocketMessage(BaseModel):
    type: str
    session_id: str
    timestamp: datetime
    data: Dict[str, Any]


class AudioDataMessage(WebSocketMessage):
    type: Literal["audio_data"]
    audio_data: bytes
    duration: float


class TextMessage(WebSocketMessage):
    type: Literal["text_message"]
    content: str
    role: Literal["user", "assistant"]


class StatusMessage(WebSocketMessage):
    type: Literal["status_update"]
    status: AgentStatus
    message: Optional[str] = None


class ErrorMessage(WebSocketMessage):
    type: Literal["error"]
    error_code: str
    error_message: str


# Utility Models
class AgentCapabilities(BaseModel):
    supported_languages: List[AgentLanguage]
    supported_scenarios: List[AgentScenario]
    supported_voices: List[AgentVoice]
    max_session_duration: int  # in minutes
    max_audio_chunk_size: int  # in bytes
    supported_audio_formats: List[str]


class AgentHealthCheck(BaseModel):
    status: Literal["healthy", "degraded", "unhealthy"]
    api_version: str
    active_connections: int
    memory_usage: Optional[float] = None
    cpu_usage: Optional[float] = None
    last_error: Optional[str] = None
    uptime: float  # in seconds
