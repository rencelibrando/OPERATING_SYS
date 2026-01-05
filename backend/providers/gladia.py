"""
Gladia Speech-to-Text Provider
Implements real-time transcription using Gladia API with automatic language detection
"""

import json
import asyncio
import websockets
import logging
from typing import Optional, Dict, Any, Callable
from .base import BaseSTTProvider

logger = logging.getLogger(__name__)

class GladiaSTTProvider(BaseSTTProvider):
    """Gladia STT provider with automatic language detection for Chinese/Korean"""
    
    def __init__(self, api_key: str, config: Optional[Dict[str, Any]] = None):
        self.api_key = api_key
        self.config = config or {}
        self.websocket_url = "wss://api.gladia.io/v2/live"
        self.websocket = None
        self.is_connected = False
        
    async def transcribe_audio_stream(
        self,
        audio_stream: Callable[[], bytes],
        language: str = "auto",
        on_partial_transcript: Optional[Callable[[str], None]] = None,
        on_final_transcript: Optional[Callable[[str, float], None]] = None,
        on_error: Optional[Callable[[str], None]] = None
    ) -> None:
        """
        Transcribe audio stream using Gladia WebSocket API
        
        Args:
            audio_stream: Function that returns audio chunks
            language: Target language (auto for automatic detection)
            on_partial_transcript: Callback for partial transcripts
            on_final_transcript: Callback for final transcripts with confidence
            on_error: Callback for errors
        """
        try:
            # Configure Gladia with automatic language detection
            gladia_config = {
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
            
            # If specific language is requested, configure it
            if language != "auto":
                language_mapping = {
                    "zh": "zh-CN",
                    "zh-CN": "zh-CN", 
                    "ko": "ko-KR",
                    "ko-KR": "ko-KR",
                    "en": "en-US",
                    "en-US": "en-US"
                }
                gladia_lang = language_mapping.get(language, language)
                gladia_config["language_config"]["languages"] = [gladia_lang]
            
            logger.info(f"[Gladia] Connecting to WebSocket with config: {gladia_config}")
            
            # Connect to Gladia WebSocket
            headers = {
                "x-gladia-key": self.api_key,
                "Content-Type": "application/json"
            }
            
            self.websocket = await websockets.connect(
                self.websocket_url,
                extra_headers=headers
            )
            self.is_connected = True
            
            # Send configuration
            await self.websocket.send(json.dumps(gladia_config))
            logger.info("[Gladia] Configuration sent, starting audio stream")
            
            # Start receiving responses
            receive_task = asyncio.create_task(
                self._receive_responses(on_partial_transcript, on_final_transcript, on_error)
            )
            
            # Send audio data
            try:
                while self.is_connected and self.websocket:
                    audio_chunk = audio_stream()
                    if audio_chunk:
                        await self.websocket.send(audio_chunk)
                    else:
                        await asyncio.sleep(0.01)  # Small delay if no audio
            except Exception as e:
                logger.error(f"[Gladia] Audio streaming error: {e}")
                if on_error:
                    on_error(str(e))
            
            # Wait for receiving to complete
            await receive_task
            
        except Exception as e:
            logger.error(f"[Gladia] Transcription error: {e}")
            if on_error:
                on_error(str(e))
        finally:
            await self.close()
    
    async def _receive_responses(
        self,
        on_partial_transcript: Optional[Callable[[str], None]],
        on_final_transcript: Optional[Callable[[str, float], None]],
        on_error: Optional[Callable[[str], None]]
    ):
        """Receive and process WebSocket responses from Gladia"""
        try:
            while self.is_connected and self.websocket:
                message = await self.websocket.recv()
                
                if isinstance(message, str):
                    data = json.loads(message)
                    
                    # Handle different message types
                    if data.get("type") == "transcription":
                        transcription = data.get("transcription", "")
                        confidence = data.get("confidence", 0.0)
                        is_final = data.get("is_final", False)
                        language_detected = data.get("language", "")
                        
                        logger.info(f"[Gladia] {'Final' if is_final else 'Partial'} transcript: '{transcription}' (confidence: {confidence}, lang: {language_detected})")
                        
                        if is_final and on_final_transcript:
                            on_final_transcript(transcription, confidence)
                        elif not is_final and on_partial_transcript:
                            on_partial_transcript(transcription)
                    
                    elif data.get("type") == "error":
                        error_msg = data.get("message", "Unknown error")
                        logger.error(f"[Gladia] Error: {error_msg}")
                        if on_error:
                            on_error(error_msg)
                    
                    elif data.get("type") == "language_detected":
                        language = data.get("language", "")
                        logger.info(f"[Gladia] Language detected: {language}")
                    
                    else:
                        logger.debug(f"[Gladia] Unknown message type: {data.get('type')}")
                
        except websockets.exceptions.ConnectionClosed:
            logger.info("[Gladia] WebSocket connection closed")
        except Exception as e:
            logger.error(f"[Gladia] Receive error: {e}")
            if on_error:
                on_error(str(e))
    
    async def close(self):
        """Close the WebSocket connection"""
        if self.websocket:
            self.is_connected = False
            try:
                await self.websocket.close()
                logger.info("[Gladia] WebSocket closed")
            except Exception as e:
                logger.error(f"[Gladia] Error closing WebSocket: {e}")
            finally:
                self.websocket = None
    
    def get_supported_languages(self) -> list[str]:
        """Get list of supported languages"""
        return [
            "zh-CN",  # Chinese (Mandarin)
            "ko-KR",  # Korean
            "en-US",  # English (US)
            "ja-JP",  # Japanese
            "es-ES",  # Spanish
            "fr-FR",  # French
            "de-DE",  # German
            "it-IT",  # Italian
            "pt-BR",  # Portuguese (Brazil)
            "ru-RU",  # Russian
        ]
    
    async def test_connection(self) -> bool:
        """Test connection to Gladia API"""
        try:
            headers = {
                "x-gladia-key": self.api_key,
                "Content-Type": "application/json"
            }
            
            # Try to connect and disconnect quickly
            websocket = await websockets.connect(
                self.websocket_url,
                extra_headers=headers
            )
            await websocket.close()
            logger.info("[Gladia] Connection test successful")
            return True
        except Exception as e:
            logger.error(f"[Gladia] Connection test failed: {e}")
            return False
