"""
API routes for audio narration generation.
Handles requests for generating and retrieving Edge TTS narration.
"""
from fastapi import APIRouter, HTTPException, status, BackgroundTasks
from pydantic import BaseModel, Field
from typing import Optional, List, Dict
import logging

from tts_service import get_tts_service
from language_detection_service import get_language_detection_service
from lesson_service import LessonService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/narration", tags=["Narration"])



# REQUEST/RESPONSE MODELS
class GenerateNarrationRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=5000)
    language_override: Optional[str] = None
    voice_override: Optional[str] = None
    use_cache: bool = True

class GenerateNarrationResponse(BaseModel):
    audio_url: Optional[str]
    language_detected: str
    confidence: float
    voice_used: str
    cached: bool

class DetectLanguageRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=5000)

class DetectLanguageResponse(BaseModel):
    language_code: str
    confidence: float
    supported: bool

class GenerateQuestionNarrationRequest(BaseModel):
    question_id: str
    regenerate: bool = False

class GenerateQuestionNarrationResponse(BaseModel):
    question_id: str
    question_audio_url: Optional[str]
    answer_audio_url: Optional[str]
    success: bool
    message: str

class GenerateLessonNarrationRequest(BaseModel):
    lesson_id: str
    regenerate: bool = False

class GenerateLessonNarrationResponse(BaseModel):
    lesson_id: str
    total_questions: int
    generated_count: int
    failed_count: int
    audio_urls: Dict[str, Dict[str, Optional[str]]]
    success: bool



# ENDPOINTS
@router.post("/detect-language", response_model=DetectLanguageResponse)
async def detect_language(request: DetectLanguageRequest):
    """
    Detect the language of a given text using FastText.
    Useful for previewing language detection before generating audio.
    """
    try:
        language_service = get_language_detection_service()
        language_code, confidence = language_service.detect_language(request.text)
        
        supported = language_code in language_service.SUPPORTED_LANGUAGES
        
        return DetectLanguageResponse(
            language_code=language_code,
            confidence=confidence,
            supported=supported
        )
        
    except Exception as e:
        logger.error(f"Error detecting language: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Language detection failed: {str(e)}"
        )


@router.post("/generate", response_model=GenerateNarrationResponse)
async def generate_narration(request: GenerateNarrationRequest):
    """
    Generate audio narration for arbitrary text.
    Useful for testing or generating one-off narrations.
    """
    try:
        tts_service = get_tts_service()
        language_service = get_language_detection_service()
        
        # Detect language if not overridden
        if request.language_override:
            language_code = request.language_override
            confidence = 1.0
        else:
            language_code, confidence = language_service.detect_language(request.text)
        
        # Check if we're using cache
        voice = tts_service.select_voice(language_code, request.voice_override)
        
        # Generate audio
        audio_url = await tts_service.generate_audio(
            text=request.text,
            language_override=request.language_override,
            voice_override=request.voice_override,
            use_cache=request.use_cache
        )
        
        if not audio_url:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to generate audio"
            )
        
        # Determine if cached (simplified check based on speed)
        cached = request.use_cache and "supabase" in audio_url
        
        return GenerateNarrationResponse(
            audio_url=audio_url,
            language_detected=language_code,
            confidence=confidence,
            voice_used=voice,
            cached=cached
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error generating narration: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Narration generation failed: {str(e)}"
        )


@router.post("/question/{question_id}", response_model=GenerateQuestionNarrationResponse)
async def generate_question_narration(
    question_id: str,
    regenerate: bool = False
):
    """
    Generate narration for a specific question and its answer.
    Updates the question's audio URLs in the database.
    """
    try:
        lesson_service = LessonService()
        tts_service = get_tts_service()
        
        # Get question
        question = await lesson_service.get_question_by_id(question_id)
        if not question:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Question not found"
            )
        
        # Check if narration is enabled
        if not getattr(question, 'enable_question_narration', True):
            return GenerateQuestionNarrationResponse(
                question_id=question_id,
                question_audio_url=None,
                answer_audio_url=None,
                success=False,
                message="Narration disabled for this question"
            )
        
        question_audio_url = None
        answer_audio_url = None
        
        # Generate question narration
        if question.question_text and (regenerate or not question.question_audio_url):
            question_audio_url = await tts_service.generate_audio(
                text=question.question_text,
                language_override=getattr(question, 'narration_language', None),
                voice_override=getattr(question, 'narration_voice', None),
                use_cache=not regenerate
            )
            
            if question_audio_url:
                # Update database
                from lesson_models import QuestionUpdate
                await lesson_service.update_question(
                    question_id,
                    QuestionUpdate(question_audio_url=question_audio_url)
                )
        else:
            question_audio_url = question.question_audio_url
        
        # Generate answer narration
        if question.answer_text and (regenerate or not question.answer_audio_url):
            if getattr(question, 'enable_answer_narration', True):
                answer_audio_url = await tts_service.generate_audio(
                    text=question.answer_text,
                    language_override=getattr(question, 'narration_language', None),
                    voice_override=getattr(question, 'narration_voice', None),
                    use_cache=not regenerate
                )
                
                if answer_audio_url:
                    # Update database
                    from lesson_models import QuestionUpdate
                    await lesson_service.update_question(
                        question_id,
                        QuestionUpdate(answer_audio_url=answer_audio_url)
                    )
            else:
                answer_audio_url = question.answer_audio_url
        
        return GenerateQuestionNarrationResponse(
            question_id=question_id,
            question_audio_url=question_audio_url,
            answer_audio_url=answer_audio_url,
            success=True,
            message="Narration generated successfully"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error generating question narration: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate question narration: {str(e)}"
        )


@router.post("/lesson/{lesson_id}", response_model=GenerateLessonNarrationResponse)
async def generate_lesson_narration(
    lesson_id: str,
    regenerate: bool = False,
    background_tasks: BackgroundTasks = None
):
    """
    Generate narration for all questions in a lesson.
    Can be run as a background task for large lessons.
    """
    try:
        lesson_service = LessonService()
        tts_service = get_tts_service()
        
        # Get lesson with questions
        lesson = await lesson_service.get_lesson_by_id(lesson_id, include_questions=True)
        if not lesson:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Lesson not found"
            )
        
        # Check if lesson narration is enabled
        if not getattr(lesson, 'enable_lesson_narration', True):
            return GenerateLessonNarrationResponse(
                lesson_id=lesson_id,
                total_questions=len(lesson.questions),
                generated_count=0,
                failed_count=0,
                audio_urls={},
                success=False
            )
        
        total_questions = len(lesson.questions)
        generated_count = 0
        failed_count = 0
        audio_urls = {}
        
        # Process each question
        for question in lesson.questions:
            question_urls = {"question": None, "answer": None}
            
            try:
                # Get language override from a lesson or question
                language_override = (
                    getattr(question, 'narration_language', None) or
                    getattr(lesson, 'narration_language', None)
                )
                voice_override = (
                    getattr(question, 'narration_voice', None) or
                    getattr(lesson, 'narration_voice', None)
                )
                
                # Generate question narration
                if question.question_text and getattr(question, 'enable_question_narration', True):
                    if regenerate or not question.question_audio_url:
                        question_audio_url = await tts_service.generate_audio(
                            text=question.question_text,
                            language_override=language_override,
                            voice_override=voice_override,
                            use_cache=not regenerate
                        )
                        
                        if question_audio_url:
                            from lesson_models import QuestionUpdate
                            await lesson_service.update_question(
                                question.id,
                                QuestionUpdate(question_audio_url=question_audio_url)
                            )
                            question_urls["question"] = question_audio_url
                            generated_count += 1
                    else:
                        question_urls["question"] = question.question_audio_url
                
                # Generate answer narration
                if question.answer_text and getattr(question, 'enable_answer_narration', True):
                    if regenerate or not question.answer_audio_url:
                        answer_audio_url = await tts_service.generate_audio(
                            text=question.answer_text,
                            language_override=language_override,
                            voice_override=voice_override,
                            use_cache=not regenerate
                        )
                        
                        if answer_audio_url:
                            from lesson_models import QuestionUpdate
                            await lesson_service.update_question(
                                question.id,
                                QuestionUpdate(answer_audio_url=answer_audio_url)
                            )
                            question_urls["answer"] = answer_audio_url
                            generated_count += 1
                    else:
                        question_urls["answer"] = question.answer_audio_url
                
                audio_urls[question.id] = question_urls
                
            except Exception as e:
                logger.error(f"Error generating narration for question {question.id}: {e}")
                failed_count += 1
                audio_urls[question.id] = {"question": None, "answer": None, "error": str(e)}
        
        return GenerateLessonNarrationResponse(
            lesson_id=lesson_id,
            total_questions=total_questions,
            generated_count=generated_count,
            failed_count=failed_count,
            audio_urls=audio_urls,
            success=failed_count == 0
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error generating lesson narration: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate lesson narration: {str(e)}"
        )


@router.get("/voices")
async def list_available_voices():
    """
    List all available voices and their language mappings.
    """
    tts_service = get_tts_service()
    language_service = get_language_detection_service()
    
    return {
        "voices": tts_service.VOICE_MAPPING,
        "supported_languages": list(language_service.SUPPORTED_LANGUAGES),
        "default_voice": tts_service.VOICE_MAPPING['en']
    }
