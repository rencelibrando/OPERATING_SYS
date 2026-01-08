from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging
import sys
import os
import sitecustomize  # noqa: F401

# Add a parent directory to a path to allow importing from deps_installer
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import settings
from models import (
    ChatRequest, 
    ChatResponse, 
    HealthResponse, 
    AIProvider,
    SaveHistoryRequest,
    SaveHistoryResponse,
    LoadHistoryRequest,
    LoadHistoryResponse,
)
from prompts import build_system_prompt, format_conversation_history
from providers.gemini import GeminiProvider
from providers.deepseek import DeepSeekProvider
from providers.exceptions import ProviderQuotaExceededError
from retry_utils import ProviderManager
from chat_history_service import ChatHistoryService
from supabase_client import SupabaseManager
from lesson_routes import router as lesson_router
from narration_routes import router as narration_router
from voice_routes import router as voice_router
from error_logger import get_logger
from tts_service import get_tts_service
from elevenlabs_agent_routes import router as elevenlabs_agent_router
from whisper_analysis_routes import router as whisper_analysis_router
from auto_installer import auto_install_dependencies
import shutil
import subprocess


def check_ffmpeg_available() -> bool:
    """Check if FFmpeg is available in the system PATH."""
    # Method 1: Use shutil.which
    ffmpeg_path = shutil.which('ffmpeg')
    if ffmpeg_path:
        return True
    
    # Method 2: Try running ffmpeg directly
    try:
        result = subprocess.run(
            ['ffmpeg', '-version'],
            capture_output=True,
            text=True,
            timeout=10
        )
        return result.returncode == 0
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass
    
    # Method 3: Check common Windows installation paths
    import sys
    if sys.platform == 'win32':
        common_paths = [
            r'C:\ffmpeg\bin\ffmpeg.exe',
            r'C:\Program Files\ffmpeg\bin\ffmpeg.exe',
            r'C:\Program Files (x86)\ffmpeg\bin\ffmpeg.exe',
            os.path.expandvars(r'%LOCALAPPDATA%\FFmpeg\ffmpeg-*\bin\ffmpeg.exe'),
            os.path.expandvars(r'%LOCALAPPDATA%\Microsoft\WinGet\Packages\*ffmpeg*\ffmpeg.exe'),
        ]
        import glob
        for pattern in common_paths:
            matches = glob.glob(pattern)
            if matches:
                # Found ffmpeg, but it's not in PATH
                return True
    
    return False


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize empty providers dict; will populate later when an API key present
providers = {}

# Initialize chat history service
chat_history_service = ChatHistoryService()

# Initialize provider manager (will be set up in lifespan)
provider_manager = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown."""
    # Startup
    logger.info("Starting AI Backend Service")
    
    # Check FFmpeg availability (critical for Whisper)
    logger.info("Checking FFmpeg availability...")
    if check_ffmpeg_available():
        logger.info("✓ FFmpeg is available")
    else:
        logger.error("="*60)
        logger.error("❌ FFmpeg NOT FOUND!")
        logger.error("="*60)
        logger.error("FFmpeg is REQUIRED for voice analysis (Whisper) to work.")
        logger.error("Without FFmpeg, voice analysis will fail with '[WinError 2]'")
        logger.error("")
        logger.error("If you just installed FFmpeg, you MUST:")
        logger.error("  1. STOP this server (Ctrl+C)")
        logger.error("  2. CLOSE this terminal completely")
        logger.error("  3. OPEN a new terminal")
        logger.error("  4. START the server again")
        logger.error("")
        logger.error("This is required because Python caches the PATH at startup.")
        logger.error("="*60)
    
    # Auto-install voice analysis dependencies (skip FFmpeg check since we did it above)
    logger.info("Checking voice analysis dependencies...")
    try:
        success = auto_install_dependencies(force_reinstall=False, skip_requirements=True)
        if not success:
            logger.warning("Some voice analysis dependencies are missing. Voice analysis may not work properly.")
    except Exception as e:
        logger.error(f"Auto-installation failed: {e}")
    
    # Initialize AI providers
    gemini_key = None
    deepseek_key = None
    # Try to fetch a Gemini API key from settings (supports both cases)
    if hasattr(settings, "GEMINI_API_KEY"):
        gemini_key = settings.GEMINI_API_KEY
    elif hasattr(settings, "gemini_api_key"):
        gemini_key = settings.gemini_api_key
    
    # Try to fetch the DeepSeek API key from settings
    if hasattr(settings, "deepseek_api_key"):
        deepseek_key = settings.deepseek_api_key

    if gemini_key:
        providers[AIProvider.GEMINI] = GeminiProvider()
        logger.info("Gemini provider initialized")
    else:
        logger.warning("Gemini API key not configured; Gemini provider disabled")
    
    if deepseek_key:
        providers[AIProvider.DEEPSEEK] = DeepSeekProvider()
        logger.info("DeepSeek provider initialized")
    else:
        logger.warning("DeepSeek API key not configured; DeepSeek provider disabled")
    
    # Initialize the provider manager with available providers
    global provider_manager
    provider_manager = ProviderManager(providers)
    logger.info(f"Provider manager initialized with {len(providers)} providers")
    
    # Initialize Supabase connection
    if SupabaseManager.is_configured():
        supabase_healthy = await SupabaseManager.health_check()
        if supabase_healthy:
            logger.info("Supabase connection established")
        else:
            logger.warning("Supabase health check failed")
    else:
        logger.warning("Supabase not configured - chat history will not be saved")
    
    yield
    
    # Shutdown
    logger.info("Shutting down AI Backend Service")


# Create FastAPI app
app = FastAPI(
    title="Language Learning AI Backend",
    description="AI-powered language learning backend supporting multiple AI providers",
    version="1.0.0",
    lifespan=lifespan,
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, use specific origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(lesson_router)
app.include_router(narration_router)
app.include_router(voice_router)
app.include_router(elevenlabs_agent_router)
app.include_router(whisper_analysis_router)

# TTS endpoint for generating audio
@app.post("/tts/generate")
async def generate_tts_audio(request: dict):
    """
    Generate audio from text using Edge TTS
    """
    try:
        text = request.get("text", "")
        language = request.get("language", "en-US")
        use_cache = request.get("use_cache", True)
        
        if not text:
            raise HTTPException(status_code=400, detail="Text is required")
        
        tts_service = get_tts_service()
        
        # Map language code to voice
        voice_mapping = {
            "zh-CN": "zh-CN-XiaoNeural",
            "ko-KR": "ko-KR-SunHiNeural",
            "en-US": "en-US-JennyNeural"
        }
        
        voice = voice_mapping.get(language, "en-US-JennyNeural")
        
        # Generate audio - TTS service returns a URL string
        audio_url = await tts_service.generate_audio(text, voice)
        
        return {
            "audio_url": audio_url,
            "text": text,
            "language": language
        }
        
    except Exception as e:
        logger.error(f"TTS generation error: {e}")
        raise HTTPException(status_code=500, detail=f"TTS generation failed: {str(e)}")

@app.get("/", tags=["General"])
async def root():

    return {
        "message": "Language Learning AI Backend",
        "version": "1.0.0",
        "status": "online"
    }


@app.get("/health", response_model=HealthResponse, tags=["General"])
async def health_check():

    provider_status = {}
    
    for provider_name, provider in providers.items():
        try:
            is_healthy = await provider.health_check()
            provider_status[provider_name.value] = is_healthy
        except Exception as e:
            logger.error(f"Health check failed for {provider_name}: {e}")
            provider_status[provider_name.value] = False
    
    # Add Supabase status
    provider_status["supabase"] = await SupabaseManager.health_check()
    
    return HealthResponse(
        status="healthy" if any(provider_status.values()) else "degraded",
        version="1.0.0",
        providers=provider_status,
    )


@app.post("/chat", response_model=ChatResponse, tags=["AI Chat"])
async def chat_completion(request: ChatRequest):
    try:
        logger.info(f"Chat request from user: {request.user_context.user_id}")
        logger.info(f"Provider: {request.provider.value}")

        if not provider_manager:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="AI providers not initialized"
            )
        
        # Build personalized system prompt
        system_prompt = build_system_prompt(
            user_context=request.user_context,
            bot_id=request.bot_id
        )
        
        logger.info(f"System prompt length: {len(system_prompt)} chars")
        
        # Format conversation history
        formatted_history = format_conversation_history(
            [msg.dict() for msg in request.conversation_history],
            max_messages=10
        )
        
        logger.info(f"History messages: {len(formatted_history)}")
        
        # Generate response using provider manager with fallback and retry
        response, provider_used = await provider_manager.generate_with_fallback(
            primary_provider=request.provider,
            message=request.message,
            system_prompt=system_prompt,
            conversation_history=formatted_history,
            temperature=request.temperature,
            max_tokens=request.max_tokens,
            enable_retry=True
        )
        
        logger.info(f"Generated response via {provider_used.value}: {len(response.message)} chars")
        logger.info(f"Tokens used: {response.tokens_used}")
        
        return response
        
    except ProviderQuotaExceededError as e:
        logger.error(f"All providers quota exceeded: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="All AI providers are currently unavailable due to quota limits. Please try again later."
        )
    except Exception as e:
        logger.error(f"Error in chat completion: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate response: {str(e)}"
        )


@app.get("/providers", tags=["General"])
async def list_providers():
    provider_info = {}
    
    for provider_name in providers.keys():
        provider_info[provider_name.value] = {
            "available": True,
            "name": provider_name.value.title(),
        }
    
    return {
        "providers": provider_info,
        "default": AIProvider.DEEPSEEK.value,
    }


@app.post("/chat/history/save", response_model=SaveHistoryResponse, tags=["Chat History"])
async def save_chat_history(request: SaveHistoryRequest):

    try:
        logger.info(f"Saving chat history for session: {request.session_id}")
        
        if not SupabaseManager.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Chat history storage is not configured"
            )
        
        if not request.messages:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="No messages provided"
            )
        
        result = await chat_history_service.save_chat_history(
            session_id=request.session_id,
            messages=request.messages,
        )
        
        return SaveHistoryResponse(**result)
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error saving chat history: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to save chat history: {str(e)}"
        )


@app.post("/chat/history/load", response_model=LoadHistoryResponse, tags=["Chat History"])
async def load_chat_history(request: LoadHistoryRequest):
    """
    Load chat history from Supabase and decompress.
    
    This endpoint:
    1. Fetches compressed chat history from Supabase
    2. Decompresses the messages
    3. Returns the full conversation history
    """
    try:
        logger.info(f"Loading chat history for session: {request.session_id}")
        
        if not SupabaseManager.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Chat history storage is not configured"
            )
        
        result = await chat_history_service.load_chat_history(
            session_id=request.session_id,
        )
        
        return LoadHistoryResponse(**result)
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error loading chat history: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to load chat history: {str(e)}"
        )


@app.delete("/chat/history/{session_id}", tags=["Chat History"])
async def delete_chat_history(session_id: str):
    try:
        logger.info(f"Deleting chat history for session: {session_id}")
        
        if not SupabaseManager.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Chat history storage is not configured"
            )
        
        await chat_history_service.delete_chat_history(session_id)
        
        return {"success": True, "message": "Chat history deleted"}
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error deleting chat history: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete chat history: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn
    import multiprocessing
    import sys
    
    # Required for Windows multiprocessing support with Uvicorn's reload feature
    multiprocessing.freeze_support()
    
    # On Windows, disable reload to avoid multiprocessing spawn issues with numpy/fasttext
    # These libraries don't play well with Uvicorn's WatchFiles reloader on Windows
    is_windows = sys.platform == "win32"
    enable_reload = settings.environment == "development" and not is_windows
    
    if is_windows and settings.environment == "development":
        logger.warning(
            "Hot-reload disabled on Windows to prevent multiprocessing issues. "
            "Restart the server manually to apply changes."
        )
    
    logger.info(f"Starting server on {settings.host}:{settings.port}")
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        reload=enable_reload,
    )

