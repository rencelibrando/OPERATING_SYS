"""
ElevenLabs Agent Platform API routes.
Handles WebSocket connections and REST endpoints for Chinese and Korean voice agents.
"""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException, Form, Body
from fastapi.responses import JSONResponse
from typing import Optional
from pydantic import BaseModel
import json
import uuid
import logging
from datetime import datetime

from elevenlabs_agent_service import get_elevenlabs_agent_service, ElevenLabsAgentService
from supabase_client import SupabaseManager


class StartConversationRequest(BaseModel):
    """Request model for starting an ElevenLabs conversation."""
    user_id: str
    language: str
    level: str = "intermediate"
    scenario: str = "language_tutor"

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/elevenlabs-agent", tags=["elevenlabs-agent"])
supabase_manager = SupabaseManager()


@router.post("/start")
async def start_elevenlabs_conversation(request: StartConversationRequest):
    """
    Start a new ElevenLabs Agent conversation.
    
    Request Body:
        user_id: User identifier
        language: Target language (Chinese /korean)
        level: Proficiency level (beginner/intermediate/advanced)
        scenario: Conversation scenario
    
    Returns:
        Session info including signed WebSocket URL for client connection
    """
    try:
        service = get_elevenlabs_agent_service()
        
        # Validate language is supported by ElevenLabs
        if not service.is_elevenlabs_language(request.language):
            raise HTTPException(
                status_code=400,
                detail=f"Language '{request.language}' is not configured for ElevenLabs Agent. Only Chinese and Korean are supported."
            )
        
        session_id = str(uuid.uuid4())
        
        result = await service.start_conversation(
            session_id=session_id,
            user_id=request.user_id,
            language=request.language,
            level=request.level,
            scenario=request.scenario
        )
        
        if result["success"]:
            # Save a session to a database (optional - table may not exist)
            try:
                client = supabase_manager.get_client()
                if client:
                    client.table("agent_sessions").insert({
                        "id": session_id,
                        "user_id": request.user_id,
                        "language": request.language,
                        "scenario": request.scenario,
                        "status": "active",
                        "created_at": datetime.utcnow().isoformat()
                    }).execute()
            except Exception as db_error:
                # Database logging is optional - don't fail the request
                logger.debug(f"Could not save session to database (table may not exist): {db_error}")
            
            return {
                "success": True,
                "session_id": session_id,
                "signed_url": result["signed_url"],
                "agent_id": result["agent_id"],
                "language": request.language,
                "level": request.level,
                "scenario": request.scenario,
                "provider": "elevenlabs",
                "message": result["message"]
            }
        else:
            raise HTTPException(status_code=500, detail=result.get("error", "Failed to start conversation"))
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error starting ElevenLabs conversation: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.websocket("/ws/{session_id}")
async def elevenlabs_websocket(websocket: WebSocket, session_id: str):
    """
    WebSocket endpoint for ElevenLabs Agent real-time communication.
    
    This endpoint proxies audio and messages between the client and ElevenLabs.
    
    Client → Backend → ElevenLabs Agent → Backend → Client
    
    Message types from a client:
    - Binary: Raw audio data from microphone
    - JSON {"type": "stop"}: End conversation
    - JSON {"type": "user_message", "text": "..."}: Text input
    
    Message types to a client:
    - Binary: TTS audio from agent
    - JSON {"type": "conversation_text", "role": "user/assistant", "content": "..."}
    - JSON {"type": "agent_message", "event": "Welcome/AgentStartedSpeaking/AgentAudioDone/..."}
    - JSON {"type": "error", "error": "..."}
    """
    await websocket.accept()
    logger.info(f"[ElevenLabsWS] Client connected for session {session_id}")
    
    service = get_elevenlabs_agent_service()
    
    # Verify session exists
    session = service.active_sessions.get(session_id)
    if not session:
        await websocket.send_text(json.dumps({
            "type": "error",
            "error": f"Session {session_id} not found. Call /elevenlabs-agent/start first."
        }))
        await websocket.close()
        return
    
    try:
        # Handle the WebSocket connection
        await service.handle_websocket_connection(
            session_id=session_id,
            client_websocket=websocket,
            on_transcript=lambda text, role, sid: logger.info(f"[{sid}] {role}: {text}"),
            on_audio_response=lambda audio, sid: logger.debug(f"[{sid}] Audio: {len(audio)} bytes")
        )
        
    except WebSocketDisconnect:
        logger.info(f"[ElevenLabsWS] Client disconnected from session {session_id}")
    except Exception as e:
        logger.error(f"[ElevenLabsWS] Error in session {session_id}: {e}")
        try:
            await websocket.send_text(json.dumps({
                "type": "error",
                "error": str(e)
            }))
        except:
            pass
    finally:
        # Cleanup session
        await service.end_conversation(session_id)
        
        # Update database
        try:
            client = supabase_manager.get_client()
            if client:
                client.table("agent_sessions").update({
                    "status": "ended",
                    "ended_at": datetime.utcnow().isoformat()
                }).eq("id", session_id).execute()
        except Exception as db_error:
            logger.warning(f"Failed to update session in database: {db_error}")


@router.post("/end/{session_id}")
async def end_elevenlabs_conversation(session_id: str):
    """
    End an active ElevenLabs Agent conversation.
    
    Args:
        session_id: Session identifier
    
    Returns:
        Success status
    """
    try:
        service = get_elevenlabs_agent_service()
        success = await service.end_conversation(session_id)
        
        if success:
            # Update database
            try:
                client = supabase_manager.get_client()
                if client:
                    client.table("agent_sessions").update({
                        "status": "ended",
                        "ended_at": datetime.utcnow().isoformat()
                    }).eq("id", session_id).execute()
            except:
                pass
            
            return {"success": True, "message": "Conversation ended"}
        else:
            raise HTTPException(status_code=404, detail="Session not found")
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error ending conversation: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/history/{session_id}")
async def get_elevenlabs_conversation_history(session_id: str):
    """
    Get conversation history for a session.
    
    Args:
        session_id: Session identifier
    
    Returns:
        Conversation history
    """
    try:
        service = get_elevenlabs_agent_service()
        history = service.get_conversation_history(session_id)
        
        return {
            "success": True,
            "session_id": session_id,
            "history": history
        }
        
    except Exception as e:
        logger.error(f"Error getting conversation history: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/sessions")
async def get_active_elevenlabs_sessions():
    """
    Get all active ElevenLabs Agent sessions.
    
    Returns:
        List of active sessions
    """
    try:
        service = get_elevenlabs_agent_service()
        sessions = service.get_active_sessions()
        
        return {
            "success": True,
            "sessions": sessions,
            "count": len(sessions)
        }
        
    except Exception as e:
        logger.error(f"Error getting active sessions: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/languages")
async def get_elevenlabs_supported_languages():
    """
    Get languages supported by ElevenLabs Agent.
    
    Returns:
        List of supported languages
    """
    return {
        "success": True,
        "languages": [
            {
                "code": "chinese",
                "aliases": ["mandarin", "zh", "zh-cn", "zh-tw"],
                "name": "Mandarin Chinese",
                "flag": "🇨🇳",
                "provider": "elevenlabs"
            },
            {
                "code": "korean",
                "aliases": ["ko", "hanger"],
                "name": "Korean",
                "flag": "🇰🇷",
                "provider": "elevenlabs"
            }
        ],
        "note": "Spanish, German, and French use Deepgram pipeline"
    }


@router.get("/health")
async def elevenlabs_health_check():
    """
    Check the health status of ElevenLabs Agent service.
    
    Returns:
        Health status information
    """
    try:
        service = get_elevenlabs_agent_service()
        active_sessions = service.get_active_sessions()
        
        # Check if agent IDs are configured
        chinese_configured = bool(service.agent_configs.get("chinese", {}).agent_id if hasattr(service.agent_configs.get("chinese"), 'agent_id') else False)
        korean_configured = bool(service.agent_configs.get("korean", {}).agent_id if hasattr(service.agent_configs.get("korean"), 'agent_id') else False)
        
        return {
            "status": "healthy",
            "provider": "elevenlabs",
            "active_connections": len(active_sessions),
            "configuration": {
                "chinese_agent_configured": chinese_configured,
                "korean_agent_configured": korean_configured,
                "api_key_configured": bool(service.api_key)
            }
        }
        
    except Exception as e:
        return {
            "status": "unhealthy",
            "provider": "elevenlabs",
            "error": str(e)
        }
