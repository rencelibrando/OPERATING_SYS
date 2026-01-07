import argparse
import subprocess
import tempfile
import wave
import time
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple, Optional

import numpy as np

try:
    import pyaudio
    PYAUDIO_AVAILABLE = True
except ImportError:
    PYAUDIO_AVAILABLE = False


@dataclass
class AudioSummary:
    path: Path
    sample_rate: int
    duration: float
    rms_energy: float
    frames: np.ndarray
    mfccs: np.ndarray
    pitch_track: np.ndarray
    formats: np.ndarray


def _needs_conversion(path: Path) -> bool:
    """Check if an audio file needs conversion to WAV PCM format."""
    with path.open("rb") as fh:
        header = fh.read(4)
    return header != b"RIFF"


@contextmanager
def ensure_wav_pcm(path: Path) -> Path:
    """Convert audio to WAV PCM format if needed."""
    if not _needs_conversion(path):
        yield path
        return

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_path = Path(tmp.name)
    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        str(path),
        "-ac",
        "1",
        "-ar",
        "16000",  # Standard for speech
        str(tmp_path),
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=False)
    except FileNotFoundError as exc:
        tmp_path.unlink(missing_ok=True)
        raise RuntimeError(
            "ffmpeg is required to decode non-WAV audio. Install it and try again."
        ) from exc
    if result.returncode != 0:
        tmp_path.unlink(missing_ok=True)
        raise RuntimeError(f"ffmpeg failed to convert audio: {result.stderr.strip()}")
    try:
        yield tmp_path
    finally:
        tmp_path.unlink(missing_ok=True)


def detect_speech_boundaries(signal: np.ndarray, sample_rate: int, 
                           energy_threshold: float = 0.02, 
                           min_silence_duration: float = 0.3) -> Tuple[int, int]:
    """
    Detect the start and end of speech in an audio signal.
    Returns (start_sample, end_sample).
    """
    # Calculate energy in small windows
    frame_size = int(0.02 * sample_rate)  # 20ms frames
    hop_size = int(0.01 * sample_rate)    # 10ms hop
    
    energy = []
    for i in range(0, len(signal) - frame_size, hop_size):
        frame = signal[i:i + frame_size]
        frame_energy = np.sqrt(np.mean(frame ** 2))
        energy.append(frame_energy)
    
    energy = np.array(energy)
    
    # Find frames above a threshold
    speech_frames = energy > energy_threshold
    
    if not np.any(speech_frames):
        # No speech detected, return full signal
        return 0, len(signal)
    
    # Find the first speech frame
    first_speech = np.argmax(speech_frames)
    
    # Find last speech frame
    last_speech = len(speech_frames) - 1 - np.argmax(speech_frames[::-1])
    
    # Convert frame indices to sample indices
    start_sample = max(0, first_speech * hop_size - frame_size)
    end_sample = min(len(signal), (last_speech + 1) * hop_size + frame_size)
    
    return start_sample, end_sample


def trim_silence(signal: np.ndarray, sample_rate: int) -> np.ndarray:
    """Trim silence from the beginning and end of the audio signal."""
    start, end = detect_speech_boundaries(signal, sample_rate)
    return signal[start:end]


def record_audio(duration: float, sample_rate: int = 16000, output_path: Optional[Path] = None) -> Path:
    """Record audio from a microphone and save to a WAV file."""
    if not PYAUDIO_AVAILABLE:
        raise RuntimeError(
            "PyAudio is required for microphone recording.\n"
            "Install it with: pip install pyaudio\n"
            "On Windows: pip install pyaudio\n"
            "On macOS: brew install portaudio && pip install pyaudio\n"
            "On Linux: sudo apt-get install portaudio19-dev && pip install pyaudio"
        )
    
    if output_path is None:
        output_path = Path(tempfile.mktemp(suffix=".wav"))
    
    chunk = 1024
    format = pyaudio.paInt16
    channels = 1
    
    print(f"\n🎤 Recording for {duration} seconds...")
    print("   Speak now!")
    
    p = pyaudio.PyAudio()
    
    try:
        stream = p.open(
            format=format,
            channels=channels,
            rate=sample_rate,
            input=True,
            frames_per_buffer=chunk
        )
        
        frames = []
        num_chunks = int(sample_rate / chunk * duration)
        
        # Show countdown
        for i in range(num_chunks):
            data = stream.read(chunk)
            frames.append(data)
            
            # Show progress
            elapsed = (i + 1) * chunk / sample_rate
            remaining = duration - elapsed
            if int(elapsed) != int(elapsed - chunk / sample_rate):
                print(f"   {remaining:.0f}s remaining...", end='\r')
        
        print("\n✓ Recording complete!       ")
        
        stream.stop_stream()
        stream.close()
        
    finally:
        p.terminate()
    
    # Save to WAV file
    with wave.open(str(output_path), 'wb') as wf:
        wf.setnchannels(channels)
        wf.setsampwidth(p.get_sample_size(format))
        wf.setframerate(sample_rate)
        wf.writeframes(b''.join(frames))
    
    return output_path


def play_audio(path: Path):
    """Play audio file (requires ffplay, part of ffmpeg)."""
    try:
        subprocess.run(
            ["ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", str(path)],
            check=False
        )
    except FileNotFoundError:
        print("   (Install ffmpeg to enable audio playback)")


def read_wav(path: Path) -> Tuple[np.ndarray, int]:
    """Read the WAV file and return normalized audio signal and sample rate."""
    with ensure_wav_pcm(path) as wav_path:
        with wave.open(str(wav_path), "rb") as wav_file:
            sample_rate = wav_file.getframerate()
            channels = wav_file.getnchannels()
            sample_width = wav_file.getsampwidth()
            n_frames = wav_file.getnframes()
            raw = wav_file.readframes(n_frames)

    dtype_map = {1: np.int8, 2: np.int16, 4: np.int32}
    if sample_width not in dtype_map:
        raise ValueError(f"Unsupported sample width: {sample_width}")

    data = np.frombuffer(raw, dtype=dtype_map[sample_width]).astype(np.float32)
    if channels > 1:
        data = data.reshape(-1, channels).mean(axis=1)

    # Normalize to [-1, 1]
    max_val = float(np.iinfo(dtype_map[sample_width]).max)
    data = data / max_val
    return data, sample_rate


def frame_signal(signal: np.ndarray, frame_size: int, hop_size: int) -> np.ndarray:
    """Split a signal into overlapping frames."""
    frames = []
    for start in range(0, len(signal) - frame_size + 1, hop_size):
        frames.append(signal[start : start + frame_size])
    if not frames:
        frames.append(np.pad(signal, (0, frame_size - len(signal))))
    return np.vstack(frames)


def extract_mfccs(signal: np.ndarray, sample_rate: int, n_mfcc: int = 13) -> np.ndarray:
    """Extract MFCCs (Mel-Frequency Cepstral Coefficients) for speech analysis."""
    frame_size = 512  # ~32ms at 16kHz
    hop_size = 160    # ~10ms at 16kHz
    n_fft = 512
    n_mels = 40
    
    frames = frame_signal(signal, frame_size, hop_size)
    window = np.hamming(frame_size)
    
    # Create mel filterbank
    mel_filters = create_mel_filterbank(n_mels, n_fft, sample_rate)
    
    mfccs = []
    for frame in frames:
        # Apply a window and FFT
        windowed = frame * window
        spectrum = np.abs(np.fft.rfft(windowed, n=n_fft))
        power_spectrum = spectrum ** 2
        
        # Apply mel filters
        mel_spectrum = np.dot(mel_filters, power_spectrum)
        mel_spectrum = np.where(mel_spectrum == 0, np.finfo(float).eps, mel_spectrum)
        
        # Log and DCT
        log_mel = np.log(mel_spectrum)
        mfcc = dct(log_mel)[:n_mfcc]
        mfccs.append(mfcc)
    
    mfccs = np.array(mfccs, dtype=np.float32)
    
    # Normalize MFCCs: mean normalization per coefficient
    # This is critical for comparing different recordings
    mean = np.mean(mfccs, axis=0, keepdims=True)
    std = np.std(mfccs, axis=0, keepdims=True) + 1e-9
    mfccs = (mfccs - mean) / std
    
    return mfccs


def create_mel_filterbank(n_mels: int, n_fft: int, sample_rate: int) -> np.ndarray:
    """Create mel-scale filterbank."""
    def hz_to_mel(hz):
        return 2595 * np.log10(1 + hz / 700)
    
    def mel_to_hz(mel):
        return 700 * (10 ** (mel / 2595) - 1)
    
    min_mel = hz_to_mel(0)
    max_mel = hz_to_mel(sample_rate / 2)
    mel_points = np.linspace(min_mel, max_mel, n_mels + 2)
    hz_points = mel_to_hz(mel_points)
    bin_points = np.floor((n_fft + 1) * hz_points / sample_rate).astype(int)
    
    filters = np.zeros((n_mels, n_fft // 2 + 1))
    for i in range(n_mels):
        left = bin_points[i]
        center = bin_points[i + 1]
        right = bin_points[i + 2]
        
        for j in range(left, center):
            filters[i, j] = (j - left) / (center - left)
        for j in range(center, right):
            filters[i, j] = (right - j) / (right - center)
    
    return filters


def dct(x: np.ndarray) -> np.ndarray:
    """Discrete Cosine Transform (Type-II)."""
    N = len(x)
    result = np.zeros(N)
    for k in range(N):
        sum_val = 0
        for n in range(N):
            sum_val += x[n] * np.cos(np.pi * k * (2 * n + 1) / (2 * N))
        result[k] = sum_val
    return result


def extract_pitch(signal: np.ndarray, sample_rate: int) -> np.ndarray:
    """Extract pitch using the autocorrelation method."""
    frame_size = 2048
    hop_size = 512
    frames = frame_signal(signal, frame_size, hop_size)
    
    pitch_values = []
    for frame in frames:
        # Autocorrelation
        correlation = np.correlate(frame, frame, mode='full')
        correlation = correlation[len(correlation) // 2:]
        
        # Find peaks in valid pitch range (80-400 Hz)
        min_period = int(sample_rate / 400)
        max_period = int(sample_rate / 80)
        
        if max_period < len(correlation):
            valid_correlation = correlation[min_period:max_period]
            if len(valid_correlation) > 0:
                peak = np.argmax(valid_correlation) + min_period
                pitch = sample_rate / peak
            else:
                pitch = 0.0
        else:
            pitch = 0.0
        
        pitch_values.append(pitch)
    
    return np.array(pitch_values, dtype=np.float32)


def extract_formants(signal: np.ndarray, sample_rate: int) -> np.ndarray:
    """Extract the first 3 formants using LPC (Linear Predictive Coding)."""
    frame_size = 512
    hop_size = 160
    frames = frame_signal(signal, frame_size, hop_size)
    
    formant_tracks = []
    for frame in frames:
        # Simple formant estimation using spectral peaks
        window = np.hamming(len(frame))
        windowed = frame * window
        spectrum = np.abs(np.fft.rfft(windowed))
        
        # Find peaks in the spectrum
        freqs = np.fft.rfftfreq(len(frame), d=1.0 / sample_rate)
        
        # Smooth spectrum
        from scipy.ndimage import gaussian_filter1d
        smoothed = gaussian_filter1d(spectrum, sigma=5)
        
        # Find local maxima in frequency ranges typical for F1, F2, F3
        f1 = find_peak_in_range(freqs, smoothed, 200, 900)
        f2 = find_peak_in_range(freqs, smoothed, 900, 2500)
        f3 = find_peak_in_range(freqs, smoothed, 2500, 3500)
        
        formant_tracks.append([f1, f2, f3])
    
    return np.array(formant_tracks, dtype=np.float32)


def find_peak_in_range(freqs: np.ndarray, spectrum: np.ndarray, 
                       min_freq: float, max_freq: float) -> float:
    """Find the frequency of the maximum peak in a given range."""
    mask = (freqs >= min_freq) & (freqs <= max_freq)
    if not np.any(mask):
        return 0.0
    
    masked_spectrum = spectrum[mask]
    masked_freqs = freqs[mask]
    
    if len(masked_spectrum) == 0:
        return 0.0
    
    peak_idx = np.argmax(masked_spectrum)
    return float(masked_freqs[peak_idx])


def summarize_audio(path: Path, trim_silence_flag: bool = False) -> AudioSummary:
    """Extract comprehensive audio features for pronunciation analysis."""
    signal, sr = read_wav(path)
    
    # Optionally trim silence
    if trim_silence_flag:
        signal = trim_silence(signal, sr)
    
    duration = len(signal) / sr
    rms_energy = float(np.sqrt(np.mean(signal**2)))
    
    mfccs = extract_mfccs(signal, sr)
    pitch_track = extract_pitch(signal, sr)
    
    # Formant extraction requires scipy, provide fallback
    try:
        formants = extract_formants(signal, sr)
    except ImportError:
        # Fallback: create dummy formants
        n_frames = len(mfccs)
        formants = np.zeros((n_frames, 3), dtype=np.float32)
    
    return AudioSummary(
        path=path,
        sample_rate=sr,
        duration=duration,
        rms_energy=rms_energy,
        frames=signal,
        mfccs=mfccs,
        pitch_track=pitch_track,
        formants=formants,
    )


def dtw_distance(ref: np.ndarray, attempt: np.ndarray) -> float:
    """Compute Dynamic Time Warping distance between feature sequences."""
    n, m = len(ref), len(attempt)
    cost = np.full((n + 1, m + 1), np.inf, dtype=np.float32)
    cost[0, 0] = 0.0
    
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            frame_dist = np.linalg.norm(ref[i - 1] - attempt[j - 1])
            cost[i, j] = frame_dist + min(
                cost[i - 1, j],      # Deletion
                cost[i, j - 1],      # Insertion
                cost[i - 1, j - 1]   # Match
            )
    
    # Normalize by path length
    return float(cost[n, m] / (n + m))


def compare_pronunciation(reference: AudioSummary, attempt: AudioSummary) -> Dict[str, float]:
    """Compare pronunciation features between reference and attempt."""
    
    # 1. MFCC similarity using multiple methods
    # Method A: DTW for temporal alignment
    mfcc_dtw = dtw_distance(reference.mfccs, attempt.mfccs)
    dtw_similarity = np.exp(-mfcc_dtw)
    
    # Method B: Cosine similarity (for identical files, should be ~1.0)
    ref_flat = reference.mfccs.flatten()
    att_flat = attempt.mfccs.flatten()
    
    # Truncate to the same length
    min_len = min(len(ref_flat), len(att_flat))
    ref_flat = ref_flat[:min_len]
    att_flat = att_flat[:min_len]
    
    cosine_sim = np.dot(ref_flat, att_flat) / (
        np.linalg.norm(ref_flat) * np.linalg.norm(att_flat) + 1e-9
    )
    cosine_sim = (cosine_sim + 1) / 2  # Convert from [-1,1] to [0,1]
    
    # Combine both methods: use the better of the two
    mfcc_similarity = max(dtw_similarity, cosine_sim)
    
    # 2. Pitch contour similarity (intonation)
    ref_pitch = reference.pitch_track[reference.pitch_track > 0]
    att_pitch = attempt.pitch_track[attempt.pitch_track > 0]
    
    if len(ref_pitch) > 0 and len(att_pitch) > 0:
        # Normalize pitch to compare contours, not absolute pitch
        ref_pitch_norm = (ref_pitch - np.mean(ref_pitch)) / (np.std(ref_pitch) + 1e-9)
        att_pitch_norm = (att_pitch - np.mean(att_pitch)) / (np.std(att_pitch) + 1e-9)
        
        # Align lengths
        min_len = min(len(ref_pitch_norm), len(att_pitch_norm))
        if min_len > 0:
            pitch_diff = np.mean(np.abs(ref_pitch_norm[:min_len] - att_pitch_norm[:min_len]))
            pitch_similarity = np.exp(-pitch_diff)  # Exponential decay
        else:
            pitch_similarity = 1.0
    else:
        pitch_similarity = 1.0  # If no pitch detected, don't penalize
    
    # 3. Duration comparison (less important now)
    duration_ratio = min(attempt.duration, reference.duration) / max(attempt.duration, reference.duration, 1e-6)
    
    # 4. Energy comparison
    energy_ratio = min(attempt.rms_energy, reference.rms_energy) / max(attempt.rms_energy, reference.rms_energy, 1e-9)
    
    # 5. Overall pronunciation score (weighted combination)
    # Reduced duration weight since we're trimming silence
    pronunciation_score = (
        0.70 * mfcc_similarity +      # MFCC similarity (increased weight)
        0.20 * pitch_similarity +      # Pitch/intonation
        0.05 * duration_ratio +        # Timing (reduced weight)
        0.05 * energy_ratio            # Volume
    )
    pronunciation_score = max(0.0, min(1.0, pronunciation_score))
    
    return {
        "mfcc_dtw_distance": mfcc_dtw,
        "mfcc_cosine_similarity": cosine_sim,
        "mfcc_similarity": mfcc_similarity,
        "pitch_similarity": pitch_similarity,
        "duration_ratio": duration_ratio,
        "energy_ratio": energy_ratio,
        "pronunciation_score": pronunciation_score,
        "duration_gap_s": abs(reference.duration - attempt.duration),
    }


def generate_feedback(metrics: Dict[str, float], reference: AudioSummary, attempt: AudioSummary) -> List[str]:
    """Generate specific pronunciation feedback based on metrics."""
    feedback = []
    score = metrics["pronunciation_score"]
    
    # Overall assessment
    if score >= 0.85:
        feedback.append("✓ Excellent pronunciation! Very close to native pronunciation.")
    elif score >= 0.70:
        feedback.append("✓ Good pronunciation! Minor improvements possible.")
    elif score >= 0.50:
        feedback.append("⚠ Fair pronunciation. Review the areas below.")
    else:
        feedback.append("✗ Needs improvement. Focus on matching the reference more closely.")
    
    # Specific issues
    if metrics["mfcc_similarity"] < 0.6:
        feedback.append("• Vowel and consonant sounds differ significantly from reference")
        feedback.append("  → Listen carefully and try to mimic the exact sounds")
    
    if metrics["pitch_similarity"] < 0.7:
        feedback.append("• Intonation pattern doesn't match the reference")
        feedback.append("  → Pay attention to rising and falling pitch (melody of speech)")
    
    # Less strict on duration since we're allowing speaking time
    if metrics["duration_ratio"] < 0.5:
        if attempt.duration > reference.duration * 1.5:
            feedback.append("• Speaking significantly slower than reference")
            feedback.append("  → Try to match the reference pace more closely")
        elif attempt.duration < reference.duration * 0.5:
            feedback.append("• Speaking much faster than reference")
            feedback.append("  → Slow down and enunciate more clearly")
    
    if metrics["energy_ratio"] < 0.7:
        if attempt.rms_energy < reference.rms_energy:
            feedback.append("• Speaking too softly")
            feedback.append("  → Increase volume and projection")
        else:
            feedback.append("• Speaking too loudly")
            feedback.append("  → Reduce volume to match reference")
    
    return feedback


def describe(summary: AudioSummary) -> str:
    """Generate human-readable description of audio summary."""
    return (
        f"{summary.path.name}\n"
        f"  Sample rate: {summary.sample_rate} Hz\n"
        f"  Duration: {summary.duration:.2f}s\n"
        f"  RMS energy: {summary.rms_energy:.4f}\n"
        f"  MFCC frames: {len(summary.mfccs)}\n"
        f"  Avg pitch: {np.mean(summary.pitch_track[summary.pitch_track > 0]):.1f} Hz"
    )


def run_cli():
    """Main CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Compare pronunciation against a reference audio sample using advanced speech analysis."
    )
    parser.add_argument("--reference", type=Path, required=True, 
                       help="Reference audio file (e.g., from gTTS)")
    parser.add_argument("--attempt", type=Path, required=False, 
                       help="User's pronunciation attempt (if not provided, will record from mic)")
    parser.add_argument("--record", action="store_true",
                       help="Record from microphone (ignores --attempt)")
    parser.add_argument("--duration", type=float, default=None,
                       help="Recording duration in seconds (default: 3x reference duration)")
    parser.add_argument("--duration-multiplier", type=float, default=3.0,
                       help="Recording time multiplier (default: 3.0x reference duration)")
    parser.add_argument("--save-recording", type=Path, default=Path("user.wav"),
                       help="Save the recording to this file (default: user.wav)")
    parser.add_argument("--play-reference", action="store_true",
                       help="Play the reference audio before recording")
    args = parser.parse_args()

    if not args.reference.exists():
        raise FileNotFoundError(f"Reference file not found: {args.reference}")
    
    print("="*60)
    print("PRONUNCIATION TRAINER")
    print("="*60)
    
    print("\n📖 Analyzing reference audio...")
    ref_summary = summarize_audio(args.reference, trim_silence_flag=False)
    print(f"   Reference duration: {ref_summary.duration:.1f}s")
    
    # Determine if we need to record
    should_record = args.record or args.attempt is None
    
    if should_record:
        # Play reference if requested
        if args.play_reference:
            print("\n🔊 Playing reference audio...")
            play_audio(args.reference)
            print("\n   Listen carefully and get ready to repeat it!")
            time.sleep(1.5)
        
        # Determine recording duration - give the user plenty of time
        if args.duration:
            record_duration = args.duration
        else:
            # Multiply reference duration to give time for preparation
            record_duration = ref_summary.duration * args.duration_multiplier
        
        print(f"\n   You'll have {record_duration:.1f}s to speak.")
        print("   Take your time - silence will be trimmed automatically.")
        time.sleep(1)
        
        # Record from microphone
        temp_recording = args.save_recording
        attempt_path = record_audio(record_duration, output_path=temp_recording)
        
        print(f"💾 Recording saved to: {args.save_recording}")
    else:
        # Use the provided attempt file
        if not args.attempt.exists():
            raise FileNotFoundError(f"Attempt file not found: {args.attempt}")
        attempt_path = args.attempt
    
    print("\n🔍 Analyzing your pronunciation...")
    print("   Trimming silence...")
    attempt_summary = summarize_audio(attempt_path, trim_silence_flag=True)
    print(f"   Your speech duration (after trimming): {attempt_summary.duration:.1f}s")
    
    print("\n" + "="*60)
    print("RESULTS")
    print("="*60)
    
    metrics = compare_pronunciation(ref_summary, attempt_summary)
    
    print(f"\n📊 Pronunciation Score: {metrics['pronunciation_score']:.1%}")
    print(f"   MFCC Similarity: {metrics['mfcc_similarity']:.1%} (DTW: {metrics['mfcc_dtw_distance']:.3f}, Cosine: {metrics['mfcc_cosine_similarity']:.1%})")
    print(f"   Pitch Similarity: {metrics['pitch_similarity']:.1%}")
    print(f"   Duration Match: {metrics['duration_ratio']:.1%}")
    print(f"   Energy Match: {metrics['energy_ratio']:.1%}")
    
    print("\n💬 Feedback:")
    feedback = generate_feedback(metrics, ref_summary, attempt_summary)
    for line in feedback:
        print(f"   {line}")
    
    print("\n" + "="*60)
    
    # Note: recordings are always saved now, no cleanup needed


if __name__ == "__main__":
    run_cli()