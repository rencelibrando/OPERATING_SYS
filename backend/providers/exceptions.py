from typing import Optional

from models import AIProvider


class ProviderError(Exception):
    """Base class for AI provider specific errors."""

    def __init__(self, provider: AIProvider, message: str):
        super().__init__(message)
        self.provider = provider


class ProviderQuotaExceededError(ProviderError):
    """Raised when the upstream model reports a quota or rate limit violation."""

    def __init__(
        self,
        provider: AIProvider,
        message: str,
        retry_after_seconds: Optional[int] = None,
    ):
        super().__init__(provider, message)
        self.retry_after_seconds = retry_after_seconds
