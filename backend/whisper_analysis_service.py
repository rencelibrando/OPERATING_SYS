"""
Local Voice Analysis Service using Whisper STT and SpeechBrain Speaker Analysis.
No API keys required - runs entirely locally.
"""
# IMPORTANT: Import sitecustomize first to apply torchaudio compatibility patches
# before any other imports that might trigger SpeechBrain loading
import sitecustomize  # noqa: F401

import os
import tempfile
import numpy as np
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import logging
import torch
import soundfile as sf

try:
    from huggingface_hub.utils._errors import EntryNotFoundError as HfEntryNotFoundError
except Exception:  # huggingface_hub < 0.15.0
    HfEntryNotFoundError = None

logger = logging.getLogger(__name__)

SPEECHBRAIN_MODEL_SOURCE = "speechbrain/spkrec-ecapa-voxceleb"
SPEECHBRAIN_MODEL_DIR = os.path.join(
    "pretrained_models",
    "spkrec-ecapa-voxceleb",
)
SPEECHBRAIN_PYMODULE_CANDIDATES: Tuple[str, ...] = (
    "custom_interface.py",
    "custom.py",
)


class AnalysisModel(Enum):
    """Available analysis models."""
    WHISPER_TINY = "tiny"
    WHISPER_BASE = "base"
    WHISPER_SMALL = "small"
    WHISPER_MEDIUM = "medium"
    WHISPER_LARGE = "large"


@dataclass
class TranscriptionResult:
    """Result from Whisper transcription."""
    text: str
    language: str
    confidence: float
    segments: List[Dict[str, Any]]
    duration: float
    words: List[Dict[str, Any]]


@dataclass
class SpeakerAnalysisResult:
    """Result from speaker analysis."""
    speaker_embedding: Optional[np.ndarray]
    voice_quality_scores: Dict[str, float]
    pronunciation_metrics: Dict[str, float]
    fluency_metrics: Dict[str, float]
    clarity_score: float
    energy_profile: List[float]


@dataclass
class VoiceAnalysisResult:
    """Combined voice analysis result."""
    success: bool
    transcription: Optional[TranscriptionResult]
    speaker_analysis: Optional[SpeakerAnalysisResult]
    overall_score: float
    feedback_messages: List[str]
    suggestions: List[str]
    error_message: Optional[str] = None


class WhisperAnalysisService:
    """
    Local voice analysis service using:
    - OpenAI Whisper for speech-to-text
    - SpeechBrain for speaker embeddings and voice analysis
    """
    
    def __init__(
        self,
        whisper_model: str = "base",
        device: Optional[str] = None
    ):
        """
        Initialize the analysis service.
        
        Args:
            whisper_model: Whisper model size (tiny, base, small, medium, large)
            device: Device to use (cuda, cpu, or None for auto-detect)
        """
        self.whisper_model_name = whisper_model
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        
        self._whisper_model = None
        self._speaker_model = None
        self._verification_model = None
        
        logger.info(f"WhisperAnalysisService initialized with device: {self.device}")
    
    def _load_whisper(self):
        """Lazy load Whisper model."""
        if self._whisper_model is None:
            try:
                import whisper
                logger.info(f"Loading Whisper model: {self.whisper_model_name}")
                self._whisper_model = whisper.load_model(
                    self.whisper_model_name, 
                    device=self.device
                )
                logger.info("Whisper model loaded successfully")
            except Exception as e:
                logger.error(f"Failed to load Whisper model: {e}")
                raise
        return self._whisper_model
    
    def _load_speechbrain_model(self, loader_cls, model_label: str):
        """Load SpeechBrain models with fallback pymodule filenames."""
        last_error: Optional[Exception] = None
        
        for pymodule_file in SPEECHBRAIN_PYMODULE_CANDIDATES:
            try:
                logger.info(
                    "Loading SpeechBrain %s using %s",
                    model_label,
                    pymodule_file,
                )
                model = loader_cls.from_hparams(
                    source=SPEECHBRAIN_MODEL_SOURCE,
                    savedir=SPEECHBRAIN_MODEL_DIR,
                    pymodule_file=pymodule_file,
                    run_opts={"device": self.device},
                )
                logger.info(
                    "SpeechBrain %s loaded successfully via %s",
                    model_label,
                    pymodule_file,
                )
                return model
            except Exception as exc:
                if self._should_retry_with_legacy_pymodule(exc, pymodule_file):
                    logger.warning(
                        "SpeechBrain %s missing %s; retrying with fallback: %s",
                        model_label,
                        pymodule_file,
                        exc,
                    )
                    last_error = exc
                    continue
                logger.error(
                    "Failed to load SpeechBrain %s: %s",
                    model_label,
                    exc,
                )
                raise
        
        logger.error(
            "Unable to load SpeechBrain %s after trying fallbacks",
            model_label,
        )
        if last_error:
            raise last_error
        raise RuntimeError(f"Unable to load SpeechBrain {model_label}")
    
    @staticmethod
    def _should_retry_with_legacy_pymodule(
        exc: Exception, module_filename: str
    ) -> bool:
        """Return True when failure indicates the pymodule file is missing."""
        module_token = module_filename.lower()
        message = str(exc).lower()
        
        if HfEntryNotFoundError is not None and isinstance(
            exc, HfEntryNotFoundError
        ):
            return True
        
        return "not found" in message and module_token in message
    
    def _load_speaker_model(self):
        """Lazy load SpeechBrain speaker embedding model."""
        if self._speaker_model is None:
            try:
                from speechbrain.inference.speaker import EncoderClassifier
                self._speaker_model = self._load_speechbrain_model(
                    EncoderClassifier,
                    "speaker embedding model",
                )
            except Exception as e:
                logger.error(f"Failed to load SpeechBrain speaker model: {e}")
                raise
        return self._speaker_model
    
    def _load_verification_model(self):
        """Lazy load SpeechBrain speaker verification model."""
        if self._verification_model is None:
            try:
                from speechbrain.inference.speaker import SpeakerRecognition
                self._verification_model = self._load_speechbrain_model(
                    SpeakerRecognition,
                    "speaker verification model",
                )
            except Exception as e:
                logger.error(f"Failed to load verification model: {e}")
                self._verification_model = None
        return self._verification_model
    
    async def transcribe_audio(
        self,
        audio_data: bytes,
        language: Optional[str] = None
    ) -> TranscriptionResult:
        """
        Transcribe audio using Whisper.
        
        Args:
            audio_data: Raw audio bytes (WAV format)
            language: Optional language code (e.g., 'en', 'fr', 'ko')
        
        Returns:
            TranscriptionResult with text and metadata
        """
        model = self._load_whisper()
        
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            f.write(audio_data)
            temp_path = f.name
        
        try:
            transcribe_options = {
                "word_timestamps": True,
                "verbose": False
            }
            
            if language:
                transcribe_options["language"] = language
            
            logger.info(f"Transcribing audio with Whisper (language={language})")
            result = model.transcribe(temp_path, **transcribe_options)
            
            segments = []
            words = []
            
            for segment in result.get("segments", []):
                seg_data = {
                    "start": segment.get("start", 0),
                    "end": segment.get("end", 0),
                    "text": segment.get("text", ""),
                    "confidence": segment.get("no_speech_prob", 0)
                }
                segments.append(seg_data)
                
                for word_info in segment.get("words", []):
                    words.append({
                        "word": word_info.get("word", ""),
                        "start": word_info.get("start", 0),
                        "end": word_info.get("end", 0),
                        "probability": word_info.get("probability", 0)
                    })
            
            avg_confidence = 1.0
            if words:
                avg_confidence = sum(w.get("probability", 0) for w in words) / len(words)
            
            duration = segments[-1]["end"] if segments else 0
            
            return TranscriptionResult(
                text=result.get("text", "").strip(),
                language=result.get("language", language or "en"),
                confidence=avg_confidence,
                segments=segments,
                duration=duration,
                words=words
            )
        finally:
            os.unlink(temp_path)
    
    async def analyze_speaker(
        self,
        audio_data: bytes,
        reference_embedding: Optional[np.ndarray] = None
    ) -> SpeakerAnalysisResult:
        """
        Analyze speaker characteristics using SpeechBrain.
        
        Args:
            audio_data: Raw audio bytes (WAV format)
            reference_embedding: Optional reference embedding for comparison
        
        Returns:
            SpeakerAnalysisResult with voice quality metrics
        """
        import torchaudio
        
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            f.write(audio_data)
            temp_path = f.name
        
        try:
            audio_samples, sample_rate = sf.read(
                temp_path,
                dtype="float32",
                always_2d=True,
            )
            waveform = torch.from_numpy(np.ascontiguousarray(audio_samples.T))
            
            if sample_rate != 16000:
                resampler = torchaudio.transforms.Resample(sample_rate, 16000)
                waveform = resampler(waveform)
                sample_rate = 16000
            
            if waveform.shape[0] > 1:
                waveform = torch.mean(waveform, dim=0, keepdim=True)
            
            speaker_model = self._load_speaker_model()
            embedding = speaker_model.encode_batch(waveform)
            speaker_embedding = embedding.squeeze().cpu().numpy()
            
            voice_quality = self._analyze_voice_quality(waveform, sample_rate)
            pronunciation = self._analyze_pronunciation_metrics(waveform, sample_rate)
            fluency = self._analyze_fluency_metrics(waveform, sample_rate)
            clarity = self._calculate_clarity_score(waveform, sample_rate)
            energy_profile = self._calculate_energy_profile(waveform, sample_rate)
            
            return SpeakerAnalysisResult(
                speaker_embedding=speaker_embedding,
                voice_quality_scores=voice_quality,
                pronunciation_metrics=pronunciation,
                fluency_metrics=fluency,
                clarity_score=clarity,
                energy_profile=energy_profile
            )
        finally:
            os.unlink(temp_path)
    
    def _analyze_voice_quality(
        self,
        waveform: torch.Tensor,
        sample_rate: int
    ) -> Dict[str, float]:
        """Analyze voice quality characteristics."""
        audio = waveform.squeeze().numpy()
        
        rms_energy = np.sqrt(np.mean(audio ** 2))
        volume_score = min(100, max(0, rms_energy * 1000))
        
        zero_crossings = np.sum(np.abs(np.diff(np.sign(audio)))) / 2
        zcr = zero_crossings / len(audio)
        
        spectral_centroid = self._calculate_spectral_centroid(audio, sample_rate)
        brightness_score = min(100, max(0, spectral_centroid / 40))
        
        stability = self._calculate_pitch_stability(audio, sample_rate)
        
        return {
            "volume": float(volume_score),
            "brightness": float(brightness_score),
            "stability": float(stability),
            "zero_crossing_rate": float(zcr * 100)
        }
    
    def _analyze_pronunciation_metrics(
        self,
        waveform: torch.Tensor,
        sample_rate: int
    ) -> Dict[str, float]:
        """Analyze pronunciation-related metrics."""
        audio = waveform.squeeze().numpy()
        
        articulation = self._estimate_articulation_clarity(audio, sample_rate)
        vowel_quality = self._estimate_vowel_quality(audio, sample_rate)
        consonant_precision = self._estimate_consonant_precision(audio, sample_rate)
        
        return {
            "articulation": float(articulation),
            "vowel_quality": float(vowel_quality),
            "consonant_precision": float(consonant_precision),
            "overall": float((articulation + vowel_quality + consonant_precision) / 3)
        }
    
    def _analyze_fluency_metrics(
        self,
        waveform: torch.Tensor,
        sample_rate: int
    ) -> Dict[str, float]:
        """Analyze fluency-related metrics."""
        audio = waveform.squeeze().numpy()
        
        pauses = self._detect_pauses(audio, sample_rate)
        speech_rate = self._estimate_speech_rate(audio, sample_rate)
        rhythm_regularity = self._calculate_rhythm_regularity(audio, sample_rate)
        
        pause_penalty = min(50, len(pauses) * 5)
        pause_score = max(0, 100 - pause_penalty)
        
        rate_score = 100 if 2.0 <= speech_rate <= 4.0 else max(0, 100 - abs(speech_rate - 3.0) * 20)
        
        return {
            "pause_score": float(pause_score),
            "speech_rate": float(speech_rate),
            "rate_score": float(rate_score),
            "rhythm": float(rhythm_regularity),
            "pause_count": len(pauses)
        }
    
    def _calculate_clarity_score(
        self,
        waveform: torch.Tensor,
        sample_rate: int
    ) -> float:
        """Calculate overall clarity score."""
        audio = waveform.squeeze().numpy()
        
        snr = self._estimate_snr(audio)
        spectral_flatness = self._calculate_spectral_flatness(audio)
        
        snr_score = min(100, max(0, snr * 5))
        tonality_score = (1 - spectral_flatness) * 100
        
        return float((snr_score + tonality_score) / 2)
    
    def _calculate_energy_profile(
        self,
        waveform: torch.Tensor,
        sample_rate: int,
        num_segments: int = 20
    ) -> List[float]:
        """Calculate energy profile over time."""
        audio = waveform.squeeze().numpy()
        segment_size = len(audio) // num_segments
        
        energy_profile = []
        for i in range(num_segments):
            start = i * segment_size
            end = start + segment_size
            segment = audio[start:end]
            energy = np.sqrt(np.mean(segment ** 2))
            energy_profile.append(float(energy))
        
        max_energy = max(energy_profile) if energy_profile else 1.0
        if max_energy > 0:
            energy_profile = [e / max_energy for e in energy_profile]
        
        return energy_profile
    
    def _calculate_energy_profile_numpy(
        self,
        audio: np.ndarray,
        sample_rate: int,
        num_segments: int = 20
    ) -> List[float]:
        """Calculate energy profile over time from numpy array."""
        segment_size = len(audio) // num_segments
        if segment_size == 0:
            return []
        
        energy_profile = []
        for i in range(num_segments):
            start = i * segment_size
            end = start + segment_size
            segment = audio[start:end]
            energy = np.sqrt(np.mean(segment ** 2))
            energy_profile.append(float(energy))
        
        max_energy = max(energy_profile) if energy_profile else 1.0
        if max_energy > 0:
            energy_profile = [e / max_energy for e in energy_profile]
        
        return energy_profile
    
    def _calculate_spectral_centroid(self, audio: np.ndarray, sample_rate: int) -> float:
        """Calculate spectral centroid."""
        fft = np.abs(np.fft.rfft(audio))
        freqs = np.fft.rfftfreq(len(audio), 1/sample_rate)
        
        if np.sum(fft) == 0:
            return 0.0
        
        centroid = np.sum(freqs * fft) / np.sum(fft)
        return float(centroid)
    
    def _calculate_pitch_stability(self, audio: np.ndarray, sample_rate: int) -> float:
        """Estimate pitch stability."""
        frame_size = int(0.025 * sample_rate)
        hop_size = int(0.010 * sample_rate)
        
        pitches = []
        for i in range(0, len(audio) - frame_size, hop_size):
            frame = audio[i:i + frame_size]
            if np.max(np.abs(frame)) > 0.01:
                autocorr = np.correlate(frame, frame, mode='full')
                autocorr = autocorr[len(autocorr)//2:]
                
                min_period = int(sample_rate / 500)
                max_period = int(sample_rate / 75)
                
                if max_period < len(autocorr):
                    peak_idx = np.argmax(autocorr[min_period:max_period]) + min_period
                    if autocorr[peak_idx] > 0.3 * autocorr[0]:
                        pitch = sample_rate / peak_idx
                        pitches.append(pitch)
        
        if len(pitches) < 2:
            return 80.0
        
        pitch_std = np.std(pitches)
        pitch_mean = np.mean(pitches)
        cv = pitch_std / pitch_mean if pitch_mean > 0 else 0
        
        stability = max(0, 100 - cv * 200)
        return float(stability)
    
    def _estimate_articulation_clarity(self, audio: np.ndarray, sample_rate: int) -> float:
        """Estimate articulation clarity from spectral characteristics."""
        fft = np.abs(np.fft.rfft(audio))
        freqs = np.fft.rfftfreq(len(audio), 1/sample_rate)
        
        speech_band = (freqs >= 300) & (freqs <= 3400)
        high_freq = freqs >= 2000
        
        speech_energy = np.sum(fft[speech_band] ** 2)
        high_energy = np.sum(fft[high_freq] ** 2)
        total_energy = np.sum(fft ** 2)
        
        if total_energy == 0:
            return 50.0
        
        speech_ratio = speech_energy / total_energy
        high_ratio = high_energy / total_energy
        
        articulation = (speech_ratio * 0.7 + high_ratio * 0.3) * 100
        return float(min(100, articulation))
    
    def _estimate_vowel_quality(self, audio: np.ndarray, sample_rate: int) -> float:
        """Estimate vowel quality from formant-like characteristics."""
        fft = np.abs(np.fft.rfft(audio))
        freqs = np.fft.rfftfreq(len(audio), 1/sample_rate)
        
        f1_band = (freqs >= 250) & (freqs <= 900)
        f2_band = (freqs >= 850) & (freqs <= 2500)
        
        f1_energy = np.sum(fft[f1_band] ** 2)
        f2_energy = np.sum(fft[f2_band] ** 2)
        total_energy = np.sum(fft ** 2)
        
        if total_energy == 0:
            return 50.0
        
        formant_ratio = (f1_energy + f2_energy) / total_energy
        return float(min(100, formant_ratio * 150))
    
    def _estimate_consonant_precision(self, audio: np.ndarray, sample_rate: int) -> float:
        """Estimate consonant precision from high-frequency transients."""
        frame_size = int(0.020 * sample_rate)
        hop_size = int(0.010 * sample_rate)
        
        transient_count = 0
        prev_energy = 0
        
        for i in range(0, len(audio) - frame_size, hop_size):
            frame = audio[i:i + frame_size]
            energy = np.sum(frame ** 2)
            
            if prev_energy > 0 and energy / prev_energy > 3.0:
                transient_count += 1
            
            prev_energy = energy if energy > 0.001 else prev_energy
        
        duration = len(audio) / sample_rate
        transient_rate = transient_count / duration if duration > 0 else 0
        
        precision = min(100, 50 + transient_rate * 10)
        return float(precision)
    
    def _detect_pauses(
        self,
        audio: np.ndarray,
        sample_rate: int,
        threshold: float = 0.02,
        min_pause_duration: float = 0.3
    ) -> List[Tuple[float, float]]:
        """Detect pauses in audio."""
        frame_size = int(0.025 * sample_rate)
        hop_size = int(0.010 * sample_rate)
        
        pauses = []
        in_pause = False
        pause_start = 0
        
        for i in range(0, len(audio) - frame_size, hop_size):
            frame = audio[i:i + frame_size]
            energy = np.sqrt(np.mean(frame ** 2))
            time = i / sample_rate
            
            if energy < threshold:
                if not in_pause:
                    in_pause = True
                    pause_start = time
            else:
                if in_pause:
                    pause_duration = time - pause_start
                    if pause_duration >= min_pause_duration:
                        pauses.append((pause_start, time))
                    in_pause = False
        
        return pauses
    
    def _estimate_speech_rate(self, audio: np.ndarray, sample_rate: int) -> float:
        """Estimate speech rate in syllables per second."""
        frame_size = int(0.025 * sample_rate)
        hop_size = int(0.010 * sample_rate)
        
        energies = []
        for i in range(0, len(audio) - frame_size, hop_size):
            frame = audio[i:i + frame_size]
            energies.append(np.sqrt(np.mean(frame ** 2)))
        
        energies = np.array(energies)
        if len(energies) == 0:
            return 0.0
        
        threshold = np.mean(energies) * 0.5
        above_threshold = energies > threshold
        
        syllable_count = 0
        was_above = False
        for is_above in above_threshold:
            if is_above and not was_above:
                syllable_count += 1
            was_above = is_above
        
        duration = len(audio) / sample_rate
        speech_rate = syllable_count / duration if duration > 0 else 0
        
        return float(speech_rate)
    
    def _calculate_rhythm_regularity(self, audio: np.ndarray, sample_rate: int) -> float:
        """Calculate rhythm regularity score."""
        frame_size = int(0.025 * sample_rate)
        hop_size = int(0.010 * sample_rate)
        
        energies = []
        for i in range(0, len(audio) - frame_size, hop_size):
            frame = audio[i:i + frame_size]
            energies.append(np.sqrt(np.mean(frame ** 2)))
        
        if len(energies) < 10:
            return 70.0
        
        energies = np.array(energies)
        diff = np.diff(energies)
        
        regularity = 100 - np.std(diff) * 500
        return float(max(0, min(100, regularity)))
    
    def _estimate_snr(self, audio: np.ndarray) -> float:
        """Estimate signal-to-noise ratio."""
        frame_size = 1024
        
        energies = []
        for i in range(0, len(audio) - frame_size, frame_size):
            frame = audio[i:i + frame_size]
            energies.append(np.sum(frame ** 2))
        
        if len(energies) < 2:
            return 10.0
        
        energies = sorted(energies)
        
        noise_estimate = np.mean(energies[:max(1, len(energies)//10)])
        signal_estimate = np.mean(energies[-max(1, len(energies)//4):])
        
        if noise_estimate == 0:
            return 20.0
        
        snr = 10 * np.log10(signal_estimate / noise_estimate)
        return float(max(0, snr))
    
    def _calculate_spectral_flatness(self, audio: np.ndarray) -> float:
        """Calculate spectral flatness (0 = tonal, 1 = noisy)."""
        fft = np.abs(np.fft.rfft(audio))
        fft = fft[fft > 0]
        
        if len(fft) == 0:
            return 0.5
        
        geometric_mean = np.exp(np.mean(np.log(fft + 1e-10)))
        arithmetic_mean = np.mean(fft)
        
        if arithmetic_mean == 0:
            return 0.5
        
        flatness = geometric_mean / arithmetic_mean
        return float(flatness)
    
    def _create_basic_speaker_analysis(self, audio_data: bytes) -> SpeakerAnalysisResult:
        """
        Create basic speaker analysis using audio signal processing.
        Used as fallback when SpeechBrain models are unavailable.
        """
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
                f.write(audio_data)
                temp_path = f.name
            
            try:
                audio_samples, sample_rate = sf.read(
                    temp_path,
                    dtype="float32",
                    always_2d=True,
                )
                audio = np.mean(audio_samples, axis=1)
                
                if sample_rate != 16000:
                    # Simple resampling
                    audio = np.interp(
                        np.linspace(0, len(audio), int(len(audio) * 16000 / sample_rate)),
                        np.arange(len(audio)),
                        audio
                    )
                    sample_rate = 16000
                
                # Calculate basic metrics from audio
                snr = self._estimate_snr(audio)
                volume = min(100, max(0, snr * 5))  # Scale SNR to volume score
                stability = self._calculate_pitch_stability(audio, sample_rate)
                articulation = self._estimate_articulation_clarity(audio, sample_rate)
                vowel_quality = self._estimate_vowel_quality(audio, sample_rate)
                consonant_precision = self._estimate_consonant_precision(audio, sample_rate)
                
                # Estimate speaking rate
                pauses = self._detect_pauses(audio, sample_rate)
                duration = len(audio) / sample_rate
                speech_duration = duration - sum(p[1] - p[0] for p in pauses)
                
                return SpeakerAnalysisResult(
                    speaker_embedding=None,
                    voice_quality_scores={
                        "volume": volume,
                        "stability": stability,
                        "clarity": articulation,
                        "snr": min(100, snr * 5),  # Scale SNR to 0-100
                    },
                    pronunciation_metrics={
                        "overall": (articulation + vowel_quality + consonant_precision) / 3,
                        "articulation": articulation,
                        "vowel_quality": vowel_quality,
                        "consonant_precision": consonant_precision,
                    },
                    fluency_metrics={
                        "rate_score": 70.0,  # Default reasonable score
                        "pause_score": min(100, max(0, 100 - len(pauses) * 10)),
                        "rhythm": stability * 0.8,
                    },
                    clarity_score=articulation,
                    energy_profile=self._calculate_energy_profile_numpy(audio, sample_rate),
                )
            finally:
                os.unlink(temp_path)
                
        except Exception as e:
            logger.error(f"Basic speaker analysis failed: {e}")
            # Return minimal default analysis
            return SpeakerAnalysisResult(
                speaker_embedding=None,
                voice_quality_scores={"volume": 70, "stability": 70, "clarity": 70},
                pronunciation_metrics={"overall": 70, "articulation": 70},
                fluency_metrics={"rate_score": 70, "pause_score": 70, "rhythm": 70},
                clarity_score=70.0,
                energy_profile=[],
            )
    
    async def analyze_voice(
        self,
        audio_data: bytes,
        language: Optional[str] = None,
        expected_text: Optional[str] = None,
        level: str = "intermediate",
        scenario: str = "daily_conversation"
    ) -> VoiceAnalysisResult:
        """
        Perform complete voice analysis.
        
        Args:
            audio_data: Raw audio bytes (WAV format)
            language: Optional language code
            expected_text: Expected text for comparison
            level: Proficiency level
            scenario: Practice scenario
        
        Returns:
            Complete VoiceAnalysisResult
        """
        try:
            logger.info("Starting voice analysis")
            
            transcription = await self.transcribe_audio(audio_data, language)
            logger.info(f"Transcription complete: {transcription.text[:50]}...")
            
            # Speaker analysis is optional - continue if it fails
            speaker_analysis = None
            try:
                speaker_analysis = await self.analyze_speaker(audio_data)
                logger.info("Speaker analysis complete")
            except Exception as speaker_err:
                logger.warning(f"Speaker analysis failed (non-critical): {speaker_err}")
                # Create default speaker analysis with basic metrics from audio
                speaker_analysis = self._create_basic_speaker_analysis(audio_data)
            
            overall_score = self._calculate_overall_score(
                transcription,
                speaker_analysis,
                expected_text
            )
            
            feedback_messages, suggestions = self._generate_feedback(
                transcription,
                speaker_analysis,
                expected_text,
                level,
                scenario
            )
            
            return VoiceAnalysisResult(
                success=True,
                transcription=transcription,
                speaker_analysis=speaker_analysis,
                overall_score=overall_score,
                feedback_messages=feedback_messages,
                suggestions=suggestions
            )
            
        except Exception as e:
            error_str = str(e)
            logger.error(f"Voice analysis failed: {e}")
            
            # Check for FFmpeg-related errors
            feedback = ["Analysis failed. Please try again."]
            suggestions = ["Ensure your audio is clear and try recording again."]
            
            if "WinError 2" in error_str or "No such file or directory" in error_str or "ffmpeg" in error_str.lower():
                feedback = ["Analysis failed: FFmpeg is not available."]
                suggestions = [
                    "FFmpeg is required for voice analysis.",
                    "If FFmpeg was just installed, restart the backend server.",
                    "Steps: 1) Stop the server, 2) Close terminal, 3) Open new terminal, 4) Start server again."
                ]
                error_str = f"FFmpeg not found. {error_str}. Please restart the backend server after installing FFmpeg."
            
            return VoiceAnalysisResult(
                success=False,
                transcription=None,
                speaker_analysis=None,
                overall_score=0.0,
                feedback_messages=feedback,
                suggestions=suggestions,
                error_message=error_str
            )
    
    def _calculate_overall_score(
        self,
        transcription: TranscriptionResult,
        speaker_analysis: SpeakerAnalysisResult,
        expected_text: Optional[str]
    ) -> float:
        """Calculate overall score from all metrics."""
        scores = []
        
        scores.append(transcription.confidence * 100)
        
        pronunciation = speaker_analysis.pronunciation_metrics.get("overall", 70)
        scores.append(pronunciation)
        
        fluency_score = (
            speaker_analysis.fluency_metrics.get("rate_score", 70) * 0.4 +
            speaker_analysis.fluency_metrics.get("pause_score", 70) * 0.3 +
            speaker_analysis.fluency_metrics.get("rhythm", 70) * 0.3
        )
        scores.append(fluency_score)
        
        scores.append(speaker_analysis.clarity_score)
        
        if expected_text:
            accuracy = self._calculate_text_accuracy(transcription.text, expected_text)
            scores.append(accuracy)
        
        overall = sum(scores) / len(scores)
        return float(min(100, max(0, overall)))
    
    def _calculate_text_accuracy(self, actual: str, expected: str) -> float:
        """Calculate text accuracy using simple word matching."""
        actual_words = set(actual.lower().split())
        expected_words = set(expected.lower().split())
        
        if not expected_words:
            return 100.0
        
        matching = len(actual_words & expected_words)
        accuracy = (matching / len(expected_words)) * 100
        
        return float(min(100, accuracy))
    
    def _generate_feedback(
        self,
        transcription: TranscriptionResult,
        speaker_analysis: SpeakerAnalysisResult,
        expected_text: Optional[str],
        level: str,
        scenario: str
    ) -> Tuple[List[str], List[str]]:
        """Generate feedback messages and suggestions."""
        messages = []
        suggestions = []
        
        if transcription.confidence >= 0.8:
            messages.append("Excellent clarity! Your speech was very clear and easy to understand.")
        elif transcription.confidence >= 0.6:
            messages.append("Good clarity. Your speech was understandable with minor areas for improvement.")
        else:
            messages.append("Speech clarity could be improved. Try speaking more clearly and at a moderate pace.")
        
        pronunciation = speaker_analysis.pronunciation_metrics
        if pronunciation.get("overall", 0) >= 80:
            messages.append("Your pronunciation is very good!")
        elif pronunciation.get("overall", 0) >= 60:
            messages.append("Pronunciation is acceptable with room for improvement.")
            suggestions.append("Practice individual sounds that are challenging for you.")
        else:
            messages.append("Focus on pronunciation practice to improve clarity.")
            suggestions.append("Listen to native speakers and practice mimicking their pronunciation.")
        
        fluency = speaker_analysis.fluency_metrics
        pause_count = fluency.get("pause_count", 0)
        if pause_count <= 2:
            messages.append("Great fluency! You spoke smoothly with natural flow.")
        elif pause_count <= 5:
            messages.append("Good fluency with some hesitations.")
            suggestions.append("Practice speaking in longer phrases to reduce pauses.")
        else:
            messages.append("Try to reduce pauses between words for better fluency.")
            suggestions.append("Practice reading aloud to improve your speaking flow.")
        
        speech_rate = fluency.get("speech_rate", 0)
        if speech_rate < 2.0:
            suggestions.append("Try speaking a bit faster for more natural delivery.")
        elif speech_rate > 4.5:
            suggestions.append("Consider slowing down slightly for clearer pronunciation.")
        
        if expected_text:
            accuracy = self._calculate_text_accuracy(transcription.text, expected_text)
            if accuracy >= 90:
                messages.append("Excellent! You matched the expected text very well.")
            elif accuracy >= 70:
                messages.append("Good job! Most of the content was correct.")
            else:
                messages.append("Practice the target phrase more to improve accuracy.")
                suggestions.append(f"The expected text was: '{expected_text}'")
        
        if speaker_analysis.clarity_score < 60:
            suggestions.append("Try recording in a quieter environment for better audio quality.")
        
        return messages, suggestions
    
    def compare_speakers(
        self,
        embedding1: np.ndarray,
        embedding2: np.ndarray
    ) -> float:
        """
        Compare two speaker embeddings.
        
        Args:
            embedding1: First speaker embedding
            embedding2: Second speaker embedding
        
        Returns:
            Similarity score (0-1)
        """
        embedding1 = embedding1 / np.linalg.norm(embedding1)
        embedding2 = embedding2 / np.linalg.norm(embedding2)
        
        similarity = np.dot(embedding1, embedding2)
        return float(max(0, min(1, (similarity + 1) / 2)))
    
    def cleanup(self):
        """Clean up resources."""
        self._whisper_model = None
        self._speaker_model = None
        self._verification_model = None
        
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        
        logger.info("WhisperAnalysisService cleaned up")


whisper_analysis_service = WhisperAnalysisService(whisper_model="base")
