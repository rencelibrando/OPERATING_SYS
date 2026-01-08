import re
from typing import Dict, List, Optional

from google import genai
from google.genai import types

from config import settings
from models import AIProvider, ChatResponse
from providers.base import AIProviderBase
from providers.exceptions import ProviderError, ProviderQuotaExceededError


class GeminiProvider(AIProviderBase):

    def __init__(self):
        """Initialize Gemini with API key."""
        if settings.gemini_api_key:
            self.client = genai.Client(api_key=settings.gemini_api_key)
            self.model_name = 'gemini-2.0-flash'
        else:
            self.client = None
            self.model_name = None
    
    async def generate_response(
        self,
        message: str,
        system_prompt: str,
        conversation_history: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 1000,
    ) -> ChatResponse:

        if not self.client:
            raise ValueError("Gemini API key not configured")
        
        try:
            # Configure generation parameters using the new SDK types
            generation_config = types.GenerateContentConfig(
                temperature=temperature,
                max_output_tokens=max_tokens,
                top_p=0.95,
                top_k=40,
            )
            
            # Build the full conversation context
            full_prompt = self._build_prompt(
                system_prompt,
                conversation_history,
                message
            )
            
            # Generate response using the new SDK client
            response = self.client.models.generate_content(
                model=self.model_name,
                contents=full_prompt,
                config=generation_config,
            )
            
            # Extract response text
            response_text = response.text if hasattr(response, 'text') else ""
            
            # Try to get token count if available
            tokens_used = None
            if hasattr(response, 'usage_metadata'):
                tokens_used = getattr(response.usage_metadata, 'total_token_count', None)
            
            return ChatResponse(
                message=response_text,
                provider=AIProvider.GEMINI,
                tokens_used=tokens_used,
                metadata={
                    "model": self.model_name,
                    "finish_reason": getattr(response, 'finish_reason', None),
                }
            )

        except Exception as e:
            error_str = str(e).lower()
            # Check for quota/rate limit errors
            if 'quota' in error_str or 'resource_exhausted' in error_str or 'rate' in error_str:
                retry_after = self._extract_retry_after_seconds_from_str(str(e))
                raise ProviderQuotaExceededError(
                    provider=AIProvider.GEMINI,
                    message="Gemini API quota exceeded. Please check billing or retry later.",
                    retry_after_seconds=retry_after,
                ) from e
            raise ProviderError(
                provider=AIProvider.GEMINI,
                message=f"Gemini API error: {str(e)}",
            ) from e

    def _build_prompt(
        self, 
        system_prompt: str, 
        history: List[Dict[str, str]], 
        current_message: str
    ) -> str:

        parts = [f"SYSTEM INSTRUCTIONS:\n{system_prompt}\n"]
        
        if history:
            parts.append("\nCONVERSATION HISTORY:")
            for msg in history:
                role = msg.get("role", "user").upper()
                content = msg.get("content", "")
                parts.append(f"{role}: {content}")
        
        parts.append(f"\nUSER: {current_message}")
        parts.append("\nASSISTANT:")
        
        return "\n".join(parts)

    def _extract_retry_after_seconds_from_str(
        self,
        error_str: str,
    ) -> Optional[int]:
        """Best-effort extraction of retry delay from error string."""
        # Fallback: parse textual message for "retry_delay { seconds: X }"
        match = re.search(r"retry_delay\s*\{\s*seconds:\s*(\d+)", error_str)
        if match:
            return int(match.group(1))
        # Also try to find seconds mentioned in different formats
        match = re.search(r"(\d+)\s*seconds?", error_str)
        if match:
            return int(match.group(1))
        return None
    
    async def health_check(self) -> bool:

        # Skip actual API call to avoid wasting quota
        # Just verify that the client is configured
        if not self.client:
            print("Gemini health check: Client not configured")
            return False
        
        print("Gemini health check: Client configured (skipping API test to preserve quota)")
        return True

