"""
API routes for local Whisper STT and SpeechBrain speaker analysis.
No API keys required - runs entirely locally.
"""
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
from typing import Optional
import logging
import json

from whisper_analysis_service import (
    whisper_analysis_service,
    VoiceAnalysisResult,
    TranscriptionResult,
    SpeakerAnalysisResult
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/local-voice", tags=["local-voice-analysis"])


@router.post("/transcribe")
async def transcribe_audio_local(
    audio_file: UploadFile = File(...),
    language: Optional[str] = Form(None)
):
    """
    Transcribe audio using local Whisper model.
    
    Args:
        audio_file: Audio file (WAV format recommended)
        language: Optional language code (en, fr, de, ko, zh, es)
    
    Returns:
        Transcription with text, confidence, and word-level timestamps
    """
    try:
        if not audio_file.content_type or not audio_file.content_type.startswith("audio/"):
            logger.warning(f"Received file with content_type: {audio_file.content_type}")
        
        audio_data = await audio_file.read()
        
        if len(audio_data) < 1000:
            raise HTTPException(
                status_code=400,
                detail="Audio file is too small. Please record for at least 1 second."
            )
        
        logger.info(f"Transcribing audio: {len(audio_data)} bytes, language={language}")
        
        result = await whisper_analysis_service.transcribe_audio(audio_data, language)
        
        return {
            "success": True,
            "transcript": result.text,
            "confidence": result.confidence,
            "language": result.language,
            "duration": result.duration,
            "words": result.words,
            "segments": result.segments
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Transcription failed: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Transcription failed: {str(e)}"
        )


@router.post("/analyze-speaker")
async def analyze_speaker(
    audio_file: UploadFile = File(...)
):
    """
    Analyze speaker characteristics using SpeechBrain.
    
    Args:
        audio_file: Audio file (WAV format recommended)
    
    Returns:
        Speaker analysis with voice quality, pronunciation, and fluency metrics
    """
    try:
        audio_data = await audio_file.read()
        
        if len(audio_data) < 1000:
            raise HTTPException(
                status_code=400,
                detail="Audio file is too small. Please record for at least 1 second."
            )
        
        logger.info(f"Analyzing speaker: {len(audio_data)} bytes")
        
        result = await whisper_analysis_service.analyze_speaker(audio_data)
        
        return {
            "success": True,
            "voice_quality": result.voice_quality_scores,
            "pronunciation": result.pronunciation_metrics,
            "fluency": result.fluency_metrics,
            "clarity_score": result.clarity_score,
            "energy_profile": result.energy_profile,
            "has_embedding": result.speaker_embedding is not None
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Speaker analysis failed: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Speaker analysis failed: {str(e)}"
        )


@router.post("/analyze")
async def analyze_voice_complete(
    audio_file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    expected_text: Optional[str] = Form(None),
    level: str = Form(default="intermediate"),
    scenario: str = Form(default="daily_conversation"),
    user_id: str = Form(...)
):
    """
    Complete voice analysis combining Whisper STT and SpeechBrain speaker analysis.
    
    Args:
        audio_file: Audio file (WAV format recommended)
        language: Target language code (en, fr, de, ko, zh, es)
        expected_text: Expected phrase for comparison
        level: Proficiency level (beginner, intermediate, advanced)
        scenario: Practice scenario
        user_id: User identifier
    
    Returns:
        Complete analysis with transcription, scores, and feedback
    """
    try:
        audio_data = await audio_file.read()
        
        if len(audio_data) < 1000:
            raise HTTPException(
                status_code=400,
                detail="Audio file is too small. Please record for at least 1 second."
            )
        
        logger.info(f"Complete voice analysis: {len(audio_data)} bytes, language={language}, user={user_id}")
        
        result = await whisper_analysis_service.analyze_voice(
            audio_data=audio_data,
            language=language,
            expected_text=expected_text,
            level=level,
            scenario=scenario
        )
        
        if not result.success:
            return {
                "success": False,
                "error": result.error_message,
                "transcript": "",
                "confidence": 0.0,
                "scores": {
                    "pronunciation": 0.0,
                    "fluency": 0.0,
                    "accuracy": 0.0
                },
                "overall_score": 0.0,
                "feedback_messages": result.feedback_messages,
                "suggestions": result.suggestions
            }
        
        transcription = result.transcription
        speaker = result.speaker_analysis
        
        scores = {
            "pronunciation": speaker.pronunciation_metrics.get("overall", 0) if speaker else 0,
            "fluency": (
                speaker.fluency_metrics.get("rate_score", 0) * 0.4 +
                speaker.fluency_metrics.get("pause_score", 0) * 0.3 +
                speaker.fluency_metrics.get("rhythm", 0) * 0.3
            ) if speaker else 0,
            "accuracy": transcription.confidence * 100 if transcription else 0,
            "clarity": speaker.clarity_score if speaker else 0,
            "volume": speaker.voice_quality_scores.get("volume", 0) if speaker else 0,
            "stability": speaker.voice_quality_scores.get("stability", 0) if speaker else 0
        }
        
        return {
            "success": True,
            "transcript": transcription.text if transcription else "",
            "confidence": transcription.confidence if transcription else 0.0,
            "language_detected": transcription.language if transcription else language,
            "duration": transcription.duration if transcription else 0.0,
            "words": transcription.words if transcription else [],
            "scores": scores,
            "overall_score": result.overall_score,
            "feedback_messages": result.feedback_messages,
            "suggestions": result.suggestions,
            "voice_quality": speaker.voice_quality_scores if speaker else {},
            "pronunciation_metrics": speaker.pronunciation_metrics if speaker else {},
            "fluency_metrics": speaker.fluency_metrics if speaker else {},
            "energy_profile": speaker.energy_profile if speaker else []
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Voice analysis failed: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Voice analysis failed: {str(e)}"
        )


@router.post("/feedback")
async def generate_local_feedback(
    transcript: str = Form(...),
    expected_text: Optional[str] = Form(None),
    language: str = Form(default="en"),
    level: str = Form(default="intermediate"),
    scenario: str = Form(default="daily_conversation"),
    user_id: str = Form(...),
    pronunciation_score: float = Form(default=0.0),
    fluency_score: float = Form(default=0.0),
    clarity_score: float = Form(default=0.0)
):
    """
    Generate feedback based on analysis scores (for use with pre-analyzed data).
    
    Args:
        transcript: Transcribed text
        expected_text: Expected phrase
        language: Language code
        level: Proficiency level
        scenario: Practice scenario
        user_id: User identifier
        pronunciation_score: Pre-calculated pronunciation score
        fluency_score: Pre-calculated fluency score
        clarity_score: Pre-calculated clarity score
    
    Returns:
        Feedback with scores and suggestions
    """
    try:
        logger.info(f"Generating feedback for user: {user_id}")
        
        feedback_messages = []
        suggestions = []
        
        if pronunciation_score >= 80:
            feedback_messages.append("Excellent pronunciation! Your articulation is very clear.")
        elif pronunciation_score >= 60:
            feedback_messages.append("Good pronunciation with minor areas for improvement.")
            suggestions.append("Practice challenging sounds by listening and repeating.")
        else:
            feedback_messages.append("Focus on pronunciation practice.")
            suggestions.append("Listen to native speakers and mimic their pronunciation patterns.")
        
        if fluency_score >= 80:
            feedback_messages.append("Great fluency! You spoke smoothly and naturally.")
        elif fluency_score >= 60:
            feedback_messages.append("Good flow with some hesitations.")
            suggestions.append("Practice speaking in longer phrases without pauses.")
        else:
            feedback_messages.append("Work on reducing pauses and hesitations.")
            suggestions.append("Read aloud daily to improve speaking fluency.")
        
        if clarity_score >= 80:
            feedback_messages.append("Excellent audio clarity!")
        elif clarity_score >= 60:
            feedback_messages.append("Acceptable clarity.")
        else:
            suggestions.append("Try recording in a quieter environment.")
        
        if expected_text and transcript:
            actual_words = set(transcript.lower().split())
            expected_words = set(expected_text.lower().split())
            
            if expected_words:
                matching = len(actual_words & expected_words)
                accuracy = (matching / len(expected_words)) * 100
                
                if accuracy >= 90:
                    feedback_messages.append("You matched the expected phrase very well!")
                elif accuracy >= 70:
                    feedback_messages.append("Most of the content was correct.")
                else:
                    feedback_messages.append("Practice the target phrase more.")
                    suggestions.append(f"Expected: '{expected_text}'")
        
        overall_score = (pronunciation_score + fluency_score + clarity_score) / 3
        
        return {
            "success": True,
            "scores": {
                "pronunciation": pronunciation_score,
                "fluency": fluency_score,
                "clarity": clarity_score
            },
            "overall_score": overall_score,
            "feedback_messages": feedback_messages,
            "suggestions": suggestions,
            "corrected_text": None
        }
        
    except Exception as e:
        logger.error(f"Feedback generation failed: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Feedback generation failed: {str(e)}"
        )


@router.get("/health")
async def health_check():
    """
    Check if the local voice analysis service is available.
    
    Returns:
        Service status and available models
    """
    try:
        import torch
        cuda_available = torch.cuda.is_available()
        device = "cuda" if cuda_available else "cpu"
        
        whisper_loaded = whisper_analysis_service._whisper_model is not None
        speaker_loaded = whisper_analysis_service._speaker_model is not None
        
        return {
            "success": True,
            "status": "healthy",
            "device": device,
            "cuda_available": cuda_available,
            "models": {
                "whisper": {
                    "loaded": whisper_loaded,
                    "model_name": whisper_analysis_service.whisper_model_name
                },
                "speechbrain": {
                    "loaded": speaker_loaded,
                    "model": "spkrec-ecapa-voxceleb"
                }
            }
        }
        
    except Exception as e:
        logger.error(f"Health check failed: {str(e)}")
        return {
            "success": False,
            "status": "unhealthy",
            "error": str(e)
        }


@router.post("/preload-models")
async def preload_models():
    """
    Preload all models to reduce first-request latency.
    
    Returns:
        Status of model loading
    """
    try:
        logger.info("Preloading models...")
        
        whisper_analysis_service._load_whisper()
        logger.info("Whisper model loaded")
        
        whisper_analysis_service._load_speaker_model()
        logger.info("SpeechBrain speaker model loaded")
        
        return {
            "success": True,
            "message": "All models preloaded successfully",
            "models_loaded": ["whisper", "speechbrain-speaker"]
        }
        
    except Exception as e:
        logger.error(f"Model preloading failed: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Model preloading failed: {str(e)}"
        )
