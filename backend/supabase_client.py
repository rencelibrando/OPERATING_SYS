from supabase import create_client, Client
from typing import Optional
import logging

from config import settings

logger = logging.getLogger(__name__)


class SupabaseManager:

    _client: Optional[Client] = None
    
    @classmethod
    def get_client(cls) -> Optional[Client]:
        if cls._client is not None:
            return cls._client
        supabase_key = settings.supabase_service_role_key or settings.supabase_key
        
        if not settings.supabase_url or not supabase_key:
            logger.warning("Supabase credentials not configured")
            return None
        
        try:
            cls._client = create_client(
                settings.supabase_url,
                supabase_key
            )
            if settings.supabase_service_role_key:
                logger.info("Supabase client initialized with service role key")
            else:
                logger.warning("Supabase client using anon key (RLS policies will apply)")
            return cls._client
        except Exception as e:
            logger.error(f"Failed to initialize Supabase client: {e}")
            return None
    
    @classmethod
    def is_configured(cls) -> bool:
        return bool(settings.supabase_url and (settings.supabase_service_role_key or settings.supabase_key))
    
    @classmethod
    async def health_check(cls) -> bool:
        try:
            client = cls.get_client()
            if not client:
                return False
            return True
        except Exception as e:
            logger.error(f"Supabase health check failed: {e}")
            return False

def get_supabase() -> Optional[Client]:
    return SupabaseManager.get_client()

