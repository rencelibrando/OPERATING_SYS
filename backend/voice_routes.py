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
from config import settings

router = APIRouter(prefix="/voice", tags=["voice"])
voice_service = VoiceService()
supabase_manager = SupabaseManager()

import logging
logger = logging.getLogger(__name__)


@router.post("/transcribe", response_model=VoiceTranscribeResponse)
async def transcribe_audio(
    audio_file: UploadFile = File(...),
    language: VoiceLanguage = Form(...),
    model: str = Form(default="nova-3")
):
    """
    Transcribe an audio file using local Whisper (Deepgram removed).
    
    Args:
        audio_file: Audio file in WAV format
        language: Target language for transcription
        model: Model parameter (ignored - using local Whisper)
    
    Returns:
        Error message directing to local transcription endpoint
    """
    try:
        # Validate a file type
        if not audio_file.content_type.startswith("audio/"):
            raise HTTPException(status_code=400, detail="File must be an audio file")
        
        # Read audio data
        audio_data = await audio_file.read()
        
        # Deepgram is no longer available - return error directing to local endpoint
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
        transcript: Transcribed text (from local Whisper recommended)
        expected_text: Expected phrase or prompt (optional)
        language: Target language
        level: User proficiency level
        scenario: Practice scenario
        user_id: User identifier
    
    Returns:
        Structured feedback with scores and suggestions
    """
    try:
        # Create a feedback request
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
    Save a voice session to Supabase.
    
    Args:
        user_id: User identifier
        language: Practice language
        level: Proficiency level
        scenario: Practice scenario
        transcript: Transcribed speech
        audio_url: URL to a stored audio file (optional)
        feedback: JSON string with AI feedback results
        session_duration: Session duration in seconds
    
    Returns:
        Session save confirmation
    """
    try:
        # Parse feedback JSON
        feedback_data = json.loads(feedback)
        
        # Create a session save request
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
    Get a user's voice learning progress.
    
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
        URL of an uploaded file
    """
    try:
        # Validate a file type
        if not audio_file.content_type.startswith("audio/"):
            raise HTTPException(status_code=400, detail="File must be an audio file")
        
        # Generate unique filename
        timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        file_extension = os.path.splitext(audio_file.filename)[1]
        filename = f"{user_id}/{session_id or 'unknown'}_{timestamp}{file_extension}"
        
        # Upload to Supabase storage
        file_data = await audio_file.read()
        
        # Create a storage bucket if it doesn't exist
        client = supabase_manager.get_client()
        if not client:
            raise HTTPException(status_code=500, detail="Failed to get Supabase client")
        try:
            client.storage.create_bucket("voice-recordings", options={"public": False})
        except:
            # Bucket might already exist
            pass
        
        # Upload file
        result = client.storage.from_("voice-recordings").upload(
            path=filename,
            file=file_data,
            file_options={"content-type": audio_file.content_type}
        )
        
        if result.data is None:
            raise HTTPException(status_code=500, detail="Failed to upload file")
        
        # Get public URL
        url = client.storage.from_("voice-recordings").get_public_url(filename)
        
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
    Get a list of supported languages.
    
    Returns:
        List of supported languages with codes and names
    """
    try:
        languages = [
            {"code": VoiceLanguage.ENGLISH, "name": "English", "flag": "EN"},
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
    Get a list of proficiency levels.
    
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


@router.post("/conversation/recording/save")
async def save_conversation_recording(
    session_id: str = Form(...),
    user_id: str = Form(...),
    language: str = Form(...),
    transcript: str = Form(...),
    turn_count: int = Form(...),
    duration: float = Form(...),
    audio_file: Optional[UploadFile] = File(None)
):
    """
    Save a conversation recording with an audio file to Supabase.
    
    Args:
        session_id: Session identifier
        user_id: User identifier
        language: Language code (en, fr, de, ko, zh, es)
        transcript: Full conversation transcript
        turn_count: Number of conversation turns
        duration: Session duration in seconds
        audio_file: Optional audio file (WAV format)
    
    Returns:
        Conversation recording data with audio URL
    """
    try:
        logger.info(f"Saving conversation recording for session: {session_id}")
        
        client = supabase_manager.get_client()
        if not client:
            raise HTTPException(status_code=503, detail="Database not available")
        
        # Upload audio file if provided
        audio_url = None
        if audio_file:
            try:
                # Generate unique filename
                timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
                file_extension = os.path.splitext(audio_file.filename)[1] or ".wav"
                filename = f"conversations/{user_id}/{session_id}_{timestamp}{file_extension}"
                
                # Read file data
                file_data = await audio_file.read()
                
                # Create a bucket if it doesn't exist
                try:
                    client.storage.create_bucket("voice-recordings", options={"public": True})
                    logger.info("Created voice-recordings bucket as public")
                except Exception as bucket_error:
                    logger.info(f"Bucket creation or already exists: {str(bucket_error)}")
                    pass  # Bucket might already exist
                
                # Upload file
                result = client.storage.from_("voice-recordings").upload(
                    path=filename,
                    file=file_data,
                    file_options={"content-type": "audio/wav"}
                )
                
                # Supabase storage upload returns None data on success
                # Check for specific error conditions instead
                if hasattr(result, 'error') and result.error is not None:
                    logger.error(f"Upload failed: {result.error}")
                else:
                    # Get public URL
                    try:
                        audio_url = client.storage.from_("voice-recordings").get_public_url(filename)
                        logger.info(f"✅ Audio uploaded successfully: {audio_url}")
                    except Exception as url_error:
                        logger.error(f"❌ Failed to get public URL: {str(url_error)}")
                        # Try alternative method
                        try:
                            audio_url = f"{settings.supabase_url}/storage/v1/object/public/voice-recordings/{filename}"
                            logger.info(f"🔧 Using constructed URL: {audio_url}")
                        except Exception as alt_error:
                            logger.error(f"❌ Failed to construct URL: {str(alt_error)}")
                            audio_url = None
            except Exception as e:
                logger.error(f"Failed to upload audio: {str(e)}")
                # Continue without audio URL
        
        # Create a recording entry
        recording_data = {
            "id": str(uuid.uuid4()),
            "session_id": session_id,
            "user_id": user_id,
            "language": language,
            "audio_url": audio_url,
            "transcript": transcript,
            "turn_count": turn_count,
            "duration": duration,
            "created_at": datetime.utcnow().isoformat()
        }
        
        # Insert into the conversation_recordings table
        response = client.table("conversation_recordings").insert(recording_data).execute()
        
        if response.data:
            logger.info(f"✅ Conversation recording saved successfully!")
            logger.info(f"  ID: {recording_data['id']}")
            logger.info(f"  Session ID: {session_id}")
            logger.info(f"  User ID: {user_id}")
            logger.info(f"  Turn count: {turn_count}")
            return {
                "id": recording_data["id"],
                "sessionId": session_id,
                "userId": user_id,
                "language": language,
                "audioUrl": audio_url,
                "transcript": transcript,
                "turnCount": turn_count,
                "duration": duration,
                "createdAt": recording_data["created_at"]
            }
        else:
            raise HTTPException(status_code=500, detail="Failed to save recording to database")
        
    except Exception as e:
        logger.error(f"Error saving conversation recording: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to save recording: {str(e)}")


@router.get("/conversation/recording/{session_id}")
async def get_conversation_recording(session_id: str):
    """
    Get conversation recording by session ID.
    
    Args:
        session_id: Session identifier
    
    Returns:
        Conversation recording data
    """
    try:
        logger.info(f"Fetching conversation recording for session: {session_id}")
        
        client = supabase_manager.get_client()
        if not client:
            raise HTTPException(status_code=503, detail="Database not available")
        
        # Query conversation_recordings table
        response = client.table("conversation_recordings").select("*").eq("session_id", session_id).execute()
        
        if not response.data:
            raise HTTPException(status_code=404, detail="Recording not found")
        
        recording = response.data[0]
        
        return {
            "id": recording.get("id"),
            "sessionId": recording.get("session_id"),
            "userId": recording.get("user_id"),
            "language": recording.get("language"),
            "audioUrl": recording.get("audio_url"),
            "transcript": recording.get("transcript"),
            "turnCount": recording.get("turn_count", 0),
            "duration": recording.get("duration", 0.0),
            "createdAt": recording.get("created_at")
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error fetching conversation recording: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch recording: {str(e)}")


@router.get("/conversation/sessions/{user_id}")
async def get_conversation_sessions(user_id: str):
    """
    Get all conversation sessions for a user.
    
    Args:
        user_id: User identifier
    
    Returns:
        List of conversation sessions with transcripts and metadata
    """
    try:
        logger.info(f"Fetching conversation sessions for user: {user_id}")
        
        client = supabase_manager.get_client()
        if not client:
            raise HTTPException(status_code=503, detail="Database not available")
        
        sessions = []
        
        # Query conversation_recordings table for agent conversations
        recordings_response = client.table("conversation_recordings").select("*").eq("user_id", user_id).execute()
        logger.info(f"📊 Retrieved {len(recordings_response.data)} recordings from conversation_recordings table")
        if recordings_response.data:
            for rec in recordings_response.data:
                logger.info(f"  - Session: {rec.get('session_id')} | Turns: {rec.get('turn_count')} | Created: {rec.get('created_at')}")
        
        for recording in recordings_response.data:
            audio_url = recording.get("audio_url")
            logger.info(f"🎵 Recording {recording.get('session_id')} audio_url: {audio_url}")
            logger.info(f"🔍 Full recording data: {recording}")
            sessions.append({
                "sessionId": recording.get("session_id"),
                "userId": recording.get("user_id"),
                "language": recording.get("language", "english"),
                "level": "intermediate",
                "scenario": "conversation",
                "transcript": recording.get("transcript", ""),
                "audioUrl": audio_url,
                "turnCount": recording.get("turn_count", 0),
                "duration": recording.get("duration", 0.0),
                "createdAt": recording.get("created_at", ""),
                "feedback": None
            })
            logger.info(f"📤 Session data being sent: audioUrl={audio_url}")
        
        # Query voice_sessions table for practice sessions
        voice_response = client.table("voice_sessions").select("*").eq("user_id", user_id).execute()
        logger.info(f"📊 Retrieved {len(voice_response.data)} sessions from voice_sessions table")
        if voice_response.data:
            for sess in voice_response.data:
                logger.info(f"  - Session: {sess.get('session_id', sess.get('id'))} | Language: {sess.get('language')} | Created: {sess.get('created_at')}")
        
        for session in voice_response.data:
            sessions.append({
                "sessionId": session.get("session_id", session.get("id")),
                "userId": session.get("user_id"),
                "language": session.get("language", "english"),
                "level": session.get("level", "intermediate"),
                "scenario": session.get("scenario", "conversation"),
                "transcript": session.get("transcript", ""),
                "audioUrl": session.get("audio_url"),
                "turnCount": session.get("feedback", {}).get("turns", 0) if isinstance(session.get("feedback"), dict) else 0,
                "duration": session.get("session_duration", 0.0),
                "createdAt": session.get("created_at", ""),
                "feedback": session.get("feedback")
            })
        
        # Sort by created_at descending
        sessions.sort(key=lambda x: x.get("createdAt", ""), reverse=True)
        
        logger.info(f"Retrieved {len(sessions)} total conversation sessions")
        return sessions
        
    except Exception as e:
        logger.error(f"Error fetching conversation sessions: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch sessions: {str(e)}")


@router.post("/conversation/session/delete/{session_id}")
async def delete_conversation_session(session_id: str):
    """
    Delete a conversation session and its associated data.
    
    Args:
        session_id: Session identifier
    
    Returns:
        Deletion confirmation
    """
    try:
        logger.info(f"Deleting conversation session: {session_id}")
        
        client = supabase_manager.get_client()
        if not client:
            raise HTTPException(status_code=503, detail="Database not available")
        
        # Delete conversation feedback if exists
        try:
            feedback_result = client.table("conversation_feedback") \
                .delete() \
                .eq("session_id", session_id) \
                .execute()
            logger.info(f"Deleted conversation feedback for session: {session_id}")
        except Exception as e:
            logger.warning(f"Failed to delete conversation feedback: {str(e)}")
        
        # Delete conversation recording if exists
        try:
            recording_result = client.table("conversation_recordings") \
                .delete() \
                .eq("session_id", session_id) \
                .execute()
            logger.info(f"Deleted conversation recording for session: {session_id}")
        except Exception as e:
            logger.warning(f"Failed to delete conversation recording: {str(e)}")
        
        # Delete from agent_sessions if exists
        try:
            agent_result = client.table("agent_sessions") \
                .delete() \
                .eq("id", session_id) \
                .execute()
            logger.info(f"Deleted agent session: {session_id}")
        except Exception as e:
            logger.warning(f"Failed to delete agent session: {str(e)}")
        
        # Delete from voice_sessions if exists
        try:
            voice_result = client.table("voice_sessions") \
                .delete() \
                .eq("id", session_id) \
                .execute()
            logger.info(f"Deleted voice session: {session_id}")
        except Exception as e:
            logger.warning(f"Failed to delete voice session: {str(e)}")
        
        logger.info(f"✅ Successfully deleted session: {session_id}")
        return {"success": True, "message": "Session deleted successfully"}
        
    except Exception as e:
        logger.error(f"Failed to delete conversation session: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to delete session: {str(e)}")
