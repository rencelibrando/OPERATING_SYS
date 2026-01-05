"""
Edge STT service for Chinese and Korean speech recognition.
Uses Deepgram v2/listen API for high-quality multilingual support.
"""
import asyncio
import logging
from typing import Optional, Callable
import json
from config import get_settings

settings = get_settings()

logger = logging.getLogger(__name__)


class EdgeSTTService:
    """
    Speech-to-Text service using Deepgram v2/listen API.
    Supports Chinese (Mandarin) and Korean with high accuracy.
    """
    
    def __init__(self):
        self.deepgram_api_key = getattr(settings, 'deepgram_api_key', None)
        self.deepgram_url = "wss://api.deepgram.com/v2/listen"
        
        if not self.deepgram_api_key:
            logger.warning("[EdgeSTT] Deepgram API key not configured. Using mock STT.")
        else:
            logger.info("[EdgeSTT] Deepgram v2/listen initialized")
    
    def get_language_code(self, language: str) -> str:
        """Map language codes to Deepgram language codes."""
        language_map = {
            'zh': 'zh',        # Mandarin Chinese
            'zh-CN': 'zh',     # Mandarin Chinese (Simplified)
            'zh-TW': 'zh',     # Mandarin Chinese (Traditional)
            'ko': 'ko',        # Korean
        }
        return language_map.get(language, 'en')
    
    async def transcribe_audio(
        self,
        audio_bytes: bytes,
        language: str,
        on_result: Optional[Callable] = None
    ) -> Optional[str]:
        """
        Transcribe audio using Deepgram v2/listen API.
        
        Args:
            audio_bytes: Raw audio data (PCM 16-bit, 16kHz)
            language: Language code ('zh', 'ko', etc.)
            on_result: Callback for intermediate results
            
        Returns:
            Transcribed text or None if failed
        """
        if not self.deepgram_api_key:
            # Fallback: return mock transcription for testing
            logger.warning("[EdgeSTT] Deepgram not configured, using mock transcription")
            return "测试语音识别" if language == 'zh' else "테스트 음성 인식"
        
        try:
            import websockets
            
            # Configure language and model
            deepgram_language = self.get_language_code(language)
            
            # Build WebSocket URL with optimized Deepgram parameters
            # - model: nova-3 for best multilingual accuracy
            # - smart_format: auto punctuation and formatting
            url = (
                f"{self.deepgram_url}"
                f"?model=nova-3"
                f"&language={deepgram_language}"
                f"&encoding=linear16"
                f"&sample_rate=16000"
                f"&channels=1"
                f"&smart_format=true"
            )
            
            logger.info(f"[EdgeSTT] Connecting to Deepgram: {url}")
            
            async with websockets.connect(
                url,
                extra_headers={"Authorization": f"Token {self.deepgram_api_key}"}
            ) as websocket:
                logger.info("[EdgeSTT] Connected to Deepgram v2/listen")
                
                # Send audio data
                await websocket.send(audio_bytes)
                
                # Close the stream to get final result
                await websocket.send(json.dumps({"type": "CloseStream"}))
                
                # Receive response
                response = await websocket.recv()
                result = json.loads(response)
                
                if result.get("type") == "FinalResult":
                    transcript = result.get("channel", {}).get("alternatives", [{}])[0].get("transcript", "")
                    confidence = result.get("channel", {}).get("alternatives", [{}])[0].get("confidence", 0)
                    
                    if transcript and confidence > 0.3:
                        logger.info(f"[EdgeSTT] Transcribed: '{transcript}' (confidence: {confidence})")
                        
                        if on_result:
                            on_result(transcript)
                        
                        return transcript
                    else:
                        logger.warning(f"[EdgeSTT] Low confidence or empty transcript: {confidence}")
                        return None
                        
                elif result.get("type") == "Error":
                    error_msg = result.get("description", "Unknown error")
                    logger.error(f"[EdgeSTT] Deepgram error: {error_msg}")
                    return None
                
        except Exception as e:
            logger.error(f"[EdgeSTT] Transcription error: {e}")
            import traceback
            traceback.print_exc()
            return None
        
        return None
    
    async def transcribe_with_streaming(
        self,
        audio_stream,
        language: str,
        on_intermediate: Optional[Callable] = None,
        on_final: Optional[Callable] = None
    ):
        """
        Transcribe audio with streaming results using Deepgram v2/listen.
        
        Args:
            audio_stream: Audio data stream
            language: Language code
            on_intermediate: Callback for intermediate results
            on_final: Callback for final results
        """
        if not self.deepgram_api_key:
            logger.warning("[EdgeSTT] Deepgram not configured, skipping streaming")
            return
        
        try:
            import websockets
            
            # Configure language and model
            deepgram_language = self.get_language_code(language)
            
            # Build WebSocket URL with optimized Deepgram streaming parameters
            # - model: nova-3 for best multilingual accuracy
            # - interim_results: show partial transcripts for responsive UI
            # - smart_format: auto punctuation and formatting
            # - endpointing: 300ms pause = sentence end
            url = (
                f"{self.deepgram_url}"
                f"?model=nova-3"
                f"&language={deepgram_language}"
                f"&encoding=linear16"
                f"&sample_rate=16000"
                f"&channels=1"
                f"&interim_results=true"
                f"&smart_format=true"
                f"&endpointing=300"
            )
            
            logger.info(f"[EdgeSTT] Connecting to Deepgram streaming: {url}")
            
            async with websockets.connect(
                url,
                extra_headers={"Authorization": f"Token {self.deepgram_api_key}"}
            ) as websocket:
                logger.info("[EdgeSTT] Connected to Deepgram v2/listen streaming")
                
                # Start listening for responses
                async def receive_responses():
                    try:
                        while True:
                            response = await websocket.recv()
                            result = json.loads(response)
                            
                            if result.get("type") == "SpeechStarted":
                                logger.debug("[EdgeSTT] Speech started")
                                
                            elif result.get("type") == "SpeechFinished":
                                logger.debug("[EdgeSTT] Speech finished")
                                
                            elif result.get("type") == "PartialResult":
                                transcript = result.get("partial", "")
                                if on_intermediate and transcript:
                                    on_intermediate(transcript)
                                    
                            elif result.get("type") == "FinalResult":
                                transcript = result.get("channel", {}).get("alternatives", [{}])[0].get("transcript", "")
                                confidence = result.get("channel", {}).get("alternatives", [{}])[0].get("confidence", 0)
                                
                                if transcript and confidence > 0.3:
                                    logger.info(f"[EdgeSTT] Final transcript: '{transcript}' (confidence: {confidence})")
                                    if on_final:
                                        on_final(transcript)
                                        
                            elif result.get("type") == "Error":
                                error_msg = result.get("description", "Unknown error")
                                logger.error(f"[EdgeSTT] Deepgram streaming error: {error_msg}")
                                break
                                
                    except websockets.exceptions.ConnectionClosed:
                        logger.info("[EdgeSTT] Streaming connection closed")
                    except Exception as e:
                        logger.error(f"[EdgeSTT] Streaming receive error: {e}")
                
                # Start receiving responses in background
                receive_task = asyncio.create_task(receive_responses())
                
                # Process audio stream (implementation depends on how audio is provided)
                # This would typically read from audio_stream and send to websocket
                # For now, this is a placeholder for the streaming implementation
                
                # Wait for streaming to complete
                await receive_task
                
        except Exception as e:
            logger.error(f"[EdgeSTT] Streaming transcription error: {e}")
            import traceback
            traceback.print_exc()


# Singleton instance
_edge_stt_service = None

def get_edge_stt_service() -> EdgeSTTService:
    """Get or create Edge STT service instance."""
    global _edge_stt_service
    if _edge_stt_service is None:
        _edge_stt_service = EdgeSTTService()
    return _edge_stt_service
