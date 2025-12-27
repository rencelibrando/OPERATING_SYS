"""
Voice Tutor API routes for speech-to-text and feedback.
"""
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Depends
from fastapi.responses import JSONResponse
from typing import Optional
import json
import uuid
import os
from datetime import datetime

from voice_service import VoiceService
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
from supabase_client import SupabaseManager

router = APIRouter(prefix="/voice", tags=["voice"])
voice_service = VoiceService()
supabase_manager = SupabaseManager()


@router.post("/transcribe", response_model=VoiceTranscribeResponse)
async def transcribe_audio(
    audio_file: UploadFile = File(...),
    language: VoiceLanguage = Form(...),
    model: str = Form(default="nova-3")
):
    """
    Transcribe audio file using Deepgram API.
    
    Args:
        audio_file: Audio file in WAV format
        language: Target language for transcription
        model: Deepgram model to use (default: nova-3)
    
    Returns:
        Transcription result with confidence score
    """
    try:
        # Validate file type
        if not audio_file.content_type.startswith("audio/"):
            raise HTTPException(status_code=400, detail="File must be an audio file")
        
        # Read audio data
        audio_data = await audio_file.read()
        
        # Create transcription request
        request = VoiceTranscribeRequest(
            audio_data=audio_data,
            language=language,
            model=model
        )
        
        # Transcribe audio
        response = await voice_service.transcribe_audio(request)
        
        return response
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")


@router.post("/feedback", response_model=VoiceFeedbackResponse)
async def generate_feedback(
    transcript: str = Form(...),
    expected_text: Optional[str] = Form(None),
    language: VoiceLanguage = Form(...),
    level: VoiceLevel = Form(...),
    scenario: VoiceScenario = Form(...),
    user_id: str = Form(...)
):
    """
    Generate AI feedback for transcribed speech.
    
    Args:
        transcript: Transcribed text from Deepgram
        expected_text: Expected phrase or prompt (optional)
        language: Target language
        level: User proficiency level
        scenario: Practice scenario
        user_id: User identifier
    
    Returns:
        Structured feedback with scores and suggestions
    """
    try:
        # Create feedback request
        request = VoiceFeedbackRequest(
            transcript=transcript,
            expected_text=expected_text,
            language=language,
            level=level,
            scenario=scenario,
            user_id=user_id
        )
        
        # Generate feedback
        response = await voice_service.generate_feedback(request)
        
        return response
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Feedback generation failed: {str(e)}")


@router.post("/session/save", response_model=VoiceSessionSaveResponse)
async def save_session(
    user_id: str = Form(...),
    language: VoiceLanguage = Form(...),
    level: VoiceLevel = Form(...),
    scenario: VoiceScenario = Form(...),
    transcript: str = Form(...),
    audio_url: Optional[str] = Form(None),
    feedback: str = Form(...),
    session_duration: float = Form(...)
):
    """
    Save voice session to Supabase.
    
    Args:
        user_id: User identifier
        language: Practice language
        level: Proficiency level
        scenario: Practice scenario
        transcript: Transcribed speech
        audio_url: URL to stored audio file (optional)
        feedback: JSON string with AI feedback results
        session_duration: Session duration in seconds
    
    Returns:
        Session save confirmation
    """
    try:
        # Parse feedback JSON
        feedback_data = json.loads(feedback)
        
        # Create session save request
        request = VoiceSessionSaveRequest(
            user_id=user_id,
            language=language,
            level=level,
            scenario=scenario,
            transcript=transcript,
            audio_url=audio_url,
            feedback=feedback_data,
            session_duration=session_duration
        )
        
        # Save session
        response = await voice_service.save_session(request)
        
        return response
        
    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid feedback JSON format")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Session save failed: {str(e)}")


@router.post("/progress", response_model=VoiceProgressResponse)
async def get_progress(
    user_id: str = Form(...),
    language: Optional[VoiceLanguage] = Form(None),
    days: int = Form(default=30)
):
    """
    Get user's voice learning progress.
    
    Args:
        user_id: User identifier
        language: Filter by language (optional)
        days: Number of days to look back (default: 30)
    
    Returns:
        Progress statistics and trends
    """
    try:
        # Create progress request
        request = VoiceProgressRequest(
            user_id=user_id,
            language=language,
            days=days
        )
        
        # Get progress
        response = await voice_service.get_progress(request)
        
        return response
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Progress retrieval failed: {str(e)}")


@router.post("/upload-audio")
async def upload_audio(
    audio_file: UploadFile = File(...),
    user_id: str = Form(...),
    session_id: Optional[str] = Form(None)
):
    """
    Upload audio file to Supabase storage.
    
    Args:
        audio_file: Audio file to upload
        user_id: User identifier
        session_id: Session identifier (optional)
    
    Returns:
        URL of uploaded file
    """
    try:
        # Validate file type
        if not audio_file.content_type.startswith("audio/"):
            raise HTTPException(status_code=400, detail="File must be an audio file")
        
        # Generate unique filename
        timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        file_extension = os.path.splitext(audio_file.filename)[1]
        filename = f"{user_id}/{session_id or 'unknown'}_{timestamp}{file_extension}"
        
        # Upload to Supabase storage
        file_data = await audio_file.read()
        
        # Create storage bucket if it doesn't exist
        try:
            supabase_manager.client.storage.create_bucket("voice-recordings", options={"public": False})
        except:
            # Bucket might already exist
            pass
        
        # Upload file
        result = supabase_manager.client.storage.from_("voice-recordings").upload(
            path=filename,
            file=file_data,
            file_options={"content-type": audio_file.content_type}
        )
        
        if result.data is None:
            raise HTTPException(status_code=500, detail="Failed to upload file")
        
        # Get public URL
        url = supabase_manager.client.storage.from_("voice-recordings").get_public_url(filename)
        
        return {"success": True, "url": url, "filename": filename}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Audio upload failed: {str(e)}")


@router.get("/scenarios/{language}/{level}")
async def get_scenarios(
    language: VoiceLanguage,
    level: VoiceLevel
):
    """
    Get available scenarios for a language and level.
    
    Args:
        language: Target language
        level: Proficiency level
    
    Returns:
        List of scenarios with sample prompts
    """
    try:
        scenarios = voice_service.scenario_prompts
        
        # Filter scenarios for the requested language and level
        result = {}
        for scenario_key, prompts in scenarios.items():
            if level.value in prompts:
                result[scenario_key] = {
                    "name": scenario_key.replace("_", " ").title(),
                    "prompts": prompts[level.value][:5]  # Return first 5 prompts
                }
        
        return {"success": True, "scenarios": result}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get scenarios: {str(e)}")


@router.get("/languages")
async def get_languages():
    """
    Get list of supported languages.
    
    Returns:
        List of supported languages with codes and names
    """
    try:
        languages = [
            {"code": VoiceLanguage.FRENCH, "name": "French", "flag": "🇫🇷"},
            {"code": VoiceLanguage.GERMAN, "name": "German", "flag": "🇩🇪"},
            {"code": VoiceLanguage.KOREAN, "name": "Korean", "flag": "🇰🇷"},
            {"code": VoiceLanguage.MANDARIN, "name": "Mandarin Chinese", "flag": "🇨🇳"},
            {"code": VoiceLanguage.SPANISH, "name": "Spanish", "flag": "🇪🇸"}
        ]
        
        return {"success": True, "languages": languages}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get languages: {str(e)}")


@router.get("/levels")
async def get_levels():
    """
    Get list of proficiency levels.
    
    Returns:
        List of available levels
    """
    try:
        levels = [
            {"code": VoiceLevel.BEGINNER, "name": "Beginner"},
            {"code": VoiceLevel.INTERMEDIATE, "name": "Intermediate"},
            {"code": VoiceLevel.ADVANCED, "name": "Advanced"}
        ]
        
        return {"success": True, "levels": levels}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get levels: {str(e)}")
