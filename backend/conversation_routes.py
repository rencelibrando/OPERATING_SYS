"""
FastAPI WebSocket routes for real-time voice conversations with AI tutor.
"""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException
from typing import Dict, Optional
import asyncio
import json
from conversation_service_simple import ConversationAgent, get_greeting_for_scenario, create_conversation_agent

router = APIRouter(prefix="/conversation", tags=["conversation"])

# Active conversation sessions
active_sessions: Dict[str, ConversationAgent] = {}


@router.websocket("/ws/{session_id}")
async def conversation_websocket(websocket: WebSocket, session_id: str):
    """
    WebSocket endpoint for real-time voice conversation with AI tutor.
    
    Protocol:
    - Client sends: {"type": "start", "language": "fr", "level": "intermediate", "scenario": "travel"}
    - Client sends: {"type": "audio", "data": <base64_audio>}
    - Server sends: {"type": "transcript", "text": "..."}
    - Server sends: {"type": "agent_response", "text": "..."}
    - Server sends: {"type": "audio", "data": <base64_audio>}
    - Client sends: {"type": "stop"}
    """
    await websocket.accept()
    print(f"[WS] Client connected: {session_id}")
    
    agent: Optional[ConversationAgent] = None
    keep_alive_task = None
    
    try:
        async def send_transcript(text: str):
            """Send user transcript to client."""
            await websocket.send_json({
                "type": "transcript",
                "text": text,
                "role": "user"
            })
        
        async def send_agent_response(text: str):
            """Send agent response text to client."""
            await websocket.send_json({
                "type": "agent_response",
                "text": text,
                "role": "assistant"
            })
        
        async def send_audio_generated(audio_url: str, text: str):
            """Send generated audio URL to client (for Edge TTS)."""
            await websocket.send_json({
                "type": "audio_generated",
                "audio_url": audio_url,
                "text": text,
                "role": "assistant"
            })
        
        while True:
            # Receive message from client
            data = await websocket.receive_json()
            msg_type = data.get("type")
            
            if msg_type == "start":
                # Start a new conversation
                language = data.get("language", "fr")
                level = data.get("level", "intermediate")
                scenario = data.get("scenario", "daily_conversation")
                provider = data.get("provider", "gemini")
                
                print(f"[WS] Starting conversation: {language}/{level}/{scenario} (provider: {provider})")
                
                # Create conversation agent with Edge TTS support
                agent = create_conversation_agent(
                    language=language,
                    level=level,
                    scenario=scenario,
                    on_transcript=send_transcript,
                    on_agent_response=send_agent_response,
                    on_audio_generated=send_audio_generated,
                    provider=provider
                )
                
                # Start the agent
                await agent.start_conversation()
                active_sessions[session_id] = agent
                
                # Send initial greeting
                greeting = get_greeting_for_scenario(language, scenario, level)
                await agent.inject_agent_greeting(greeting)
                
                await websocket.send_json({
                    "type": "started",
                    "message": "Conversation started",
                    "greeting": greeting,
                    "uses_edge_tts": agent.use_edge_tts,
                    "provider": agent.provider
                })
            
            elif msg_type == "audio":
                # Process audio: transcribe + generate response
                if not agent:
                    await websocket.send_json({
                        "type": "error",
                        "message": "No active conversation"
                    })
                    continue
                
                import base64
                audio_b64 = data.get("data", "")
                audio_bytes = base64.b64decode(audio_b64)
                
                # Process audio and get AI response
                ai_response = await agent.process_audio(audio_bytes)
                
                if not ai_response:
                    await websocket.send_json({
                        "type": "error",
                        "message": "Failed to process audio"
                    })
            
            elif msg_type == "stop":
                # Stop conversation
                if agent:
                    await agent.stop_conversation()
                    if session_id in active_sessions:
                        del active_sessions[session_id]
                    
                    # Get conversation history
                    history = agent.get_conversation_history()
                    
                    await websocket.send_json({
                        "type": "stopped",
                        "message": "Conversation stopped",
                        "history": history
                    })
                    
                    agent = None
            
            elif msg_type == "ping":
                # Ping/pong for connection health
                await websocket.send_json({"type": "pong"})
            
            else:
                await websocket.send_json({
                    "type": "error",
                    "message": f"Unknown message type: {msg_type}"
                })
    
    except WebSocketDisconnect:
        print(f"[WS] Client disconnected: {session_id}")
    except Exception as e:
        print(f"[WS] Error: {e}")
        await websocket.send_json({
            "type": "error",
            "message": str(e)
        })
    finally:
        # Cleanup
        if agent:
            await agent.stop_conversation()
        if session_id in active_sessions:
            del active_sessions[session_id]
        print(f"[WS] Cleaned up session: {session_id}")


@router.get("/sessions")
async def get_active_sessions():
    """Get list of active conversation sessions with details."""
    sessions = []
    for session_id, agent in active_sessions.items():
        sessions.append({
            "session_id": session_id,
            "language": agent.language,
            "level": agent.level,
            "scenario": agent.scenario,
            "provider": agent.provider,
            "uses_edge_tts": agent.use_edge_tts,
            "history_length": len(agent.get_conversation_history())
        })
    
    return {
        "active_sessions": sessions,
        "count": len(sessions)
    }


@router.post("/sessions/{session_id}/generate-audio")
async def generate_audio_for_text(session_id: str, text: str):
    """
    Generate audio for specific text using Edge TTS (Chinese/Korean only).
    
    Args:
        session_id: Session identifier
        text: Text to convert to speech
    
    Returns:
        Audio URL
    """
    if session_id not in active_sessions:
        raise HTTPException(status_code=404, detail="Session not found")
    
    agent = active_sessions[session_id]
    
    if not agent.use_edge_tts:
        raise HTTPException(
            status_code=400, 
            detail=f"Edge TTS not available for language: {agent.language}"
        )
    
    try:
        # Generate audio
        audio_url = await agent.get_audio_url_for_text(text)
        
        if audio_url:
            return {
                "session_id": session_id,
                "text": text,
                "audio_url": audio_url,
                "language": agent.language,
                "status": "audio_generated"
            }
        else:
            raise HTTPException(status_code=500, detail="Audio generation failed")
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Audio generation failed: {str(e)}")


@router.post("/sessions/{session_id}/stop")
async def stop_session(session_id: str):
    """Stop a specific conversation session."""
    if session_id in active_sessions:
        agent = active_sessions[session_id]
        await agent.stop_conversation()
        del active_sessions[session_id]
        return {"message": "Session stopped", "session_id": session_id}
    else:
        raise HTTPException(status_code=404, detail="Session not found")
