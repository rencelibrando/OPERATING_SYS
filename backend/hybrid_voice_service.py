"""
Hybrid Voice Service - Routes voice API calls to the correct provider

Voice Agent Routing:
- ElevenLabs Agent: All languages (English, Spanish, German, French, Korean, Mandarin Chinese)
  Full conversational AI with native language support

TTS-Only Routing:
- Deepgram TTS: English, Spanish, German, French (fallback)
- ElevenLabs TTS: Korean, Mandarin Chinese, and all languages via Agent
"""
import os
import json
from typing import Optional, Dict, Any
from deepgram import DeepgramClient
import aiohttp
from voice_models import (
    VoiceTranscribeRequest,
    VoiceTranscribeResponse,
    VoiceLanguage
)
from config import settings


class HybridVoiceService:
    """
    Manages voice transcription and synthesis using the appropriate provider per language.

    Routing Rules:
    - ElevenLabs Agent: All languages (full conversational AI via ElevenLabs Platform)
    
    Clients should use the /elevenlabs-agent/* endpoints which provide 
    full conversational AI with native language support for all languages.
    """

    # Languages that use ElevenLabs Agent Platform (now includes all supported languages)
    ELEVENLABS_AGENT_LANGUAGES = {
        "korean", "mandarin", "chinese", "ko", "zh",
        "english", "en",
        "french", "fr",
        "german", "de",
        "spanish", "es"
    }

    def __init__(self):
        # Initialize Deepgram client for transcription
        self.deepgram_client = DeepgramClient(api_key=settings.deepgram_api_key)

        # ElevenLabs configuration
        self.elevenlabs_api_key = settings.eleven_labs_api_key
        self.elevenlabs_base_url = "https://api.elevenlabs.io/v1"

        # Language routing configuration
        self.deepgram_languages = {
            VoiceLanguage.ENGLISH: "en",
            VoiceLanguage.SPANISH: "es",
            VoiceLanguage.GERMAN: "de",
            VoiceLanguage.FRENCH: "fr"
        }

        self.elevenlabs_languages = {
            VoiceLanguage.KOREAN: "ko",
            VoiceLanguage.MANDARIN: "zh"
        }

        # ElevenLabs voice IDs for different languages
        self.elevenlabs_voices = {
            VoiceLanguage.KOREAN: "mWWuFlaksGqN2ufDOCo92",  # Configure in dashboard
            VoiceLanguage.MANDARIN: "mWWuFlaksGqN2ufDOCo92"  # Configure in dashboard
        }

        print("[HybridVoice] Initialized with routing:")
        print(f"  Deepgram: {list(self.deepgram_languages.keys())}")
        print(f"  ElevenLabs: {list(self.elevenlabs_languages.keys())}")

    def get_provider_for_language(self, language: VoiceLanguage) -> str:
        """Determine which provider to use for a given language."""
        if language in self.deepgram_languages:
            return "deepgram"
        elif language in self.elevenlabs_languages:
            return "elevenlabs"
        else:
            # Default to Deepgram for unknown languages
            print(f"[HybridVoice] WARNING: Unknown language {language}, defaulting to Deepgram")
            return "deepgram"
    
    @classmethod
    def should_use_elevenlabs_agent(cls, language: str) -> bool:
        """
        Check if a language should use ElevenLabs Agent Platform.
        
        ElevenLabs Agent provides full conversational AI for Chinese and Korean,
        including native STT, LLM, and TTS in a single integrated pipeline.
        
        Args:
            language: Language string (e.g., "korean", "mandarin", "zh", "ko")
            
        Returns:
            True if ElevenLabs Agent should be used, False for Deepgram Agent
        """
        return language.lower() in cls.ELEVENLABS_AGENT_LANGUAGES
    
    @classmethod
    def get_voice_provider_info(cls) -> dict:
        """Get information about voice provider routing."""
        return {
            "elevenlabs_agent_languages": list(cls.ELEVENLABS_AGENT_LANGUAGES),
            "deepgram_agent_languages": [],  # All languages now use ElevenLabs Agent
            "routing_rules": {
                "korean": "elevenlabs_agent",
                "mandarin": "elevenlabs_agent", 
                "chinese": "elevenlabs_agent",
                "english": "elevenlabs_agent",
                "spanish": "elevenlabs_agent",
                "german": "elevenlabs_agent",
                "french": "elevenlabs_agent"
            },
            "endpoints": {
                "elevenlabs_agent": "/elevenlabs-agent/start",
                "deepgram_agent": "/agent/start"  # Kept for backward compatibility
            }
        }

    async def transcribe_audio(self, request: VoiceTranscribeRequest) -> VoiceTranscribeResponse:
        """
        Transcribe audio using the appropriate provider.

        All transcription uses Deepgram (nova-3 with multilingual support).
        Even for Korean/Chinese, we use Deepgram for STT.
        """
        try:
            # Always use Deepgram for transcription (it supports all languages)
            model = request.model if request.model else settings.deepgram_model

            # Use a multilingual model for best results
            language_code = settings.deepgram_language  # "multi"

            print(f"[HybridVoice] Transcribing with Deepgram: model={model}, language={language_code}")

            # Prepare transcription options with optimized settings from config
            options = {
                "model": model,
                "language": language_code,
                "punctuate": True,
                "paragraphs": True,
                "diarize": False,
                "profanity_filter": True,
                "smart_format": settings.deepgram_smart_format,  # From config
                "utterances": True,  # Enable for better analysis
                "interim_results": settings.deepgram_interim_results,  # Real-time feedback
            }

            # Transcribe the audio
            response = self.deepgram_client.listen.v1.media.transcribe_file(
                request=request.audio_data,
                **options
            )

            # Extract transcription data
            alternatives = response.results.channels[0].alternatives[0]
            transcript = alternatives.transcript
            confidence = alternatives.confidence

            # Extract words with timing information (crucial for analysis)
            words = []
            if hasattr(alternatives, 'words') and alternatives.words:
                for word in alternatives.words:
                    word_dict = {
                        'word': word.word if hasattr(word, 'word') else '',
                        'start': word.start if hasattr(word, 'start') else 0.0,
                        'end': word.end if hasattr(word, 'end') else 0.0,
                        'confidence': word.confidence if hasattr(word, 'confidence') else 0.0,
                        'punctuated_word': word.punctuated_word if hasattr(word, 'punctuated_word') else ''
                    }
                    words.append(word_dict)

            # Get detected languages
            detected_languages = None
            if hasattr(alternatives, 'languages'):
                detected_languages = alternatives.languages

            duration = response.metadata.duration if hasattr(response, 'metadata') and hasattr(response.metadata, 'duration') else None

            print(f"[HybridVoice] Transcription successful: {len(transcript)} chars, {len(words)} words, confidence={confidence:.2f}")

            return VoiceTranscribeResponse(
                success=True,
                transcript=transcript,
                confidence=confidence,
                words=words,
                language_detected=str(detected_languages) if detected_languages else language_code,
                duration=duration
            )

        except Exception as e:
            error_msg = str(e)
            print(f"[HybridVoice] Transcription error: {error_msg}")

            return VoiceTranscribeResponse(
                success=False,
                transcript="",
                confidence=0.0,
                words=[],
                language_detected=None,
                duration=None
            )

    async def synthesize_speech(
        self,
        text: str,
        language: VoiceLanguage,
        voice_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Synthesize speech using the appropriate provider.

        - Deepgram: English, Spanish, German, French
        - ElevenLabs: Korean, Mandarin
        """
        provider = self.get_provider_for_language(language)

        if provider == "deepgram":
            return await self._synthesize_deepgram(text, language)
        else:
            return await self._synthesize_elevenlabs(text, language, voice_id)

    async def _synthesize_deepgram(
        self,
        text: str,
        language: VoiceLanguage
    ) -> Dict[str, Any]:
        """Synthesize speech using Deepgram TTS."""
        try:
            print(f"[HybridVoice] Synthesizing with Deepgram for {language.value}")

            # Map language to Deepgram voice model
            voice_models = {
                VoiceLanguage.ENGLISH: "aura-asteria-en",
                VoiceLanguage.SPANISH: "aura-luna-es",
                VoiceLanguage.GERMAN: "aura-helios-de",
                VoiceLanguage.FRENCH: "aura-helios-fr"
            }

            voice_model = voice_models.get(language, "aura-asteria-en")

            # Use Deepgram Speak API
            options = {
                "model": voice_model,
                "encoding": "linear16",
                "sample_rate": 24000
            }

            response = self.deepgram_client.speak.v1.synthesize(
                text=text,
                **options
            )

            audio_data = response.stream.getvalue()

            print(f"[HybridVoice] Deepgram TTS successful: {len(audio_data)} bytes")

            return {
                "success": True,
                "audio_data": audio_data,
                "provider": "deepgram",
                "format": "linear16"
            }

        except Exception as e:
            print(f"[HybridVoice] Deepgram TTS error: {str(e)}")
            return {
                "success": False,
                "error": str(e),
                "provider": "deepgram"
            }

    async def _synthesize_elevenlabs(
        self,
        text: str,
        language: VoiceLanguage,
        voice_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """Synthesize speech using ElevenLabs TTS."""
        try:
            print(f"[HybridVoice] Synthesizing with ElevenLabs for {language.value}")

            # Select voice ID
            selected_voice_id = voice_id or self.elevenlabs_voices.get(
                language,
                self.elevenlabs_voices[VoiceLanguage.MANDARIN]
            )

            # Prepare request
            url = f"{self.elevenlabs_base_url}/text-to-speech/{selected_voice_id}"

            headers = {
                "Accept": "audio/mpeg",
                "Content-Type": "application/json",
                "xi-api-key": self.elevenlabs_api_key
            }

            payload = {
                "text": text,
                "model_id": "eleven_turbo_v2_5",  # Multilingual model
                "voice_settings": {
                    "stability": 0.75,
                    "similarity_boost": 0.75,
                    "style": 0.0,
                    "use_speaker_boost": True
                }
            }

            # Make async request
            async with aiohttp.ClientSession() as session:
                async with session.post(url, json=payload, headers=headers) as response:
                    if response.status == 200:
                        audio_data = await response.read()
                        print(f"[HybridVoice] ElevenLabs TTS successful: {len(audio_data)} bytes")

                        return {
                            "success": True,
                            "audio_data": audio_data,
                            "provider": "elevenlabs",
                            "format": "mp3"
                        }
                    else:
                        error_text = await response.text()
                        print(f"[HybridVoice] ElevenLabs API error: {response.status} - {error_text}")
                        return {
                            "success": False,
                            "error": f"ElevenLabs API error: {response.status}",
                            "provider": "elevenlabs"
                        }

        except Exception as e:
            print(f"[HybridVoice] ElevenLabs TTS error: {str(e)}")
            return {
                "success": False,
                "error": str(e),
                "provider": "elevenlabs"
            }

    def get_routing_info(self) -> Dict[str, Any]:
        """Get information about language routing."""
        return {
            "deepgram_languages": [lang.value for lang in self.deepgram_languages.keys()],
            "elevenlabs_languages": [lang.value for lang in self.elevenlabs_languages.keys()],
            "routing_rules": {
                "transcription": "All languages use Deepgram (nova-3 multilingual)",
                "synthesis": {
                    "deepgram": ["english", "spanish", "german", "french"],
                    "elevenlabs": ["korean", "mandarin"]
                }
            }
        }
