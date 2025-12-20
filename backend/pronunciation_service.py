"""
Pronunciation Practice Service
Handles reference audio generation and pronunciation comparison
"""
import tempfile
import logging
from pathlib import Path
from typing import Dict, Optional
from datetime import datetime

from gtts import gTTS
from compare_audio import summarize_audio, compare_pronunciation, generate_feedback
from supabase_client import SupabaseManager

logger = logging.getLogger(__name__)


# Language code mapping: LessonLanguage -> gTTS language code
LANGUAGE_CODES = {
    "ko": "ko",  # Korean
    "zh": "zh",  # Chinese
    "fr": "fr",  # French
    "de": "de",  # German
    "es": "es",  # Spanish
    "en": "en",  # English
}


def generate_reference_audio(word: str, language_code: str, word_id: Optional[str] = None) -> str:
    """
    Generate reference audio using gTTS and save directly to Supabase storage.
    Also saves the URL to vocabulary_words table if word_id is provided.
    
    Args:
        word: The word to generate audio for
        language_code: Language code (ko, zh, fr, de, es, en)
        word_id: Optional ID of the word in vocabulary_words table to save audio_url
    
    Returns:
        Supabase public URL of the uploaded audio file
    """
    try:
        # Map language code to gTTS format
        gtts_lang = LANGUAGE_CODES.get(language_code.lower(), "en")
        
        logger.info(f"Generating reference audio for '{word}' in {gtts_lang}")
        
        # Get Supabase client
        supabase = SupabaseManager.get_client()
        if not supabase:
            raise Exception("Supabase not configured. Please set SUPABASE_URL and SUPABASE_KEY in .env file")
        
        # Generate audio using gTTS
        # Note: gTTS saves as MP3 by default, but we'll convert to WAV for better compatibility
        tts = gTTS(text=word, lang=gtts_lang, slow=False)
        
        # Save to temporary MP3 file first (gTTS default format)
        with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as tmp:
            mp3_path = Path(tmp.name)
        
        tts.save(str(mp3_path))
        logger.info(f"Reference audio generated (MP3): {mp3_path}")
        
        # Convert MP3 to WAV for better Java AudioSystem compatibility
        # This ensures the audio can be played directly in the app without external players
        temp_path = mp3_path
        try:
            from pydub import AudioSegment
            from pydub.utils import which
            
            # Check if ffmpeg is available (required for MP3 conversion)
            if not which("ffmpeg"):
                raise RuntimeError(
                    "ffmpeg is required to convert MP3 to WAV. "
                    "Please install ffmpeg:\n"
                    "  Windows: Download from https://ffmpeg.org/download.html\n"
                    "  macOS: brew install ffmpeg\n"
                    "  Linux: sudo apt-get install ffmpeg"
                )
            
            logger.info("Converting MP3 to WAV using pydub...")
            audio = AudioSegment.from_mp3(str(mp3_path))
            temp_path = Path(str(mp3_path).replace(".mp3", ".wav"))
            
            # Export as WAV PCM format (compatible with Java AudioSystem)
            # Use explicit parameters to ensure PCM format
            try:
                audio.export(
                    str(temp_path), 
                    format="wav",
                    parameters=[
                        "-acodec", "pcm_s16le",  # PCM 16-bit little-endian (standard WAV)
                        "-ac", "1",              # Mono
                        "-ar", "44100"           # Sample rate 44.1kHz
                    ]
                )
            except Exception as e:
                # If pydub export fails, try using ffmpeg directly
                logger.warning(f"pydub export failed: {e}, trying ffmpeg directly...")
                import subprocess
                result = subprocess.run(
                    [
                        "ffmpeg",
                        "-y",  # Overwrite output
                        "-i", str(mp3_path),
                        "-acodec", "pcm_s16le",
                        "-ac", "1",
                        "-ar", "44100",
                        str(temp_path)
                    ],
                    capture_output=True,
                    text=True
                )
                if result.returncode != 0:
                    raise RuntimeError(f"ffmpeg conversion failed: {result.stderr}")
            
            logger.info(f"Converted to WAV: {temp_path} (size: {temp_path.stat().st_size} bytes)")
            
            # Verify the file was created and has content
            if not temp_path.exists() or temp_path.stat().st_size == 0:
                raise RuntimeError("WAV conversion produced empty or missing file")
            
            # Verify it's a valid WAV file by checking header
            with open(temp_path, "rb") as f:
                header = f.read(12)
                if not header.startswith(b"RIFF"):
                    raise RuntimeError("Converted file does not have valid WAV header")
                logger.info("WAV file header verified: RIFF format")
            
            # Clean up MP3 file
            mp3_path.unlink()
        except ImportError:
            raise RuntimeError(
                "pydub is required to convert MP3 to WAV. "
                "Please install it: pip install pydub"
            )
        except Exception as e:
            logger.error(f"Failed to convert MP3 to WAV: {e}")
            # Clean up MP3 file if conversion failed
            if mp3_path.exists():
                mp3_path.unlink()
            raise RuntimeError(
                f"Failed to convert audio to WAV format: {e}. "
                "Please ensure ffmpeg is installed and pydub is available."
            ) from e
        
        try:
            # Create storage path: pronunciation-references/{language_code}/{word}_{timestamp}.wav
            # Use word_id in filename if available for better organization
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            if word_id:
                storage_path = f"{language_code.lower()}/{word_id}_{timestamp}.wav"
            else:
                # Sanitize word for filename (remove special characters)
                safe_word = "".join(c if c.isalnum() or c in (' ', '-', '_') else '_' for c in word)
                safe_word = safe_word.replace(' ', '_')
                storage_path = f"{language_code.lower()}/{safe_word}_{timestamp}.wav"
            
            # Read the file data
            with open(temp_path, "rb") as f:
                file_data = f.read()
            
            # Upload to Supabase storage bucket: pronunciation-references
            logger.info(f"Uploading to Supabase storage: pronunciation-references/{storage_path}")
            response = supabase.storage.from_("pronunciation-references").upload(
                path=storage_path,
                file=file_data,
                file_options={"content-type": "audio/wav", "upsert": "true"}
            )
            
            # Get public URL (since bucket is public)
            public_url = supabase.storage.from_("pronunciation-references").get_public_url(storage_path)
            
            logger.info(f"Reference audio uploaded to Supabase: {public_url}")
            
            # Save URL to vocabulary_words table if word_id is provided
            if word_id and public_url:
                try:
                    result = supabase.table("vocabulary_words").update({
                        "audio_url": public_url
                    }).eq("id", word_id).execute()
                    logger.info(f"Saved audio URL to vocabulary_words.audio_url for word_id: {word_id}")
                except Exception as e:
                    logger.warning(f"Failed to save audio URL to database: {e}")
                    # Don't fail the whole operation if DB update fails
            
            # Clean up temporary file
            try:
                temp_path.unlink()
                logger.info(f"Cleaned up temporary file: {temp_path}")
            except Exception as e:
                logger.warning(f"Failed to delete temp file: {e}")
            
            return public_url
            
        except Exception as e:
            logger.error(f"Failed to upload to Supabase: {e}")
            # Clean up temp file even on error
            try:
                temp_path.unlink()
            except:
                pass
            raise
            
    except Exception as e:
        logger.error(f"Error generating reference audio: {e}", exc_info=True)
        raise


def compare_user_pronunciation(
    reference_audio_path: Path,
    user_audio_path: Path,
    word: str
) -> Dict:
    """
    Compare user's pronunciation with reference audio.
    
    Args:
        reference_audio_path: Path to reference audio file
        user_audio_path: Path to user's recorded audio file
        word: The word being practiced
    
    Returns:
        Dictionary with pronunciation metrics and feedback
    """
    try:
        logger.info(f"Comparing pronunciation for '{word}'")
        
        # Analyze both audio files
        logger.info("Analyzing reference audio...")
        ref_summary = summarize_audio(reference_audio_path, trim_silence_flag=False)
        
        logger.info("Analyzing user audio...")
        user_summary = summarize_audio(user_audio_path, trim_silence_flag=True)
        
        # Compare pronunciations
        logger.info("Computing pronunciation metrics...")
        metrics = compare_pronunciation(ref_summary, user_summary)
        
        # Generate feedback
        feedback_messages = generate_feedback(metrics, ref_summary, user_summary)
        
        # Calculate overall score (0-100)
        overall_score = int(metrics["pronunciation_score"] * 100)
        
        result = {
            "success": True,
            "overall_score": overall_score,
            "pronunciation_score": int(metrics["mfcc_similarity"] * 100),
            "clarity_score": int(metrics["pitch_similarity"] * 100),
            "fluency_score": int(metrics["duration_ratio"] * 100),
            "metrics": {
                "mfcc_similarity": metrics["mfcc_similarity"],
                "pitch_similarity": metrics["pitch_similarity"],
                "duration_ratio": metrics["duration_ratio"],
                "energy_ratio": metrics["energy_ratio"],
            },
            "feedback_messages": feedback_messages,
            "suggestions": [
                "Practice the word slowly, then gradually increase speed",
                "Record yourself multiple times and compare",
                "Listen to native speakers and mimic their pronunciation"
            ]
        }
        
        logger.info(f"Pronunciation comparison complete. Score: {overall_score}%")
        
        return result
        
    except Exception as e:
        logger.error(f"Error comparing pronunciation: {e}", exc_info=True)
        raise

