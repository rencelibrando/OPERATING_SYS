"""
Data models for the AI backend service.
"""
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List
from enum import Enum


class AIProvider(str, Enum):
    GEMINI = "gemini"

class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"


class ChatMessage(BaseModel):
    role: MessageRole
    content: str
    timestamp: Optional[int] = None


class UserContext(BaseModel):
    user_id: str
    # Personal information
    first_name: Optional[str] = None
    last_name: Optional[str] = None

    # Basic profile
    native_language: Optional[str] = None
    target_languages: List[str] = Field(default_factory=list)
    current_level: Optional[str] = None
    primary_goal: Optional[str] = None
    learning_style: Optional[str] = None
    focus_areas: List[str] = Field(default_factory=list)
    motivations: List[str] = Field(default_factory=list)
    personality_preferences: Optional[Dict[str, Any]] = None
    interests: List[str] = Field(default_factory=list)
    ai_profile: Optional[Dict[str, Any]] = None

    # Comprehensive learning data
    learning_progress: Optional[Dict[str, Any]] = None
    skill_progress: List[Dict[str, Any]] = Field(default_factory=list)
    vocabulary_stats: Optional[Dict[str, Any]] = None
    lesson_progress: Optional[Dict[str, Any]] = None
    chat_history: Optional[Dict[str, Any]] = None
    achievements: List[str] = Field(default_factory=list)
    user_settings: Optional[Dict[str, Any]] = None


class ChatRequest(BaseModel):
    message: str
    user_context: UserContext
    conversation_history: List[ChatMessage] = Field(default_factory=list)
    provider: AIProvider = AIProvider.GEMINI
    bot_id: Optional[str] = None
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    max_tokens: int = Field(default=1000, ge=1, le=4096)


class ChatResponse(BaseModel):
    message: str
    provider: AIProvider
    tokens_used: Optional[int] = None
    metadata: Optional[Dict[str, Any]] = None


class HealthResponse(BaseModel):
    status: str
    version: str
    providers: Dict[str, bool]


class ChatHistoryData(BaseModel):
    session_id: str
    messages: List[ChatMessage]
    compress: bool = True


class SaveHistoryRequest(BaseModel):
    session_id: str
    messages: List[ChatMessage]
    compress: bool = True


class SaveHistoryResponse(BaseModel):
    success: bool
    session_id: str
    message_count: int
    original_size: int
    compressed_size: int
    compression_ratio: float
    compression_type: str


class LoadHistoryRequest(BaseModel):
    session_id: str


class LoadHistoryResponse(BaseModel):
    success: bool
    session_id: str
    messages: List[ChatMessage]
    message_count: int
    original_size: int
    compressed_size: int
    compression_ratio: float
