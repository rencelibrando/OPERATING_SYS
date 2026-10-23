import io
import logging
from typing import Any, Dict, Optional
import google.generativeai as genai
from config import settings

logger = logging.getLogger(__name__)

class TranscriptionService:
    def __init__(self):
        self.gemini_model = None
        if settings.gemini_api_key:
            try:
                genai.configure(api_key=settings.gemini_api_key)
                self.gemini_model = genai.GenerativeModel("gemini-2.0-flash")
            except Exception as e:
                logger.warning(f"Failed to initialize Gemini for transcription: {e}")

    async def transcribe(self, audio_bytes: bytes, language: Optional[str] = None) -> Dict[str, Any]:
        if not audio_bytes:
            return {"success": False, "transcript": "", "confidence": 0.0, "words": []}

        # Try Gemini multimodal audio transcription first
        if self.gemini_model:
            try:
                lang_prompt = f"Target spoken language: {language}." if language else "Multilingual audio."
                prompt = (
                    f"Listen to this audio recording carefully. {lang_prompt} "
                    "Transcribe the spoken words exactly as spoken. "
                    "Return ONLY the verbatim transcribed text, without commentary, markdown, quotes, or timestamps."
                )
                audio_part = {
                    "mime_type": "audio/wav",
                    "data": audio_bytes,
                }
                response = self.gemini_model.generate_content([prompt, audio_part])
                text = response.text.strip() if hasattr(response, "text") and response.text else ""
                words = [{"word": w} for w in text.split()]
                return {
                    "success": True,
                    "transcript": text,
                    "confidence": 0.95,
                    "words": words,
                    "language_detected": language or "auto",
                }
            except Exception as e:
                logger.error(f"Gemini transcription error: {e}")

        # Fallback to OpenAI Whisper if OPENAI_API_KEY is available
        openai_key = getattr(settings, "openai_api_key", None) or getattr(settings, "OPENAI_API_KEY", None)
        if openai_key:
            try:
                from openai import OpenAI
                client = OpenAI(api_key=openai_key)
                buffer = io.BytesIO(audio_bytes)
                buffer.name = "audio.wav"
                transcript_obj = client.audio.transcriptions.create(
                    model="whisper-1",
                    file=buffer,
                    language=language if language and len(language) == 2 else None,
                )
                text = getattr(transcript_obj, "text", str(transcript_obj)).strip()
                words = [{"word": w} for w in text.split()]
                return {
                    "success": True,
                    "transcript": text,
                    "confidence": 0.95,
                    "words": words,
                    "language_detected": language or "auto",
                }
            except Exception as e:
                logger.error(f"OpenAI Whisper transcription error: {e}")

        return {
            "success": False,
            "transcript": "",
            "confidence": 0.0,
            "words": [],
            "error": "No available transcription provider configured (Gemini or OpenAI).",
        }

_instance: Optional[TranscriptionService] = None

def get_transcription_service() -> TranscriptionService:
    global _instance
    if _instance is None:
        _instance = TranscriptionService()
    return _instance
