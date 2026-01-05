"""
WebSocket routes for Edge STT conversation (Chinese/Korean).
Complete flow: Edge STT → DeepSeek LLM → Edge TTS
"""
import asyncio
import json
import logging
from typing import Optional
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from conversation_service_simple import ConversationAgent
from edge_stt_service import get_edge_stt_service
from tts_service import get_tts_service

logger = logging.getLogger(__name__)

router = APIRouter()


class EdgeConversationManager:
    """Manages WebSocket connections for Edge STT conversations."""
    
    def __init__(self):
        self.active_connections: dict[str, dict] = {}
    
    async def connect(
        self,
        websocket: WebSocket,
        session_id: str,
        language: str,
        level: str,
        scenario: str,
        provider: str = "deepseek"
    ):
        """Accept connection and initialize conversation agent."""
        await websocket.accept()
        
        # Initialize conversation agent
        async def on_transcript(text: str):
            """Handle transcript from STT."""
            message = {
                "type": "UserTranscript",
                "text": text
            }
            await websocket.send_json(message)
        
        async def on_agent_response(text: str):
            """Handle AI response from LLM."""
            message = {
                "type": "AgentResponse",
                "text": text
            }
            await websocket.send_json(message)
        
        async def on_audio_generated(audio_url: str, text: str):
            """Handle audio generation from Edge TTS."""
            message = {
                "type": "AgentAudio",
                "audio_url": audio_url,
                "text": text
            }
            await websocket.send_json(message)
        
        # Create conversation agent
        agent = ConversationAgent(
            language=language,
            level=level,
            scenario=scenario,
            on_transcript=on_transcript,
            on_agent_response=on_agent_response,
            on_audio_generated=on_audio_generated,
            provider=provider
        )
        
        # Store connection
        self.active_connections[session_id] = {
            "websocket": websocket,
            "agent": agent,
            "language": language,
            "level": level,
            "scenario": scenario
        }
        
        logger.info(f"[EdgeConversation] Connected: session={session_id}, language={language}")
        
        # Send welcome message
        await self.send_welcome(session_id)
    
    async def send_welcome(self, session_id: str):
        """Send welcome message and generate audio."""
        if session_id not in self.active_connections:
            return
        
        conn = self.active_connections[session_id]
        agent = conn["agent"]
        
        # Get contextual greeting
        greeting = self._get_greeting(conn["language"], conn["level"], conn["scenario"])
        
        # Inject greeting and generate audio
        await agent.inject_agent_greeting(greeting)
    
    def _get_greeting(self, language: str, level: str, scenario: str) -> str:
        """Get contextual greeting based on language, level, and scenario."""
        greetings = {
            'zh': {
                'beginner': {
                    'daily_conversation': "你好！我很高兴帮你学中文。随时可以开始说话！",
                    'travel': "你好！我是你的中文导游。我们可以练习旅行对话！",
                    'food': "你好！我是你的中文服务员。我们可以练习点餐对话！"
                },
                'intermediate': {
                    'daily_conversation': "你好！让我们练习日常对话。你想聊什么？",
                    'travel': "你好！准备好练习旅行对话了吗？我们在听！",
                    'food': "你好！让我们练习点餐对话。你想吃什么？"
                },
                'advanced': {
                    'daily_conversation': "你好！让我们深入讨论日常话题。我准备好听你说了！",
                    'travel': "你好！让我们练习更复杂的旅行对话。开始吧！",
                    'food': "你好！让我们练习高级点餐对话。我已经准备好了！"
                }
            },
            'ko': {
                'beginner': {
                    'daily_conversation': "안녕하세요! 한국어 공부를 도와드릴게요. 편하게 말씀해 주세요!",
                    'travel': "안녕하세요! 저는 한국어 여행 가이드입니다. 여행 대화를 연습해 봐요!",
                    'food': "안녕하세요! 저는 한국어 웨이터입니다. 주문 대화를 연습해 봐요!"
                },
                'intermediate': {
                    'daily_conversation': "안녕하세요! 일상 대화를 연습해 봐요. 무엇을 이야기하고 싶으세요?",
                    'travel': "안녕하세요! 여행 대화 연습할 준비 되셨나요? 시작해 볼까요?",
                    'food': "안녕하세요! 주문 대화를 연습해 봐요. 뭐 드시고 싶으세요?"
                },
                'advanced': {
                    'daily_conversation': "안녕하세요! 일상 주제에 대해 깊이 이야기해 봐요. 준비됐어요!",
                    'travel': "안녕하세요! 더 복잡한 여행 대화를 연습해 봐요. 시작해요!",
                    'food': "안녕하세요! 고급 주문 대화를 연습해 봐요. 준비됐어요!"
                }
            }
        }
        
        return greetings.get(language, {}).get(level, {}).get(scenario, "Hello!")
    
    async def disconnect(self, session_id: str):
        """Disconnect session."""
        if session_id in self.active_connections:
            del self.active_connections[session_id]
            logger.info(f"[EdgeConversation] Disconnected: session={session_id}")
    
    async def process_audio(self, session_id: str, audio_data: bytes):
        """Process audio from client."""
        if session_id not in self.active_connections:
            logger.warning(f"[EdgeConversation] Session not found: {session_id}")
            return
        
        conn = self.active_connections[session_id]
        agent = conn["agent"]
        
        # Transcribe audio using Edge STT
        stt_service = get_edge_stt_service()
        transcript = await stt_service.transcribe_audio(
            audio_data,
            conn["language"]
        )
        
        if transcript:
            # Generate response using LLM
            await agent.generate_response(transcript)
    
    async def send_keepalive(self, session_id: str):
        """Send keepalive message."""
        if session_id in self.active_connections:
            conn = self.active_connections[session_id]
            message = {"type": "KeepAlive"}
            await conn["websocket"].send_json(message)


# Singleton instance
manager = EdgeConversationManager()


@router.websocket("/ws/edge-conversation/{session_id}")
async def edge_conversation_websocket(
    websocket: WebSocket,
    session_id: str,
    language: str,
    level: str,
    scenario: str,
    provider: str = "deepseek"
):
    """
    WebSocket endpoint for Edge STT conversation.
    
    Flow:
    1. Client sends audio chunks
    2. Edge STT transcribes to text
    3. DeepSeek LLM generates response
    4. Edge TTS generates audio
    5. Client receives text + audio URL
    """
    try:
        # Accept connection
        await manager.connect(
            websocket,
            session_id,
            language,
            level,
            scenario,
            provider
        )
        
        # Main loop
        while True:
            # Receive message from client
            data = await websocket.receive()
            
            if data["type"] == "websocket.receive":
                # Check if it's text or binary
                if "text" in data:
                    # Text message (JSON)
                    message = json.loads(data["text"])
                    
                    if message.get("type") == "CloseStream":
                        logger.info(f"[EdgeConversation] Client requested close: {session_id}")
                        break
                    
                elif "bytes" in data:
                    # Binary audio data
                    audio_data = data["bytes"]
                    
                    # Process audio
                    await manager.process_audio(session_id, audio_data)
            
            elif data["type"] == "websocket.disconnect":
                logger.info(f"[EdgeConversation] Client disconnected: {session_id}")
                break
    
    except WebSocketDisconnect:
        logger.info(f"[EdgeConversation] WebSocket disconnected: {session_id}")
    
    except Exception as e:
        logger.error(f"[EdgeConversation] Error: {e}")
        import traceback
        traceback.print_exc()
    
    finally:
        # Cleanup
        await manager.disconnect(session_id)
