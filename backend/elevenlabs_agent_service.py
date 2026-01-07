"""
ElevenLabs Agent Platform Service
Handles conversational AI agents for Chinese and Korean language tutoring.
Uses the official ElevenLabs Python SDK for Conversational AI.

Based on ElevenLabs documentation:
https://elevenlabs.io/docs/conversational-ai/docs/introduction
"""
import asyncio
import json
import logging
import base64
import websockets
from typing import Optional, Dict, Any, Callable, List
from datetime import datetime
from dataclasses import dataclass
from enum import Enum

from elevenlabs.client import ElevenLabs
import uuid

from config import settings
from supabase_client import SupabaseManager

logger = logging.getLogger(__name__)


class ElevenLabsAgentLanguage(str, Enum):
    """Supported languages for ElevenLabs Agent"""
    CHINESE = "zh"
    KOREAN = "ko"
    ENGLISH = "en"
    FRENCH = "fr"
    GERMAN = "de"
    SPANISH = "es"


@dataclass
class ElevenLabsAgentConfig:
    """Configuration for an ElevenLabs Agent"""
    agent_id: str
    language: ElevenLabsAgentLanguage
    name: str
    description: str


class ElevenLabsAgentService:
    """
    Service for ElevenLabs Conversational AI Agent Platform.
    Handles real-time voice conversations for language tutoring.
    
    Supported languages: Chinese, Korean, English, French, German, Spanish
    
    Uses the official ElevenLabs Python SDK for:
    - Agent management (create, update, get)
    - Real-time conversations via WebSocket
    - Audio streaming with low latency
    
    Agents should be pre-configured in ElevenLabs Dashboard with:
    - Language-specific personality
    - Teaching behavior
    - Pronunciation correction capabilities
    """
    
    def __init__(self):
        self.api_key = settings.eleven_labs_api_key
        
        if not self.api_key:
            logger.error("[ElevenLabsAgent] API key not configured")
            raise ValueError("ELEVEN_LABS_API_KEY not configured")
        
        # Initialize official ElevenLabs client
        self.client = ElevenLabs(api_key=self.api_key)
        
        # Agent IDs - configured in ElevenLabs Dashboard
        # These agents are pre-configured with language-specific personalities
        self.agent_configs: Dict[str, ElevenLabsAgentConfig] = {
            "chinese": ElevenLabsAgentConfig(
                agent_id=getattr(settings, 'elevenlabs_chinese_agent_id', ''),
                language=ElevenLabsAgentLanguage.CHINESE,
                name="Chinese Language Tutor",
                description="AI tutor for Mandarin Chinese language learning"
            ),
            "korean": ElevenLabsAgentConfig(
                agent_id=getattr(settings, 'elevenlabs_korean_agent_id', ''),
                language=ElevenLabsAgentLanguage.KOREAN,
                name="Korean Language Tutor", 
                description="AI tutor for Korean language learning"
            ),
            "english": ElevenLabsAgentConfig(
                agent_id=getattr(settings, 'elevenlabs_english_agent_id', ''),
                language=ElevenLabsAgentLanguage.ENGLISH,
                name="English Language Tutor",
                description="AI tutor for English language learning"
            ),
            "french": ElevenLabsAgentConfig(
                agent_id=getattr(settings, 'elevenlabs_french_agent_id', ''),
                language=ElevenLabsAgentLanguage.FRENCH,
                name="French Language Tutor",
                description="AI tutor for French language learning"
            ),
            "german": ElevenLabsAgentConfig(
                agent_id=getattr(settings, 'elevenlabs_german_agent_id', ''),
                language=ElevenLabsAgentLanguage.GERMAN,
                name="German Language Tutor",
                description="AI tutor for German language learning"
            ),
            "spanish": ElevenLabsAgentConfig(
                agent_id=getattr(settings, 'elevenlabs_spanish_agent_id', ''),
                language=ElevenLabsAgentLanguage.SPANISH,
                name="Spanish Language Tutor",
                description="AI tutor for Spanish language learning"
            )
        }
        
        # Active sessions storage
        self.active_sessions: Dict[str, Dict[str, Any]] = {}
        
        logger.info("[ElevenLabsAgent] Service initialized with official SDK")
        for lang, config in self.agent_configs.items():
            if config.agent_id:
                logger.info(f"[ElevenLabsAgent] {lang.capitalize()} Agent ID: {config.agent_id[:20]}...")
            else:
                logger.info(f"[ElevenLabsAgent] {lang.capitalize()} Agent ID: NOT CONFIGURED")
    
    def get_agent_for_language(self, language: str) -> Optional[ElevenLabsAgentConfig]:
        """Get the agent configuration for a specific language."""
        language_lower = language.lower()
        
        if language_lower in ["chinese", "mandarin", "zh", "zh-cn", "zh-tw"]:
            return self.agent_configs.get("chinese")
        elif language_lower in ["korean", "ko", "hangeul"]:
            return self.agent_configs.get("korean")
        elif language_lower in ["english", "en", "en-us", "en-gb"]:
            return self.agent_configs.get("english")
        elif language_lower in ["french", "fr", "fr-fr"]:
            return self.agent_configs.get("french")
        elif language_lower in ["german", "de", "de-de"]:
            return self.agent_configs.get("german")
        elif language_lower in ["spanish", "es", "es-es"]:
            return self.agent_configs.get("spanish")
        
        return None
    
    def is_elevenlabs_language(self, language: str) -> bool:
        """Check if language should use ElevenLabs Agent."""
        return self.get_agent_for_language(language) is not None
    
    def get_signed_url(self, agent_id: str) -> Optional[str]:
        """
        Get a signed WebSocket URL for the ElevenLabs Conversational AI.
        Uses the official ElevenLabs SDK.
        """
        try:
            # Use SDK to get signed URL
            response = self.client.conversational_ai.conversations.get_signed_url(
                agent_id=agent_id
            )
            signed_url = response.signed_url
            logger.info(f"[ElevenLabsAgent] Got signed URL for agent {agent_id[:20]}...")
            return signed_url
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Error getting signed URL: {e}")
            return None
    
    def create_agent_if_not_exists(self, language: str) -> Optional[str]:
        """
        Create a new agent for a language if one doesn't exist.
        Returns the agent ID.
        
        NOTE: Prefer using pre-configured agents from the ElevenLabs Dashboard.
        This method is for fallback/development purposes.
        """
        try:
            language_lower = language.lower()
            language_name_map = {
                "chinese": "Mandarin Chinese",
                "mandarin": "Mandarin Chinese",
                "zh": "Mandarin Chinese",
                "korean": "Korean",
                "ko": "Korean",
                "english": "English",
                "en": "English",
                "french": "French",
                "fr": "French",
                "german": "German",
                "de": "German",
                "spanish": "Spanish",
                "es": "Spanish"
            }
            language_name = language_name_map.get(language_lower, "English")
            
            prompt = f"""You are a friendly and patient {language_name} language tutor.

Your role is to help students learn {language_name} through natural conversation.

Tasks:
- Answer questions about {language_name} grammar, vocabulary, and pronunciation
- Practice conversations with the student in {language_name}
- Correct mistakes gently and explain the correct usage
- Provide cultural context when relevant

Guidelines:
- Maintain a friendly, encouraging tone
- Speak primarily in {language_name} to help immersion
- Provide English translations when needed
- Keep responses conversational (2-4 sentences)
- Adapt to the student's level"""

            response = self.client.conversational_ai.agents.create(
                name=f"{language_name} Language Tutor",
                tags=["language-learning", language.lower()],
                conversation_config={
                    "agent": {
                        "first_message": f"Hello! I'm your {language_name} tutor. How can I help you today?",
                        "prompt": {
                            "prompt": prompt
                        }
                    },
                    "tts": {
                        "model_id": "eleven_flash_v2"
                    }
                }
            )
            
            logger.info(f"[ElevenLabsAgent] Created new agent: {response.agent_id}")
            return response.agent_id
            
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Error creating agent: {e}")
            return None
    
    async def start_conversation(
        self,
        session_id: str,
        user_id: str,
        language: str,
        level: str = "intermediate",
        scenario: str = "language_tutor",
        on_message: Optional[Callable] = None,
        on_audio: Optional[Callable] = None
    ) -> Dict[str, Any]:
        """
        Start a new ElevenLabs Agent conversation.
        
        Args:
            session_id: Unique session identifier
            user_id: User identifier
            language: Target language (chinese/korean)
            level: Proficiency level (beginner/intermediate/advanced)
            scenario: Conversation scenario
            on_message: Callback for text messages
            on_audio: Callback for audio data
            
        Returns:
            Session information including WebSocket URL for client connection
        """
        try:
            # Get agent config for language
            agent_config = self.get_agent_for_language(language)
            if not agent_config:
                return {
                    "success": False,
                    "error": f"No ElevenLabs agent configured for language: {language}"
                }
            
            agent_id = agent_config.agent_id
            
            # If agent ID not configured, try to create one dynamically
            if not agent_id:
                logger.warning(f"[ElevenLabsAgent] No agent ID for {language}, creating dynamically...")
                agent_id = self.create_agent_if_not_exists(language)
                if not agent_id:
                    return {
                        "success": False,
                        "error": f"ElevenLabs agent ID not configured for {language}. Please set ELEVENLABS_{language.upper()}_AGENT_ID in .env or create an agent in the ElevenLabs Dashboard."
                    }
                # Update config with new agent ID
                agent_config.agent_id = agent_id
            
            # Get signed URL for WebSocket connection (sync call)
            signed_url = self.get_signed_url(agent_id)
            if not signed_url:
                return {
                    "success": False,
                    "error": "Failed to get signed URL from ElevenLabs"
                }
            
            # Store session state
            self.active_sessions[session_id] = {
                "session_id": session_id,
                "user_id": user_id,
                "language": language,
                "level": level,
                "scenario": scenario,
                "agent_config": agent_config,
                "agent_id": agent_id,
                "signed_url": signed_url,
                "websocket": None,
                "is_active": True,
                "conversation_log": [],
                "on_message": on_message,
                "on_audio": on_audio,
                "start_time": datetime.utcnow(),
                "audio_buffer": bytearray()
            }
            
            logger.info(f"[ElevenLabsAgent] Session {session_id} initialized for {language}")
            
            return {
                "success": True,
                "session_id": session_id,
                "signed_url": signed_url,
                "agent_id": agent_id,
                "language": language,
                "message": f"ElevenLabs {agent_config.name} ready"
            }
            
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Error starting conversation: {e}")
            return {
                "success": False,
                "error": str(e)
            }
    
    async def handle_websocket_connection(
        self,
        session_id: str,
        client_websocket,
        on_transcript: Optional[Callable] = None,
        on_audio_response: Optional[Callable] = None
    ):
        """
        Handle the WebSocket connection between client and ElevenLabs Agent.
        Proxies audio and messages bidirectionally.
        
        Args:
            session_id: Session identifier
            client_websocket: FastAPI WebSocket connection from client
            on_transcript: Callback for transcripts
            on_audio_response: Callback for TTS audio responses
        """
        session = self.active_sessions.get(session_id)
        if not session:
            logger.error(f"[ElevenLabsAgent] Session {session_id} not found")
            return
        
        signed_url = session["signed_url"]
        
        try:
            logger.info(f"[ElevenLabsAgent] Connecting to ElevenLabs for session {session_id}")
            
            async with websockets.connect(signed_url) as elevenlabs_ws:
                session["websocket"] = elevenlabs_ws
                logger.info(f"[ElevenLabsAgent] Connected to ElevenLabs WebSocket")
                
                # Send initial configuration with dynamic variables
                # These match the {{variable}} placeholders in the dashboard prompts
                level = session.get("level", "intermediate")
                scenario = session.get("scenario", "language_tutor")
                language = session.get("language", "english")
                
                # Ensure values are not empty
                if not level:
                    level = "intermediate"
                if not scenario:
                    scenario = "language_tutor"
                
                # Get level-specific instructions
                level_instructions = self._get_level_instructions(level, language)
                
                # Get scenario-specific context
                scenario_context = self._get_scenario_context(scenario, language)
                
                init_message = {
                    "type": "conversation_initiation_client_data",
                    "dynamic_variables": {
                        "level": level,
                        "scenario": scenario.replace("_", " ").title(),
                        "level_instructions": level_instructions,
                        "scenario_context": scenario_context
                    }
                }
                logger.info(f"[ElevenLabsAgent] Sending init with dynamic variables: level={level}, scenario={scenario}")
                await elevenlabs_ws.send(json.dumps(init_message))
                logger.info(f"[ElevenLabsAgent] Init sent successfully")
                
                # Notify client of successful connection
                await client_websocket.send_text(json.dumps({
                    "type": "connection",
                    "event": "connected",
                    "session_id": session_id,
                    "message": "Connected to ElevenLabs Agent"
                }))
                
                # Create bidirectional streaming tasks
                receive_task = asyncio.create_task(
                    self._receive_from_elevenlabs(
                        session_id,
                        elevenlabs_ws,
                        client_websocket,
                        on_transcript,
                        on_audio_response
                    )
                )
                send_task = asyncio.create_task(
                    self._send_to_elevenlabs(
                        session_id,
                        client_websocket,
                        elevenlabs_ws
                    )
                )
                
                # Wait for either task to complete
                done, pending = await asyncio.wait(
                    [receive_task, send_task],
                    return_when=asyncio.FIRST_COMPLETED
                )
                
                # Cancel remaining tasks
                for task in pending:
                    task.cancel()
                    try:
                        await task
                    except asyncio.CancelledError:
                        pass
                
        except websockets.exceptions.ConnectionClosed as e:
            logger.info(f"[ElevenLabsAgent] WebSocket closed: {e.code} - {e.reason}")
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] WebSocket error: {e}")
            import traceback
            traceback.print_exc()
        finally:
            session["is_active"] = False
            session["websocket"] = None
            logger.info(f"[ElevenLabsAgent] Session {session_id} connection closed")
    
    async def _receive_from_elevenlabs(
        self,
        session_id: str,
        elevenlabs_ws,
        client_websocket,
        on_transcript: Optional[Callable],
        on_audio_response: Optional[Callable]
    ):
        """Receive messages from ElevenLabs and forward to client."""
        session = self.active_sessions.get(session_id)
        
        try:
            async for message in elevenlabs_ws:
                if isinstance(message, bytes):
                    # Binary audio data from TTS
                    logger.info(f"[ElevenLabsAgent] Received AUDIO: {len(message)} bytes")
                    
                    # Forward audio to client
                    await client_websocket.send_bytes(message)
                    
                    # Store in buffer
                    if session:
                        session["audio_buffer"].extend(message)
                    
                    if on_audio_response:
                        on_audio_response(message, session_id)
                        
                elif isinstance(message, str):
                    # JSON message - log raw for debugging
                    logger.info(f"[ElevenLabsAgent] RAW message: {message[:500]}")
                    data = json.loads(message)
                    msg_type = data.get("type", "")
                    
                    logger.info(f"[ElevenLabsAgent] Parsed type: {msg_type}, keys: {list(data.keys())}")
                    
                    if msg_type == "conversation_initiation_metadata":
                        # Connection established
                        metadata = data.get("conversation_initiation_metadata_event", {})
                        conversation_id = metadata.get("conversation_id")
                        logger.info(f"[ElevenLabsAgent] Conversation started: {conversation_id}")
                        await client_websocket.send_text(json.dumps({
                            "type": "agent_message",
                            "event": "Welcome",
                            "conversation_id": conversation_id
                        }))
                    
                    elif msg_type == "user_transcript":
                        # User's speech transcribed - try multiple possible field names
                        # ElevenLabs may use different structures depending on API version
                        event_data = data.get("user_transcription_event", data)
                        transcript = (
                            event_data.get("user_transcript") or
                            event_data.get("transcript") or
                            data.get("transcript") or
                            data.get("text") or
                            ""
                        )
                        # Check if this is a final transcript or interim
                        is_final = data.get("is_final", True)
                        turn_id = session.get("current_user_turn_id") if session else None
                        
                        # Generate turn_id for new user turns
                        if not turn_id or is_final:
                            import uuid
                            turn_id = str(uuid.uuid4())
                            if session:
                                session["current_user_turn_id"] = turn_id
                        
                        logger.info(f"[ElevenLabsAgent] User said: {transcript} (final={is_final})")
                        
                        # Log conversation only for final transcripts
                        if is_final and session:
                            session["conversation_log"].append({
                                "role": "user",
                                "content": transcript,
                                "timestamp": datetime.utcnow().isoformat()
                            })
                            # Clear turn_id after final
                            session["current_user_turn_id"] = None
                        
                        await client_websocket.send_text(json.dumps({
                            "type": "conversation_text",
                            "role": "user",
                            "content": transcript,
                            "is_final": is_final,
                            "turn_id": turn_id
                        }))
                        
                        if on_transcript and is_final:
                            on_transcript(transcript, "user", session_id)
                    
                    elif msg_type == "transcript":
                        # Interim transcript during speech (real-time streaming)
                        event_data = data.get("transcript_event", data)
                        transcript = (
                            event_data.get("transcript") or
                            data.get("text") or
                            ""
                        )
                        is_final = data.get("is_final", False)
                        
                        # Get or create turn_id for streaming updates
                        turn_id = session.get("current_user_turn_id") if session else None
                        if not turn_id:
                            import uuid
                            turn_id = str(uuid.uuid4())
                            if session:
                                session["current_user_turn_id"] = turn_id
                        
                        if transcript:
                            logger.debug(f"[ElevenLabsAgent] Interim: {transcript[:50]}... (final={is_final})")
                            await client_websocket.send_text(json.dumps({
                                "type": "conversation_text",
                                "role": "user",
                                "content": transcript,
                                "is_final": is_final,
                                "turn_id": turn_id
                            }))
                    
                    elif msg_type == "agent_response":
                        # Agent's text response - try multiple possible field names
                        event_data = data.get("agent_response_event", data)
                        response = (
                            event_data.get("agent_response") or
                            event_data.get("response") or
                            data.get("response") or
                            data.get("text") or
                            ""
                        )
                        # Check if this is final or streaming
                        is_final = data.get("is_final", True)
                        turn_id = session.get("current_agent_turn_id") if session else None
                        
                        # Generate turn_id for new agent turns
                        if not turn_id:
                            import uuid
                            turn_id = str(uuid.uuid4())
                            if session:
                                session["current_agent_turn_id"] = turn_id
                        
                        logger.info(f"[ElevenLabsAgent] Agent said: {response} (final={is_final})")
                        
                        # Log conversation only for final responses
                        if is_final and session:
                            session["conversation_log"].append({
                                "role": "assistant",
                                "content": response,
                                "timestamp": datetime.utcnow().isoformat()
                            })
                            # Clear turn_id after final
                            session["current_agent_turn_id"] = None
                        
                        await client_websocket.send_text(json.dumps({
                            "type": "conversation_text",
                            "role": "assistant",
                            "content": response,
                            "is_final": is_final,
                            "turn_id": turn_id
                        }))
                        
                        if on_transcript and is_final:
                            on_transcript(response, "assistant", session_id)
                    
                    elif msg_type == "agent_response_correction":
                        # Streaming agent response updates (real-time text generation)
                        event_data = data.get("agent_response_correction_event", data)
                        response = (
                            event_data.get("corrected_text") or
                            event_data.get("text") or
                            data.get("text") or
                            ""
                        )
                        
                        # Get existing turn_id for streaming updates
                        turn_id = session.get("current_agent_turn_id") if session else None
                        if not turn_id:
                            import uuid
                            turn_id = str(uuid.uuid4())
                            if session:
                                session["current_agent_turn_id"] = turn_id
                        
                        if response:
                            logger.debug(f"[ElevenLabsAgent] Agent streaming: {response[:50]}...")
                            await client_websocket.send_text(json.dumps({
                                "type": "conversation_text",
                                "role": "assistant",
                                "content": response,
                                "is_final": False,
                                "turn_id": turn_id
                            }))
                    
                    elif msg_type == "audio":
                        # Agent's audio response - nested in audio_event
                        event_data = data.get("audio_event", {})
                        audio_b64 = event_data.get("audio_base_64", "")
                        
                        if audio_b64:
                            # Decode base64 to binary and send to client
                            audio_bytes = base64.b64decode(audio_b64)
                            logger.info(f"[ElevenLabsAgent] Decoded audio: {len(audio_bytes)} bytes")
                            
                            # Notify client agent is starting to speak BEFORE sending audio
                            await client_websocket.send_text(json.dumps({
                                "type": "agent_message",
                                "event": "AgentStartedSpeaking"
                            }))
                            
                            await client_websocket.send_bytes(audio_bytes)
                            
                            # Store in buffer
                            if session:
                                session["audio_buffer"].extend(audio_bytes)
                            
                            if on_audio_response:
                                on_audio_response(audio_bytes, session_id)
                            
                            # Calculate audio duration and schedule AgentAudioDone in background
                            # Audio is PCM 16kHz, 16-bit mono = 2 bytes per sample
                            audio_duration_seconds = len(audio_bytes) / 2 / 16000
                            logger.info(f"[ElevenLabsAgent] Audio duration: {audio_duration_seconds:.2f}s")
                            
                            # Schedule AgentAudioDone without blocking (allows other messages to flow)
                            async def send_audio_done_delayed():
                                await asyncio.sleep(audio_duration_seconds + 0.2)
                                try:
                                    await client_websocket.send_text(json.dumps({
                                        "type": "agent_message",
                                        "event": "AgentAudioDone"
                                    }))
                                    logger.info("[ElevenLabsAgent] Sent AgentAudioDone")
                                except Exception as e:
                                    logger.debug(f"[ElevenLabsAgent] Could not send AgentAudioDone: {e}")
                            
                            asyncio.create_task(send_audio_done_delayed())
                    
                    elif msg_type == "audio_done":
                        # Agent finished speaking
                        await client_websocket.send_text(json.dumps({
                            "type": "agent_message",
                            "event": "AgentAudioDone"
                        }))
                    
                    elif msg_type == "interruption":
                        # User interrupted the agent
                        logger.info("[ElevenLabsAgent] User interrupted")
                        await client_websocket.send_text(json.dumps({
                            "type": "agent_message",
                            "event": "UserStartedSpeaking"
                        }))
                    
                    elif msg_type == "ping":
                        # Respond to ping with pong
                        event_data = data.get("ping_event", {})
                        event_id = event_data.get("event_id")
                        await elevenlabs_ws.send(json.dumps({
                            "type": "pong",
                            "event_id": event_id
                        }))
                    
                    elif msg_type == "error":
                        error_msg = data.get("message", data.get("error", "Unknown error"))
                        error_code = data.get("code", "")
                        logger.error(f"[ElevenLabsAgent] Error: {error_msg} (code: {error_code})")
                        await client_websocket.send_text(json.dumps({
                            "type": "error",
                            "error": error_msg,
                            "code": error_code
                        }))
                    
                    else:
                        # Log unrecognized message types for debugging
                        logger.warning(f"[ElevenLabsAgent] Unhandled message type: {msg_type}")
                        logger.debug(f"[ElevenLabsAgent] Full message: {message[:500]}")
                    
                    # Forward raw message to callback
                    if session and session.get("on_message"):
                        session["on_message"](data)
                        
        except websockets.exceptions.ConnectionClosed:
            logger.info(f"[ElevenLabsAgent] ElevenLabs connection closed for session {session_id}")
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Receive error: {e}")
    
    async def _send_to_elevenlabs(
        self,
        session_id: str,
        client_websocket,
        elevenlabs_ws
    ):
        """Send audio from client to ElevenLabs."""
        audio_bytes_sent = 0
        try:
            while True:
                message = await client_websocket.receive()
                
                if "bytes" in message:
                    # Binary audio data from client microphone (16kHz, 16-bit, mono PCM)
                    audio_data = message["bytes"]
                    
                    # ElevenLabs expects base64 encoded audio in JSON with specific format
                    audio_b64 = base64.b64encode(audio_data).decode('utf-8')
                    
                    # Send in ElevenLabs expected format
                    await elevenlabs_ws.send(json.dumps({
                        "type": "user_audio_chunk",
                        "user_audio_chunk": audio_b64
                    }))
                    
                    audio_bytes_sent += len(audio_data)
                    # Log periodically (every ~1 second of audio at 16kHz = 32KB)
                    if audio_bytes_sent % 32000 < len(audio_data):
                        logger.info(f"[ElevenLabsAgent] Total audio sent: {audio_bytes_sent // 1000}KB")
                    
                elif "text" in message:
                    # Text message (control commands)
                    data = json.loads(message["text"])
                    
                    if data.get("type") == "stop":
                        logger.info(f"[ElevenLabsAgent] Stop requested for session {session_id}")
                        break
                    elif data.get("type") == "user_message":
                        # Text input instead of audio
                        await elevenlabs_ws.send(json.dumps({
                            "type": "user_message",
                            "text": data.get("text", "")
                        }))
                        
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Send error: {e}")
    
    def _get_level_instructions(self, level: str, language: str) -> str:
        """Get level-specific teaching instructions for dynamic variable injection."""
        language_lower = language.lower()
        language_name_map = {
            "chinese": "Mandarin Chinese", "mandarin": "Mandarin Chinese", "zh": "Mandarin Chinese",
            "korean": "Korean", "ko": "Korean",
            "english": "English", "en": "English",
            "french": "French", "fr": "French",
            "german": "German", "de": "German",
            "spanish": "Spanish", "es": "Spanish"
        }
        language_name = language_name_map.get(language_lower, "the target language")
        
        level_instructions = {
            "beginner": f"""BEGINNER LEVEL APPROACH:
• Use simple, high-frequency vocabulary only
• Speak slowly with clear pronunciation - pause between phrases
• Use short sentences (5-8 words maximum)
• Repeat key words and phrases for reinforcement
• Provide English translations immediately after {language_name} phrases
• Celebrate small victories enthusiastically
• If student struggles, simplify further or switch to English briefly
• Focus on: greetings, numbers, basic questions, essential nouns""",
            
            "intermediate": f"""INTERMEDIATE LEVEL APPROACH:
• Use everyday vocabulary with some less common words
• Speak at a moderate, natural pace
• Use compound sentences and basic complex structures
• Correct errors gently with brief explanations
• Provide English only when student seems confused
• Introduce cultural context and idiomatic expressions
• Encourage longer responses from the student
• Focus on: expressing opinions, narrating events, common idioms""",
            
            "advanced": f"""ADVANCED LEVEL APPROACH:
• Use sophisticated vocabulary, idioms, and colloquialisms
• Speak at natural native speed with authentic intonation
• Use complex grammatical structures freely
• Correct subtle errors and discuss nuance
• Stay primarily in {language_name} - minimal English
• Discuss abstract topics, current events, cultural depth
• Challenge the student with follow-up questions
• Focus on: fluency, nuance, register variation, advanced grammar"""
        }
        
        return level_instructions.get(level, level_instructions["intermediate"])
    
    def _get_scenario_context(self, scenario: str, language: str) -> str:
        """Get scenario-specific context for dynamic variable injection."""
        language_lower = language.lower()
        language_name_map = {
            "chinese": "Mandarin Chinese", "mandarin": "Mandarin Chinese", "zh": "Mandarin Chinese",
            "korean": "Korean", "ko": "Korean",
            "english": "English", "en": "English",
            "french": "French", "fr": "French",
            "german": "German", "de": "German",
            "spanish": "Spanish", "es": "Spanish"
        }
        language_name = language_name_map.get(language_lower, "the target language")
        
        scenario_contexts = {
            "language_tutor": f"""You are a general language tutor helping the student practice {language_name}.
• Adapt conversation topics based on student's interests
• Balance speaking practice with gentle corrections
• Introduce new vocabulary naturally in context
• Keep the conversation flowing with follow-up questions""",
            
            "travel": f"""You are helping the student practice {language_name} for travel situations.
PRACTICE SCENARIOS:
• Booking hotels and accommodations
• Ordering food at restaurants and cafes
• Asking for directions and using transportation
• Shopping and bargaining
• Handling emergencies and asking for help
• Making small talk with locals
Use realistic phrases travelers actually need. Include cultural tips about travel etiquette.""",
            
            "food": f"""You are helping the student practice {language_name} in food-related contexts.
PRACTICE SCENARIOS:
• Ordering at restaurants (appetizers, main courses, desserts, drinks)
• Describing flavors and ingredients
• Discussing cooking methods and recipes
• Food allergies and dietary restrictions
• Complimenting the chef and tipping etiquette
• Local cuisine and food culture
Include food vocabulary specific to {language_name}-speaking cultures.""",
            
            "daily_conversation": f"""You are practicing everyday {language_name} conversations.
PRACTICE SCENARIOS:
• Talking about weather and seasons
• Discussing family and relationships
• Hobbies and leisure activities
• Daily routines and schedules
• Shopping for groceries and essentials
• Making plans with friends
Keep conversations natural and relatable to daily life.""",
            
            "work": f"""You are helping the student practice professional {language_name}.
PRACTICE SCENARIOS:
• Job interviews and self-introduction
• Meeting colleagues and networking
• Phone calls and scheduling
• Presenting ideas in meetings
• Writing and discussing emails
• Business etiquette and formality levels
Use appropriate formal register. Teach business vocabulary and professional phrases.""",
            
            "culture": f"""You are exploring {language_name}-speaking culture through conversation.
DISCUSSION TOPICS:
• Traditional holidays and celebrations
• Art, music, and literature
• History and historical figures
• Social customs and etiquette
• Modern pop culture and trends
• Regional differences and dialects
Share cultural insights while practicing language. Encourage questions about culture."""
        }
        
        return scenario_contexts.get(scenario, scenario_contexts["language_tutor"])
    
    def _get_dynamic_prompt(self, language: str, level: str, scenario: str) -> str:
        """Generate a dynamic prompt based on session parameters."""
        language_lower = language.lower()
        language_name_map = {
            "chinese": "Mandarin Chinese",
            "mandarin": "Mandarin Chinese",
            "zh": "Mandarin Chinese",
            "korean": "Korean",
            "ko": "Korean",
            "english": "English",
            "en": "English",
            "french": "French",
            "fr": "French",
            "german": "German",
            "de": "German",
            "spanish": "Spanish",
            "es": "Spanish"
        }
        language_name = language_name_map.get(language_lower, "English")
        
        level_instructions = {
            "beginner": "Use simple vocabulary and short sentences. Speak slowly and clearly. Provide lots of encouragement.",
            "intermediate": "Use everyday vocabulary with some complex structures. Correct errors gently and explain briefly.",
            "advanced": "Use natural, complex language with idioms. Focus on fluency and nuance."
        }
        
        scenario_instructions = {
            "language_tutor": "Help the student practice speaking and improve their language skills.",
            "travel": "Practice travel-related conversations like hotels, restaurants, and directions.",
            "food": "Discuss food, cooking, and restaurant scenarios.",
            "daily_conversation": "Practice everyday conversations about daily life.",
            "work": "Practice professional and workplace communication.",
            "culture": "Discuss cultural topics and traditions."
        }
        
        return f"""You are a friendly and patient {language_name} language tutor.

LANGUAGE: {language_name}
STUDENT LEVEL: {level}
SCENARIO: {scenario}

{level_instructions.get(level, level_instructions["intermediate"])}

{scenario_instructions.get(scenario, scenario_instructions["language_tutor"])}

IMPORTANT GUIDELINES:
- Speak primarily in {language_name} to help immersion
- Provide English translations when needed for understanding
- Correct pronunciation and grammar mistakes gently
- Be encouraging and supportive
- Keep responses conversational (2-4 sentences)
- Adapt to the student's responses and pace"""
    
    async def end_conversation(self, session_id: str) -> bool:
        """End an active conversation and save to database."""
        session = self.active_sessions.get(session_id)
        if not session:
            return False
        
        session["is_active"] = False
        
        # Close WebSocket if open
        if session.get("websocket"):
            try:
                await session["websocket"].close()
            except:
                pass
        
        # Save conversation to database (non-blocking - run in background)
        try:
            # Copy session data before removing
            session_copy = dict(session)
            asyncio.create_task(self._save_conversation_to_database(session_id, session_copy))
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Error scheduling save: {e}")
        
        # Remove from active sessions
        del self.active_sessions[session_id]
        
        logger.info(f"[ElevenLabsAgent] Session {session_id} ended")
        return True
    
    async def _save_conversation_to_database(self, session_id: str, session: Dict[str, Any]):
        """Save conversation data to conversation_recordings table for AI feedback."""
        try:
            client = SupabaseManager.get_client()
            if not client:
                logger.warning("[ElevenLabsAgent] Supabase not available, skipping conversation save")
                return
            
            conversation_log = session.get("conversation_log", [])
            if not conversation_log:
                logger.info("[ElevenLabsAgent] No conversation log to save")
                return
            
            # Build transcript from conversation log
            transcript = "\n".join([
                f"{turn.get('role', 'unknown')}: {turn.get('content', '')}"
                for turn in conversation_log
            ])
            
            # Calculate duration
            start_time = session.get("start_time", datetime.utcnow())
            duration = (datetime.utcnow() - start_time).total_seconds()
            
            # Count turns (user messages only for turn count)
            turn_count = len([t for t in conversation_log if t.get("role") == "user"])
            
            # Get audio data if available
            audio_url = None
            audio_buffer = session.get("audio_buffer")
            if audio_buffer and len(audio_buffer) > 0:
                try:
                    # Upload audio to Supabase storage
                    storage = client.storage.from_("voice-recordings")  # Use the correct bucket name
                    audio_path = f"conversations/{session.get('user_id', 'unknown')}/{session_id}.wav"
                    
                    # Create WAV header for the audio buffer
                    audio_data = bytes(audio_buffer)
                    wav_data = self._create_wav_data(audio_data)
                    
                    storage.upload(audio_path, wav_data, file_options={"content-type": "audio/wav", "upsert": "true"})
                    
                    # Get public URL
                    audio_url = storage.get_public_url(audio_path)
                    logger.info(f"[ElevenLabsAgent] Audio uploaded: {audio_url}")
                except Exception as audio_error:
                    logger.warning(f"[ElevenLabsAgent] Failed to upload audio: {audio_error}")
                    import traceback
                    traceback.print_exc()
            
            # Create recording entry
            recording_data = {
                "id": str(uuid.uuid4()),
                "session_id": session_id,
                "user_id": session.get("user_id", "unknown"),
                "language": session.get("language", "english"),
                "audio_url": audio_url,
                "transcript": transcript,
                "turn_count": turn_count,
                "duration": duration,
                "created_at": datetime.utcnow().isoformat()
            }
            
            # Insert into conversation_recordings table
            response = client.table("conversation_recordings").insert(recording_data).execute()
            
            if response.data:
                logger.info(f"[ElevenLabsAgent] Conversation saved to database")
                logger.info(f"  Session ID: {session_id}")
                logger.info(f"  User ID: {session.get('user_id')}")
                logger.info(f"  Language: {session.get('language')}")
                logger.info(f"  Turns: {turn_count}")
                logger.info(f"  Duration: {duration:.1f}s")
            else:
                logger.warning("[ElevenLabsAgent] Failed to save conversation to database")
                
        except Exception as e:
            logger.error(f"[ElevenLabsAgent] Error saving conversation: {e}")
            import traceback
            traceback.print_exc()
    
    def _create_wav_data(self, audio_data: bytes) -> bytes:
        """Create WAV file data from raw PCM audio (16kHz, 16-bit, mono)."""
        import struct
        
        sample_rate = 16000
        bits_per_sample = 16
        channels = 1
        
        data_size = len(audio_data)
        file_size = data_size + 36
        
        # WAV header
        header = struct.pack(
            '<4sI4s4sIHHIIHH4sI',
            b'RIFF',
            file_size,
            b'WAVE',
            b'fmt ',
            16,  # Subchunk1Size
            1,   # AudioFormat (PCM)
            channels,
            sample_rate,
            sample_rate * channels * bits_per_sample // 8,  # ByteRate
            channels * bits_per_sample // 8,  # BlockAlign
            bits_per_sample,
            b'data',
            data_size
        )
        
        return header + audio_data
    
    def get_conversation_history(self, session_id: str) -> List[Dict[str, Any]]:
        """Get conversation history for a session."""
        session = self.active_sessions.get(session_id)
        if session:
            return session.get("conversation_log", [])
        return []
    
    def get_active_sessions(self) -> List[Dict[str, Any]]:
        """Get list of active sessions."""
        sessions = []
        for session_id, session in self.active_sessions.items():
            if session["is_active"]:
                sessions.append({
                    "session_id": session_id,
                    "user_id": session["user_id"],
                    "language": session["language"],
                    "level": session["level"],
                    "scenario": session["scenario"],
                    "start_time": session["start_time"].isoformat(),
                    "duration": (datetime.utcnow() - session["start_time"]).total_seconds()
                })
        return sessions


# Singleton instance
_elevenlabs_agent_service: Optional[ElevenLabsAgentService] = None


def get_elevenlabs_agent_service() -> ElevenLabsAgentService:
    """Get or create ElevenLabs Agent service instance."""
    global _elevenlabs_agent_service
    if _elevenlabs_agent_service is None:
        _elevenlabs_agent_service = ElevenLabsAgentService()
    return _elevenlabs_agent_service
