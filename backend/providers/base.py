from abc import ABC, abstractmethod
from typing import List, Dict, Any
from models import ChatResponse, UserContext

class AIProviderBase(ABC):

    @abstractmethod
    async def generate_response(
        self,
        message: str,
        system_prompt: str,
        conversation_history: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 1000,
    ) -> ChatResponse:
        pass
    
    @abstractmethod
    async def health_check(self) -> bool:
        pass

