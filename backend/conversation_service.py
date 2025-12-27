"""
Voice conversation service using Deepgram Voice Agent API.
Based on official Deepgram SDK documentation.
"""
import os
import json
from typing import Optional, Dict, List, Callable
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
from config import get_settings

settings = get_settings()


class ConversationAgent:
    """
    Manages real-time voice conversations using Deepgram's Agent API.
    Combines STT, LLM, and TTS for natural dialogue.
    """
    
    def __init__(
        self,
        language: str,
        level: str,
        scenario: str,
        on_transcript: callable = None,
        on_agent_response: callable = None,
        on_audio: callable = None,
    ):
        self.language = language
        self.level = level
        self.scenario = scenario
        self.on_transcript = on_transcript
        self.on_agent_response = on_agent_response
        self.on_audio = on_audio
        
        self.client = None
        self.connection = None
        self.conversation_history: List[Dict[str, str]] = []
        
    def _get_language_code_for_tts(self) -> str:
        """Get Deepgram TTS model for the target language."""
        language_models = {
            "fr": "aura-2-helios-fr",  # French
            "de": "aura-2-helios-de",  # German
            "ko": "aura-2-helios-ko",  # Korean
            "zh": "aura-2-helios-zh",  # Mandarin
            "es": "aura-2-helios-es",  # Spanish
        }
        return language_models.get(self.language, "aura-2-asteria-en")
    
    def _get_language_name(self) -> str:
        """Get full language name."""
        names = {
            "fr": "French",
            "de": "German",
            "ko": "Korean",
            "zh": "Mandarin Chinese",
            "es": "Spanish",
        }
        return names.get(self.language, "English")
    
    def _generate_system_prompt(self) -> str:
        """Generate scenario-specific system prompt for the AI tutor."""
        lang_name = self._get_language_name()
        
        prompts = {
            "travel": f"""You are a friendly {lang_name} tutor helping a {self.level} level student practice travel conversations.
Speak ONLY in {lang_name}. Keep responses short (1-2 sentences). Ask questions to encourage dialogue.
Act as: a hotel receptionist, taxi driver, tourist information officer, or restaurant staff.
Be patient and speak clearly. Correct major mistakes gently by repeating the correct phrase.""",
            
            "food": f"""You are a friendly {lang_name} tutor helping a {self.level} level student practice food and dining conversations.
Speak ONLY in {lang_name}. Keep responses short (1-2 sentences). Ask questions about their preferences.
Act as: a waiter, chef, food vendor, or grocery store clerk.
Be patient and speak clearly. Help them order food and discuss ingredients.""",
            
            "daily_conversation": f"""You are a friendly {lang_name} tutor helping a {self.level} level student practice everyday conversations.
Speak ONLY in {lang_name}. Keep responses short (1-2 sentences). Make natural small talk.
Act as: a friend, neighbor, colleague, or acquaintance.
Be conversational and encouraging. Ask about their day, hobbies, or plans.""",
            
            "work": f"""You are a friendly {lang_name} tutor helping a {self.level} level student practice professional conversations.
Speak ONLY in {lang_name}. Keep responses short (1-2 sentences). Be professional but friendly.
Act as: a colleague, manager, client, or business partner.
Help them practice meetings, emails, presentations, and workplace small talk.""",
            
            "culture": f"""You are a friendly {lang_name} tutor helping a {self.level} level student learn about culture and traditions.
Speak ONLY in {lang_name}. Keep responses short (1-2 sentences). Share cultural insights.
Discuss: holidays, customs, history, art, music, or local traditions.
Be enthusiastic and educational. Encourage questions about culture.""",
        }
        
        base_prompt = prompts.get(self.scenario, prompts["daily_conversation"])
        
        # Add level-specific guidance
        level_guidance = {
            "beginner": "Use very simple vocabulary and short sentences. Speak slowly.",
            "intermediate": "Use moderate vocabulary. Speak at a natural pace.",
            "advanced": "Use rich vocabulary and idioms. Speak naturally.",
        }
        
        return f"{base_prompt}\n\nLevel guidance: {level_guidance.get(self.level, level_guidance['intermediate'])}"
    
    async def start_conversation(self):
        """Initialize and start the conversation agent."""
        try:
            # Initialize Deepgram client
            self.client = DeepgramClient(settings.DEEPGRAM_API_KEY)
            
            # Connect to Live Transcription (Agent API not available in SDK v5)
            # Using Live Transcription as fallback
            self.connection = self.client.listen.live.v("1")
            
            # Set up event handlers
            def on_message(self, result, **kwargs):
                sentence = result.channel.alternatives[0].transcript
                if len(sentence) > 0:
                    print(f"[Transcript] {sentence}")
                    if self.on_transcript:
                        self.on_transcript(sentence)
                        self.conversation_history.append({"role": "user", "content": sentence})
            
            def on_open(self, open, **kwargs):
                print("[Agent] Connection opened")
            
            def on_close(self, close, **kwargs):
                print("[Agent] Connection closed")
            
            def on_error(self, error, **kwargs):
                print(f"[Agent] Error: {error}")
            
            self.connection.on(LiveTranscriptionEvents.Transcript, on_message)
            self.connection.on(LiveTranscriptionEvents.Open, on_open)
            self.connection.on(LiveTranscriptionEvents.Close, on_close)
            self.connection.on(LiveTranscriptionEvents.Error, on_error)
            
            # Configure live transcription options
            options = LiveOptions(
                model="nova-2",
                language=self.language,
                smart_format=True,
                encoding="linear16",
                sample_rate=16000,
            )
            
            # Start the connection
            if await self.connection.start(options):
                print(f"[Agent] Started conversation: {self.language} / {self.level} / {self.scenario}")
            else:
                raise Exception("Failed to start connection")
            
        except Exception as e:
            print(f"[Agent] Error starting conversation: {e}")
            raise
    
    async def send_audio(self, audio_bytes: bytes):
        """Send audio data to the agent."""
        if self.connection:
            self.connection.send(audio_bytes)
    
    async def inject_agent_greeting(self, greeting: str):
        """Inject an initial greeting from the agent."""
        # Manual greeting - just add to history and notify callback
        if self.on_agent_response:
            self.on_agent_response(greeting)
        self.conversation_history.append({"role": "assistant", "content": greeting})
    
    async def keep_alive(self):
        """Send keep-alive message to maintain connection."""
        if self.connection:
            self.connection.keep_alive()
    
    async def stop_conversation(self):
        """Close the conversation connection."""
        if self.connection:
            await self.connection.finish()
            self.connection = None
        print("[Agent] Conversation stopped")
    
    def get_conversation_history(self) -> List[Dict[str, str]]:
        """Get the conversation history."""
        return self.conversation_history


def get_greeting_for_scenario(language: str, scenario: str, level: str) -> str:
    """Generate an opening greeting for the conversation."""
    greetings = {
        "fr": {
            "travel": "Bonjour! Comment puis-je vous aider aujourd'hui?",
            "food": "Bonjour! Vous êtes prêt à commander?",
            "daily_conversation": "Salut! Comment ça va?",
            "work": "Bonjour! Comment allez-vous?",
            "culture": "Bonjour! Qu'est-ce qui vous intéresse?",
        },
        "de": {
            "travel": "Guten Tag! Wie kann ich Ihnen helfen?",
            "food": "Hallo! Möchten Sie bestellen?",
            "daily_conversation": "Hi! Wie geht's?",
            "work": "Guten Tag! Wie geht es Ihnen?",
            "culture": "Hallo! Was interessiert Sie?",
        },
        "ko": {
            "travel": "안녕하세요! 무엇을 도와드릴까요?",
            "food": "안녕하세요! 주문하시겠어요?",
            "daily_conversation": "안녕! 어떻게 지내?",
            "work": "안녕하세요! 어떻게 지내세요?",
            "culture": "안녕하세요! 무엇이 궁금하세요?",
        },
        "zh": {
            "travel": "你好！我能帮你什么？",
            "food": "你好！你想点什么？",
            "daily_conversation": "嗨！你好吗？",
            "work": "您好！您好吗？",
            "culture": "你好！你对什么感兴趣？",
        },
        "es": {
            "travel": "¡Hola! ¿Cómo puedo ayudarte?",
            "food": "¡Hola! ¿Estás listo para ordenar?",
            "daily_conversation": "¡Hola! ¿Cómo estás?",
            "work": "Buenos días. ¿Cómo está usted?",
            "culture": "¡Hola! ¿Qué te interesa?",
        },
    }
    
    return greetings.get(language, {}).get(scenario, "Hello! How are you?")
