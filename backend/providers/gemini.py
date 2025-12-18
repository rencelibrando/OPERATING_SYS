import re
from typing import Dict, List, Optional

import google.generativeai as genai
from google.api_core import exceptions as google_exceptions

from config import settings
from models import AIProvider, ChatResponse
from providers.base import AIProviderBase
from providers.exceptions import ProviderError, ProviderQuotaExceededError


class GeminiProvider(AIProviderBase):

    def __init__(self):
        """Initialize Gemini with API key."""
        if settings.gemini_api_key:
            genai.configure(api_key=settings.gemini_api_key)
            self.model = genai.GenerativeModel('gemini-2.0-flash')
        else:
            self.model = None
    
    async def generate_response(
        self,
        message: str,
        system_prompt: str,
        conversation_history: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 1000,
    ) -> ChatResponse:

        if not self.model:
            raise ValueError("Gemini API key not configured")
        
        try:
            # Configure generation parameters
            generation_config = genai.GenerationConfig(
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
            
            # Generate response
            response = self.model.generate_content(
                full_prompt,
                generation_config=generation_config,
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
                    "model": "gemini-2.0-flash",
                    "finish_reason": getattr(response, 'finish_reason', None),
                }
            )

        except google_exceptions.ResourceExhausted as e:
            retry_after = self._extract_retry_after_seconds(e)
            raise ProviderQuotaExceededError(
                provider=AIProvider.GEMINI,
                message="Gemini API quota exceeded. Please check billing or retry later.",
                retry_after_seconds=retry_after,
            ) from e
        except google_exceptions.GoogleAPICallError as e:
            raise ProviderError(
                provider=AIProvider.GEMINI,
                message=f"Gemini API call failed: {str(e)}",
            ) from e
        except Exception as e:
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

    def _extract_retry_after_seconds(
        self,
        error: google_exceptions.ResourceExhausted,
    ) -> Optional[int]:
        """Best-effort extraction of retry delay from error metadata."""
        retry_info = getattr(error, "retry_info", None)
        if retry_info and retry_info.retry_delay:
            seconds = retry_info.retry_delay.seconds
            if retry_info.retry_delay.nanos:
                # Round up fractional seconds
                seconds += 1
            return seconds or None

        # Fallback: parse textual message for "retry_delay { seconds: X }"
        match = re.search(r"retry_delay\s*\{\s*seconds:\s*(\d+)", str(error))
        if match:
            return int(match.group(1))
        return None
    
    async def health_check(self) -> bool:

        # Skip actual API call to avoid wasting quota
        # Just verify that the model is configured
        if not self.model:
            print("Gemini health check: Model not configured")
            return False
        
        print("Gemini health check: Model configured (skipping API test to preserve quota)")
        return True

