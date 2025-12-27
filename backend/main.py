from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging
import sys
import os

# Add parent directory to path to allow importing from deps_installer
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
from chat_history_service import ChatHistoryService
from supabase_client import SupabaseManager
from lesson_routes import router as lesson_router
from narration_routes import router as narration_router
from voice_routes import router as voice_router
from conversation_routes import router as conversation_router
from agent_routes import router as agent_router
from error_logger import get_logger


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize empty providers dict; will populate later when API key present
providers = {}

# Initialize chat history service
chat_history_service = ChatHistoryService()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown."""
    # Startup
    logger.info("Starting AI Backend Service")
    
    # Initialize AI providers
    gemini_key = None
    # Try to fetch Gemini API key from settings (supports both cases)
    if hasattr(settings, "GEMINI_API_KEY"):
        gemini_key = settings.GEMINI_API_KEY
    elif hasattr(settings, "gemini_api_key"):
        gemini_key = settings.gemini_api_key

    if gemini_key:
        providers[AIProvider.GEMINI] = GeminiProvider()
        logger.info("Gemini provider initialized")
    else:
        logger.warning("Gemini API key not configured; Gemini provider disabled")
    
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
app.include_router(conversation_router)
app.include_router(agent_router)

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

        if request.provider not in providers:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"AI provider '{request.provider.value}' is not available"
            )
        
        provider = providers[request.provider]
        
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
        
        # Generate response from AI provider
        response = await provider.generate_response(
            message=request.message,
            system_prompt=system_prompt,
            conversation_history=formatted_history,
            temperature=request.temperature,
            max_tokens=request.max_tokens,
        )
        
        logger.info(f"Generated response: {len(response.message)} chars")
        logger.info(f"Tokens used: {response.tokens_used}")
        
        return response
        
    except HTTPException:
        raise
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
        "default": AIProvider.GEMINI.value,
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
    
    logger.info(f"Starting server on {settings.host}:{settings.port}")
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.environment == "development",
    )

