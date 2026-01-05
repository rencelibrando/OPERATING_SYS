"""
Gladia Live Speech-to-Text WebSocket Service
Handles real-time audio streaming to Gladia API with automatic language detection
"""

import asyncio
import json
import logging
import websockets
from typing import Optional
from fastapi import WebSocket, WebSocketDisconnect
import httpx
from config import settings

logger = logging.getLogger(__name__)

class GladiaSTTService:
    """WebSocket service for Gladia live transcription"""
    
    def __init__(self):
        self.api_key = settings.gladia_api_key
        self.init_url = "https://api.gladia.io/v2/live"
        self.gladia_ws: Optional[websockets.WebSocketClientProtocol] = None
        self.session_id: Optional[str] = None
        
        if not self.api_key:
            logger.error("GLADIA_API_KEY not configured in settings")
        else:
            logger.info(f"Gladia API Key configured: {self.api_key[:10]}...")
        
    async def initialize_session(self, language: str = "auto") -> dict:
        """Initialize Gladia session and get WebSocket URL"""
        try:
            # Configure for automatic language detection
            config = {
                "model": "solaria-1",
                "encoding": "wav/pcm",
                "sample_rate": 16000,
                "bit_depth": 16,
                "channels": 1,
                "language_config": {
                    "languages": [],  # Empty for automatic detection
                    "code_switching": False
                },
                "messages_config": {
                    "receive_partial_transcripts": True
                }
            }
            
            # If specific language requested, set it
            if language != "auto":
                lang_mapping = {
                    "zh": "zh",
                    "ko": "ko",
                    "en": "en"
                }
                gladia_lang = lang_mapping.get(language, language)
                config["language_config"]["languages"] = [gladia_lang]
            
            logger.info(f"[Gladia] Initializing session with config: {config}")
            
            # POST to init endpoint
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    self.init_url,
                    headers={
                        "x-gladia-key": self.api_key,
                        "Content-Type": "application/json"
                    },
                    json=config,
                    timeout=30.0
                )
                
                if response.status_code != 201:
                    error_text = response.text
                    logger.error(f"[Gladia] Init failed: {response.status_code} - {error_text}")
                    raise Exception(f"Gladia init failed: {error_text}")
                
                result = response.json()
                self.session_id = result.get("id")
                ws_url = result.get("url")
                
                logger.info(f"[Gladia] Session initialized: {self.session_id}")
                logger.info(f"[Gladia] WebSocket URL: {ws_url}")
                
                return {
                    "session_id": self.session_id,
                    "ws_url": ws_url
                }
                
        except Exception as e:
            logger.error(f"[Gladia] Session init error: {e}")
            raise
    
    async def handle_client_connection(
        self,
        client_ws: WebSocket,
        language: str = "auto"
    ):
        """Handle client WebSocket connection and proxy to Gladia"""
        try:
            # Initialize Gladia session
            session_info = await self.initialize_session(language)
            ws_url = session_info["ws_url"]
            
            logger.info(f"[Gladia] Connecting to WebSocket: {ws_url}")
            
            # Connect to Gladia WebSocket
            self.gladia_ws = await websockets.connect(ws_url)
            logger.info("[Gladia] WebSocket connected")
            
            # Send welcome message to client
            await client_ws.send_text(json.dumps({
                "type": "connection",
                "event": "opened",
                "session_id": self.session_id
            }))
            
            # Create tasks for bidirectional streaming
            receive_task = asyncio.create_task(
                self._receive_from_gladia(client_ws)
            )
            send_task = asyncio.create_task(
                self._send_to_gladia(client_ws)
            )
            
            # Wait for either task to complete
            done, pending = await asyncio.wait(
                [receive_task, send_task],
                return_when=asyncio.FIRST_COMPLETED
            )
            
            # Cancel remaining tasks
            for task in pending:
                task.cancel()
            
        except Exception as e:
            logger.error(f"[Gladia] Connection error: {e}")
            try:
                await client_ws.send_text(json.dumps({
                    "type": "error",
                    "message": str(e)
                }))
            except:
                pass
        finally:
            await self.close()
    
    async def _receive_from_gladia(self, client_ws: WebSocket):
        """Receive messages from Gladia and forward to client"""
        last_partial_text = ""
        last_confidence = 0.0
        last_language = ""
        last_partial_time = None
        
        logger.info("[Gladia] Starting receive loop")
        
        try:
            while self.gladia_ws:
                message = await self.gladia_ws.recv()
                
                if isinstance(message, str):
                    data = json.loads(message)
                    logger.info(f"[Gladia] Received: {data.get('type')}")
                    
                    # Handle different message types
                    if data.get("type") == "transcript":
                        utterance = data.get("data", {}).get("utterance", {})
                        text = utterance.get("text", "")
                        is_final = data.get("data", {}).get("is_final", False)
                        confidence = utterance.get("confidence", 0.0)
                        language = data.get("data", {}).get("language", "")
                        
                        # Store last partial for speech_end handling
                        if not is_final:
                            last_partial_text = text
                            last_confidence = confidence
                            last_language = language
                            last_partial_time = asyncio.get_event_loop().time()
                        
                        logger.info(f"[Gladia] {'Final' if is_final else 'Partial'}: '{text}' (confidence: {confidence}, lang: {language})")
                        
                        # Format for Kotlin client
                        if is_final:
                            await client_ws.send_text(json.dumps({
                                "type": "conversation_text",
                                "content": text,
                                "role": "user",
                                "confidence": confidence,
                                "language": language
                            }))
                            # Clear stored partial
                            last_partial_text = ""
                            last_partial_time = None
                        else:
                            await client_ws.send_text(json.dumps({
                                "type": "agent_message",
                                "event": "PartialTranscript",
                                "content": text,
                                "role": "user"
                            }))
                    
                    elif data.get("type") == "speech_end":
                        logger.info("[Gladia] Speech ended")
                        # If we have a partial transcript, send it as final
                        if last_partial_text:
                            logger.info(f"[Gladia] Converting partial to final: '{last_partial_text}' (confidence: {last_confidence})")
                            await client_ws.send_text(json.dumps({
                                "type": "conversation_text",
                                "content": last_partial_text,
                                "role": "user",
                                "confidence": last_confidence,
                                "language": last_language
                            }))
                            last_partial_text = ""
                            last_partial_time = None
                
                # Check for timeout - if no new partial for 2 seconds, send last partial as final
                if last_partial_text and last_partial_time:
                    current_time = asyncio.get_event_loop().time()
                    if current_time - last_partial_time > 2.0:
                        logger.info(f"[Gladia] Timeout - converting partial to final: '{last_partial_text}'")
                        await client_ws.send_text(json.dumps({
                            "type": "conversation_text",
                            "content": last_partial_text,
                            "role": "user",
                            "confidence": last_confidence,
                            "language": last_language
                        }))
                        last_partial_text = ""
                        last_partial_time = None
                    
                    elif data.get("type") == "language_detection":
                        language = data.get("language", "")
                        logger.info(f"[Gladia] Language detected: {language}")
                        await client_ws.send_text(json.dumps({
                            "type": "language_detected",
                            "language": language
                        }))
                    
                    elif data.get("type") == "error":
                        error_msg = data.get("message", "Unknown error")
                        logger.error(f"[Gladia] Error: {error_msg}")
                        await client_ws.send_text(json.dumps({
                            "type": "error",
                            "message": error_msg
                        }))
                    
                    elif data.get("type") == "started":
                        logger.info("[Gladia] Session started")
                        await client_ws.send_text(json.dumps({
                            "type": "session_started"
                        }))
                    
                    elif data.get("type") == "ended":
                        logger.info("[Gladia] Session ended")
                        await client_ws.send_text(json.dumps({
                            "type": "session_ended"
                        }))
                        break
                        
        except websockets.exceptions.ConnectionClosed as e:
            logger.info(f"[Gladia] WebSocket connection closed: {e.code} - {e.reason}")
        except Exception as e:
            logger.error(f"[Gladia] Receive error: {e}")
            import traceback
            traceback.print_exc()
    
    async def _send_to_gladia(self, client_ws: WebSocket):
        """Receive audio from client and send to Gladia"""
        try:
            while True:
                # Receive audio from Kotlin client
                message = await client_ws.receive()
                
                if "bytes" in message:
                    # Binary audio data
                    audio_data = message["bytes"]
                    if self.gladia_ws:
                        await self.gladia_ws.send(audio_data)
                
                elif "text" in message:
                    # Text message (control commands)
                    data = json.loads(message["text"])
                    
                    if data.get("type") == "stop_recording":
                        logger.info("[Gladia] Stop recording requested")
                        break
                        
        except WebSocketDisconnect:
            logger.info("[Gladia] Client disconnected")
        except Exception as e:
            logger.error(f"[Gladia] Send error: {e}")
    
    async def close(self):
        """Close Gladia WebSocket connection"""
        if self.gladia_ws:
            try:
                await self.gladia_ws.close()
                logger.info("[Gladia] WebSocket closed")
            except Exception as e:
                logger.error(f"[Gladia] Error closing WebSocket: {e}")
            finally:
                self.gladia_ws = None
                self.session_id = None


# Singleton instance
gladia_service = GladiaSTTService()


async def handle_gladia_websocket(websocket: WebSocket, language: str = "auto"):
    """
    Main handler for Gladia WebSocket connections
    
    Args:
        websocket: FastAPI WebSocket connection from client
        language: Target language (auto for automatic detection)
    """
    await websocket.accept()
    logger.info(f"[Gladia] Client connected, language: {language}")
    
    try:
        await gladia_service.handle_client_connection(websocket, language)
    except Exception as e:
        logger.error(f"[Gladia] Handler error: {e}")
    finally:
        logger.info("[Gladia] Connection handler finished")
