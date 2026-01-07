"""
Standardized error logging utility for the WordBridge backend.

All errors are logged in the format: [FileName]{ErrorMessage}

This ensures:
- Clear indication of the source file where the error originated
- Consistent logging across all modules and layers
- No exposure of sensitive information (sanitized messages)

Usage:
    from error_logger import ErrorLogger
    
    Logger = ErrorLogger("my_file.py")
    logger.error("Something went wrong")
    logger.warning("Connection slows")
    logger.info("Processing started")
"""

import re
import logging
from typing import Optional

# Sensitive patterns to sanitize from error messages
SENSITIVE_PATTERNS = [
    re.compile(r'password[=:]\s*\S+', re.IGNORECASE),
    re.compile(r'token[=:]\s*\S+', re.IGNORECASE),
    re.compile(r'api[_-]?key[=:]\s*\S+', re.IGNORECASE),
    re.compile(r'secret[=:]\s*\S+', re.IGNORECASE),
    re.compile(r'authorization[=:]\s*\S+', re.IGNORECASE),
    re.compile(r'bearer\s+\S+', re.IGNORECASE),
    re.compile(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'),  # Email pattern
]


def sanitize_message(message: str) -> str:
    """Remove sensitive information from error messages."""
    sanitized = message
    for pattern in SENSITIVE_PATTERNS:
        sanitized = pattern.sub('[REDACTED]', sanitized)
    return sanitized


class ErrorLogger:
    """Standardized error logger for the WordBridge backend."""
    
    def __init__(self, file_name: str):
        """
        Initialize the error logger.
        
        Args:
            file_name: The source file name (e.g., "main.py")
        """
        self.file_name = file_name
        self._logger = logging.getLogger(file_name)
    
    def error(self, message: str, exc: Optional[Exception] = None):
        """
        Log an error message with standardized format.
        
        Args:
            message: The error message to log
            exc: Optional exception that caused the error
        """
        sanitized = sanitize_message(message)
        formatted = f"[{self.file_name}]{{{sanitized}}}"
        
        if exc:
            self._logger.error(formatted, exc_info=exc)
        else:
            self._logger.error(formatted)
    
    def warning(self, message: str):
        """
        Log a warning message with standardized format.
        
        Args:
            message: The warning message to log
        """
        sanitized = sanitize_message(message)
        formatted = f"[{self.file_name}][WARNING]{{{sanitized}}}"
        self._logger.warning(formatted)
    
    def info(self, message: str):
        """
        Log an info message with standardized format.
        
        Args:
            message: The info message to log
        """
        sanitized = sanitize_message(message)
        formatted = f"[{self.file_name}][INFO]{{{sanitized}}}"
        self._logger.info(formatted)
    
    def debug(self, message: str):
        """
        Log a debug message with a standardized format.
        
        Args:
            message: The debug message to log
        """
        sanitized = sanitize_message(message)
        formatted = f"[{self.file_name}][DEBUG]{{{sanitized}}}"
        self._logger.debug(formatted)


# Convenience function to create a logger for a file
def get_logger(file_name: str) -> ErrorLogger:
    """
    Get a standardized error logger for a file.
    
    Args:
        file_name: The source file name (e.g., "main.py")
        
    Returns:
        An ErrorLogger instance for the file
    """
    return ErrorLogger(file_name)
