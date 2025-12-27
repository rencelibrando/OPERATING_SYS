"""
Voice Agent service using Deepgram's Voice Agent API for conversational interactions.
"""
import os
import json
import asyncio
import threading
import time
from datetime import datetime
from typing import Dict, Any, List, Optional, Callable
from deepgram import DeepgramClient
from deepgram.core.events import EventType
from deepgram.extensions.types.sockets import (
    AgentV1Agent,
    AgentV1AudioConfig,
    AgentV1AudioInput,
    AgentV1AudioOutput,
    AgentV1DeepgramSpeakProvider,
    AgentV1Listen,
    AgentV1ListenProvider,
    AgentV1OpenAiThinkProvider,
    AgentV1SettingsMessage,
    AgentV1SocketClientResponse,
    AgentV1SpeakProviderConfig,
    AgentV1Think,
)
from providers.gemini import GeminiProvider
from supabase_client import SupabaseManager
from config import settings


class VoiceAgentService:
    def __init__(self):
        # Initialize Deepgram client
        self.deepgram_client = DeepgramClient(api_key=settings.deepgram_api_key)
        
        # Initialize Gemini provider for fallback functionality
        self.gemini_provider = GeminiProvider()
        
        # Initialize Supabase manager
        self.supabase_manager = SupabaseManager()
        
        # Active connections storage
        self.active_connections: Dict[str, Dict[str, Any]] = {}
        
        # Language mapping for agent configuration
        self.language_mapping = {
            "english": "en",
            "french": "fr",
            "german": "de",
            "korean": "ko",
            "mandarin": "zh",
            "spanish": "es"
        }
        
        # Agent personalities for different scenarios
        self.agent_personalities = self._load_agent_personalities()
    
    def _load_agent_personalities(self) -> Dict[str, Dict[str, str]]:
        """Load agent personalities for different conversation scenarios."""
        return {
            "language_tutor": {
                "role": "You are a friendly and patient language tutor.",
                "instructions": """
                Help the user practice speaking in their target language. Your role is to:
                1. Engage in natural conversation
                2. Correct pronunciation and grammar gently
                3. Provide vocabulary suggestions
                4. Ask questions to encourage more speaking
                5. Be encouraging and supportive
                6. Adapt your responses to the user's proficiency level
                """
            },
            "conversation_partner": {
                "role": "You are a friendly conversation partner.",
                "instructions": """
                Engage in natural, flowing conversation with the user. Your role is to:
                1. Ask open-ended questions about various topics
                2. Share your own experiences and opinions
                3. Listen actively and respond naturally
                4. Keep the conversation interesting and engaging
                5. Be supportive and encouraging
                """
            },
            "interview_practice": {
                "role": "You are a professional interviewer.",
                "instructions": """
                Conduct a mock interview with the user. Your role is to:
                1. Ask common interview questions
                2. Provide feedback on their responses
                3. Help them practice professional communication
                4. Ask follow-up questions to test their knowledge
                5. Be professional yet encouraging
                """
            },
            "travel_companion": {
                "role": "You are a knowledgeable travel guide.",
                "instructions": """
                Help the user practice travel-related conversations. Your role is to:
                1. Simulate travel scenarios (hotels, restaurants, directions)
                2. Teach travel-related vocabulary
                3. Provide cultural context and tips
                4. Ask questions about travel plans and preferences
                5. Be helpful and informative
                """
            }
        }
    
    def create_agent_settings(
        self,
        language: str = "english",
        scenario: str = "language_tutor",
        voice_model: str = "aura-2-thalia-en",
        temperature: float = 0.7,
        custom_prompt: Optional[str] = None
    ) -> AgentV1SettingsMessage:
        """Create agent settings configuration."""
        
        # Get language code
        lang_code = self.language_mapping.get(language.lower(), "en")
        
        # Get personality based on scenario
        personality = self.agent_personalities.get(scenario, self.agent_personalities["language_tutor"])
        
        # Create prompt
        if custom_prompt:
            prompt = custom_prompt
        else:
            prompt = f"""
            {personality['role']}
            
            {personality['instructions']}
            
            Current date: {datetime.now().strftime("%A, %B %d, %Y")}
            
            Language: {language.title()}
            Scenario: {scenario.replace('_', ' ').title()}
            
            Remember to:
            - Speak naturally and conversationally
            - Adjust your language level to match the user
            - Be patient and encouraging
            - Provide helpful feedback when appropriate
            - Keep the conversation flowing naturally
            """
        
        # Configure audio settings
        audio_config = AgentV1AudioConfig(
            input=AgentV1AudioInput(
                encoding="linear16",
                sample_rate=24000,
            ),
            output=AgentV1AudioOutput(
                encoding="linear16",
                sample_rate=24000,
                container="wav",
            ),
        )
        
        # Configure agent settings (use specific language for TTS, nova-3 handles multilingual STT)
        agent_config = AgentV1Agent(
            language=lang_code,
            listen=AgentV1Listen(
                provider=AgentV1ListenProvider(
                    type="deepgram",
                    model=settings.deepgram_model,
                )
            ),
            think=AgentV1Think(
                provider=AgentV1OpenAiThinkProvider(
                    type="open_ai",
                    model="gpt-4o-mini",
                    temperature=temperature,
                ),
                prompt=prompt,
            ),
            speak=AgentV1SpeakProviderConfig(
                provider=AgentV1DeepgramSpeakProvider(
                    type="deepgram",
                    model=voice_model,
                )
            ),
            greeting=f"Hello! I'm your {scenario.replace('_', ' ')} for {language} practice. How can I help you today?",
        )
        
        return AgentV1SettingsMessage(
            audio=audio_config,
            agent=agent_config,
            tags=[scenario, language],
            experimental=False,
            mip_opt_out=False,
            flags={"history": True}
        )
    
    async def start_conversation(
        self,
        session_id: str,
        user_id: str,
        language: str = "english",
        scenario: str = "language_tutor",
        voice_model: str = "aura-2-thalia-en",
        temperature: float = 0.7,
        custom_prompt: Optional[str] = None,
        on_message: Optional[Callable] = None,
        on_audio: Optional[Callable] = None
    ) -> Dict[str, Any]:
        """Start a new voice agent conversation."""
        try:
            # Create agent settings with multilingual support
            settings = self.create_agent_settings(
                language=language,
                scenario=scenario,
                voice_model=voice_model,
                temperature=temperature,
                custom_prompt=custom_prompt
            )
            
            print(f"Starting conversation: model={settings.agent.listen.provider.model}, language={settings.agent.language} (nova-3 auto-detects multilingual)")
            
            # Initialize connection state
            connection_state = {
                "session_id": session_id,
                "user_id": user_id,
                "language": language,
                "scenario": scenario,
                "connection": None,
                "audio_buffer": bytearray(),
                "conversation_log": [],
                "is_active": False,
                "on_message": on_message,
                "on_audio": on_audio,
                "start_time": datetime.utcnow()
            }
            
            # Create WebSocket connection
            connection = self.deepgram_client.agent.v1.connect()
            connection_state["connection"] = connection
            
            # Set up event handlers
            self._setup_event_handlers(session_id, connection_state)
            
            # Start connection
            with connection as conn:
                connection_state["is_active"] = True
                self.active_connections[session_id] = connection_state
                
                # Send settings
                conn.send_settings(settings)
                
                # Start listening in background
                listener_thread = threading.Thread(
                    target=self._start_listening,
                    args=(session_id, conn),
                    daemon=True
                )
                listener_thread.start()
                
                # Save session to database
                await self._save_conversation_session(session_id, user_id, language, scenario)
                
                return {
                    "success": True,
                    "session_id": session_id,
                    "message": "Conversation started successfully",
                    "settings": {
                        "language": language,
                        "scenario": scenario,
                        "voice_model": voice_model
                    }
                }
                
        except Exception as e:
            print(f"Error starting conversation: {str(e)}")
            return {
                "success": False,
                "session_id": session_id,
                "message": f"Failed to start conversation: {str(e)}"
            }
    
    def _setup_event_handlers(self, session_id: str, connection_state: Dict[str, Any]):
        """Set up event handlers for the voice agent connection."""
        connection = connection_state["connection"]
        
        def on_open(event):
            """Handle connection open."""
            self._log_event(session_id, "Connection opened")
            if connection_state["on_message"]:
                connection_state["on_message"]({
                    "type": "connection",
                    "event": "opened",
                    "session_id": session_id
                })
        
        def on_message(message: AgentV1SocketClientResponse):
            """Handle incoming messages."""
            try:
                # Handle binary audio data
                if isinstance(message, bytes):
                    connection_state["audio_buffer"].extend(message)
                    if connection_state["on_audio"]:
                        connection_state["on_audio"](message, session_id)
                    return
                
                # Handle JSON messages
                msg_type = getattr(message, "type", "Unknown")
                self._log_event(session_id, f"Received {msg_type} event")
                
                # Process specific message types
                if msg_type == "Welcome":
                    self._handle_welcome(session_id, message, connection_state)
                elif msg_type == "SettingsApplied":
                    self._handle_settings_applied(session_id, message, connection_state)
                elif msg_type == "ConversationText":
                    self._handle_conversation_text(session_id, message, connection_state)
                elif msg_type == "UserStartedSpeaking":
                    self._handle_user_started_speaking(session_id, message, connection_state)
                elif msg_type == "AgentThinking":
                    self._handle_agent_thinking(session_id, message, connection_state)
                elif msg_type == "AgentStartedSpeaking":
                    self._handle_agent_started_speaking(session_id, message, connection_state)
                elif msg_type == "AgentAudioDone":
                    self._handle_agent_audio_done(session_id, message, connection_state)
                elif msg_type == "FunctionCallRequest":
                    self._handle_function_call_request(session_id, message, connection_state)
                elif msg_type == "Error":
                    self._handle_error(session_id, message, connection_state)
                elif msg_type == "Warning":
                    self._handle_warning(session_id, message, connection_state)
                
                # Notify callback
                if connection_state["on_message"]:
                    connection_state["on_message"]({
                        "type": "agent_message",
                        "event": msg_type,
                        "session_id": session_id,
                        "data": message.__dict__ if hasattr(message, '__dict__') else str(message)
                    })
                    
            except Exception as e:
                print(f"Error handling message for session {session_id}: {str(e)}")
        
        def on_error(error):
            """Handle connection errors."""
            self._log_event(session_id, f"Error: {error}")
            if connection_state["on_message"]:
                connection_state["on_message"]({
                    "type": "error",
                    "event": "connection_error",
                    "session_id": session_id,
                    "error": str(error)
                })
        
        def on_close(event):
            """Handle connection close."""
            self._log_event(session_id, "Connection closed")
            connection_state["is_active"] = False
            if session_id in self.active_connections:
                del self.active_connections[session_id]
            if connection_state["on_message"]:
                connection_state["on_message"]({
                    "type": "connection",
                    "event": "closed",
                    "session_id": session_id
                })
        
        # Register event handlers
        connection.on(EventType.OPEN, on_open)
        connection.on(EventType.MESSAGE, on_message)
        connection.on(EventType.ERROR, on_error)
        connection.on(EventType.CLOSE, on_close)
    
    def _start_listening(self, session_id: str, connection):
        """Start listening for events in background thread."""
        try:
            connection.start_listening()
        except Exception as e:
            print(f"Error in listener thread for session {session_id}: {str(e)}")
    
    def send_audio(self, session_id: str, audio_data: bytes) -> bool:
        """Send audio data to the voice agent."""
        try:
            if session_id not in self.active_connections:
                return False
            
            connection_state = self.active_connections[session_id]
            if not connection_state["is_active"]:
                return False
            
            connection = connection_state["connection"]
            connection.send_media(audio_data)
            
            self._log_event(session_id, f"Sent audio data: {len(audio_data)} bytes")
            return True
            
        except Exception as e:
            print(f"Error sending audio for session {session_id}: {str(e)}")
            return False
    
    def send_text_message(self, session_id: str, content: str) -> bool:
        """Send a text message as user input."""
        try:
            if session_id not in self.active_connections:
                return False
            
            connection_state = self.active_connections[session_id]
            if not connection_state["is_active"]:
                return False
            
            connection = connection_state["connection"]
            connection.send_message({
                "type": "InjectUserMessage",
                "content": content
            })
            
            self._log_event(session_id, f"Sent text message: {content}")
            return True
            
        except Exception as e:
            print(f"Error sending text message for session {session_id}: {str(e)}")
            return False
    
    def update_agent_prompt(self, session_id: str, new_prompt: str) -> bool:
        """Update the agent's prompt during conversation."""
        try:
            if session_id not in self.active_connections:
                return False
            
            connection_state = self.active_connections[session_id]
            if not connection_state["is_active"]:
                return False
            
            connection = connection_state["connection"]
            connection.send_message({
                "type": "UpdatePrompt",
                "prompt": new_prompt
            })
            
            self._log_event(session_id, "Updated agent prompt")
            return True
            
        except Exception as e:
            print(f"Error updating prompt for session {session_id}: {str(e)}")
            return False
    
    def update_agent_voice(self, session_id: str, voice_model: str) -> bool:
        """Update the agent's voice model during conversation."""
        try:
            if session_id not in self.active_connections:
                return False
            
            connection_state = self.active_connections[session_id]
            if not connection_state["is_active"]:
                return False
            
            connection = connection_state["connection"]
            connection.send_message({
                "type": "UpdateSpeak",
                "speak": {
                    "provider": {
                        "type": "deepgram",
                        "model": voice_model
                    }
                }
            })
            
            self._log_event(session_id, f"Updated agent voice to: {voice_model}")
            return True
            
        except Exception as e:
            print(f"Error updating voice for session {session_id}: {str(e)}")
            return False
    
    def end_conversation(self, session_id: str) -> bool:
        """End an active conversation."""
        try:
            if session_id not in self.active_connections:
                return False
            
            connection_state = self.active_connections[session_id]
            connection_state["is_active"] = False
            
            # Close connection
            if connection_state["connection"]:
                connection_state["connection"].close()
            
            # Remove from active connections
            del self.active_connections[session_id]
            
            self._log_event(session_id, "Conversation ended")
            return True
            
        except Exception as e:
            print(f"Error ending conversation for session {session_id}: {str(e)}")
            return False
    
    def get_conversation_history(self, session_id: str) -> List[Dict[str, Any]]:
        """Get the conversation history for a session."""
        try:
            if session_id not in self.active_connections:
                return []
            
            connection_state = self.active_connections[session_id]
            return connection_state.get("conversation_log", [])
            
        except Exception as e:
            print(f"Error getting conversation history for session {session_id}: {str(e)}")
            return []
    
    def get_active_sessions(self) -> List[Dict[str, Any]]:
        """Get list of all active sessions."""
        try:
            sessions = []
            for session_id, connection_state in self.active_connections.items():
                if connection_state["is_active"]:
                    sessions.append({
                        "session_id": session_id,
                        "user_id": connection_state["user_id"],
                        "language": connection_state["language"],
                        "scenario": connection_state["scenario"],
                        "start_time": connection_state["start_time"].isoformat(),
                        "duration": (datetime.utcnow() - connection_state["start_time"]).total_seconds()
                    })
            return sessions
            
        except Exception as e:
            print(f"Error getting active sessions: {str(e)}")
            return []
    
    # Event handler methods
    def _handle_welcome(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle welcome message."""
        self._log_event(session_id, f"Welcome: {message}")
    
    def _handle_settings_applied(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle settings applied confirmation."""
        self._log_event(session_id, f"Settings applied: {message}")
    
    def _handle_conversation_text(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle conversation text."""
        conversation_entry = {
            "timestamp": datetime.utcnow().isoformat(),
            "role": getattr(message, 'role', 'unknown'),
            "content": getattr(message, 'content', '')
        }
        connection_state["conversation_log"].append(conversation_entry)
        self._log_event(session_id, f"Conversation: {conversation_entry}")
    
    def _handle_user_started_speaking(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle user started speaking event."""
        self._log_event(session_id, "User started speaking")
    
    def _handle_agent_thinking(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle agent thinking event."""
        self._log_event(session_id, f"Agent thinking: {getattr(message, 'content', '')}")
    
    def _handle_agent_started_speaking(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle agent started speaking event."""
        # Clear audio buffer for new response
        connection_state["audio_buffer"] = bytearray()
        self._log_event(session_id, "Agent started speaking")
    
    def _handle_agent_audio_done(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle agent audio done event."""
        audio_buffer = connection_state["audio_buffer"]
        if len(audio_buffer) > 0:
            # Save audio file or process as needed
            self._log_event(session_id, f"Agent audio done: {len(audio_buffer)} bytes")
    
    def _handle_function_call_request(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle function call request."""
        self._log_event(session_id, f"Function call request: {message}")
        # Here you would implement function call handling if needed
    
    def _handle_error(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle error message with enhanced diagnostics."""
        error_desc = getattr(message, 'description', 'Unknown error')
        error_code = getattr(message, 'code', 'UNKNOWN')
        
        # Enhanced error handling for configuration issues
        error_lower = error_desc.lower()
        if "language" in error_lower:
            print(f"[{session_id}] Language configuration error. Ensure language=multi is supported.")
        elif "model" in error_lower:
            print(f"[{session_id}] Model error. Verify nova-3 model supports multilingual transcription.")
        elif "unsupported" in error_lower:
            print(f"[{session_id}] Unsupported feature. Check Deepgram account has multilingual code-switching enabled.")
        
        self._log_event(session_id, f"Error [{error_code}]: {error_desc}")
    
    def _handle_warning(self, session_id: str, message, connection_state: Dict[str, Any]):
        """Handle warning message."""
        warning_desc = getattr(message, 'description', 'Unknown warning')
        warning_code = getattr(message, 'code', 'UNKNOWN')
        self._log_event(session_id, f"Warning [{warning_code}]: {warning_desc}")
    
    def _log_event(self, session_id: str, event: str):
        """Log an event for a session."""
        try:
            log_entry = {
                "timestamp": datetime.utcnow().isoformat(),
                "event": event
            }
            print(f"[{session_id}] {event}")
            
            # Store in connection state if available
            if session_id in self.active_connections:
                connection_state = self.active_connections[session_id]
                if "events_log" not in connection_state:
                    connection_state["events_log"] = []
                connection_state["events_log"].append(log_entry)
                
        except Exception as e:
            print(f"Error logging event for session {session_id}: {str(e)}")
    
    async def _save_conversation_session(self, session_id: str, user_id: str, language: str, scenario: str):
        """Save conversation session to database."""
        try:
            session_data = {
                "id": session_id,
                "user_id": user_id,
                "language": language,
                "scenario": scenario,
                "status": "active",
                "created_at": datetime.utcnow().isoformat()
            }
            
            self.supabase_manager.client.table("agent_sessions").insert(session_data).execute()
            
        except Exception as e:
            print(f"Error saving conversation session: {str(e)}")
