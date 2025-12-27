"""
Voice Agent API routes for conversational interactions using Deepgram's Voice Agent.
"""
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Depends, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from typing import Optional, List, Dict, Any
import json
import uuid
import asyncio
import base64
from datetime import datetime

from agent_service import VoiceAgentService
from agent_models import (
    AgentStartRequest,
    AgentStartResponse,
    AgentAudioRequest,
    AgentAudioResponse,
    AgentTextMessageRequest,
    AgentTextMessageResponse,
    AgentUpdateResponse,
    AgentPromptUpdateRequest,
    AgentVoiceUpdateRequest,
    AgentEndRequest,
    AgentEndResponse,
    AgentHistoryRequest,
    AgentHistoryResponse,
    AgentActiveSessionsResponse,
    AgentScenario,
    AgentLanguage,
    AgentVoice,
    AgentEvent,
    AgentStatus,
    AgentConfiguration,
    AgentCapabilities,
    AgentHealthCheck
)
from supabase_client import SupabaseManager

router = APIRouter(prefix="/agent", tags=["voice-agent"])
agent_service = VoiceAgentService()
supabase_manager = SupabaseManager()

# WebSocket connection manager
class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
        self.connection_sessions: Dict[str, str] = {}  # connection_id -> session_id
    
    async def connect(self, websocket: WebSocket, session_id: str):
        await websocket.accept()
        connection_id = str(uuid.uuid4())
        self.active_connections[connection_id] = websocket
        self.connection_sessions[connection_id] = session_id
        return connection_id
    
    def disconnect(self, connection_id: str):
        if connection_id in self.active_connections:
            del self.active_connections[connection_id]
        if connection_id in self.connection_sessions:
            del self.connection_sessions[connection_id]
    
    async def send_personal_message(self, message: dict, connection_id: str):
        if connection_id in self.active_connections:
            await self.active_connections[connection_id].send_text(json.dumps(message))
    
    async def broadcast_to_session(self, message: dict, session_id: str):
        for connection_id, ws_session_id in self.connection_sessions.items():
            if ws_session_id == session_id and connection_id in self.active_connections:
                await self.active_connections[connection_id].send_text(json.dumps(message))

manager = ConnectionManager()


@router.post("/start", response_model=AgentStartResponse)
async def start_conversation(request: AgentStartRequest):
    """
    Start a new voice agent conversation.
    
    Args:
        request: Agent start configuration
    
    Returns:
        Session information and connection details
    """
    try:
        # Generate unique session ID
        session_id = str(uuid.uuid4())
        
        # Define message callback for WebSocket events
        def on_message(message_data: Dict[str, Any]):
            # This will be handled by WebSocket connections
            asyncio.create_task(manager.broadcast_to_session(message_data, session_id))
        
        def on_audio(audio_data: bytes, session_id: str):
            # Handle audio data - could be streamed to client
            pass
        
        # Start conversation
        result = await agent_service.start_conversation(
            session_id=session_id,
            user_id=request.user_id,
            language=request.language.value,
            scenario=request.scenario.value,
            voice_model=request.voice_model.value,
            temperature=request.temperature,
            custom_prompt=request.custom_prompt,
            on_message=on_message,
            on_audio=on_audio
        )
        
        return AgentStartResponse(**result)
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to start conversation: {str(e)}")


@router.post("/audio", response_model=AgentAudioResponse)
async def send_audio(request: AgentAudioRequest):
    """
    Send audio data to the voice agent.
    
    Args:
        request: Audio data with session ID
    
    Returns:
        Success status
    """
    try:
        success = agent_service.send_audio(request.session_id, request.audio_data)
        
        if success:
            return AgentAudioResponse(
                success=True,
                message="Audio data sent successfully"
            )
        else:
            return AgentAudioResponse(
                success=False,
                message="Failed to send audio data - session not found or inactive"
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to send audio: {str(e)}")


@router.post("/message", response_model=AgentTextMessageResponse)
async def send_text_message(request: AgentTextMessageRequest):
    """
    Send a text message to the voice agent.
    
    Args:
        request: Text message with session ID
    
    Returns:
        Success status
    """
    try:
        success = agent_service.send_text_message(request.session_id, request.content)
        
        if success:
            return AgentTextMessageResponse(
                success=True,
                message="Text message sent successfully"
            )
        else:
            return AgentTextMessageResponse(
                success=False,
                message="Failed to send text message - session not found or inactive"
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to send text message: {str(e)}")


@router.put("/prompt", response_model=AgentUpdateResponse)
async def update_agent_prompt(request: AgentPromptUpdateRequest):
    """
    Update the agent's prompt during conversation.
    
    Args:
        request: Prompt update request
    
    Returns:
        Success status
    """
    try:
        success = agent_service.update_agent_prompt(request.session_id, request.new_prompt)
        
        if success:
            return AgentUpdateResponse(
                success=True,
                message="Agent prompt updated successfully"
            )
        else:
            return AgentUpdateResponse(
                success=False,
                message="Failed to update prompt - session not found or inactive"
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to update prompt: {str(e)}")


@router.put("/voice", response_model=AgentUpdateResponse)
async def update_agent_voice(request: AgentVoiceUpdateRequest):
    """
    Update the agent's voice model during conversation.
    
    Args:
        request: Voice update request
    
    Returns:
        Success status
    """
    try:
        success = agent_service.update_agent_voice(request.session_id, request.voice_model.value)
        
        if success:
            return AgentUpdateResponse(
                success=True,
                message="Agent voice updated successfully"
            )
        else:
            return AgentUpdateResponse(
                success=False,
                message="Failed to update voice - session not found or inactive"
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to update voice: {str(e)}")


@router.post("/end", response_model=AgentEndResponse)
async def end_conversation(request: AgentEndRequest):
    """
    End an active conversation.
    
    Args:
        request: End conversation request
    
    Returns:
        Success status
    """
    try:
        success = agent_service.end_conversation(request.session_id)
        
        if success:
            # Update session in database
            try:
                supabase_manager.client.table("agent_sessions").update({
                    "status": "ended",
                    "ended_at": datetime.utcnow().isoformat()
                }).eq("id", request.session_id).execute()
            except:
                pass  # Database update is not critical
            
            return AgentEndResponse(
                success=True,
                message="Conversation ended successfully"
            )
        else:
            return AgentEndResponse(
                success=False,
                message="Failed to end conversation - session not found or already ended"
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to end conversation: {str(e)}")


@router.get("/history/{session_id}", response_model=AgentHistoryResponse)
async def get_conversation_history(session_id: str):
    """
    Get conversation history for a session.
    
    Args:
        session_id: Session identifier
    
    Returns:
        Conversation history and session info
    """
    try:
        # Get conversation history from service
        history = agent_service.get_conversation_history(session_id)
        
        # Get session info from database
        session_info = None
        try:
            response = supabase_manager.client.table("agent_sessions").select("*").eq("id", session_id).execute()
            if response.data:
                session_data = response.data[0]
                session_info = {
                    "session_id": session_data["id"],
                    "user_id": session_data["user_id"],
                    "language": session_data["language"],
                    "scenario": session_data["scenario"],
                    "status": session_data["status"],
                    "created_at": session_data["created_at"],
                    "ended_at": session_data.get("ended_at"),
                    "duration": session_data.get("duration", 0)
                }
        except:
            pass
        
        return AgentHistoryResponse(
            success=True,
            conversation_history=history,
            session_info=session_info
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get conversation history: {str(e)}")


@router.get("/sessions", response_model=AgentActiveSessionsResponse)
async def get_active_sessions():
    """
    Get all active conversation sessions.
    
    Returns:
        List of active sessions
    """
    try:
        active_sessions = agent_service.get_active_sessions()
        
        # Convert to response model
        session_infos = []
        for session in active_sessions:
            session_infos.append({
                "session_id": session["session_id"],
                "user_id": session["user_id"],
                "language": session["language"],
                "scenario": session["scenario"],
                "status": AgentStatus.ACTIVE,
                "start_time": datetime.fromisoformat(session["start_time"]),
                "duration": session["duration"]
            })
        
        return AgentActiveSessionsResponse(
            success=True,
            active_sessions=session_infos,
            total_count=len(session_infos)
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get active sessions: {str(e)}")


@router.get("/scenarios")
async def get_available_scenarios():
    """
    Get available conversation scenarios.
    
    Returns:
        List of scenarios with descriptions
    """
    try:
        scenarios = [
            {
                "value": AgentScenario.LANGUAGE_TUTOR,
                "name": "Language Tutor",
                "description": "Practice speaking with a patient language tutor who provides gentle corrections and vocabulary help."
            },
            {
                "value": AgentScenario.CONVERSATION_PARTNER,
                "name": "Conversation Partner",
                "description": "Engage in natural, flowing conversation on various topics."
            },
            {
                "value": AgentScenario.INTERVIEW_PRACTICE,
                "name": "Interview Practice",
                "description": "Practice professional interview skills with feedback on your responses."
            },
            {
                "value": AgentScenario.TRAVEL_COMPANION,
                "name": "Travel Companion",
                "description": "Practice travel-related conversations and learn travel vocabulary."
            }
        ]
        
        return {"success": True, "scenarios": scenarios}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get scenarios: {str(e)}")


@router.get("/languages")
async def get_available_languages():
    """
    Get available conversation languages.
    
    Returns:
        List of supported languages
    """
    try:
        languages = [
            {"value": AgentLanguage.ENGLISH, "name": "English", "flag": "🇺🇸"},
            {"value": AgentLanguage.FRENCH, "name": "French", "flag": "🇫🇷"},
            {"value": AgentLanguage.GERMAN, "name": "German", "flag": "🇩🇪"},
            {"value": AgentLanguage.KOREAN, "name": "Korean", "flag": "🇰🇷"},
            {"value": AgentLanguage.MANDARIN, "name": "Mandarin Chinese", "flag": "🇨🇳"},
            {"value": AgentLanguage.SPANISH, "name": "Spanish", "flag": "🇪🇸"}
        ]
        
        return {"success": True, "languages": languages}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get languages: {str(e)}")


@router.get("/voices")
async def get_available_voices():
    """
    Get available voice models.
    
    Returns:
        List of supported voice models
    """
    try:
        voices = [
            {
                "value": AgentVoice.THALIA,
                "name": "Thalia",
                "description": "Natural female voice, warm and friendly",
                "language": "English"
            },
            {
                "value": AgentVoice.ANDROMEDA,
                "name": "Andromeda",
                "description": "Natural female voice, clear and articulate",
                "language": "English"
            },
            {
                "value": AgentVoice.APOLLO,
                "name": "Apollo",
                "description": "Natural male voice, confident and professional",
                "language": "English"
            },
            {
                "value": AgentVoice.ARIES,
                "name": "Aries",
                "description": "Natural male voice, energetic and engaging",
                "language": "English"
            },
            {
                "value": AgentVoice.ARCAS,
                "name": "Arcas",
                "description": "Natural male voice, calm and soothing",
                "language": "English"
            },
            {
                "value": AgentVoice.HELENA,
                "name": "Helena",
                "description": "Natural female voice, sophisticated and elegant",
                "language": "English"
            }
        ]
        
        return {"success": True, "voices": voices}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get voices: {str(e)}")


@router.get("/capabilities", response_model=AgentCapabilities)
async def get_agent_capabilities():
    """
    Get agent capabilities and configuration limits.
    
    Returns:
        Agent capabilities information
    """
    try:
        return AgentCapabilities(
            supported_languages=list(AgentLanguage),
            supported_scenarios=list(AgentScenario),
            supported_voices=list(AgentVoice),
            max_session_duration=120,  # 2 hours
            max_audio_chunk_size=8192,  # bytes
            supported_audio_formats=["linear16", "wav", "mp3"]
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get capabilities: {str(e)}")


@router.get("/health", response_model=AgentHealthCheck)
async def health_check():
    """
    Check the health status of the voice agent service.
    
    Returns:
        Health status information
    """
    try:
        active_sessions = agent_service.get_active_sessions()
        
        return AgentHealthCheck(
            status="healthy",
            api_version="1.0.0",
            active_connections=len(active_sessions),
            memory_usage=None,  # Could be implemented with psutil
            cpu_usage=None,     # Could be implemented with psutil
            last_error=None,
            uptime=0.0  # Could be tracked from service start time
        )
        
    except Exception as e:
        return AgentHealthCheck(
            status="unhealthy",
            api_version="1.0.0",
            active_connections=0,
            last_error=str(e),
            uptime=0.0
        )


@router.websocket("/ws/{session_id}")
async def websocket_endpoint(websocket: WebSocket, session_id: str):
    """
    WebSocket endpoint for real-time communication with the voice agent.
    
    Args:
        websocket: WebSocket connection
        session_id: Session identifier
    """
    await manager.connect(websocket, session_id)
    
    try:
        while True:
            # Receive message from client
            data = await websocket.receive_text()
            message = json.loads(data)
            
            # Handle different message types
            if message.get("type") == "audio":
                # Decode base64 audio data
                audio_data = base64.b64decode(message.get("data", ""))
                agent_service.send_audio(session_id, audio_data)
                
            elif message.get("type") == "text":
                # Send text message to agent
                content = message.get("content", "")
                agent_service.send_text_message(session_id, content)
                
            elif message.get("type") == "ping":
                # Respond to ping
                await manager.send_personal_message({
                    "type": "pong",
                    "timestamp": datetime.utcnow().isoformat()
                }, connection_id=list(manager.connection_sessions.keys())[list(manager.connection_sessions.values()).index(session_id)])
                
    except WebSocketDisconnect:
        manager.disconnect(list(manager.connection_sessions.keys())[list(manager.connection_sessions.values()).index(session_id)])
        # End the conversation when WebSocket disconnects
        agent_service.end_conversation(session_id)
        
    except Exception as e:
        print(f"WebSocket error for session {session_id}: {str(e)}")
        manager.disconnect(list(manager.connection_sessions.keys())[list(manager.connection_sessions.values()).index(session_id)])


@router.get("/user/{user_id}/sessions")
async def get_user_sessions(user_id: str, limit: int = 10):
    """
    Get conversation sessions for a specific user.
    
    Args:
        user_id: User identifier
        limit: Maximum number of sessions to return
    
    Returns:
        List of user sessions
    """
    try:
        response = supabase_manager.client.table("agent_sessions").select("*").eq("user_id", user_id).order("created_at", desc=True).limit(limit).execute()
        sessions = response.data
        
        return {"success": True, "sessions": sessions}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get user sessions: {str(e)}")


@router.get("/user/{user_id}/stats")
async def get_user_stats(user_id: str):
    """
    Get usage statistics for a specific user.
    
    Args:
        user_id: User identifier
    
    Returns:
        User usage statistics
    """
    try:
        # Get all sessions for the user
        response = supabase_manager.client.table("agent_sessions").select("*").eq("user_id", user_id).execute()
        sessions = response.data
        
        if not sessions:
            return {
                "success": True,
                "total_sessions": 0,
                "total_duration": 0,
                "favorite_language": None,
                "favorite_scenario": None,
                "last_session": None
            }
        
        # Calculate statistics
        total_sessions = len(sessions)
        total_duration = sum(s.get("duration", 0) for s in sessions)
        
        # Find favorites
        languages = {}
        scenarios = {}
        for session in sessions:
            lang = session.get("language", "unknown")
            scenario = session.get("scenario", "unknown")
            languages[lang] = languages.get(lang, 0) + 1
            scenarios[scenario] = scenarios.get(scenario, 0) + 1
        
        favorite_language = max(languages, key=languages.get) if languages else None
        favorite_scenario = max(scenarios, key=scenarios.get) if scenarios else None
        
        # Find last session
        last_session = max(sessions, key=lambda s: s.get("created_at", ""))["created_at"]
        
        return {
            "success": True,
            "total_sessions": total_sessions,
            "total_duration": total_duration,
            "favorite_language": favorite_language,
            "favorite_scenario": favorite_scenario,
            "last_session": last_session
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get user stats: {str(e)}")
