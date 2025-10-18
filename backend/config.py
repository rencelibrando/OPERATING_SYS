from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    
    # AI Provider API Keys
    gemini_api_key: str = ""
    
    # Supabase Configuration
    supabase_url: str = ""
    supabase_key: str = ""  # Anonymous/Public key (for client operations)
    supabase_service_role_key: str = ""  # Service role key (bypasses RLS)
    
    # Server Configuration
    host: str = "0.0.0.0"
    port: int = 8000
    environment: str = "development"
    
    # CORS Configuration
    allowed_origins: List[str] = ["http://localhost:*", "http://127.0.0.1:*"]
    
    # Chat History Configuration
    enable_chat_history: bool = True
    compression_type: str = "gzip"  # gzip, zlib, or none
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()

