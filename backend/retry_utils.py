import asyncio
import time
import random
from typing import Callable, TypeVar, Optional, List, Type, Union
from providers.exceptions import ProviderQuotaExceededError, ProviderError

T = TypeVar('T')


async def retry_with_exponential_backoff(
    func: Callable[..., T],
    max_retries: int = 3,
    base_delay: float = 1.0,
    max_delay: float = 60.0,
    backoff_factor: float = 2.0,
    jitter: bool = True,
    retry_on_exceptions: List[Type[Exception]] = None,
    *args,
    **kwargs
) -> T:
    """
    Execute a function with exponential backoff retry logic.
    
    Args:
        func: The async function to execute
        max_retries: Maximum number of retry attempts
        base_delay: Initial delay between retries in seconds
        max_delay: Maximum delay between retries in seconds
        backoff_factor: Multiplier for delay after each retry
        jitter: Whether to add random jitter to delay
        retry_on_exceptions: List of exception types to retry on
        *args, **kwargs: Arguments to pass to the function
    
    Returns:
        The result of the function call
    
    Raises:
        The last exception if all retries fail
    """
    if retry_on_exceptions is None:
        retry_on_exceptions = [ProviderQuotaExceededError, ProviderError]
    
    last_exception = None
    
    for attempt in range(max_retries + 1):  # +1 for initial attempt
        try:
            return await func(*args, **kwargs)
        except Exception as e:
            last_exception = e
            
            # Check if this exception type should be retried
            should_retry = any(isinstance(e, exc_type) for exc_type in retry_on_exceptions)
            
            # For quota errors, check if retry_after is provided
            if isinstance(e, ProviderQuotaExceededError) and e.retry_after_seconds is not None:
                delay = min(e.retry_after_seconds, max_delay)
                logger_instance = _get_logger()
                logger_instance.warning(f"Quota exceeded, retrying after {delay}s (attempt {attempt + 1}/{max_retries + 1})")
                await asyncio.sleep(delay)
                continue
            
            # Don't retry on the last attempt or if an exception type shouldn't be retried
            if attempt == max_retries or not should_retry:
                break
            
            # Calculate delay for next attempt
            delay = min(base_delay * (backoff_factor ** attempt), max_delay)
            
            # Add jitter to prevent thundering herd
            if jitter:
                delay *= (0.5 + random.random() * 0.5)  # 50% to 100% of calculated delay
            
            logger_instance = _get_logger()
            logger_instance.warning(
                f"Attempt {attempt + 1}/{max_retries + 1} failed with {type(e).__name__}: {str(e)}. "
                f"Retrying after {delay:.2f}s"
            )
            
            await asyncio.sleep(delay)
    
    # All retries failed, raise the last exception
    raise last_exception


def _get_logger():
    """Get a logger instance (imported here to avoid circular imports)."""
    import logging
    return logging.getLogger(__name__)


class ProviderManager:
    """
    Manages multiple AI providers with fallback and retry logic.
    """
    
    def __init__(self, providers: dict):
        self.providers = providers
        self.provider_health = {}  # Track provider health
        self.last_error_time = {}  # Track the last error time for each provider
    
    async def generate_with_fallback(
        self,
        primary_provider: str,
        message: str,
        system_prompt: str,
        conversation_history: List[dict],
        temperature: float = 0.7,
        max_tokens: int = 1000,
        enable_retry: bool = True
    ) -> tuple:
        """
        Generate a response using providers with fallback and retry logic.
        
        Args:
            primary_provider: The preferred provider to use first
            message: User message
            system_prompt: System prompt
            conversation_history: Conversation history
            temperature: Generation temperature
            max_tokens: Maximum tokens to generate
            enable_retry: Whether to enable retry logic
        
        Returns:
            Tuple of (response, provider_used)
        """
        from models import AIProvider
        
        # Determine fallback order
        provider_order = [primary_provider]
        
        # Add fallback providers
        if primary_provider == AIProvider.GEMINI and AIProvider.DEEPSEEK in self.providers:
            provider_order.append(AIProvider.DEEPSEEK)
        elif primary_provider == AIProvider.DEEPSEEK and AIProvider.GEMINI in self.providers:
            provider_order.append(AIProvider.GEMINI)
        
        last_exception = None
        
        for provider_enum in provider_order:
            if provider_enum not in self.providers:
                continue
            
            provider = self.providers[provider_enum]
            
            try:
                if enable_retry:
                    response = await retry_with_exponential_backoff(
                        provider.generate_response,
                        max_retries=1,  # Reduce retries for faster fallback
                        base_delay=0.2,  # Reduce base delay
                        message=message,
                        system_prompt=system_prompt,
                        conversation_history=conversation_history,
                        temperature=temperature,
                        max_tokens=max_tokens
                    )
                else:
                    response = await provider.generate_response(
                        message=message,
                        system_prompt=system_prompt,
                        conversation_history=conversation_history,
                        temperature=temperature,
                        max_tokens=max_tokens
                    )
                
                # Mark provider as healthy
                self.provider_health[provider_enum] = True
                
                # Add fallback metadata if needed
                if provider_enum != primary_provider:
                    response.metadata = response.metadata or {}
                    response.metadata["fallback_used"] = True
                    response.metadata["original_provider"] = primary_provider.value
                    response.metadata["actual_provider"] = provider_enum.value
                
                return response, provider_enum
                
            except ProviderQuotaExceededError as e:
                last_exception = e
                self.provider_health[provider_enum] = False
                self.last_error_time[provider_enum] = time.time()
                
                # Log the quota error and immediately continue to the next provider
                logger_instance = _get_logger()
                logger_instance.warning(f"Provider {provider_enum.value} quota exceeded: {str(e)}")
                logger_instance.info(f"Immediately falling back to next provider...")
                
                # Continue to the next provider without a retry
                continue
                
            except Exception as e:
                last_exception = e
                self.provider_health[provider_enum] = False
                self.last_error_time[provider_enum] = time.time()
                
                logger_instance = _get_logger()
                logger_instance.error(f"Provider {provider_enum.value} failed: {str(e)}")
                
                # For non-quota errors, try retry once
                if provider_enum == provider_order[-1]:  # Last provider
                    raise
                # Continue to the next provider
                continue
        
        # All providers failed
        raise last_exception or Exception("All providers failed")
    
    def get_provider_status(self) -> dict:
        """Get the status of all providers."""
        status = {}
        for provider_enum in self.providers:
            status[provider_enum.value] = {
                "available": provider_enum in self.providers,
                "healthy": self.provider_health.get(provider_enum, True),
                "last_error_time": self.last_error_time.get(provider_enum)
            }
        return status
