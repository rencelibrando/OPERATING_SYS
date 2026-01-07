"""
Edge TTS Service with Language-to-Voice Mapping
Handles text-to-speech conversion using Microsoft Edge TTS with automatic
language detection via FastText.
"""
import edge_tts
import asyncio
import hashlib
import logging
from typing import Optional, Dict
from pathlib import Path
import os

from language_detection_service import get_language_detection_service
from supabase_client import get_supabase

logger = logging.getLogger(__name__)


class TTSService:
    """
    Service for text-to-speech conversion using Edge TTS.
    Supports automatic language detection and voice selection.
    """
    
    # Language to Edge TTS voice mapping 
    VOICE_MAPPING = {
        'ko': 'ko-KR-SunHiNeural',      # Korean - Female
        'de': 'de-DE-KatjaNeural',      # German - Female
        'zh': 'zh-CN-XiaoNeural',   # Chinese - Female
        'es': 'es-ES-ElviraNeural',     # Spanish - Female
        'fr': 'fr-FR-DeniseNeural',     # French - Female
        'en': 'en-US-JennyNeural',      # English - Female (Fallback)
    }
    
    # Supabase storage bucket name
    STORAGE_BUCKET = 'lesson-audio'
    
    # Local cache directory (for development/backup)
    CACHE_DIR = Path(__file__).parent / "cache" / "audio"
    
    def __init__(self):
        self.language_service = get_language_detection_service()
        self.supabase = get_supabase()
        
        # Ensure the cache directory exists
        self.CACHE_DIR.mkdir(parents=True, exist_ok=True)
    
    def _generate_audio_hash(self, text: str, voice: str) -> str:
        """
        Generate a unique hash for audio caching.
        
        Args:
            text: Text to convert to speech
            voice: Edge TTS voice name
            
        Returns:
            SHA256 hash string
        """
        content = f"{text}:{voice}"
        return hashlib.sha256(content.encode('utf-8')).hexdigest()
    
    def _get_cache_path(self, audio_hash: str) -> Path:
        """Get a local cache file path for an audio hash."""
        return self.CACHE_DIR / f"{audio_hash}.mp3"
    
    def _get_storage_path(self, audio_hash: str) -> str:
        """Get Supabase storage path for an audio hash."""
        return f"narration/{audio_hash}.mp3"
    
    async def _check_supabase_cache(self, storage_path: str) -> Optional[str]:
        """
        Check if an audio file exists in Supabase Storage.
        
        Args:
            storage_path: Path in Supabase storage
            
        Returns:
            Public URL if a file exists, None otherwise
        """
        if not self.supabase:
            return None
        
        try:
            # Try to get the public URL (this doesn't check if a file exists)
            public_url = self.supabase.storage.from_(self.STORAGE_BUCKET).get_public_url(storage_path)
            
            # Verify a file exists by listing
            # Note: This is a workaround since Supabase Python SDK doesn't have a direct "exists" method
            folder = os.path.dirname(storage_path)
            filename = os.path.basename(storage_path)
            
            files = self.supabase.storage.from_(self.STORAGE_BUCKET).list(folder)
            
            if any(f['name'] == filename for f in files):
                logger.info(f"Audio file found in Supabase cache: {storage_path}")
                return public_url
            
            return None
            
        except Exception as e:
            logger.warning(f"Error checking Supabase cache: {e}")
            return None
    
    async def _delete_from_supabase(self, storage_path: str) -> bool:
        """
        Delete an audio file from Supabase Storage.
        
        Args:
            storage_path: Path in Supabase storage to delete
            
        Returns:
            True if successful or a file doesn't exist, False on error
        """
        if not self.supabase:
            logger.warning("Supabase not configured, skipping deletion")
            return True
        
        try:
            # Attempt to delete the file
            self.supabase.storage.from_(self.STORAGE_BUCKET).remove([storage_path])
            logger.info(f"Deleted audio from Supabase: {storage_path}")
            return True
            
        except Exception as e:
            # If the file doesn't exist, that's fine
            logger.info(f"Could not delete audio (may not exist): {storage_path} - {e}")
            return True
    
    async def _upload_to_supabase(self, file_path: Path, storage_path: str) -> Optional[str]:
        """
        Upload audio file to Supabase Storage.
        
        Args:
            file_path: Local file path
            storage_path: Destination path in Supabase storage
            
        Returns:
            Public URL if successful, None otherwise
        """
        if not self.supabase:
            logger.warning("Supabase not configured, skipping upload")
            return None
        
        try:
            # Read file content
            with open(file_path, 'rb') as f:
                file_content = f.read()
            
            # Upload to Supabase Storage (upsert to replace if exists)
            self.supabase.storage.from_(self.STORAGE_BUCKET).upload(
                storage_path,
                file_content,
                {"content-type": "audio/mpeg", "upsert": "true"}
            )
            
            # Get public URL
            public_url = self.supabase.storage.from_(self.STORAGE_BUCKET).get_public_url(storage_path)
            
            logger.info(f"Uploaded audio to Supabase: {storage_path}")
            return public_url
            
        except Exception as e:
            logger.error(f"Failed to upload to Supabase: {e}")
            return None
    
    def select_voice(
        self, 
        language_code: Optional[str] = None,
        voice_override: Optional[str] = None
    ) -> str:
        """
        Select appropriate Edge TTS voice.
        
        Args:
            language_code: Detected or specified language code
            voice_override: Manual voice override
            
        Returns:
            Edge TTS voice name
        """
        # Use override if provided
        if voice_override:
            logger.info(f"Using voice override: {voice_override}")
            return voice_override
        
        # Use language mapping
        if language_code and language_code in self.VOICE_MAPPING:
            voice = self.VOICE_MAPPING[language_code]
            logger.info(f"Selected voice for {language_code}: {voice}")
            return voice
        
        # Fallback to English
        fallback_voice = self.VOICE_MAPPING['en']
        logger.warning(f"Using fallback voice: {fallback_voice}")
        return fallback_voice
    
    async def generate_audio(
        self,
        text: str,
        language_override: Optional[str] = None,
        voice_override: Optional[str] = None,
        use_cache: bool = True
    ) -> Optional[str]:
        """
        Generate audio from text using Edge TTS.
        
        Args:
            text: Text to convert to speech
            language_override: Override auto-detection with a specific language
            voice_override: Override voice selection
            use_cache: Whether to use cached audio if available
            
        Returns:
            Public URL to the audio file in Supabase Storage, or None if failed
        """
        if not text or not text.strip():
            logger.warning("Empty text provided, skipping audio generation")
            return None
        
        try:
            logger.info(f"[TTS] Starting audio generation for text: '{text[:50]}...' (length: {len(text)})")
            
            # Step 1: Detect or use a specified language
            if language_override:
                language_code = language_override
                confidence = 1.0
                logger.info(f"[TTS] Using language override: {language_code}")
            else:
                logger.info(f"[TTS] Detecting language for text: '{text}'")
                language_code, confidence = self.language_service.detect_language(text)
                logger.info(f"[TTS] Auto-detected language: {language_code} (confidence: {confidence:.2f})")
            
            # Step 2: Select voice
            logger.info(f"[TTS] Selecting voice for language: {language_code}")
            voice = self.select_voice(language_code, voice_override)
            logger.info(f"[TTS] Selected voice: {voice}")
            
            # Step 3: Generate the cache key
            logger.info(f"[TTS] Generating cache key for text and voice")
            audio_hash = self._generate_audio_hash(text, voice)
            storage_path = self._get_storage_path(audio_hash)
            cache_path = self._get_cache_path(audio_hash)
            logger.info(f"[TTS] Cache paths - Storage: {storage_path}, Local: {cache_path}")
            
            # Step 4: Check Supabase cache if enabled
            if use_cache:
                cached_url = await self._check_supabase_cache(storage_path)
                if cached_url:
                    logger.info(f"Using cached audio: {cached_url}")
                    return cached_url
            else:
                # If regenerating, delete existing audio first
                await self._delete_from_supabase(storage_path)
                # Also delete the local cache
                if cache_path.exists():
                    cache_path.unlink()
                    logger.info(f"Deleted local cache: {cache_path}")
            
            # Step 5: Generate audio using Edge TTS
            logger.info(f"[TTS] ===== STARTING EDGE TTS GENERATION =====")
            logger.info(f"[TTS] Text: '{text}'")
            logger.info(f"[TTS] Voice: {voice}")
            logger.info(f"[TTS] Target file: {cache_path}")
            
            try:
                communicate = edge_tts.Communicate(text, voice)
                logger.info(f"[TTS] Edge TTS Communicate object created successfully")
                
                logger.info(f"[TTS] Starting audio save to: {cache_path}")
                await communicate.save(str(cache_path))
                logger.info(f"[TTS] Audio saved successfully to: {cache_path}")
                
                # Verify a file was created
                if cache_path.exists():
                    file_size = cache_path.stat().st_size
                    logger.info(f"[TTS] Audio file verified - Size: {file_size} bytes")
                else:
                    logger.error(f"[TTS] Audio file was not created at {cache_path}")
                    raise FileNotFoundError(f"Audio file not created: {cache_path}")
                    
            except Exception as edge_error:
                logger.error(f"[TTS] ===== EDGE TTS GENERATION FAILED =====")
                logger.error(f"[TTS] Error type: {type(edge_error).__name__}")
                logger.error(f"[TTS] Error message: {str(edge_error)}")
                logger.error(f"[TTS] Text attempted: '{text}'")
                logger.error(f"[TTS] Voice attempted: {voice}")
                import traceback
                logger.error(f"[TTS] Full traceback:\n{traceback.format_exc()}")
                raise
            
            logger.info(f"[TTS] ===== EDGE TTS GENERATION COMPLETED =====")
            
            # Step 6: Upload to Supabase Storage
            logger.info(f"[TTS] Starting Supabase upload")
            public_url = await self._upload_to_supabase(cache_path, storage_path)
            
            if public_url:
                logger.info(f"[TTS] Audio generation completed successfully - URL: {public_url}")
                return public_url
            else:
                # If upload fails, return the local path as fallback (for development)
                logger.warning(f"[TTS] Supabase upload failed, audio only available locally at {cache_path}")
                return str(cache_path)
            
        except Exception as e:
            logger.error(f"[TTS] ===== AUDIO GENERATION FAILED =====")
            logger.error(f"[TTS] Error type: {type(e).__name__}")
            logger.error(f"[TTS] Error message: {str(e)}")
            logger.error(f"[TTS] Input text: '{text}'")
            import traceback
            logger.error(f"[TTS] Full traceback:\n{traceback.format_exc()}")
            return None
    
    async def generate_batch(
        self,
        texts: Dict[str, str],
        language_override: Optional[str] = None,
        voice_override: Optional[str] = None
    ) -> Dict[str, Optional[str]]:
        """
        Generate audio for multiple texts in parallel.
        
        Args:
            texts: Dictionary of {id: text} pairs
            language_override: Override language detection
            voice_override: Override voice selection
            
        Returns:
            Dictionary of {id: audio_url} pairs
        """
        tasks = {
            text_id: self.generate_audio(
                text, 
                language_override, 
                voice_override
            )
            for text_id, text in texts.items()
        }
        
        results = {}
        for text_id, task in tasks.items():
            results[text_id] = await task
        
        return results


# Singleton instance
_tts_service = None

def get_tts_service() -> TTSService:
    """Get the singleton TTS service instance."""
    global _tts_service
    if _tts_service is None:
        _tts_service = TTSService()
    return _tts_service
