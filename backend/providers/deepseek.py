import re
from typing import Dict, List, Optional

import openai
from config import settings
from models import AIProvider, ChatResponse
from providers.base import AIProviderBase
from providers.exceptions import ProviderError, ProviderQuotaExceededError


class DeepSeekProvider(AIProviderBase):

    def __init__(self):
        """Initialize DeepSeek with API key."""
        if settings.deepseek_api_key:
            self.client = openai.OpenAI(
                api_key=settings.deepseek_api_key,
                base_url="https://api.deepseek.com"
            )
        else:
            self.client = None
    
    async def generate_response(
        self,
        message: str,
        system_prompt: str,
        conversation_history: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 1000,
    ) -> ChatResponse:

        if not self.client:
            raise ValueError("DeepSeek API key not configured")
        
        try:
            # Build messages for OpenAI-compatible API
            messages = [
                {"role": "system", "content": system_prompt}
            ]
            
            # Add conversation history
            for msg in conversation_history:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                if role and content:
                    messages.append({"role": role, "content": content})
            
            # Add current message
            messages.append({"role": "user", "content": message})
            
            # Generate response
            response = self.client.chat.completions.create(
                model="deepseek-chat",
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
            )
            
            # Extract response text
            response_text = response.choices[0].message.content
            tokens_used = response.usage.total_tokens if response.usage else None
            
            return ChatResponse(
                message=response_text,
                provider=AIProvider.DEEPSEEK,
                tokens_used=tokens_used,
                metadata={
                    "model": "deepseek-chat",
                    "finish_reason": response.choices[0].finish_reason,
                }
            )

        except openai.RateLimitError as e:
            retry_after = self._extract_retry_after(e)
            raise ProviderQuotaExceededError(
                provider=AIProvider.DEEPSEEK,
                message="DeepSeek API quota exceeded. Please check billing or retry later.",
                retry_after_seconds=retry_after,
            ) from e
        except openai.APIError as e:
            raise ProviderError(
                provider=AIProvider.DEEPSEEK,
                message=f"DeepSeek API call failed: {str(e)}",
            ) from e
        except Exception as e:
            raise ProviderError(
                provider=AIProvider.DEEPSEEK,
                message=f"DeepSeek API error: {str(e)}",
            ) from e

    def _extract_retry_after(self, error: openai.RateLimitError) -> Optional[int]:
        """Extract retry delay from error response."""
        try:
            if hasattr(error, 'response') and error.response:
                headers = error.response.headers
                retry_after = headers.get('retry-after')
                if retry_after:
                    return int(retry_after)
        except:
            pass
        return None
    
    async def health_check(self) -> bool:
        """Check if DeepSeek API is accessible."""
        if not self.client:
            print("DeepSeek health check: Client not configured")
            return False
        
        try:
            # Make a minimal API call to check connectivity
            response = self.client.chat.completions.create(
                model="deepseek-chat",
                messages=[{"role": "user", "content": "test"}],
                max_tokens=1,
            )
            return True
        except Exception as e:
            print(f"DeepSeek health check failed: {str(e)}")
            return False
