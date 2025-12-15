"""
Data models for lesson content system.
Supports multiple question types with media.
"""
from pydantic import BaseModel, Field
from typing import Optional, List, Literal
from datetime import datetime
from enum import Enum


# ============================================
# ENUMS
# ============================================

class QuestionType(str, Enum):
    MULTIPLE_CHOICE = "multiple_choice"
    TEXT_ENTRY = "text_entry"
    MATCHING = "matching"
    PARAPHRASING = "paraphrasing"
    ERROR_CORRECTION = "error_correction"


# ============================================
# CHOICE MODELS
# ============================================

class QuestionChoiceBase(BaseModel):
    choice_text: str = Field(..., min_length=1, max_length=500)
    choice_order: int = Field(default=0, ge=0)
    is_correct: bool = False
    image_url: Optional[str] = None
    audio_url: Optional[str] = None
    # For matching questions
    match_pair_id: Optional[str] = None  # ID to link pairs together


class QuestionChoiceCreate(QuestionChoiceBase):
    pass


class QuestionChoiceUpdate(BaseModel):
    choice_text: Optional[str] = None
    choice_order: Optional[int] = None
    is_correct: Optional[bool] = None
    image_url: Optional[str] = None
    audio_url: Optional[str] = None
    match_pair_id: Optional[str] = None


class QuestionChoice(QuestionChoiceBase):
    id: str
    question_id: str
    created_at: datetime

    class Config:
        from_attributes = True


# ============================================
# QUESTION MODELS
# ============================================

class QuestionBase(BaseModel):
    question_type: QuestionType
    question_text: str = Field(..., min_length=1)
    question_order: int = Field(default=0, ge=0)
    answer_text: Optional[str] = None  # For text_entry, paraphrasing questions
    question_audio_url: Optional[str] = None
    answer_audio_url: Optional[str] = None
    # For error correction - text with intentional errors
    error_text: Optional[str] = None
    # Explanation text for all question types
    explanation: Optional[str] = None
    # Custom wrong answer feedback message
    wrong_answer_feedback: Optional[str] = None


class QuestionCreate(QuestionBase):
    choices: Optional[List[QuestionChoiceCreate]] = []


class QuestionUpdate(BaseModel):
    question_type: Optional[QuestionType] = None
    question_text: Optional[str] = None
    question_order: Optional[int] = None
    answer_text: Optional[str] = None
    question_audio_url: Optional[str] = None
    answer_audio_url: Optional[str] = None
    error_text: Optional[str] = None
    explanation: Optional[str] = None
    wrong_answer_feedback: Optional[str] = None


class Question(QuestionBase):
    id: str
    lesson_id: str
    choices: List[QuestionChoice] = []
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


# ============================================
# LESSON MODELS
# ============================================

class LessonBase(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    description: Optional[str] = None
    lesson_order: int = Field(default=0, ge=0)
    is_published: bool = False


class LessonCreate(LessonBase):
    topic_id: str
    questions: Optional[List[QuestionCreate]] = []


class LessonUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    lesson_order: Optional[int] = None
    is_published: Optional[bool] = None


class Lesson(LessonBase):
    id: str
    topic_id: str
    questions: List[Question] = []
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class LessonSummary(BaseModel):
    """Lightweight lesson info without questions"""
    id: str
    topic_id: str
    title: str
    description: Optional[str] = None
    lesson_order: int
    is_published: bool
    question_count: int = 0
    created_at: datetime
    updated_at: datetime


# ============================================
# USER PROGRESS MODELS
# ============================================

class UserLessonProgressBase(BaseModel):
    is_completed: bool = False
    score: Optional[float] = Field(None, ge=0, le=100)
    time_spent_seconds: int = Field(default=0, ge=0)


class UserLessonProgressCreate(UserLessonProgressBase):
    user_id: str
    lesson_id: str


class UserLessonProgressUpdate(BaseModel):
    is_completed: Optional[bool] = None
    score: Optional[float] = Field(None, ge=0, le=100)
    time_spent_seconds: Optional[int] = None


class UserLessonProgress(UserLessonProgressBase):
    id: str
    user_id: str
    lesson_id: str
    started_at: datetime
    completed_at: Optional[datetime] = None
    last_accessed_at: datetime

    class Config:
        from_attributes = True


# ============================================
# USER ANSWER MODELS
# ============================================

class UserQuestionAnswerBase(BaseModel):
    selected_choice_id: Optional[str] = None  # For multiple choice
    answer_text: Optional[str] = None  # For identification
    voice_recording_url: Optional[str] = None  # For voice recording
    is_correct: Optional[bool] = None


class UserQuestionAnswerCreate(UserQuestionAnswerBase):
    user_id: str
    question_id: str
    lesson_id: str


class UserQuestionAnswerUpdate(BaseModel):
    selected_choice_id: Optional[str] = None
    answer_text: Optional[str] = None
    voice_recording_url: Optional[str] = None
    is_correct: Optional[bool] = None


class UserQuestionAnswer(UserQuestionAnswerBase):
    id: str
    user_id: str
    question_id: str
    lesson_id: str
    attempted_at: datetime

    class Config:
        from_attributes = True


# ============================================
# REQUEST/RESPONSE MODELS
# ============================================

class LessonListResponse(BaseModel):
    lessons: List[LessonSummary]
    total: int


class LessonDetailResponse(BaseModel):
    lesson: Lesson


class MediaUploadResponse(BaseModel):
    url: str
    file_name: str
    file_size: int
    media_type: str  # "image", "audio", "video"


class BulkQuestionCreate(BaseModel):
    lesson_id: str
    questions: List[QuestionCreate]


class SubmitLessonAnswersRequest(BaseModel):
    user_id: str
    lesson_id: str
    answers: List[UserQuestionAnswerCreate]


class SubmitLessonAnswersResponse(BaseModel):
    score: float
    total_questions: int
    correct_answers: int
    is_passed: bool
    completed_at: str
    
    class Config:
        json_encoders = {
            datetime: lambda v: v.isoformat()
        }
