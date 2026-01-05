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
        # Deepgram Agent API only supports 'en' and 'es' for the language field
        self.language_mapping = {
            "english": "en",
            "french": "en",    # Use English for French tutoring
            "german": "en",    # Use English for German tutoring
            "korean": "en",    # Use English for Korean tutoring
            "mandarin": "en",  # Use English for Mandarin tutoring
            "spanish": "es"    # Spanish supported
        }
        
        # Language-specific voice models for better TTS quality
        # Using only available Deepgram voice models
        self.voice_models = {
            "english": "aura-2-thalia-en",
            "french": "aura-2-asteria-en",  # Use English voice for French tutoring
            "german": "aura-2-athena-en",   # Use English voice for German tutoring
            "korean": "aura-2-luna-en",     # Use English voice for Korean tutoring
            "mandarin": "aura-2-orion-en",  # Use English voice for Mandarin tutoring
            "spanish": "aura-2-sirio-es"    # Spanish voice available
        }
        
        # Agent personalities for different scenarios
        self.agent_personalities = self._load_agent_personalities()
    
    def get_language_info(self, language: str) -> Dict[str, Any]:
        """Get language information including greeting, context, and voice model."""
        language_lower = language.lower()
        
        language_greetings = {
            "english": "Hello! I'm your English language tutor. How can I help you today?",
            "french": "Hello! I'm your French language tutor. How can I help you today?",
            "german": "Hello! I'm your German language tutor. How can I help you today?",
            "korean": "Hello! I'm your Korean language tutor. How can I help you today?",
            "mandarin": "Hello! I'm your Mandarin Chinese language tutor. How can I help you today?",
            "spanish": "Hello! I'm your Spanish language tutor. How can I help you today?"
        }
        
        language_context = {
            "english": {
                "examples": ["Hello, how are you?", "The weather is nice today.", "I enjoy learning new things."],
                "phrases": ["Good morning", "Thank you very much", "Excuse me", "How much does this cost?"],
                "cultural_notes": "English is a global language with many dialects. It's known for its relatively simple grammar but extensive vocabulary."
            },
            "french": {
                "examples": ["Bonjour, comment ça va?", "Le temps est beau aujourd'hui.", "J'aime apprendre de nouvelles choses."],
                "phrases": ["Bonjour", "Merci beaucoup", "Excusez-moi", "C'est combien?"],
                "cultural_notes": "French is known for its romantic sound and formal/informal distinctions (tu/vous)."
            },
            "german": {
                "examples": ["Guten Tag, wie geht es Ihnen?", "Das Wetter ist heute schön.", "Ich lerne gerne neue Dinge."],
                "phrases": ["Guten Tag", "Vielen Dank", "Entschuldigung", "Wie viel kostet das?"],
                "cultural_notes": "German is known for its compound words and precise grammar. All nouns are capitalized."
            },
            "korean": {
                "examples": ["안녕하세요, 어떻게 지내세요?", "오늘 날씨가 좋네요.", "저는 새로운 것을 배우하는 것을 좋아해요."],
                "phrases": ["안녕하세요", "감사합니다", "실례합니다", "이거 얼마예요?"],
                "cultural_notes": "Korean has formal and informal speech levels, and uses honorifics to show respect."
            },
            "mandarin": {
                "examples": ["你好，你好吗？", "今天天气很好。", "我喜欢学习新东西。"],
                "phrases": ["你好", "谢谢", "不好意思", "这个多少钱？"],
                "cultural_notes": "Mandarin Chinese is a tonal language with four main tones. Characters represent meaning rather than sound."
            },
            "spanish": {
                "examples": ["Hola, ¿cómo estás?", "El tiempo está bueno hoy.", "Me gusta aprender cosas nuevas."],
                "phrases": ["Hola", "Muchas gracias", "Disculpe", "¿Cuánto cuesta esto?"],
                "cultural_notes": "Spanish has formal/informal distinctions (usted/tú) and varies significantly across regions."
            }
        }
        
        # Language-specific voice models for better TTS quality
        # Using only available Deepgram voice models
        voice_models = {
            "english": "aura-2-thalia-en",
            "french": "aura-2-asteria-en",  # Use English voice for French tutoring
            "german": "aura-2-athena-en",   # Use English voice for German tutoring
            "korean": "aura-2-luna-en",     # Use English voice for Korean tutoring
            "mandarin": "aura-2-orion-en",  # Use English voice for Mandarin tutoring
            "spanish": "aura-2-sirio-es"    # Spanish voice available
        }
        
        return {
            "language": language,
            "language_code": self.language_mapping.get(language_lower, "en"),
            "greeting": language_greetings.get(language_lower, language_greetings["english"]),
            "context": language_context.get(language_lower, language_context["english"]),
            "voice_model": voice_models.get(language_lower, voice_models["english"])
        }
    
    def _get_personality_for_scenario_and_level(self, scenario: str, level: str) -> Dict[str, str]:
        """Get personality configuration based on scenario and proficiency level."""
        base_personalities = self.agent_personalities
        
        # Level-specific modifications
        level_modifiers = {
            "beginner": {
                "language_tutor": {
                    "role": "You are a friendly and patient language tutor for beginners.",
                    "instructions": """
                    Help the beginner user practice speaking in their target language. Your role is to:
                    1. Use simple, clear language and speak slowly
                    2. Focus on basic vocabulary and simple sentence structures
                    3. Repeat and rephrase when necessary
                    4. Provide lots of encouragement and positive feedback
                    5. Correct major errors gently and simply
                    6. Use gestures and simple explanations
                    7. Ask yes/no questions and simple choice questions
                    """
                },
                "travel": {
                    "role": "You are a helpful travel guide for beginners.",
                    "instructions": """
                    Help beginners practice essential travel phrases. Your role is to:
                    1. Focus on basic travel vocabulary (hotel, restaurant, directions)
                    2. Use simple, practical scenarios
                    3. Repeat important phrases slowly
                    4. Provide context for when to use each phrase
                    5. Be very patient and encouraging
                    6. Use role-play for common travel situations
                    """
                },
                "food": {
                    "role": "You are a friendly food guide for beginners.",
                    "instructions": """
                    Help beginners practice food-related vocabulary. Your role is to:
                    1. Focus on basic food words and ordering phrases
                    2. Use simple restaurant scenarios
                    3. Teach essential phrases like "I would like", "thank you", "the bill please"
                    4. Be patient and repeat when needed
                    5. Use visual descriptions and simple explanations
                    """
                },
                "daily_conversation": {
                    "role": "You are a friendly conversation partner for beginners.",
                    "instructions": """
                    Help beginners practice daily conversation. Your role is to:
                    1. Use simple greetings and basic questions
                    2. Focus on personal information and daily activities
                    3. Speak slowly and clearly
                    4. Use repetition and simple sentence structures
                    5. Be very encouraging and positive
                    6. Ask about family, weather, hobbies in simple terms
                    """
                },
                "work": {
                    "role": "You are a patient workplace communication coach for beginners.",
                    "instructions": """
                    Help beginners practice basic workplace communication. Your role is to:
                    1. Focus on simple office vocabulary and phrases
                    2. Practice basic meeting and phone call scenarios
                    3. Use simple professional language
                    4. Teach essential phrases like "I have a meeting", "thank you"
                    5. Be patient and provide clear examples
                    """
                },
                "culture": {
                    "role": "You are a cultural guide for beginners.",
                    "instructions": """
                    Help beginners learn about culture through simple conversations. Your role is to:
                    1. Focus on basic cultural topics and traditions
                    2. Use simple language to explain cultural concepts
                    3. Share interesting cultural facts simply
                    4. Ask about the user's culture and compare
                    5. Be respectful and encouraging
                    """
                }
            },
            "intermediate": {
                "language_tutor": {
                    "role": "You are an engaging language tutor for intermediate learners.",
                    "instructions": """
                    Help the intermediate user improve their language skills. Your role is to:
                    1. Use more complex vocabulary and grammar
                    2. Introduce idioms and natural expressions
                    3. Discuss a variety of topics and current events
                    4. Provide detailed feedback on pronunciation and grammar
                    5. Challenge the user with more complex questions
                    6. Encourage spontaneous conversation
                    """
                },
                "travel": {
                    "role": "You are an experienced travel guide for intermediate learners.",
                    "instructions": """
                    Help intermediate learners practice realistic travel conversations. Your role is to:
                    1. Practice complex travel scenarios (booking, complaints, recommendations)
                    2. Use more detailed travel vocabulary
                    3. Discuss cultural aspects of travel
                    4. Practice problem-solving situations while traveling
                    5. Encourage more detailed descriptions and questions
                    """
                },
                "food": {
                    "role": "You are a knowledgeable food guide for intermediate learners.",
                    "instructions": """
                    Help intermediate learners discuss food in detail. Your role is to:
                    1. Practice detailed food descriptions and reviews
                    2. Discuss cooking methods and recipes
                    3. Talk about dietary preferences and restrictions
                    4. Practice restaurant recommendations and detailed ordering
                    5. Discuss cultural aspects of food and dining
                    """
                },
                "daily_conversation": {
                    "role": "You are an engaging conversation partner for intermediate learners.",
                    "instructions": """
                    Help intermediate learners practice natural daily conversations. Your role is to:
                    1. Discuss opinions, experiences, and future plans
                    2. Use more complex sentence structures
                    3. Practice storytelling and detailed descriptions
                    4. Discuss current events and interesting topics
                    5. Encourage expressing opinions and feelings
                    """
                },
                "work": {
                    "role": "You are a professional communication coach for intermediate learners.",
                    "instructions": """
                    Help intermediate learners practice workplace communication. Your role is to:
                    1. Practice professional emails and meetings
                    2. Discuss project management and teamwork
                    3. Practice negotiation and presentation skills
                    4. Use business-appropriate vocabulary
                    5. Discuss career development and goals
                    """
                },
                "culture": {
                    "role": "You are a cultural expert for intermediate learners.",
                    "instructions": """
                    Help intermediate learners discuss cultural topics in depth. Your role is to:
                    1. Discuss cultural differences and similarities
                    2. Talk about history, art, and traditions
                    3. Practice discussing cultural observations
                    4. Encourage critical thinking about cultural topics
                    5. Share insights about cultural etiquette
                    """
                }
            },
            "advanced": {
                "language_tutor": {
                    "role": "You are a sophisticated language coach for advanced learners.",
                    "instructions": """
                    Help the advanced user achieve fluency and nuance. Your role is to:
                    1. Use sophisticated vocabulary and complex grammar
                    2. Focus on idiomatic expressions and cultural nuances
                    3. Discuss abstract topics and professional subjects
                    4. Provide subtle corrections and stylistic feedback
                    5. Challenge with debates and complex discussions
                    6. Focus on achieving native-like fluency
                    """
                },
                "travel": {
                    "role": "You are a sophisticated travel expert for advanced learners.",
                    "instructions": """
                    Help advanced learners practice complex travel discussions. Your role is to:
                    1. Discuss travel planning, logistics, and budgeting
                    2. Practice complex travel problem-solving
                    3. Discuss sustainable and cultural tourism
                    4. Practice detailed travel reviews and recommendations
                    5. Discuss travel writing and photography
                    """
                },
                "food": {
                    "role": "You are a culinary expert for advanced learners.",
                    "instructions": """
                    Help advanced learners discuss food at an expert level. Your role is to:
                    1. Discuss gastronomy and culinary techniques
                    2. Talk about food science and nutrition
                    3. Practice detailed food criticism and reviews
                    4. Discuss food industry trends and sustainability
                    5. Practice complex culinary vocabulary
                    """
                },
                "daily_conversation": {
                    "role": "You are an intellectual conversation partner for advanced learners.",
                    "instructions": """
                    Help advanced learners practice sophisticated conversations. Your role is to:
                    1. Discuss philosophy, politics, and current events
                    2. Practice debating and expressing complex opinions
                    3. Use humor, sarcasm, and cultural references
                    4. Discuss abstract concepts and theories
                    5. Practice persuasive communication
                    """
                },
                "work": {
                    "role": "You are an executive communication coach for advanced learners.",
                    "instructions": """
                    Help advanced learners practice high-level professional communication. Your role is to:
                    1. Practice executive presentations and negotiations
                    2. Discuss strategic planning and leadership
                    3. Practice complex business writing
                    4. Discuss industry trends and innovations
                    5. Practice networking and relationship building
                    """
                },
                "culture": {
                    "role": "You are a cultural anthropologist for advanced learners.",
                    "instructions": """
                    Help advanced learners discuss culture at an academic level. Your role is to:
                    1. Discuss cultural theory and anthropology
                    2. Analyze cultural impacts on society
                    3. Practice discussing cultural research and studies
                    4. Discuss globalization and cultural exchange
                    5. Encourage critical cultural analysis
                    """
                }
            }
        }
        
        # Get the level-specific personality or fall back to base
        if scenario in level_modifiers.get(level, {}):
            return level_modifiers[level][scenario]
        elif scenario in base_personalities:
            return base_personalities[scenario]
        else:
            return base_personalities["language_tutor"]
    
    def _get_level_context(self, level: str, scenario: str) -> str:
        """Get level and scenario specific context and prompts."""
        
        # Sample prompts for different levels and scenarios
        sample_prompts = {
            "beginner": {
                "travel": [
                    "Where is the nearest hotel?",
                    "How much does this cost?",
                    "I would like to order coffee",
                    "Can you help me find the train station?",
                    "Do you speak English?"
                ],
                "food": [
                    "I would like water",
                    "This is delicious",
                    "The bill please",
                    "Do you have a menu in English?",
                    "I'm allergic to nuts"
                ],
                "daily_conversation": [
                    "Hello, how are you?",
                    "My name is...",
                    "Nice to meet you",
                    "See you later",
                    "Thank you very much"
                ],
                "work": [
                    "I have a meeting",
                    "Please send me an email",
                    "What time is the appointment?",
                    "I need to make a phone call",
                    "Can we schedule a meeting?"
                ],
                "culture": [
                    "This is beautiful",
                    "Tell me about this tradition",
                    "Happy holidays!",
                    "I enjoy learning about your culture",
                    "What is this celebration?"
                ]
            },
            "intermediate": {
                "travel": [
                    "Could you recommend a good local restaurant?",
                    "I need to book a room for two nights",
                    "What time does the museum close?",
                    "Is there a pharmacy nearby?",
                    "I'd like to buy a ticket to the city center"
                ],
                "food": [
                    "What is today's special?",
                    "Can I have the recipe for this dish?",
                    "Is this dish traditionally spicy?",
                    "What wine would you recommend?",
                    "I'd like to make a reservation for dinner"
                ],
                "daily_conversation": [
                    "What did you do today?",
                    "How was your weekend?",
                    "I'm planning to visit the cinema",
                    "What's the weather like today?",
                    "I need to go to the post office"
                ],
                "work": [
                    "I'll prepare the presentation",
                    "Let's review the quarterly report",
                    "We need to discuss the project timeline",
                    "Could you forward me the documents?",
                    "I'd like to propose a new strategy"
                ],
                "culture": [
                    "Can you explain this historical event?",
                    "I'm interested in local art and music",
                    "What are some cultural etiquette tips?",
                    "How do people celebrate this festival?",
                    "I'd like to learn more about traditional crafts"
                ]
            },
            "advanced": {
                "travel": [
                    "What are the must-see attractions for a first-time visitor?",
                    "Could you explain the local customs I should be aware of?",
                    "I'm looking for authentic local experiences off the beaten path",
                    "How has tourism impacted the local community?",
                    "What sustainable travel options are available in this region?"
                ],
                "food": [
                    "Could you tell me about the regional cuisine?",
                    "What cooking techniques are used in this dish?",
                    "How has this dish evolved over time?",
                    "What are the seasonal ingredients in this cuisine?",
                    "How does this food reflect the local culture and history?"
                ],
                "daily_conversation": [
                    "What's your opinion on current events?",
                    "I've been thinking about career development",
                    "Let's discuss the pros and cons of remote work",
                    "How do you see technology changing our daily lives?",
                    "What are your thoughts on work-life balance?"
                ],
                "work": [
                    "Let's analyze the market trends",
                    "We should optimize our workflow",
                    "I'd like to discuss the strategic direction",
                    "How can we improve team collaboration?",
                    "What are the key performance indicators we should track?"
                ],
                "culture": [
                    "How has globalization affected local traditions?",
                    "What role does religion play in daily life?",
                    "Can you discuss the influence of historical events?",
                    "How do cultural values shape social behavior?",
                    "What are the generational differences in cultural attitudes?"
                ]
            }
        }
        
        # Get sample prompts for this level and scenario
        prompts = sample_prompts.get(level, {}).get(scenario, [])
        
        if prompts:
            prompts_text = "\nSAMPLE PROMPTS FOR THIS LEVEL AND SCENARIO:\n"
            prompts_text += "- " + "\n- ".join(prompts[:3])  # Show first 3 prompts
            prompts_text += f"\n\nUse these types of {level} level {scenario.replace('_', ' ')} conversations as examples."
            return prompts_text
        else:
            return f"Focus on {level} level {scenario.replace('_', ' ')} conversations and scenarios."
    
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
        level: str = "beginner",
        voice_model: Optional[str] = None,
        temperature: float = 0.7,
        custom_prompt: Optional[str] = None
    ) -> AgentV1SettingsMessage:
        """Create agent settings configuration."""
        
        # Get language code
        lang_code = self.language_mapping.get(language.lower(), "en")
        
        # Use language-specific voice model if none provided
        if voice_model is None:
            voice_model = self.voice_models.get(language.lower(), self.voice_models["english"])
        
        # Get personality based on scenario and level
        personality = self._get_personality_for_scenario_and_level(scenario, level)
        
        # Get language-specific info
        lang_info = self.get_language_info(language)
        greeting = lang_info["greeting"]
        context_info = lang_info["context"]
        
        # Get level and scenario specific prompts
        level_context = self._get_level_context(level, scenario)
        
        # Create prompt with language, level, and scenario context
        if custom_prompt:
            prompt = custom_prompt
        else:
            prompt = f"""
            {personality['role']}
            
            {personality['instructions']}
            
            Current date: {datetime.now().strftime("%A, %B %d, %Y")}
            
            Language: {language.title()}
            Level: {level.title()}
            Scenario: {scenario.replace('_', ' ').title()}
            
            LANGUAGE CONTEXT FOR {language.upper()}:
            - Common phrases: {', '.join(context_info['phrases'])}
            - Example sentences: {', '.join(context_info['examples'])}
            - Cultural notes: {context_info['cultural_notes']}
            
            {level_context}
            
            IMPORTANT INSTRUCTIONS:
            - Speak in English during normal conversation
            - When the user asks to hear {language} phrases or wants to practice pronunciation, provide the {language} text and then say it in {language}
            - Always provide English translations when speaking in {language}
            - Be encouraging and supportive
            - Keep the conversation flowing naturally
            - Adjust your language level to match the user's {level} proficiency
            - Focus on {scenario.replace('_', ' ')} scenarios
            """
        
        # Configure audio settings - Deepgram default 24kHz for best quality
        audio_config = AgentV1AudioConfig(
            input=AgentV1AudioInput(
                encoding="linear16",
                sample_rate=24000,  # Deepgram recommended 24kHz
            ),
            output=AgentV1AudioOutput(
                encoding="linear16",
                sample_rate=24000,  # Deepgram default 24kHz
                container="none",  # Use "none" for WebSocket connections to prevent audio artifacts
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
                    type="groq",  # Groq is optimized for low-latency voice agents
                    model="openai/gpt-oss-20b",
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
            greeting=greeting,
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
        level: str = "beginner",
        voice_model: Optional[str] = None,
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
                level=level,
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
