"""
FastText Language Detection Service
Singleton service for detecting language using FastText lid.176.bin model.
"""
import fasttext
import os
import logging
from typing import Optional, Tuple
from pathlib import Path
import urllib.request

logger = logging.getLogger(__name__)


class LanguageDetectionService:
    """
    Singleton service for FastText language detection.
    Model is loaded once and reused across requests.
    """

    _instance: Optional['LanguageDetectionService'] = None
    _model: Optional[fasttext.FastText._FastText] = None
    
    # FastText language codes to full language names
    LANGUAGE_MAP = {
        '__label__ko': 'ko',  # Korean
        '__label__de': 'de',  # German
        '__label__zh': 'zh',  # Chinese
        '__label__es': 'es',  # Spanish
        '__label__fr': 'fr',  # French
        '__label__en': 'en',  # English
    }
    
    # Supported languages
    SUPPORTED_LANGUAGES = {'ko', 'de', 'zh', 'es', 'fr', 'en'}
    
    # Minimum confidence threshold - lowered to improve detection for all languages
    CONFIDENCE_THRESHOLD = 0.3
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(LanguageDetectionService, cls).__new__(cls)
        return cls._instance
    
    def __init__(self):
        if self._model is None:
            self._load_model()
    
    def _get_model_path(self) -> Path:
        """Get the path to the FastText model file."""
        model_dir = Path(__file__).parent / "models"
        model_dir.mkdir(exist_ok=True)
        return model_dir / "lid.176.bin"
    
    def _download_model(self, model_path: Path) -> None:
        """Download the FastText language identification model if not present."""
        model_url = "https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.bin"
        
        logger.info(f"Downloading FastText model from {model_url}")
        logger.info(f"Saving to {model_path}")
        
        try:
            urllib.request.urlretrieve(model_url, model_path)
            logger.info("FastText model downloaded successfully")
        except Exception as e:
            logger.error(f"Failed to download FastText model: {e}")
            raise
    
    def _load_model(self) -> None:
        """Load the FastText model (downloads if necessary)."""
        try:
            model_path = self._get_model_path()
            
            # Download model if it doesn't exist
            if not model_path.exists():
                logger.warning(f"FastText model not found at {model_path}")
                self._download_model(model_path)
            
            logger.info(f"Loading FastText model from {model_path}")
            
            # Suppress FastText warnings
            fasttext.FastText.eprint = lambda x: None
            
            self._model = fasttext.load_model(str(model_path))
            logger.info("FastText model loaded successfully")
            
        except Exception as e:
            logger.error(f"Failed to load FastText model: {e}")
            self._model = None
            raise
    
    def detect_language(
        self, 
        text: str, 
        fallback: str = 'en'
    ) -> Tuple[str, float]:
        """
        Detect the language of the given text.

        Args:
            text: Text to detect language for
            fallback: Language code to use if detection fails

        Returns:
            Tuple of (language_code, confidence)
            language_code: ISO 639-1 code (ko, de, zh, es, fr, en)
            confidence: Detection confidence (0.0 to 1.0)
        """
        logger.info(f"[LANG_DETECT] ===== STARTING LANGUAGE DETECTION =====")
        logger.info(f"[LANG_DETECT] Input text: '{text}'")
        logger.info(f"[LANG_DETECT] Text length: {len(text)} characters")
        logger.info(f"[LANG_DETECT] Fallback language: {fallback}")
        
        if not self._model:
            logger.error("[LANG_DETECT] FastText model not loaded, using fallback")
            return (fallback, 0.0)
        
        if not text or not text.strip():
            logger.warning("Empty text provided, using fallback language")
            return (fallback, 0.0)
        
        try:
            # Quick check for Korean characters first (bypass FastText issues)
            has_korean = any('\uAC00' <= char <= '\uD7AF' for char in text)
            if has_korean:
                logger.info(f"[LANG_DETECT] Korean characters detected directly, bypassing FastText")
                return ('ko', 1.0)

            # Quick check for Chinese characters
            has_chinese = any('\u4E00' <= char <= '\u9FFF' for char in text)
            if has_chinese:
                logger.info(f"[LANG_DETECT] Chinese characters detected directly, bypassing FastText")
                return ('zh', 1.0)

            # Preprocess text: normalize whitespace while preserving text structure
            # Important: Keep original characters intact for non-Latin scripts
            logger.info(f"[LANG_DETECT] Preprocessing text...")
            processed_text = ' '.join(text.strip().split())
            logger.info(f"[LANG_DETECT] After whitespace normalization: '{processed_text}'")

            # If text is too short, try with original to preserve context
            if len(processed_text) < 10:
                logger.info(f"[LANG_DETECT] Text too short ({len(processed_text)} chars), using original")
                processed_text = text.strip()
            
            logger.info(f"[LANG_DETECT] Final processed text: '{processed_text}'")

            # Predict language (k=1 returns top prediction)
            # Suppress NumPy warnings for FastText compatibility
            logger.info(f"[LANG_DETECT] Calling FastText prediction...")
            import warnings
            with warnings.catch_warnings():
                warnings.filterwarnings('ignore', category=FutureWarning)
                warnings.filterwarnings('ignore', category=DeprecationWarning)
                predictions = self._model.predict(processed_text, k=1)
            
            logger.info(f"[LANG_DETECT] FastText prediction completed")
            logger.info(f"[LANG_DETECT] Raw predictions: {predictions}")

            # Extract label and confidence
            label = predictions[0][0]  # e.g., '__label__en'
            confidence = float(predictions[1][0])
            logger.info(f"[LANG_DETECT] Extracted label: {label}")
            logger.info(f"[LANG_DETECT] Extracted confidence: {confidence}")

            # Map FastText label to language code
            logger.info(f"[LANG_DETECT] Mapping label to language code...")
            language_code = self.LANGUAGE_MAP.get(label)
            logger.info(f"[LANG_DETECT] Mapped language code: {language_code}")
            
            if not language_code:
                logger.warning(f"[LANG_DETECT] Unsupported language detected: {label}, using fallback {fallback}")
                logger.warning(f"[LANG_DETECT] Available language mappings: {list(self.LANGUAGE_MAP.keys())}")
                return (fallback, 0.0)

            # Check if language is in supported list
            logger.info(f"[LANG_DETECT] Checking if {language_code} is in supported languages...")
            if language_code not in self.SUPPORTED_LANGUAGES:
                logger.warning(f"[LANG_DETECT] Language {language_code} not in supported list, using fallback {fallback}")
                logger.warning(f"[LANG_DETECT] Supported languages: {self.SUPPORTED_LANGUAGES}")
                return (fallback, confidence)

            # Check confidence threshold
            logger.info(f"[LANG_DETECT] Checking confidence threshold ({self.CONFIDENCE_THRESHOLD})...")
            if confidence < self.CONFIDENCE_THRESHOLD:
                logger.warning(
                    f"[LANG_DETECT] Low confidence ({confidence:.2f}) for language {language_code}, threshold is {self.CONFIDENCE_THRESHOLD}, using fallback {fallback}"
                )
                return (fallback, confidence)
            
            logger.info(f"[LANG_DETECT] ===== LANGUAGE DETECTION SUCCESSFUL =====")
            logger.info(f"[LANG_DETECT] Detected language: {language_code} (confidence: {confidence:.2f})")
            return (language_code, confidence)
            
        except Exception as e:
            logger.error(f"[LANG_DETECT] ===== LANGUAGE DETECTION FAILED =====")
            logger.error(f"[LANG_DETECT] Error type: {type(e).__name__}")
            logger.error(f"[LANG_DETECT] Error message: {str(e)}")
            logger.error(f"[LANG_DETECT] Input text: '{text}'")
            import traceback
            logger.error(f"[LANG_DETECT] Full traceback:\n{traceback.format_exc()}")
            return (fallback, 0.0)
    
    def is_model_loaded(self) -> bool:
        """Check if the FastText model is loaded."""
        return self._model is not None


# Singleton instance
_language_detection_service = None

def get_language_detection_service() -> LanguageDetectionService:
    """Get the singleton language detection service instance."""
    global _language_detection_service
    if _language_detection_service is None:
        _language_detection_service = LanguageDetectionService()
    return _language_detection_service
