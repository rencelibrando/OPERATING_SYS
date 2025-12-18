"""
API routes for lesson content management.
Handles lesson CRUD, questions, and user progress.
"""
from fastapi import APIRouter, HTTPException, status, UploadFile, File, Form
from typing import List, Optional
import logging
import os
import uuid
from pathlib import Path

from lesson_models import (
    Lesson, LessonCreate, LessonUpdate, LessonSummary,
    Question, QuestionCreate, QuestionUpdate,
    QuestionChoice, QuestionChoiceCreate,
    UserLessonProgress, UserLessonProgressCreate, UserLessonProgressUpdate,
    UserQuestionAnswer, UserQuestionAnswerCreate,
    LessonListResponse, LessonDetailResponse,
    MediaUploadResponse, BulkQuestionCreate,
    SubmitLessonAnswersRequest, SubmitLessonAnswersResponse
)
from lesson_service import LessonService
from supabase_client import SupabaseManager

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/lessons", tags=["Lessons"])
lesson_service = LessonService()



# LESSON ENDPOINTS
@router.get("/topic/{topic_id}", response_model=LessonListResponse)
async def get_lessons_by_topic(
    topic_id: str,
    published_only: bool = True
):
    """
    Get all lessons for a specific topic.
    
    - **topic_id**: ID of the topic
    - **published_only**: If True, only return published lessons
    """
    try:
        lessons = await lesson_service.get_lessons_by_topic(topic_id, published_only)
        return LessonListResponse(lessons=lessons, total=len(lessons))
    except Exception as e:
        logger.error(f"Error fetching lessons: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to fetch lessons: {str(e)}"
        )


@router.get("/{lesson_id}", response_model=LessonDetailResponse)
async def get_lesson(
    lesson_id: str,
    include_questions: bool = True
):
    """
    Get a specific lesson by ID.
    
    - **lesson_id**: ID of the lesson
    - **include_questions**: If True, include all questions and choices
    """
    try:
        lesson = await lesson_service.get_lesson_by_id(lesson_id, include_questions)
        if not lesson:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Lesson not found"
            )
        return LessonDetailResponse(lesson=lesson)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error fetching lesson: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to fetch lesson: {str(e)}"
        )


@router.post("/", response_model=LessonDetailResponse, status_code=status.HTTP_201_CREATED)
async def create_lesson(lesson_data: LessonCreate):
    """
    Create a new lesson with optional questions.
    
    The lesson will be created with all nested questions and choices.
    """
    try:
        lesson = await lesson_service.create_lesson(lesson_data)
        return LessonDetailResponse(lesson=lesson)
    except Exception as e:
        logger.error(f"Error creating lesson: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create lesson: {str(e)}"
        )


@router.put("/{lesson_id}", response_model=LessonDetailResponse)
async def update_lesson(lesson_id: str, lesson_data: LessonUpdate):
    """
    Update an existing lesson.
    
    Only provided fields will be updated.
    """
    try:
        lesson = await lesson_service.update_lesson(lesson_id, lesson_data)
        return LessonDetailResponse(lesson=lesson)
    except Exception as e:
        logger.error(f"Error updating lesson: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update lesson: {str(e)}"
        )


@router.delete("/{lesson_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_lesson(lesson_id: str):
    """
    Delete a lesson and all its questions.
    
    This action cannot be undone.
    """
    try:
        await lesson_service.delete_lesson(lesson_id)
    except Exception as e:
        logger.error(f"Error deleting lesson: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete lesson: {str(e)}"
        )



# QUESTION ENDPOINTS
@router.post("/{lesson_id}/questions", response_model=Question, status_code=status.HTTP_201_CREATED)
async def create_question(lesson_id: str, question_data: QuestionCreate):
    """
    Create a new question for a lesson.
    
    For multiple choice questions, include choices in the request.
    """
    try:
        question = await lesson_service.create_question(lesson_id, question_data)
        return question
    except Exception as e:
        logger.error(f"Error creating question: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create question: {str(e)}"
        )


@router.post("/questions/bulk", response_model=List[Question])
async def create_questions_bulk(bulk_data: BulkQuestionCreate):
    """
    Create multiple questions at once for a lesson.
    """
    try:
        questions = []
        for question_data in bulk_data.questions:
            question = await lesson_service.create_question(
                bulk_data.lesson_id, 
                question_data
            )
            questions.append(question)
        return questions
    except Exception as e:
        logger.error(f"Error creating bulk questions: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create questions: {str(e)}"
        )


@router.get("/questions/{question_id}", response_model=Question)
async def get_question(question_id: str):
    """Get a specific question by ID"""
    try:
        question = await lesson_service.get_question_by_id(question_id)
        if not question:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Question not found"
            )
        return question
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error fetching question: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to fetch question: {str(e)}"
        )


@router.put("/questions/{question_id}", response_model=Question)
async def update_question(question_id: str, question_data: QuestionUpdate):
    """Update an existing question"""
    try:
        question = await lesson_service.update_question(question_id, question_data)
        return question
    except Exception as e:
        logger.error(f"Error updating question: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update question: {str(e)}"
        )


@router.delete("/questions/{question_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_question(question_id: str):
    """Delete a question and all its choices"""
    try:
        await lesson_service.delete_question(question_id)
    except Exception as e:
        logger.error(f"Error deleting question: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete question: {str(e)}"
        )



# CHOICE ENDPOINTS
@router.post("/questions/{question_id}/choices", response_model=QuestionChoice, status_code=status.HTTP_201_CREATED)
async def create_choice(question_id: str, choice_data: QuestionChoiceCreate):
    """Create a new choice for a multiple choice question"""
    try:
        choice = await lesson_service.create_choice(question_id, choice_data)
        return choice
    except Exception as e:
        logger.error(f"Error creating choice: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create choice: {str(e)}"
        )


@router.delete("/choices/{choice_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_choice(choice_id: str):
    """Delete a choice"""
    try:
        await lesson_service.delete_choice(choice_id)
    except Exception as e:
        logger.error(f"Error deleting choice: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete choice: {str(e)}"
        )



# USER PROGRESS ENDPOINTS
@router.get("/progress/{user_id}/{lesson_id}", response_model=UserLessonProgress)
async def get_user_progress(user_id: str, lesson_id: str):
    """Get user's progress for a specific lesson"""
    try:
        progress = await lesson_service.get_user_lesson_progress(user_id, lesson_id)
        if not progress:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Progress not found"
            )
        return progress
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error fetching progress: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to fetch progress: {str(e)}"
        )


@router.post("/progress", response_model=UserLessonProgress)
async def update_user_progress(progress_data: UserLessonProgressCreate):
    """Create or update user's lesson progress"""
    try:
        progress = await lesson_service.upsert_user_lesson_progress(progress_data)
        return progress
    except Exception as e:
        logger.error(f"Error updating progress: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update progress: {str(e)}"
        )


@router.post("/submit-answers", response_model=SubmitLessonAnswersResponse)
async def submit_lesson_answers(request: SubmitLessonAnswersRequest):
    """
    Submit answers for a lesson and get score.
    
    This will:
    1. Save all user answers
    2. Calculate the score
    3. Update lesson progress
    4. Return results
    """
    try:
        result = await lesson_service.submit_lesson_answers(
            request.user_id,
            request.lesson_id,
            request.answers
        )
        logger.info(f"[SUBMIT_ANSWERS] Result from service: {result}")
        response = SubmitLessonAnswersResponse(**result)
        logger.info(f"[SUBMIT_ANSWERS] Response created: {response}")
        return response
    except Exception as e:
        logger.error(f"[SUBMIT_ANSWERS] ===== ERROR SUBMITTING ANSWERS =====")
        logger.error(f"[SUBMIT_ANSWERS] Error type: {type(e).__name__}")
        logger.error(f"[SUBMIT_ANSWERS] Error message: {str(e)}")
        import traceback
        logger.error(f"[SUBMIT_ANSWERS] Full traceback:\n{traceback.format_exc()}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to submit answers: {str(e)}"
        )



# MEDIA UPLOAD ENDPOINTS
@router.post("/media/upload", response_model=MediaUploadResponse)
async def upload_media(
    file: UploadFile = File(...),
    media_type: str = Form(...)  # "image", "audio", "video"
):
    """
    Upload media file (image, audio, video) to Supabase Storage.
    
    Supported formats:
    - Images: jpg, jpeg, png, gif, webp
    - Audio: mp3, wav, m4a, mov (audio)
    - Video: mp4, mov, webm
    """
    try:
        if not SupabaseManager.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Storage service not configured"
            )
        
        # Validate file type
        allowed_extensions = {
            "image": [".jpg", ".jpeg", ".png", ".gif", ".webp"],
            "audio": [".mp3", ".wav", ".m4a", ".mov"],
            "video": [".mp4", ".mov", ".webm"]
        }
        
        file_ext = Path(file.filename).suffix.lower()
        if file_ext not in allowed_extensions.get(media_type, []):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid file type for {media_type}"
            )
        
        # Generate unique filename
        unique_filename = f"{uuid.uuid4()}{file_ext}"
        storage_path = f"lesson-media/{media_type}s/{unique_filename}"
        
        # Read file content
        file_content = await file.read()
        file_size = len(file_content)
        
        # Upload to Supabase Storage
        supabase = SupabaseManager.get_client()
        bucket_name = "lesson-media"  # Create this bucket in Supabase
        
        # Upload file
        supabase.storage.from_(bucket_name).upload(
            storage_path,
            file_content,
            {"content-type": file.content_type}
        )
        
        # Get public URL
        public_url = supabase.storage.from_(bucket_name).get_public_url(storage_path)
        
        return MediaUploadResponse(
            url=public_url,
            file_name=file.filename,
            file_size=file_size,
            media_type=media_type
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error uploading media: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to upload media: {str(e)}"
        )


@router.delete("/media", status_code=status.HTTP_204_NO_CONTENT)
async def delete_media(file_url: str):
    """
    Delete a media file from storage.
    
    Provide the full URL of the file to delete.
    """
    try:
        if not SupabaseManager.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Storage service not configured"
            )
        
        # Extract path from URL
        # Expected format: https://[project].supabase.co/storage/v1/object/public/lesson-media/[path]
        url_parts = file_url.split("/storage/v1/object/public/")
        if len(url_parts) != 2:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid file URL format"
            )
        
        full_path = url_parts[1]
        bucket_name = full_path.split("/")[0]
        file_path = "/".join(full_path.split("/")[1:])
        
        # Delete file
        supabase = SupabaseManager.get_client()
        supabase.storage.from_(bucket_name).remove([file_path])
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error deleting media: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete media: {str(e)}"
        )
