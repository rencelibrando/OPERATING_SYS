import google.generativeai as genai
from typing import List, Dict
from models import ChatResponse, AIProvider
from providers.base import AIProviderBase
from config import settings

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
            
        except Exception as e:
            raise Exception(f"Gemini API error: {str(e)}")
    
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
    
    async def health_check(self) -> bool:

        if not self.model:
            return False
        
        try:
            # Try a simple generation to verify API access
            test_response = self.model.generate_content(
                "Say 'OK' if you can read this.",
                generation_config=genai.GenerationConfig(
                    max_output_tokens=10,
                ),
            )
            return bool(test_response and hasattr(test_response, 'text'))
        except Exception as e:
            print(f"Gemini health check failed: {e}")
            return False

