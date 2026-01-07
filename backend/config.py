from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List
import os
from pathlib import Path
import logging
from pydantic import Field

logger = logging.getLogger(__name__)


def find_env_file():
    """Find .env file in common locations"""
    current_dir = Path.cwd()
    script_dir = Path(__file__).parent

    possible_locations = [
        current_dir / ".env",  # Current working directory
        script_dir / ".env",  # Backend directory
        script_dir.parent / ".env",  # Parent of backend (project root)
        script_dir.parent / "KotlinProject" / "composeApp" / ".env",  # Kotlin app .env
    ]

    for env_path in possible_locations:
        if env_path.exists():
            logger.info(f"Found .env file at: {env_path.absolute()}")
            return str(env_path.absolute())

    logger.warning("No .env file found in any checked location")
    for loc in possible_locations:
        logger.warning(f"  Checked: {loc.absolute()}")
    return None


class Settings(BaseSettings):
    # pydantic_settings automatically reads from:
    # 1. .env file (if found)
    # 2. System environment variables
    # 3. Default values
    model_config = SettingsConfigDict(
        env_file=find_env_file() or ".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",  # Ignore extra env vars
        # Also read from environment variables
        env_ignore_empty=True,
    )
    
    # AI Provider API Keys
    gemini_api_key: str = Field(..., env="GEMINI_API_KEY")
    deepgram_api_key: str = Field(..., env="DEEPGRAM_API_KEY")
    deepseek_api_key: str = Field(..., env="DEEPSEEK_API_KEY")
    eleven_labs_api_key: str = Field(..., env="ELEVEN_LABS_API_KEY")
    
    # ElevenLabs Agent Platform Configuration
    # Agent IDs are created in ElevenLabs Dashboard and configured per language
    elevenlabs_chinese_agent_id: str = Field(default="", env="ELEVENLABS_CHINESE_AGENT_ID")
    elevenlabs_korean_agent_id: str = Field(default="", env="ELEVENLABS_KOREAN_AGENT_ID")
    elevenlabs_english_agent_id: str = Field(default="", env="ELEVENLABS_ENGLISH_AGENT_ID")
    elevenlabs_french_agent_id: str = Field(default="", env="ELEVENLABS_FRENCH_AGENT_ID")
    elevenlabs_german_agent_id: str = Field(default="", env="ELEVENLABS_GERMAN_AGENT_ID")
    elevenlabs_spanish_agent_id: str = Field(default="", env="ELEVENLABS_SPANISH_AGENT_ID")
    
    # Deepgram Configuration - Optimized for lower latency
    deepgram_model: str = Field(default="nova-3", env="DEEPGRAM_MODEL")
    deepgram_language: str = Field(default="multi", env="DEEPGRAM_LANGUAGE")
    deepgram_endpoint: int = Field(default=300, env="DEEPGRAM_ENDPOINT")  # 300ms for faster end-of-speech detection
    deepgram_interim_results: bool = Field(default=True, env="DEEPGRAM_INTERIM_RESULTS")  # Enable real-time feedback
    deepgram_utterance_end_ms: int = Field(default=300, env="DEEPGRAM_UTTERANCE_END_MS")  # Silence duration to trigger response
    deepgram_smart_format: bool = Field(default=True, env="DEEPGRAM_SMART_FORMAT")  # Better formatting
    
    # Supabase Configuration
    # Accepts both SUPABASE_KEY and SUPABASE_ANON_KEY
    supabase_url: str = ""
    supabase_key: str = ""
    supabase_anon_key: str = ""  # Alias for compatibility
    supabase_service_role_key: str = ""
    
    # Server Configuration
    host: str = "0.0.0.0"
    port: int = 8000
    environment: str = "development"
    
    # CORS Configuration
    allowed_origins: List[str] = ["http://localhost:*", "http://127.0.0.1:*"]
    
    # Chat History Configuration
    enable_chat_history: bool = True
    compression_type: str = "gzip"  # gzip, zlib, or none


settings = Settings()

# Use SUPABASE_ANON_KEY as a fallback if SUPABASE_KEY is not set
if not settings.supabase_key and settings.supabase_anon_key:
    settings.supabase_key = settings.supabase_anon_key
    logger.info("Using SUPABASE_ANON_KEY as SUPABASE_KEY")

# Debug: Log configuration status
logger.info(f"Supabase URL configured: {bool(settings.supabase_url)}")
logger.info(f"Supabase Key configured: {bool(settings.supabase_key)}")
logger.info(f"Supabase Service Role Key configured: {bool(settings.supabase_service_role_key)}")
if settings.supabase_url:
    logger.info(f"Supabase URL: {settings.supabase_url[:30]}...")
if not settings.supabase_url and not settings.supabase_key:
    logger.error("=" * 60)
    logger.error("ERROR: Supabase credentials not found!")
    logger.error("Please ensure your .env file contains:")
    logger.error("  SUPABASE_URL=your_supabase_url")
    logger.error("  SUPABASE_KEY=your_supabase_key (or SUPABASE_ANON_KEY)")
    logger.error(f"Current working directory: {os.getcwd()}")
    logger.error(f"Checked .env file: {find_env_file()}")
    logger.error("=" * 60)


def get_settings() -> Settings:
    """Get the application settings instance."""
    return settings
