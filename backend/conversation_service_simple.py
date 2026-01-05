"""
Simplified voice conversation service for real-time AI tutor dialogue.
Uses Deepgram for transcription, Gemini/DeepSeek for AI responses, and Edge TTS for Chinese/Korean.
"""
import asyncio
from typing import Dict, List, Callable, Optional
from deepgram import DeepgramClient
from providers.gemini import GeminiProvider
from providers.deepseek import DeepSeekProvider
from config import get_settings
from tts_service import get_tts_service

settings = get_settings()


class ConversationAgent:
    """
    Manages voice conversations using Deepgram transcription + LLM responses + Edge TTS for Chinese/Korean.
    """
    
    def __init__(
        self,
        language: str,
        level: str,
        scenario: str,
        on_transcript: Optional[Callable] = None,
        on_agent_response: Optional[Callable] = None,
        on_audio_generated: Optional[Callable] = None,
        provider: str = "gemini"  # "gemini" or "deepseek"
    ):
        self.language = language
        self.level = level
        self.scenario = scenario
        self.on_transcript = on_transcript
        self.on_agent_response = on_agent_response
        self.on_audio_generated = on_audio_generated
        self.provider = provider
        
        # Initialize clients
        self.deepgram_client = DeepgramClient(api_key=settings.DEEPGRAM_API_KEY)
        
        # Initialize LLM provider
        if provider == "deepseek":
            self.llm_provider = DeepSeekProvider()
        else:
            self.llm_provider = GeminiProvider()
        
        # Initialize TTS service (for Edge TTS)
        self.tts_service = get_tts_service()
        
        self.conversation_history: List[Dict[str, str]] = []
        self.is_active = False
        
        # Determine if we should use Edge TTS for this language
        self.use_edge_tts = language in ['zh', 'ko']
        
        print(f"[ConversationAgent] Initialized: {language} / {level} / {scenario}")
        print(f"[ConversationAgent] Provider: {provider}")
        print(f"[ConversationAgent] Edge TTS: {self.use_edge_tts}")
        
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
        """Initialize the conversation."""
        self.is_active = True
        print(f"[Agent] Started conversation: {self.language} / {self.level} / {self.scenario}")
    
    async def transcribe_audio(self, audio_bytes: bytes) -> Optional[str]:
        """Transcribe audio using Deepgram (same pattern as voice_service.py)."""
        try:
            # Prepare transcription options (same as voice_service.py)
            options = {
                "model": "nova-2",
                "language": self.language,
                "punctuate": True,
                "paragraphs": True,
                "diarize": False,
                "profanity_filter": True,
                "smart_format": True
            }
            
            # Transcribe the audio (same method as voice_service.py)
            response = self.deepgram_client.listen.v1.media.transcribe_file(
                request=audio_bytes,
                **options
            )
            
            # Extract transcription (same as voice_service.py)
            transcript = response.results.channels[0].alternatives[0].transcript
            
            if transcript and len(transcript.strip()) > 0:
                print(f"[Transcript] {transcript}")
                if self.on_transcript:
                    await self.on_transcript(transcript)
                self.conversation_history.append({"role": "user", "content": transcript})
                return transcript
            
            return None
            
        except Exception as e:
            print(f"[Agent] Transcription error: {e}")
            import traceback
            traceback.print_exc()
            return None
    
    async def generate_response(self, user_message: str) -> Optional[str]:
        """Generate AI response using selected LLM provider and Edge TTS for Chinese/Korean."""
        try:
            system_prompt = self._generate_system_prompt()
            
            # Format conversation history for LLM
            history = []
            for turn in self.conversation_history[-10:]:  # Last 10 turns
                history.append(turn)
            
            # Generate response using appropriate provider
            if self.provider == "deepseek":
                response = await self.llm_provider.generate_response(
                    message=user_message,
                    system_prompt=system_prompt,
                    conversation_history=history,
                    temperature=0.8,
                    max_tokens=150,
                )
            else:
                response = await self.llm_provider.generate_response(
                    message=user_message,
                    system_prompt=system_prompt,
                    conversation_history=history,
                    temperature=0.8,
                    max_tokens=150,
                )
            
            ai_response = response.message
            
            if ai_response:
                print(f"[Agent] {ai_response}")
                if self.on_agent_response:
                    await self.on_agent_response(ai_response)
                self.conversation_history.append({"role": "assistant", "content": ai_response})
                
                # Generate audio if using Edge TTS for Chinese/Korean
                if self.use_edge_tts:
                    await self._generate_audio_with_edge_tts(ai_response)
                
                return ai_response
            
            return None
            
        except Exception as e:
            print(f"[Agent] Response generation error: {e}")
            return None
    
    async def process_audio(self, audio_bytes: bytes) -> Optional[str]:
        """Process audio: transcribe + generate response."""
        transcript = await self.transcribe_audio(audio_bytes)
        if transcript:
            response = await self.generate_response(transcript)
            return response
        return None
    
    async def _generate_audio_with_edge_tts(self, text: str):
        """Generate audio using Edge TTS for Chinese/Korean."""
        try:
            print(f"[Agent] Generating Edge TTS audio for: {text[:50]}...")
            
            # Generate audio using Edge TTS
            audio_url = await self.tts_service.generate_audio(
                text=text,
                language_override=self.language,
                use_cache=True
            )
            
            if audio_url:
                print(f"[Agent] Edge TTS audio generated: {audio_url}")
                if self.on_audio_generated:
                    await self.on_audio_generated(audio_url, text)
            else:
                print(f"[Agent] Edge TTS audio generation failed")
                
        except Exception as e:
            print(f"[Agent] Edge TTS error: {e}")
            import traceback
            traceback.print_exc()
    
    async def inject_agent_greeting(self, greeting: str):
        """Add initial greeting and generate audio if needed."""
        if self.on_agent_response:
            await self.on_agent_response(greeting)
        self.conversation_history.append({"role": "assistant", "content": greeting})
        
        # Generate audio for greeting if using Edge TTS
        if self.use_edge_tts:
            await self._generate_audio_with_edge_tts(greeting)
    
    async def stop_conversation(self):
        """Close the conversation."""
        self.is_active = False
        print("[Agent] Conversation stopped")
    
    def get_conversation_history(self) -> List[Dict[str, str]]:
        """Get the conversation history."""
        return self.conversation_history
    
    def get_audio_url_for_text(self, text: str) -> Optional[str]:
        """
        Get audio URL for any text using Edge TTS (for manual audio generation).
        Useful for generating audio on demand.
        """
        if not self.use_edge_tts:
            print(f"[Agent] Edge TTS not available for language: {self.language}")
            return None
        
        try:
            # Run the async method in a new event loop if needed
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # If we're already in an async context, create a new task
                task = asyncio.create_task(
                    self.tts_service.generate_audio(
                        text=text,
                        language_override=self.language,
                        use_cache=True
                    )
                )
                return task
            else:
                # If we're not in an async context, run it directly
                return loop.run_until_complete(
                    self.tts_service.generate_audio(
                        text=text,
                        language_override=self.language,
                        use_cache=True
                    )
                )
        except Exception as e:
            print(f"[Agent] Manual audio generation error: {e}")
            return None


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


# Factory function
def create_conversation_agent(
    language: str,
    level: str, 
    scenario: str,
    on_transcript: Optional[Callable] = None,
    on_agent_response: Optional[Callable] = None,
    on_audio_generated: Optional[Callable] = None,
    provider: str = "gemini"
) -> ConversationAgent:
    """
    Create a conversation agent with Edge TTS support.
    
    Args:
        language: Language code (zh, ko, fr, de, es, etc.)
        level: Proficiency level (beginner, intermediate, advanced)
        scenario: Conversation scenario (travel, food, daily_conversation, etc.)
        on_transcript: Callback for transcription results
        on_agent_response: Callback for AI text responses
        on_audio_generated: Callback for audio generation (Edge TTS)
        provider: LLM provider (gemini or deepseek)
    
    Returns:
        ConversationAgent instance
    """
    return ConversationAgent(
        language=language,
        level=level,
        scenario=scenario,
        on_transcript=on_transcript,
        on_agent_response=on_agent_response,
        on_audio_generated=on_audio_generated,
        provider=provider
    )
