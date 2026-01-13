#!/usr/bin/env python3
"""
Comprehensive Installation Verification Script
Tests all components: PyAudio, PyTorch, Torchaudio, FFmpeg, Whisper, SpeechBrain

Usage:
    python test_installation.py           # Run all tests
    python test_installation.py --quick   # Quick import tests only
    python test_installation.py --audio   # Test audio devices
    python test_installation.py --gpu     # Test GPU/CUDA availability
"""

import sys
import os
import platform
import subprocess
import shutil
from typing import Tuple, List, Optional
from dataclasses import dataclass
from enum import Enum


class TestStatus(Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    WARN = "WARN"
    SKIP = "SKIP"


@dataclass
class TestResult:
    name: str
    status: TestStatus
    message: str
    details: Optional[str] = None


class Color:
    """ANSI color codes."""
    RESET = "\033[0m"
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    CYAN = "\033[96m"
    BOLD = "\033[1m"


def colorize(text: str, color: str) -> str:
    """Apply color to text if terminal supports it."""
    if sys.stdout.isatty():
        return f"{color}{text}{Color.RESET}"
    return text


def print_header(title: str):
    """Print section header."""
    print(f"\n{colorize('=' * 60, Color.CYAN)}")
    print(f"{colorize(title, Color.BOLD)}")
    print(f"{colorize('=' * 60, Color.CYAN)}")


def print_result(result: TestResult):
    """Print test result with appropriate color."""
    status_colors = {
        TestStatus.PASS: Color.GREEN,
        TestStatus.FAIL: Color.RED,
        TestStatus.WARN: Color.YELLOW,
        TestStatus.SKIP: Color.BLUE,
    }
    color = status_colors.get(result.status, Color.RESET)
    status_str = f"[{result.status.value}]"
    print(f"  {colorize(status_str, color):15} {result.name}: {result.message}")
    if result.details:
        for line in result.details.split('\n'):
            print(f"                 {line}")


class InstallationTester:
    """Comprehensive installation tester."""
    
    def __init__(self):
        self.results: List[TestResult] = []
        self.platform = platform.system().lower()
        self.architecture = platform.machine().lower()
        self.is_windows = self.platform == 'windows'
        self.is_linux = self.platform == 'linux'
        self.is_macos = self.platform == 'darwin'
        self.is_arm64 = self.architecture in ('arm64', 'aarch64')
    
    def add_result(self, name: str, status: TestStatus, message: str, details: str = None):
        """Add a test result."""
        result = TestResult(name, status, message, details)
        self.results.append(result)
        print_result(result)
    
    def test_python_version(self) -> bool:
        """Test Python version compatibility."""
        version = sys.version_info
        if version.major >= 3 and version.minor >= 8:
            self.add_result(
                "Python Version",
                TestStatus.PASS,
                f"{version.major}.{version.minor}.{version.micro}"
            )
            return True
        else:
            self.add_result(
                "Python Version",
                TestStatus.FAIL,
                f"{version.major}.{version.minor}.{version.micro} (requires 3.8+)"
            )
            return False
    
    def test_import(self, module_name: str, package_name: str = None) -> Tuple[bool, Optional[str]]:
        """Test if a module can be imported."""
        package_name = package_name or module_name
        try:
            module = __import__(module_name)
            version = getattr(module, '__version__', 'unknown')
            self.add_result(
                package_name,
                TestStatus.PASS,
                f"v{version}"
            )
            return True, version
        except ImportError as e:
            self.add_result(
                package_name,
                TestStatus.FAIL,
                f"Import failed: {e}"
            )
            return False, None
        except Exception as e:
            self.add_result(
                package_name,
                TestStatus.WARN,
                f"Import warning: {e}"
            )
            return True, None
    
    def test_pyaudio(self) -> bool:
        """Test PyAudio installation and audio device enumeration."""
        try:
            import pyaudio
            self.add_result("PyAudio Import", TestStatus.PASS, "Module loaded")
            
            # Test device enumeration
            try:
                p = pyaudio.PyAudio()
                device_count = p.get_device_count()
                
                # Get default devices
                default_input = None
                default_output = None
                try:
                    default_input = p.get_default_input_device_info()
                except:
                    pass
                try:
                    default_output = p.get_default_output_device_info()
                except:
                    pass
                
                details = f"Found {device_count} audio device(s)"
                if default_input:
                    details += f"\nDefault Input: {default_input.get('name', 'Unknown')}"
                if default_output:
                    details += f"\nDefault Output: {default_output.get('name', 'Unknown')}"
                
                p.terminate()
                
                self.add_result(
                    "PyAudio Devices",
                    TestStatus.PASS if device_count > 0 else TestStatus.WARN,
                    f"{device_count} device(s) found",
                    details if device_count > 0 else None
                )
                return True
            except Exception as e:
                self.add_result(
                    "PyAudio Devices",
                    TestStatus.WARN,
                    f"Device enumeration failed: {e}"
                )
                return True  # Import worked, devices are optional
                
        except ImportError as e:
            self.add_result("PyAudio", TestStatus.FAIL, f"Not installed: {e}")
            return False
    
    def test_pytorch(self) -> bool:
        """Test PyTorch installation, tensor ops, and GPU availability."""
        try:
            import torch
            self.add_result("PyTorch Import", TestStatus.PASS, f"v{torch.__version__}")
            
            # Test tensor creation
            try:
                tensor = torch.zeros(3, 3)
                tensor_sum = tensor.sum().item()
                self.add_result("PyTorch Tensor Ops", TestStatus.PASS, "Tensor creation works")
            except Exception as e:
                self.add_result("PyTorch Tensor Ops", TestStatus.FAIL, f"Failed: {e}")
                return False
            
            # Test CUDA
            if torch.cuda.is_available():
                device_name = torch.cuda.get_device_name(0)
                cuda_version = torch.version.cuda
                self.add_result(
                    "CUDA",
                    TestStatus.PASS,
                    f"Available ({device_name})",
                    f"CUDA Version: {cuda_version}"
                )
                
                # Test CUDA tensor
                try:
                    cuda_tensor = torch.zeros(3, 3, device='cuda')
                    self.add_result("CUDA Tensor Ops", TestStatus.PASS, "CUDA tensors work")
                except Exception as e:
                    self.add_result("CUDA Tensor Ops", TestStatus.FAIL, f"Failed: {e}")
            else:
                self.add_result("CUDA", TestStatus.SKIP, "Not available (CPU-only installation)")
            
            # Test MPS (Apple Silicon)
            if self.is_macos and hasattr(torch.backends, 'mps'):
                if torch.backends.mps.is_available():
                    self.add_result("MPS (Metal)", TestStatus.PASS, "Available for Apple Silicon")
                    
                    # Test MPS tensor
                    try:
                        mps_tensor = torch.zeros(3, 3, device='mps')
                        self.add_result("MPS Tensor Ops", TestStatus.PASS, "MPS tensors work")
                    except Exception as e:
                        self.add_result("MPS Tensor Ops", TestStatus.WARN, f"Warning: {e}")
                elif self.is_arm64:
                    self.add_result("MPS (Metal)", TestStatus.WARN, "Apple Silicon detected but MPS unavailable")
                else:
                    self.add_result("MPS (Metal)", TestStatus.SKIP, "Intel Mac - MPS not applicable")
            
            return True
            
        except ImportError as e:
            self.add_result("PyTorch", TestStatus.FAIL, f"Not installed: {e}")
            return False
    
    def test_torchaudio(self) -> bool:
        """Test Torchaudio installation."""
        try:
            import torchaudio
            self.add_result("Torchaudio Import", TestStatus.PASS, f"v{torchaudio.__version__}")
            
            # Test basic functionality
            try:
                # Check available backends
                backends = []
                if hasattr(torchaudio, 'list_audio_backends'):
                    backends = torchaudio.list_audio_backends()
                self.add_result(
                    "Torchaudio Backends",
                    TestStatus.PASS if backends else TestStatus.WARN,
                    f"{len(backends)} backend(s): {', '.join(backends) if backends else 'None found'}"
                )
            except Exception as e:
                self.add_result("Torchaudio Backends", TestStatus.WARN, f"Could not list: {e}")
            
            return True
            
        except ImportError as e:
            self.add_result("Torchaudio", TestStatus.FAIL, f"Not installed: {e}")
            return False
    
    def test_ffmpeg(self) -> bool:
        """Test FFmpeg installation and functionality."""
        # Check if ffmpeg is in PATH
        ffmpeg_path = shutil.which('ffmpeg')
        if not ffmpeg_path:
            self.add_result("FFmpeg", TestStatus.FAIL, "Not found in PATH")
            return False
        
        self.add_result("FFmpeg Path", TestStatus.PASS, ffmpeg_path)
        
        # Get version
        try:
            result = subprocess.run(
                ['ffmpeg', '-version'],
                capture_output=True,
                text=True,
                timeout=10
            )
            if result.returncode == 0:
                version_line = result.stdout.split('\n')[0]
                self.add_result("FFmpeg Version", TestStatus.PASS, version_line)
                return True
            else:
                self.add_result("FFmpeg Version", TestStatus.WARN, "Could not get version")
                return True
        except Exception as e:
            self.add_result("FFmpeg Version", TestStatus.WARN, f"Error: {e}")
            return True
    
    def test_whisper(self) -> bool:
        """Test Whisper installation."""
        try:
            import whisper
            self.add_result("Whisper Import", TestStatus.PASS, "Module loaded")
            
            # List available models
            try:
                if hasattr(whisper, 'available_models'):
                    models = whisper.available_models()
                    self.add_result(
                        "Whisper Models",
                        TestStatus.PASS,
                        f"{len(models)} models available"
                    )
            except Exception as e:
                self.add_result("Whisper Models", TestStatus.WARN, f"Could not list: {e}")
            
            return True
            
        except ImportError as e:
            self.add_result("Whisper", TestStatus.FAIL, f"Not installed: {e}")
            return False
    
    def test_speechbrain(self) -> bool:
        """Test SpeechBrain installation."""
        try:
            import speechbrain
            version = getattr(speechbrain, '__version__', 'unknown')
            self.add_result("SpeechBrain Import", TestStatus.PASS, f"v{version}")
            return True
        except ImportError as e:
            self.add_result("SpeechBrain", TestStatus.FAIL, f"Not installed: {e}")
            return False
    
    def test_other_packages(self) -> bool:
        """Test other required packages."""
        packages = [
            ('fastapi', 'FastAPI'),
            ('uvicorn', 'Uvicorn'),
            ('numpy', 'NumPy'),
            ('scipy', 'SciPy'),
            ('soundfile', 'SoundFile'),
            ('pydub', 'Pydub'),
        ]
        
        all_ok = True
        for module, name in packages:
            success, _ = self.test_import(module, name)
            if not success:
                all_ok = False
        
        return all_ok
    
    def run_all_tests(self, quick: bool = False) -> bool:
        """Run all installation tests."""
        print_header("INSTALLATION VERIFICATION TEST SUITE")
        print(f"\nPlatform: {platform.system()} ({self.architecture})")
        print(f"Python: {platform.python_version()}")
        
        # Basic tests
        print_header("1. Python Environment")
        self.test_python_version()
        
        print_header("2. Core Packages")
        self.test_import('numpy', 'NumPy')
        self.test_import('scipy', 'SciPy')
        
        print_header("3. PyAudio")
        self.test_pyaudio()
        
        print_header("4. PyTorch & CUDA")
        self.test_pytorch()
        
        print_header("5. Torchaudio")
        self.test_torchaudio()
        
        print_header("6. FFmpeg")
        self.test_ffmpeg()
        
        print_header("7. Voice Analysis")
        self.test_whisper()
        self.test_speechbrain()
        
        if not quick:
            print_header("8. Additional Packages")
            self.test_other_packages()
        
        # Summary
        self._print_summary()
        
        return all(r.status in (TestStatus.PASS, TestStatus.WARN, TestStatus.SKIP) 
                   for r in self.results)
    
    def _print_summary(self):
        """Print test summary."""
        print_header("TEST SUMMARY")
        
        passed = sum(1 for r in self.results if r.status == TestStatus.PASS)
        failed = sum(1 for r in self.results if r.status == TestStatus.FAIL)
        warned = sum(1 for r in self.results if r.status == TestStatus.WARN)
        skipped = sum(1 for r in self.results if r.status == TestStatus.SKIP)
        total = len(self.results)
        
        print(f"\n  {colorize('PASSED:', Color.GREEN)}  {passed}")
        print(f"  {colorize('FAILED:', Color.RED)}  {failed}")
        print(f"  {colorize('WARNINGS:', Color.YELLOW)} {warned}")
        print(f"  {colorize('SKIPPED:', Color.BLUE)}  {skipped}")
        print(f"  {'TOTAL:':10} {total}")
        
        if failed == 0:
            print(f"\n{colorize('✓ All critical tests passed!', Color.GREEN)}")
            if warned > 0:
                print(f"  {warned} warning(s) - review above for details")
        else:
            print(f"\n{colorize('✗ Some tests failed!', Color.RED)}")
            print("  Run: python auto_installer.py --force")
            print("\nFailed tests:")
            for r in self.results:
                if r.status == TestStatus.FAIL:
                    print(f"  - {r.name}: {r.message}")


def main():
    """Main entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Comprehensive installation verification for voice analysis dependencies"
    )
    parser.add_argument(
        '--quick', '-q',
        action='store_true',
        help='Run quick tests only (skip additional packages)'
    )
    parser.add_argument(
        '--audio',
        action='store_true',
        help='Focus on audio device testing'
    )
    parser.add_argument(
        '--gpu',
        action='store_true',
        help='Focus on GPU/CUDA testing'
    )
    
    args = parser.parse_args()
    
    tester = InstallationTester()
    
    if args.audio:
        print_header("AUDIO DEVICE TEST")
        tester.test_pyaudio()
        tester._print_summary()
    elif args.gpu:
        print_header("GPU/CUDA TEST")
        tester.test_pytorch()
        tester._print_summary()
    else:
        success = tester.run_all_tests(quick=args.quick)
        sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
