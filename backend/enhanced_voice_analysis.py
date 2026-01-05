"""
Enhanced Voice Analysis Service
Provides detailed pronunciation, fluency, pace, and confidence analysis
"""
from typing import Dict, List, Any, Optional
from dataclasses import dataclass
import statistics


@dataclass
class WordTiming:
    word: str
    start: float
    end: float
    confidence: float
    duration: float


@dataclass
class VoiceAnalysis:
    # Pronunciation metrics
    pronunciation_score: float  # 0-100
    average_word_confidence: float  # 0-1
    low_confidence_words: List[str]

    # Fluency metrics
    fluency_score: float  # 0-100
    words_per_minute: float
    speaking_rate_category: str  # slow, normal, fast

    # Pace and pauses
    pace_score: float  # 0-100
    total_pauses: int
    average_pause_duration: float
    long_pause_count: int  # Pauses > 1 second
    pause_locations: List[Dict[str, float]]

    # Confidence metrics
    overall_confidence: float  # 0-100
    confidence_category: str  # low, medium, high

    # Detailed feedback
    strengths: List[str]
    areas_for_improvement: List[str]
    specific_tips: List[str]


class EnhancedVoiceAnalyzer:
    """Analyzes voice recordings with detailed metrics."""

    # Speaking rate thresholds (words per minute)
    SLOW_WPM = 100
    NORMAL_WPM_MIN = 120
    NORMAL_WPM_MAX = 160
    FAST_WPM = 180

    # Pause analysis thresholds (seconds)
    SHORT_PAUSE = 0.3
    NORMAL_PAUSE = 0.6
    LONG_PAUSE = 1.0

    # Confidence thresholds
    LOW_CONFIDENCE = 0.6
    MEDIUM_CONFIDENCE = 0.8

    def __init__(self):
        print("[EnhancedAnalyzer] Initialized")

    def analyze_transcription(
        self,
        words: List[Dict[str, Any]],
        total_duration: float,
        transcript: str
    ) -> VoiceAnalysis:
        """
        Perform comprehensive voice analysis.

        Args:
            words: List of word dictionaries from Deepgram (word, start, end, confidence)
            total_duration: Total audio duration in seconds
            transcript: Full transcript text

        Returns:
            VoiceAnalysis object with detailed metrics
        """
        print(f"[EnhancedAnalyzer] Analyzing {len(words)} words, duration={total_duration:.2f}s")

        # Parse word timings
        word_timings = self._parse_word_timings(words)

        # Calculate pronunciation metrics
        pronunciation_metrics = self._analyze_pronunciation(word_timings)

        # Calculate fluency metrics
        fluency_metrics = self._analyze_fluency(word_timings, total_duration)

        # Calculate pace and pause metrics
        pace_metrics = self._analyze_pace_and_pauses(word_timings, total_duration)

        # Calculate overall confidence
        confidence_metrics = self._analyze_confidence(
            pronunciation_metrics,
            fluency_metrics,
            pace_metrics
        )

        # Generate detailed feedback
        feedback = self._generate_feedback(
            pronunciation_metrics,
            fluency_metrics,
            pace_metrics,
            confidence_metrics
        )

        return VoiceAnalysis(
            pronunciation_score=pronunciation_metrics['score'],
            average_word_confidence=pronunciation_metrics['avg_confidence'],
            low_confidence_words=pronunciation_metrics['low_confidence_words'],
            fluency_score=fluency_metrics['score'],
            words_per_minute=fluency_metrics['wpm'],
            speaking_rate_category=fluency_metrics['rate_category'],
            pace_score=pace_metrics['score'],
            total_pauses=pace_metrics['total_pauses'],
            average_pause_duration=pace_metrics['avg_pause_duration'],
            long_pause_count=pace_metrics['long_pause_count'],
            pause_locations=pace_metrics['pause_locations'],
            overall_confidence=confidence_metrics['overall_score'],
            confidence_category=confidence_metrics['category'],
            strengths=feedback['strengths'],
            areas_for_improvement=feedback['areas_for_improvement'],
            specific_tips=feedback['specific_tips']
        )

    def _parse_word_timings(self, words: List[Dict[str, Any]]) -> List[WordTiming]:
        """Parse word timing data into structured format."""
        word_timings = []
        for word_data in words:
            start = word_data.get('start', 0.0)
            end = word_data.get('end', 0.0)
            word_timings.append(WordTiming(
                word=word_data.get('word', ''),
                start=start,
                end=end,
                confidence=word_data.get('confidence', 0.0),
                duration=end - start
            ))
        return word_timings

    def _analyze_pronunciation(self, word_timings: List[WordTiming]) -> Dict[str, Any]:
        """Analyze pronunciation quality based on word confidence scores."""
        if not word_timings:
            return {
                'score': 0.0,
                'avg_confidence': 0.0,
                'low_confidence_words': []
            }

        confidences = [wt.confidence for wt in word_timings]
        avg_confidence = statistics.mean(confidences)

        # Find low confidence words
        low_confidence_words = [
            wt.word for wt in word_timings
            if wt.confidence < self.LOW_CONFIDENCE
        ]

        # Calculate pronunciation score (0-100)
        # Penalize based on number and severity of low confidence words
        base_score = avg_confidence * 100
        penalty = len(low_confidence_words) / len(word_timings) * 15  # Up to 15 point penalty
        pronunciation_score = max(0, base_score - penalty)

        return {
            'score': pronunciation_score,
            'avg_confidence': avg_confidence,
            'low_confidence_words': low_confidence_words[:10],  # Top 10 problematic words
            'confidence_distribution': {
                'high': sum(1 for c in confidences if c >= self.MEDIUM_CONFIDENCE),
                'medium': sum(1 for c in confidences if self.LOW_CONFIDENCE <= c < self.MEDIUM_CONFIDENCE),
                'low': sum(1 for c in confidences if c < self.LOW_CONFIDENCE)
            }
        }

    def _analyze_fluency(
        self,
        word_timings: List[WordTiming],
        total_duration: float
    ) -> Dict[str, Any]:
        """Analyze speaking fluency and rate."""
        if not word_timings or total_duration == 0:
            return {
                'score': 0.0,
                'wpm': 0.0,
                'rate_category': 'unknown'
            }

        word_count = len(word_timings)
        duration_minutes = total_duration / 60.0
        wpm = word_count / duration_minutes if duration_minutes > 0 else 0

        # Categorize speaking rate
        if wpm < self.SLOW_WPM:
            rate_category = 'slow'
            rate_score = 60 + (wpm / self.SLOW_WPM) * 20  # 60-80
        elif self.NORMAL_WPM_MIN <= wpm <= self.NORMAL_WPM_MAX:
            rate_category = 'normal'
            rate_score = 90 + ((wpm - self.NORMAL_WPM_MIN) / (self.NORMAL_WPM_MAX - self.NORMAL_WPM_MIN)) * 10  # 90-100
        elif wpm > self.FAST_WPM:
            rate_category = 'fast'
            excess = min(wpm - self.FAST_WPM, 60)  # Cap excess at 60 WPM
            rate_score = 85 - (excess / 60) * 15  # 70-85
        else:
            rate_category = 'normal'
            rate_score = 85

        # Check for consistent word spacing (fluency indicator)
        word_durations = [wt.duration for wt in word_timings]
        duration_variance = statistics.stdev(word_durations) if len(word_durations) > 1 else 0

        # Lower variance = more fluent
        fluency_bonus = max(0, 10 - duration_variance * 20)
        fluency_score = min(100, rate_score + fluency_bonus)

        return {
            'score': fluency_score,
            'wpm': wpm,
            'rate_category': rate_category,
            'duration_variance': duration_variance
        }

    def _analyze_pace_and_pauses(
        self,
        word_timings: List[WordTiming],
        total_duration: float
    ) -> Dict[str, Any]:
        """Analyze speaking pace and pause patterns."""
        if len(word_timings) < 2:
            return {
                'score': 50.0,
                'total_pauses': 0,
                'avg_pause_duration': 0.0,
                'long_pause_count': 0,
                'pause_locations': []
            }

        # Calculate pauses between words
        pauses = []
        pause_locations = []

        for i in range(len(word_timings) - 1):
            current_word = word_timings[i]
            next_word = word_timings[i + 1]

            pause_duration = next_word.start - current_word.end

            if pause_duration > self.SHORT_PAUSE:
                pauses.append(pause_duration)
                pause_locations.append({
                    'after_word': current_word.word,
                    'before_word': next_word.word,
                    'duration': pause_duration,
                    'timestamp': current_word.end
                })

        # Pause metrics
        total_pauses = len(pauses)
        avg_pause_duration = statistics.mean(pauses) if pauses else 0.0
        long_pause_count = sum(1 for p in pauses if p > self.LONG_PAUSE)

        # Calculate pace score
        # Ideal: moderate number of natural pauses, not too many long pauses
        words_per_pause = len(word_timings) / total_pauses if total_pauses > 0 else len(word_timings)

        # Ideal: 5-10 words between pauses
        if 5 <= words_per_pause <= 10:
            pace_base_score = 95
        elif 3 <= words_per_pause < 5:
            pace_base_score = 85
        elif 10 < words_per_pause <= 15:
            pace_base_score = 85
        else:
            pace_base_score = 70

        # Penalize excessive long pauses
        long_pause_penalty = min(long_pause_count * 5, 25)
        pace_score = max(0, pace_base_score - long_pause_penalty)

        return {
            'score': pace_score,
            'total_pauses': total_pauses,
            'avg_pause_duration': avg_pause_duration,
            'long_pause_count': long_pause_count,
            'pause_locations': pause_locations[:10],  # Top 10 significant pauses
            'words_per_pause': words_per_pause
        }

    def _analyze_confidence(
        self,
        pronunciation_metrics: Dict,
        fluency_metrics: Dict,
        pace_metrics: Dict
    ) -> Dict[str, Any]:
        """Calculate overall confidence score and category."""
        # Weighted average of all metrics
        overall_score = (
            pronunciation_metrics['score'] * 0.4 +
            fluency_metrics['score'] * 0.35 +
            pace_metrics['score'] * 0.25
        )

        # Categorize confidence
        if overall_score >= 85:
            category = 'high'
        elif overall_score >= 70:
            category = 'medium'
        else:
            category = 'low'

        return {
            'overall_score': overall_score,
            'category': category
        }

    def _generate_feedback(
        self,
        pronunciation_metrics: Dict,
        fluency_metrics: Dict,
        pace_metrics: Dict,
        confidence_metrics: Dict
    ) -> Dict[str, List[str]]:
        """Generate human-readable feedback and tips."""
        strengths = []
        areas_for_improvement = []
        specific_tips = []

        # Pronunciation feedback
        if pronunciation_metrics['score'] >= 85:
            strengths.append("Excellent pronunciation clarity")
        elif pronunciation_metrics['score'] >= 70:
            strengths.append("Good pronunciation overall")
        else:
            areas_for_improvement.append("Pronunciation clarity needs improvement")
            if pronunciation_metrics['low_confidence_words']:
                specific_tips.append(
                    f"Practice these words: {', '.join(pronunciation_metrics['low_confidence_words'][:5])}"
                )

        # Fluency feedback
        wpm = fluency_metrics['wpm']
        rate_category = fluency_metrics['rate_category']

        if rate_category == 'normal':
            strengths.append(f"Natural speaking pace ({wpm:.0f} words/minute)")
        elif rate_category == 'slow':
            areas_for_improvement.append("Speaking pace is slower than typical")
            specific_tips.append("Try to speak more naturally without overthinking each word")
        elif rate_category == 'fast':
            areas_for_improvement.append("Speaking pace is faster than typical")
            specific_tips.append("Try to slow down slightly for better clarity")

        # Pace feedback
        if pace_metrics['score'] >= 85:
            strengths.append("Great rhythm and natural pauses")
        elif pace_metrics['long_pause_count'] > 3:
            areas_for_improvement.append("Several long hesitations detected")
            specific_tips.append("Practice speaking more continuously to reduce hesitations")

        # Overall confidence feedback
        if confidence_metrics['category'] == 'high':
            strengths.append("Strong overall speaking confidence")
        elif confidence_metrics['category'] == 'low':
            specific_tips.append("Regular practice will help build your speaking confidence")

        return {
            'strengths': strengths,
            'areas_for_improvement': areas_for_improvement,
            'specific_tips': specific_tips
        }
