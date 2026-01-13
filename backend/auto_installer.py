"""
Auto-installer for voice analysis dependencies.
Automatically detects and installs missing packages.
Works on Windows, Linux, and macOS.
"""
import subprocess
import sys
import importlib
import os
import platform
import shutil
import tempfile
import urllib.request
import zipfile
import glob
from pathlib import Path
from typing import List, Tuple, Optional
import logging
import sitecustomize  # noqa: F401

logger = logging.getLogger(__name__)

# Configure logging and encoding for standalone execution
if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s'
    )
    
    # Fix Windows console encoding for Unicode support
    if sys.platform == 'win32':
        try:
            # Enable UTF-8 mode on Windows
            import io
            sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
            sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
        except Exception:
            pass

# Emoji to ASCII fallback mapping for systems that don't support Unicode
EMOJI_FALLBACK = {
    '🚀': '[*]',
    '📋': '[>]',
    '📦': '[#]',
    '🔧': '[+]',
    '🔍': '[?]',
    '🎉': '[!]',
    '✓': '[OK]',
    '✗': '[X]',
    '⚠️': '[!]',
    '❌': '[X]',
    '⏳': '[..]',
}


def safe_print(text: str):
    """Print text safely, handling Unicode encoding issues on Windows."""
    try:
        print(text)
    except UnicodeEncodeError:
        # Fallback: replace emojis with ASCII alternatives
        for emoji, replacement in EMOJI_FALLBACK.items():
            text = text.replace(emoji, replacement)
        try:
            print(text)
        except UnicodeEncodeError:
            # Last resort: encode with errors='replace'
            print(text.encode('ascii', errors='replace').decode('ascii'))


class AutoInstaller:
    """Cross-platform dependency installer for voice analysis."""
    
    # PyTorch versions - pinned for SpeechBrain compatibility
    PYTORCH_VERSION = "2.2.0"
    TORCHAUDIO_VERSION = "2.2.0"
    CUDA_VERSION = "cu121"  # CUDA 12.1
    
    def __init__(self):
        self.required_packages = {
            'whisper': 'openai-whisper',
            'speechbrain': 'speechbrain', 
            'torch': f'torch=={self.PYTORCH_VERSION}',  # Pinned for SpeechBrain compatibility
            'torchaudio': f'torchaudio=={self.TORCHAUDIO_VERSION}',  # Pinned for SpeechBrain compatibility
            'soundfile': 'soundfile',
            'ffmpeg': 'ffmpeg-python',
            'numpy': 'numpy',
            'scipy': 'scipy'
        }
        
        self.system_requirements = {
            'ffmpeg': 'ffmpeg'
        }
        
        # Audio system dependencies for PyAudio compilation
        self.audio_system_deps = {
            'linux': [
                'portaudio19-dev',
                'libasound2-dev', 
                'libportaudio2',
                'libportaudiocpp0',
                'python3-dev',
                'build-essential',
                'libsndfile1-dev'
            ],
            'macos': [
                'portaudio',
                'libsndfile'
            ],
            'windows': []  # PyAudio wheels are available for Windows
        }
        
        self.platform = sys.platform.lower()
        self.is_windows = self.platform == 'win32' or self.platform.startswith('windows')
        self.is_linux = self.platform.startswith('linux')
        self.is_macos = self.platform.startswith('darwin')
        
        # Architecture detection
        self.architecture = self._detect_architecture()
        self.is_arm64 = self.architecture in ('arm64', 'aarch64')
        self.is_x86_64 = self.architecture in ('x86_64', 'amd64', 'AMD64')
        
        # GPU detection (will be populated on first check)
        self._has_nvidia_gpu = None
        self._cuda_available = None
        
        # macOS Homebrew prefix
        self.homebrew_prefix = self._get_homebrew_prefix() if self.is_macos else None
    
    def _detect_architecture(self) -> str:
        """Detect system architecture."""
        import platform as plat
        machine = plat.machine().lower()
        return machine
    
    def _get_homebrew_prefix(self) -> str:
        """Get Homebrew prefix based on architecture (Apple Silicon vs Intel)."""
        if self.is_arm64:
            return "/opt/homebrew"
        return "/usr/local"
    
    def detect_nvidia_gpu(self) -> Tuple[bool, bool]:
        """
        Detect NVIDIA GPU and CUDA availability.
        
        Returns:
            (has_nvidia_gpu, cuda_available)
        """
        if self._has_nvidia_gpu is not None:
            return self._has_nvidia_gpu, self._cuda_available
        
        has_gpu = False
        cuda_available = False
        
        # Check for nvidia-smi
        if shutil.which("nvidia-smi"):
            try:
                result = subprocess.run(
                    ["nvidia-smi"], 
                    capture_output=True, 
                    text=True, 
                    timeout=10
                )
                if result.returncode == 0:
                    has_gpu = True
                    logger.info("NVIDIA GPU detected via nvidia-smi")
                    
                    # Check CUDA version from nvidia-smi output
                    if "CUDA Version" in result.stdout:
                        cuda_available = True
                        # Extract CUDA version
                        import re
                        cuda_match = re.search(r'CUDA Version:\s*(\d+\.\d+)', result.stdout)
                        if cuda_match:
                            logger.info(f"CUDA version detected: {cuda_match.group(1)}")
            except (subprocess.TimeoutExpired, FileNotFoundError, Exception) as e:
                logger.debug(f"nvidia-smi check failed: {e}")
        
        # Windows: Also check via WMIC if nvidia-smi not found
        if self.is_windows and not has_gpu:
            try:
                result = subprocess.run(
                    ["wmic", "path", "win32_VideoController", "get", "name"],
                    capture_output=True,
                    text=True,
                    timeout=10
                )
                if "NVIDIA" in result.stdout.upper():
                    has_gpu = True
                    logger.info("NVIDIA GPU detected via WMIC")
                    # Assume CUDA might be available if GPU is present
                    cuda_available = shutil.which("nvcc") is not None
            except Exception as e:
                logger.debug(f"WMIC GPU check failed: {e}")
        
        # Linux: Check /proc/driver/nvidia/version
        if self.is_linux and not has_gpu:
            try:
                nvidia_version_path = Path("/proc/driver/nvidia/version")
                if nvidia_version_path.exists():
                    has_gpu = True
                    cuda_available = True
                    logger.info("NVIDIA GPU detected via /proc/driver/nvidia/version")
            except Exception:
                pass
        
        self._has_nvidia_gpu = has_gpu
        self._cuda_available = cuda_available
        return has_gpu, cuda_available
    
    def get_pytorch_install_command(self) -> List[str]:
        """
        Get the appropriate PyTorch installation command based on platform and GPU.
        
        Returns:
            List of pip install arguments for PyTorch
        """
        has_gpu, cuda_available = self.detect_nvidia_gpu()
        
        torch_pkg = f"torch=={self.PYTORCH_VERSION}"
        torchaudio_pkg = f"torchaudio=={self.TORCHAUDIO_VERSION}"
        
        # macOS: Always CPU/MPS version (no CUDA support)
        if self.is_macos:
            logger.info("macOS detected - using CPU/MPS PyTorch (MPS auto-enabled on Apple Silicon)")
            return [torch_pkg, torchaudio_pkg]
        
        # Linux/Windows with NVIDIA GPU: Use CUDA version
        if has_gpu and cuda_available:
            logger.info(f"NVIDIA GPU with CUDA detected - using CUDA {self.CUDA_VERSION} PyTorch")
            return [
                torch_pkg, torchaudio_pkg,
                "--index-url", f"https://download.pytorch.org/whl/{self.CUDA_VERSION}"
            ]
        
        # Linux/Windows without GPU: Use CPU version
        if self.is_linux or self.is_windows:
            logger.info("No NVIDIA GPU detected - using CPU-only PyTorch")
            return [
                torch_pkg, torchaudio_pkg,
                "--index-url", "https://download.pytorch.org/whl/cpu"
            ]
        
        # Default fallback
        return [torch_pkg, torchaudio_pkg]
    
    def install_pytorch(self) -> Tuple[bool, str]:
        """
        Install PyTorch and Torchaudio with intelligent version selection.
        
        Returns:
            (success, message)
        """
        safe_print("\n⏳ Installing PyTorch and Torchaudio...")
        
        # Print system info
        has_gpu, cuda_available = self.detect_nvidia_gpu()
        safe_print(f"   Platform: {platform.system()} ({self.architecture})")
        safe_print(f"   NVIDIA GPU: {'Yes' if has_gpu else 'No'}")
        safe_print(f"   CUDA Available: {'Yes' if cuda_available else 'No'}")
        
        install_args = self.get_pytorch_install_command()
        
        # Build pip command
        cmd = [sys.executable, '-m', 'pip', 'install', '--upgrade'] + install_args
        
        safe_print(f"   Installing: {' '.join(install_args[:2])}")
        if len(install_args) > 2:
            safe_print(f"   Index URL: {install_args[-1]}")
        
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=900  # 15 minutes for large PyTorch download
            )
            
            if result.returncode == 0:
                msg = f"PyTorch {self.PYTORCH_VERSION} and Torchaudio {self.TORCHAUDIO_VERSION} installed successfully"
                logger.info(msg)
                safe_print(f"✓ {msg}")
                return True, msg
            else:
                error_msg = f"PyTorch installation failed: {result.stderr[:500]}"
                logger.error(error_msg)
                return False, error_msg
                
        except subprocess.TimeoutExpired:
            return False, "PyTorch installation timed out (>15 minutes)"
        except Exception as e:
            return False, f"PyTorch installation error: {str(e)}"
    
    def install_pyaudio(self) -> Tuple[bool, str]:
        """
        Install PyAudio with platform-specific handling.
        
        Returns:
            (success, message)
        """
        safe_print("\n⏳ Installing PyAudio...")
        
        # Windows: Use pre-built wheels
        if self.is_windows:
            try:
                result = subprocess.run(
                    [sys.executable, '-m', 'pip', 'install', 'pyaudio>=0.2.14'],
                    capture_output=True,
                    text=True,
                    timeout=120
                )
                if result.returncode == 0:
                    return True, "PyAudio installed from wheel"
                
                # Fallback: try pipwin
                if shutil.which('pipwin'):
                    result = subprocess.run(
                        ['pipwin', 'install', 'pyaudio'],
                        capture_output=True,
                        text=True,
                        timeout=120
                    )
                    if result.returncode == 0:
                        return True, "PyAudio installed via pipwin"
                
                return False, f"PyAudio installation failed: {result.stderr[:200]}"
            except Exception as e:
                return False, f"PyAudio installation error: {str(e)}"
        
        # macOS: Use build flags based on architecture
        if self.is_macos:
            prefix = self.homebrew_prefix
            env = os.environ.copy()
            
            # Set build flags for PortAudio
            env['LDFLAGS'] = f"-L{prefix}/lib"
            env['CPPFLAGS'] = f"-I{prefix}/include"
            env['CFLAGS'] = f"-I{prefix}/include"
            
            safe_print(f"   Using Homebrew prefix: {prefix}")
            
            try:
                result = subprocess.run(
                    [sys.executable, '-m', 'pip', 'install', 'pyaudio>=0.2.14'],
                    capture_output=True,
                    text=True,
                    timeout=300,
                    env=env
                )
                if result.returncode == 0:
                    return True, f"PyAudio installed with {prefix} build flags"
                else:
                    return False, f"PyAudio build failed: {result.stderr[:200]}"
            except Exception as e:
                return False, f"PyAudio installation error: {str(e)}"
        
        # Linux: Build from source using system PortAudio
        if self.is_linux:
            try:
                result = subprocess.run(
                    [sys.executable, '-m', 'pip', 'install', 'pyaudio>=0.2.14'],
                    capture_output=True,
                    text=True,
                    timeout=300
                )
                if result.returncode == 0:
                    return True, "PyAudio installed from source"
                else:
                    return False, f"PyAudio build failed (ensure portaudio19-dev is installed): {result.stderr[:200]}"
            except Exception as e:
                return False, f"PyAudio installation error: {str(e)}"
        
        return False, "Unsupported platform for PyAudio"
    
    def check_package(self, package_name: str) -> bool:
        """Check if a Python package is installed."""
        try:
            importlib.import_module(package_name)
            return True
        except ImportError:
            return False
        except Exception as exc:
            logger.warning(f"Error importing {package_name}: {exc}")
            return False
    
    def check_system_command(self, command: str) -> bool:
        """Check if a system command is available."""
        # Fast path: command already resolvable via PATH
        if shutil.which(command):
            return True
        
        # Special handling for FFmpeg on Windows where installers may not add it to PATH
        if command == 'ffmpeg':
            ffmpeg_path = self._locate_existing_ffmpeg()
            if ffmpeg_path:
                logger.info(f"Detected FFmpeg binary at {ffmpeg_path}")
                self._ensure_directory_on_path(os.path.dirname(ffmpeg_path), persist=True)
                return self._try_command([command, '--version'])
        
        try:
            result = subprocess.run([command, '--version'], 
                                  capture_output=True, 
                                  text=True, 
                                  timeout=10)
            return result.returncode == 0
        except (subprocess.TimeoutExpired, FileNotFoundError):
            return False
    
    def install_package(self, package_name: str) -> Tuple[bool, str]:
        """Install a Python package."""
        try:
            logger.info(f"Installing {package_name}...")
            result = subprocess.run(
                [sys.executable, '-m', 'pip', 'install', package_name],
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout
            )
            
            if result.returncode == 0:
                logger.info(f"Successfully installed {package_name}")
                return True, f"Successfully installed {package_name}"
            else:
                error_msg = f"Failed to install {package_name}: {result.stderr}"
                logger.error(error_msg)
                return False, error_msg
                
        except subprocess.TimeoutExpired:
            error_msg = f"Installation of {package_name} timed out"
            logger.error(error_msg)
            return False, error_msg
        except Exception as e:
            error_msg = f"Error installing {package_name}: {str(e)}"
            logger.error(error_msg)
            return False, error_msg
    
    def install_system_package(self, package_name: str, password: str = None) -> Tuple[bool, str]:
        """Attempt to auto-install system packages."""
        if package_name == 'ffmpeg':
            return self._install_ffmpeg(password)
        
        return False, f"No auto-installation available for {package_name}"
    
    def _install_ffmpeg(self, password: str = None) -> Tuple[bool, str]:
        """Auto-install FFmpeg using available package managers."""
        platform = sys.platform.lower()
        
        if platform == 'win32' or platform == 'windows':
            return self._install_ffmpeg_windows()
        elif platform.startswith('linux'):
            return self._install_ffmpeg_linux(password)
        elif platform.startswith('darwin'):
            return self._install_ffmpeg_macos()
        
        return False, self._get_ffmpeg_manual_instructions('unknown platform: {platform}')
    
    def _install_ffmpeg_windows(self) -> Tuple[bool, str]:
        """Install FFmpeg on Windows using available package managers or direct download."""
        
        # Try winget first (built into Windows 10/11)
        if self._try_command(['winget', '--version']):
            logger.info("Installing FFmpeg using winget...")
            try:
                result = subprocess.run(
                    ['winget', 'install', 'ffmpeg', '--accept-package-agreements', '--accept-source-agreements'],
                    capture_output=True,
                    text=True,
                    timeout=600  # 10 minutes for download
                )
                if result.returncode == 0 or 'successfully installed' in result.stdout.lower():
                    logger.info("FFmpeg installed successfully with winget")
                    self._notify_path_refresh()
                    return True, "FFmpeg installed successfully using winget. Please restart your terminal for PATH changes."
                else:
                    logger.warning(f"Winget install output: {result.stdout}")
                    logger.warning(f"Winget install stderr: {result.stderr}")
            except subprocess.TimeoutExpired:
                logger.warning("Winget install timed out")
            except Exception as e:
                logger.warning(f"Winget install error: {e}")
        
        # Try Chocolatey
        if self._try_command(['choco', '--version']):
            logger.info("Installing FFmpeg using Chocolatey...")
            try:
                result = subprocess.run(
                    ['choco', 'install', 'ffmpeg', '-y'],
                    capture_output=True,
                    text=True,
                    timeout=600
                )
                if result.returncode == 0:
                    logger.info("FFmpeg installed successfully with Chocolatey")
                    self._notify_path_refresh()
                    return True, "FFmpeg installed successfully using Chocolatey. Please restart your terminal for PATH changes."
                else:
                    logger.warning(f"Chocolatey install failed: {result.stderr}")
            except Exception as e:
                logger.warning(f"Chocolatey install error: {e}")
        
        # Try direct download as fallback
        logger.info("Attempting direct FFmpeg download...")
        success, message = self._download_ffmpeg_windows()
        if success:
            return True, message
        
        # If all methods failed, provide manual instructions
        return False, self._get_ffmpeg_manual_instructions('windows')
    
    def _download_ffmpeg_windows(self) -> Tuple[bool, str]:
        """Download FFmpeg directly for Windows and add to PATH."""
        try:
            # FFmpeg essentials build URL (smaller download)
            ffmpeg_url = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
            
            # Download location
            download_dir = Path(os.environ.get('LOCALAPPDATA', tempfile.gettempdir())) / "FFmpeg"
            download_dir.mkdir(parents=True, exist_ok=True)
            
            zip_path = download_dir / "ffmpeg.zip"
            
            logger.info(f"Downloading FFmpeg from {ffmpeg_url}...")
            safe_print("Downloading FFmpeg (this may take a few minutes)...")
            
            # Download with progress
            urllib.request.urlretrieve(ffmpeg_url, zip_path)
            
            logger.info("Extracting FFmpeg...")
            safe_print("Extracting FFmpeg...")
            
            # Extract
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(download_dir)
            
            # Find the bin directory
            ffmpeg_bin = None
            for item in download_dir.iterdir():
                if item.is_dir() and item.name.startswith('ffmpeg'):
                    bin_path = item / 'bin'
                    if bin_path.exists():
                        ffmpeg_bin = bin_path
                        break
            
            if not ffmpeg_bin:
                return False, "Failed to find FFmpeg bin directory after extraction"
            
            # Add to user PATH
            success = self._add_to_windows_path(str(ffmpeg_bin))
            
            # Clean up zip file
            try:
                zip_path.unlink()
            except:
                pass
            
            if success:
                self._notify_path_refresh()
                return True, f"FFmpeg downloaded and installed to {ffmpeg_bin}. Please restart your terminal."
            else:
                return True, f"FFmpeg downloaded to {ffmpeg_bin}. Please add this directory to your PATH manually."
                
        except Exception as e:
            logger.error(f"Failed to download FFmpeg: {e}")
            return False, f"Failed to download FFmpeg: {e}"
    
    def _add_to_windows_path(self, directory: str) -> bool:
        """Add a directory to the Windows user PATH."""
        try:
            import winreg
            
            # Open the user environment variables
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER,
                r"Environment",
                0,
                winreg.KEY_ALL_ACCESS
            )
            
            try:
                # Get current PATH
                current_path, _ = winreg.QueryValueEx(key, "Path")
            except WindowsError:
                current_path = ""
            
            # Check if already in PATH
            if directory.lower() in current_path.lower():
                logger.info(f"{directory} already in PATH")
                return True
            
            # Add to PATH
            new_path = f"{current_path};{directory}" if current_path else directory
            winreg.SetValueEx(key, "Path", 0, winreg.REG_EXPAND_SZ, new_path)
            winreg.CloseKey(key)
            
            # Notify Windows of the change
            try:
                import ctypes
                HWND_BROADCAST = 0xFFFF
                WM_SETTINGCHANGE = 0x1A
                ctypes.windll.user32.SendMessageW(HWND_BROADCAST, WM_SETTINGCHANGE, 0, "Environment")
            except:
                pass
            
            logger.info(f"Added {directory} to PATH")
            return True
            
        except Exception as e:
            logger.error(f"Failed to add to PATH: {e}")
            return False
    
    def _ensure_directory_on_path(self, directory: str, persist: bool = False) -> None:
        """Ensure a directory is present on PATH (current process and optionally user PATH)."""
        if not directory:
            return
        
        directory = os.path.abspath(directory)
        current_path = os.environ.get('PATH', '')
        path_entries = [entry for entry in current_path.split(os.pathsep) if entry]
        normalized_directory = os.path.normcase(directory)
        normalized_entries = {os.path.normcase(os.path.abspath(entry)) for entry in path_entries if entry}
        
        if normalized_directory not in normalized_entries:
            os.environ['PATH'] = directory + os.pathsep + current_path if current_path else directory
            logger.info(f"Prepended {directory} to current process PATH")
            if persist and self.is_windows:
                self._add_to_windows_path(directory)
    
    def _locate_existing_ffmpeg(self) -> Optional[str]:
        """Attempt to locate an FFmpeg binary outside of PATH."""
        if self.is_windows:
            return self._locate_existing_ffmpeg_windows()
        elif self.is_macos:
            candidates = [
                '/opt/homebrew/bin/ffmpeg',
                '/usr/local/bin/ffmpeg',
                '/usr/bin/ffmpeg'
            ]
        else:
            candidates = [
                '/usr/local/bin/ffmpeg',
                '/usr/bin/ffmpeg'
            ]
        
        for candidate in candidates:
            if candidate and os.path.isfile(candidate):
                return candidate
        
        return None
    
    def _locate_existing_ffmpeg_windows(self) -> Optional[str]:
        """Search common Windows installation paths for FFmpeg."""
        local_app = os.environ.get('LOCALAPPDATA', '')
        program_files = os.environ.get('ProgramFiles', r'C:\Program Files')
        program_files_x86 = os.environ.get('ProgramFiles(x86)', r'C:\Program Files (x86)')
        
        patterns = [
            r'C:\ffmpeg\bin\ffmpeg.exe',
            os.path.join(program_files, 'ffmpeg', 'bin', 'ffmpeg.exe'),
            os.path.join(program_files_x86, 'ffmpeg', 'bin', 'ffmpeg.exe'),
        ]
        
        if local_app:
            patterns.extend([
                os.path.join(local_app, 'FFmpeg', 'ffmpeg-*', 'bin', 'ffmpeg.exe'),
                os.path.join(local_app, 'Microsoft', 'WinGet', 'Packages', '*ffmpeg*', 'ffmpeg.exe'),
                os.path.join(local_app, 'Microsoft', 'WinGet', 'Packages', '*ffmpeg*', 'ffmpeg', 'bin', 'ffmpeg.exe'),
            ])
        
        for pattern in patterns:
            matches = glob.glob(pattern)
            for match in matches:
                if os.path.isfile(match):
                    return match
        
        return None
    
    def _notify_path_refresh(self):
        """Notify user about PATH refresh requirement."""
        safe_print("\n" + "="*60)
        safe_print("⚠️  IMPORTANT: PATH Update Required")
        safe_print("="*60)
        safe_print("FFmpeg has been installed, but you need to restart your")
        safe_print("terminal/command prompt for the changes to take effect.")
        safe_print("")
        safe_print("After restarting, verify with: ffmpeg -version")
        safe_print("="*60 + "\n")
    
    def _install_ffmpeg_linux(self, password: str = None) -> Tuple[bool, str]:
        """Install FFmpeg on Linux."""
        
        # Try apt (Ubuntu/Debian)
        if shutil.which('apt') or shutil.which('apt-get'):
            logger.info("Detected apt package manager (Ubuntu/Debian)...")
            
            # Try with password if provided
            if password:
                try:
                    # Update package list first
                    logger.info("Updating package list...")
                    result = subprocess.run(
                        ['sudo', '-S', 'apt-get', 'update', '-y'],
                        input=f"{password}\n",
                        text=True,
                        capture_output=True,
                        timeout=120
                    )
                    if result.returncode != 0:
                        return False, f"Failed to update package list: {result.stderr}"
                    
                    # Install ffmpeg
                    logger.info("Installing FFmpeg using apt...")
                    result = subprocess.run(
                        ['sudo', '-S', 'apt-get', 'install', '-y', 'ffmpeg'],
                        input=f"{password}\n",
                        text=True,
                        capture_output=True,
                        timeout=300
                    )
                    if result.returncode == 0:
                        logger.info("FFmpeg installed successfully with apt")
                        return True, "FFmpeg installed successfully using apt"
                    else:
                        logger.warning(f"Apt install failed: {result.stderr}")
                        return False, f"Failed to install FFmpeg: {result.stderr}"
                except subprocess.TimeoutExpired:
                    return False, "FFmpeg installation timed out"
                except Exception as e:
                    return False, f"Error installing FFmpeg: {e}"
            
            # Try passwordless sudo
            elif self._check_sudo_access():
                try:
                    # Update package list first
                    logger.info("Updating package list...")
                    subprocess.run(
                        ['sudo', 'apt-get', 'update', '-y'],
                        capture_output=True,
                        text=True,
                        timeout=120
                    )
                    
                    # Install ffmpeg
                    logger.info("Installing FFmpeg using apt...")
                    result = subprocess.run(
                        ['sudo', 'apt-get', 'install', '-y', 'ffmpeg'],
                        capture_output=True,
                        text=True,
                        timeout=300
                    )
                    if result.returncode == 0:
                        logger.info("FFmpeg installed successfully with apt")
                        return True, "FFmpeg installed successfully using apt"
                    else:
                        logger.warning(f"Apt install failed: {result.stderr}")
                        return False, f"Failed to install FFmpeg: {result.stderr}"
                except Exception as e:
                    return False, f"Error installing FFmpeg: {e}"
            
            else:
                return False, "sudo access required. Run: sudo apt-get install -y ffmpeg"
        
        # Try dnf (Fedora/RHEL)
        if shutil.which('dnf'):
            logger.info("Detected dnf package manager (Fedora/RHEL)...")
            
            if has_sudo:
                try:
                    # Enable RPM Fusion for ffmpeg (may be needed on Fedora)
                    logger.info("Installing FFmpeg using dnf...")
                    result = subprocess.run(
                        ['sudo', 'dnf', 'install', '-y', 'ffmpeg', 'ffmpeg-devel'],
                        capture_output=True,
                        text=True,
                        timeout=300
                    )
                    if result.returncode == 0:
                        logger.info("FFmpeg installed successfully with dnf")
                        return True, "FFmpeg installed successfully using dnf"
                    else:
                        logger.warning(f"dnf install failed: {result.stderr}")
                except Exception as e:
                    logger.warning(f"dnf install error: {e}")
            else:
                return False, "sudo access required. Run: sudo dnf install -y ffmpeg"
        
        # Try pacman (Arch Linux)
        if shutil.which('pacman'):
            logger.info("Detected pacman package manager (Arch Linux)...")
            
            if has_sudo:
                try:
                    result = subprocess.run(
                        ['sudo', 'pacman', '-S', '--noconfirm', 'ffmpeg'],
                        capture_output=True,
                        text=True,
                        timeout=300
                    )
                    if result.returncode == 0:
                        logger.info("FFmpeg installed successfully with pacman")
                        return True, "FFmpeg installed successfully using pacman"
                except Exception as e:
                    logger.warning(f"pacman install error: {e}")
            else:
                return False, "sudo access required. Run: sudo pacman -S ffmpeg"
        
        # Try snap as fallback
        if shutil.which('snap'):
            logger.info("Trying snap as fallback...")
            try:
                result = subprocess.run(
                    ['sudo', 'snap', 'install', 'ffmpeg'],
                    capture_output=True,
                    text=True,
                    timeout=300
                )
                if result.returncode == 0:
                    return True, "FFmpeg installed successfully using snap"
            except Exception as e:
                logger.warning(f"snap install error: {e}")
        
        return False, self._get_ffmpeg_manual_instructions('linux')
    
    def _check_sudo_access(self) -> bool:
        """Check if we have passwordless sudo access."""
        try:
            result = subprocess.run(
                ['sudo', '-n', 'true'],
                capture_output=True,
                timeout=5
            )
            return result.returncode == 0
        except:
            return False
    
    def install_audio_dependencies(self, password: str = None) -> Tuple[bool, str]:
        """Install audio system dependencies for PyAudio compilation."""
        
        if self.is_windows:
            return True, "Windows: PyAudio wheels are pre-compiled"
        
        if self.is_linux:
            deps = self.audio_system_deps['linux']
            logger.info(f"Installing Linux audio dependencies: {', '.join(deps)}")
            
            # Try with password if provided
            if password:
                try:
                    cmd = ['sudo', '-S', 'apt-get', 'install', '-y'] + deps
                    result = subprocess.run(
                        cmd,
                        input=f"{password}\n",
                        text=True,
                        capture_output=True,
                        timeout=300
                    )
                    if result.returncode == 0:
                        return True, f"Audio dependencies installed: {', '.join(deps)}"
                    else:
                        return False, f"Failed to install audio dependencies: {result.stderr}"
                except subprocess.TimeoutExpired:
                    return False, "Audio dependencies installation timed out"
                except Exception as e:
                    return False, f"Error installing audio dependencies: {e}"
            
            # Try passwordless sudo
            elif self._check_sudo_access():
                try:
                    cmd = ['sudo', 'apt-get', 'install', '-y'] + deps
                    result = subprocess.run(
                        cmd,
                        capture_output=True,
                        text=True,
                        timeout=300
                    )
                    if result.returncode == 0:
                        return True, f"Audio dependencies installed: {', '.join(deps)}"
                    else:
                        return False, f"Failed to install audio dependencies: {result.stderr}"
                except Exception as e:
                    return False, f"Error installing audio dependencies: {e}"
            
            else:
                return False, f"Sudo required. Install manually: sudo apt-get install -y {' '.join(deps)}"
        
        elif self.is_macos:
            deps = self.audio_system_deps['macos']
            logger.info(f"Installing macOS audio dependencies: {', '.join(deps)}")
            
            if shutil.which('brew'):
                try:
                    for dep in deps:
                        result = subprocess.run(
                            ['brew', 'install', dep],
                            capture_output=True,
                            text=True,
                            timeout=300
                        )
                        if result.returncode != 0:
                            logger.warning(f"Failed to install {dep}: {result.stderr}")
                    return True, f"Audio dependencies installed via Homebrew"
                except Exception as e:
                    return False, f"Error installing audio dependencies: {e}"
            else:
                return False, "Homebrew not found. Install with: /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        
        return False, "Unsupported platform for audio dependencies"
    
    def _install_ffmpeg_macos(self) -> Tuple[bool, str]:
        """Install FFmpeg on macOS."""
        
        # Try Homebrew (most common on macOS)
        if shutil.which('brew'):
            logger.info("Installing FFmpeg using Homebrew...")
            safe_print("Installing FFmpeg via Homebrew (this may take a few minutes)...")
            try:
                result = subprocess.run(
                    ['brew', 'install', 'ffmpeg'],
                    capture_output=True,
                    text=True,
                    timeout=600  # 10 minutes - ffmpeg has many dependencies
                )
                if result.returncode == 0:
                    logger.info("FFmpeg installed successfully with Homebrew")
                    return True, "FFmpeg installed successfully using Homebrew"
                else:
                    # Check if already installed
                    if 'already installed' in result.stderr.lower():
                        return True, "FFmpeg is already installed via Homebrew"
                    logger.warning(f"Homebrew install failed: {result.stderr}")
            except subprocess.TimeoutExpired:
                logger.warning("Homebrew install timed out - ffmpeg may still be installing")
            except Exception as e:
                logger.warning(f"Homebrew install error: {e}")
        else:
            # Homebrew not installed - try to install it first
            logger.info("Homebrew not found. Attempting to install Homebrew first...")
            safe_print("Homebrew not found. Installing Homebrew first...")
            
            try:
                # Install Homebrew
                homebrew_install_cmd = '/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
                result = subprocess.run(
                    homebrew_install_cmd,
                    shell=True,
                    capture_output=True,
                    text=True,
                    timeout=600
                )
                
                if result.returncode == 0 or shutil.which('brew'):
                    logger.info("Homebrew installed, now installing FFmpeg...")
                    # Now install ffmpeg
                    result = subprocess.run(
                        ['brew', 'install', 'ffmpeg'],
                        capture_output=True,
                        text=True,
                        timeout=600
                    )
                    if result.returncode == 0:
                        return True, "Homebrew and FFmpeg installed successfully"
            except Exception as e:
                logger.warning(f"Failed to install Homebrew: {e}")
        
        # Try MacPorts as fallback
        if shutil.which('port'):
            logger.info("Trying MacPorts as fallback...")
            try:
                result = subprocess.run(
                    ['sudo', 'port', 'install', 'ffmpeg'],
                    capture_output=True,
                    text=True,
                    timeout=600
                )
                if result.returncode == 0:
                    return True, "FFmpeg installed successfully using MacPorts"
            except Exception as e:
                logger.warning(f"MacPorts install error: {e}")
        
        return False, self._get_ffmpeg_manual_instructions('macos')
    
    def _try_command(self, command: List[str]) -> bool:
        """Check if a command is available."""
        try:
            result = subprocess.run(command, capture_output=True, timeout=10)
            return result.returncode == 0
        except (subprocess.TimeoutExpired, FileNotFoundError):
            return False
    
    def _get_ffmpeg_manual_instructions(self, platform: str) -> str:
        """Get manual installation instructions for FFmpeg."""
        instructions = {
            'windows': [
                "Option 1: winget install ffmpeg (recommended)",
                "Option 2: choco install ffmpeg (if Chocolatey installed)",
                "Option 3: Download from https://www.gyan.dev/ffmpeg/builds/",
                "   - Download 'ffmpeg-release-essentials.zip'",
                "   - Extract to C:\\ffmpeg",
                "   - Add C:\\ffmpeg\\bin to your PATH environment variable",
                "After installation, restart your terminal and verify: ffmpeg -version"
            ],
            'linux': [
                "Ubuntu/Debian: sudo apt-get update && sudo apt-get install -y ffmpeg",
                "Fedora/RHEL: sudo dnf install -y ffmpeg",
                "Arch Linux: sudo pacman -S ffmpeg",
                "Snap: sudo snap install ffmpeg",
                "After installation, verify: ffmpeg -version"
            ],
            'macos': [
                "Option 1: brew install ffmpeg (recommended - install Homebrew first)",
                "   - Install Homebrew: /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"",
                "Option 2: sudo port install ffmpeg (MacPorts)",
                "After installation, verify: ffmpeg -version"
            ]
        }
        
        steps = instructions.get(platform, instructions['windows'])
        return f"Manual FFmpeg installation required for {platform}:\n" + "\n".join(f"  {step}" for step in steps)
    
    def install_requirements(self, requirements_file: str = None) -> Tuple[bool, List[str]]:
        """
        Install all packages from requirements.txt.
        
        Args:
            requirements_file: Path to requirements.txt (auto-detected if not provided)
        
        Returns:
            (success, list of failed packages)
        """
        if requirements_file is None:
            # Auto-detect requirements.txt
            script_dir = Path(__file__).parent
            requirements_file = script_dir / "requirements.txt"
            
            if not requirements_file.exists():
                logger.warning("requirements.txt not found")
                return True, []
        
        requirements_path = Path(requirements_file)
        if not requirements_path.exists():
            logger.error(f"Requirements file not found: {requirements_file}")
            return False, [f"File not found: {requirements_file}"]
        
        logger.info(f"Installing packages from {requirements_file}...")
        safe_print(f"\n📦 Installing Python packages from requirements.txt...")
        
        failed_packages = []
        
        try:
            # First, install PyTorch and Torchaudio with intelligent version selection
            # This must be done BEFORE installing speechbrain to avoid version conflicts
            pytorch_success, pytorch_msg = self.install_pytorch()
            if not pytorch_success:
                logger.warning(f"PyTorch installation warning: {pytorch_msg}")
                failed_packages.append(f"PyTorch: {pytorch_msg}")
            
            # Now install all requirements (torch/torchaudio versions already satisfied)
            result = subprocess.run(
                [sys.executable, '-m', 'pip', 'install', '-r', str(requirements_path)],
                capture_output=True,
                text=True,
                timeout=600  # 10 minutes
            )
            
            if result.returncode == 0:
                logger.info("All requirements installed successfully")
                safe_print("✓ All Python packages installed successfully")
                return True, []
            else:
                logger.warning(f"Some packages may have failed: {result.stderr}")
                # Try to parse which packages failed
                for line in result.stderr.split('\n'):
                    if 'error' in line.lower() or 'failed' in line.lower():
                        failed_packages.append(line.strip())
                
        except subprocess.TimeoutExpired:
            logger.error("Requirements installation timed out")
            failed_packages.append("Installation timed out")
        except Exception as e:
            logger.error(f"Failed to install requirements: {e}")
            failed_packages.append(str(e))
        
        return len(failed_packages) == 0, failed_packages
    
    def install_system_dependencies(self, password: str = None) -> Tuple[bool, List[str]]:
        """
        Install system-level dependencies (like ffmpeg).
        
        Returns:
            (success, list of messages)
        """
        messages = []
        all_success = True
        
        safe_print("\n🔧 Checking system dependencies...")
        
        for cmd_name, package_name in self.system_requirements.items():
            if self.check_system_command(cmd_name):
                msg = f"✓ {cmd_name} is already installed"
                logger.info(msg)
                safe_print(msg)
                messages.append(msg)
            else:
                msg = f"✗ {cmd_name} not found, attempting installation..."
                logger.info(msg)
                safe_print(msg)
                
                success, install_msg = self.install_system_package(cmd_name, password)
                messages.append(install_msg)
                
                if not success:
                    all_success = False
                    safe_print(f"⚠️  {install_msg}")
                else:
                    safe_print(f"✓ {install_msg}")
        
        return all_success, messages
    
    def auto_install(self, force_reinstall: bool = False, install_from_requirements: bool = True, password: str = None) -> List[Tuple[str, bool, str]]:
        """
        Auto-install missing dependencies.
        
        Args:
            force_reinstall: Whether to reinstall even if already installed
            install_from_requirements: Whether to install from requirements.txt first
        
        Returns:
            List of (package_name, success, message) tuples
        """
        results = []
        
        logger.info("Starting auto-installation of dependencies...")
        safe_print("\n" + "="*60)
        safe_print("🚀 AUTO-INSTALLER FOR VOICE ANALYSIS DEPENDENCIES")
        safe_print("="*60)
        safe_print(f"Platform: {platform.system()} ({platform.machine()})")
        safe_print(f"Python: {platform.python_version()}")
        safe_print("="*60)
        
        # Step 1: Install system dependencies first (FFmpeg + Audio deps)
        safe_print("\n📋 Step 1/3: System Dependencies")
        safe_print("-"*40)
        
        # Install FFmpeg
        for command_name in self.system_requirements:
            if not force_reinstall and self.check_system_command(command_name):
                logger.info(f"✓ {command_name} system command available")
                safe_print(f"✓ {command_name} is already installed")
                results.append((f"{command_name} (system)", True, "Already available"))
            else:
                logger.warning(f"✗ {command_name} system command not found")
                safe_print(f"⏳ Installing {command_name}...")
                success, message = self.install_system_package(command_name, password)
                results.append((f"{command_name} (system)", success, message))
                if success:
                    safe_print(f"✓ {message}")
                else:
                    safe_print(f"⚠️  {message}")
        
        # Install audio system dependencies (for PyAudio)
        safe_print(f"⏳ Installing audio system dependencies...")
        audio_success, audio_message = self.install_audio_dependencies(password)
        results.append(("audio_system_deps", audio_success, audio_message))
        if audio_success:
            safe_print(f"✓ {audio_message}")
        else:
            safe_print(f"⚠️  {audio_message}")
        
        # Install PyAudio with platform-specific handling
        pyaudio_success, pyaudio_message = self.install_pyaudio()
        results.append(("pyaudio", pyaudio_success, pyaudio_message))
        if pyaudio_success:
            safe_print(f"✓ {pyaudio_message}")
        else:
            safe_print(f"⚠️  {pyaudio_message}")
        
        # Step 2: Install from requirements.txt
        if install_from_requirements:
            safe_print("\n📋 Step 2/3: Python Packages (requirements.txt)")
            safe_print("-"*40)
            
            req_success, req_failures = self.install_requirements()
            if req_success:
                results.append(("requirements.txt", True, "All packages installed"))
            else:
                results.append(("requirements.txt", False, f"Failed: {', '.join(req_failures)}"))
        
        # Step 3: Verify voice analysis packages
        safe_print("\n📋 Step 3/3: Verifying Voice Analysis Packages")
        safe_print("-"*40)
        
        for module_name, package_name in self.required_packages.items():
            if self.check_package(module_name):
                logger.info(f"✓ {package_name} available")
                safe_print(f"✓ {package_name}")
                results.append((package_name, True, "Available"))
            else:
                if force_reinstall:
                    logger.info(f"Installing {package_name}...")
                    safe_print(f"⏳ Installing {package_name}...")
                    success, message = self.install_package(package_name)
                    results.append((package_name, success, message))
                    if success:
                        safe_print(f"✓ {package_name} installed")
                    else:
                        safe_print(f"✗ {package_name} failed: {message}")
                else:
                    # Should have been installed by requirements.txt
                    logger.warning(f"✗ {package_name} not available")
                    safe_print(f"⚠️  {package_name} not available")
                    results.append((package_name, False, "Not available after requirements install"))
        
        return results
    
    def get_installation_summary(self, results: List[Tuple[str, bool, str]]) -> Tuple[int, int, List[str]]:
        """
        Get summary of installation results.
        
        Returns:
            (success_count, total_count, warnings)
        """
        success_count = sum(1 for _, success, _ in results if success)
        total_count = len(results)
        
        warnings = []
        for package, success, message in results:
            if not success:
                if "(system)" in package:
                    warnings.append(f"⚠️  {message}")
                else:
                    warnings.append(f"❌ Failed to install {package}: {message}")
        
        return success_count, total_count, warnings
    
    def print_results(self, results: List[Tuple[str, bool, str]]) -> None:
        """Print installation results in a formatted way."""
        safe_print("\n" + "="*60)
        safe_print("VOICE ANALYSIS DEPENDENCIES INSTALLATION")
        safe_print("="*60)
        
        success_count, total_count, warnings = self.get_installation_summary(results)
        
        for package, success, message in results:
            status = "✓" if success else "✗"
            safe_print(f"{status} {package}: {message}")
        
        safe_print(f"\nSummary: {success_count}/{total_count} dependencies installed")
        
        if warnings:
            safe_print("\nWarnings/Issues:")
            for warning in warnings:
                safe_print(f"  {warning}")
        
        if success_count == total_count:
            safe_print("\n🎉 All dependencies installed successfully!")
        else:
            safe_print(f"\n⚠️  {total_count - success_count} dependencies need attention")
        
        safe_print("="*60)


def auto_install_dependencies(force_reinstall: bool = False, skip_requirements: bool = False, password: str = None) -> bool:
    """
    Auto-install all voice analysis dependencies.
    
    Args:
        force_reinstall: Whether to reinstall even if already installed
        skip_requirements: Whether to skip requirements.txt installation
    
    Returns:
        True if all critical dependencies are installed
    """
    installer = AutoInstaller()
    results = installer.auto_install(force_reinstall, install_from_requirements=not skip_requirements, password=password)
    installer.print_results(results)
    
    # Check if critical packages are installed
    critical_packages = ['whisper', 'speechbrain', 'torch', 'torchaudio', 'soundfile']
    missing_critical = []
    
    for package in critical_packages:
        if not installer.check_package(package):
            missing_critical.append(package)
    
    if missing_critical:
        safe_print(f"\n❌ Critical packages missing: {', '.join(missing_critical)}")
        safe_print("   Try running: pip install openai-whisper speechbrain torch torchaudio soundfile")
        return False
    
    # Check FFmpeg (critical for Whisper)
    if not installer.check_system_command('ffmpeg'):
        safe_print("\n" + "="*60)
        safe_print("⚠️  WARNING: FFmpeg not found!")
        safe_print("="*60)
        safe_print("FFmpeg is REQUIRED for Whisper speech-to-text to work.")
        safe_print("Whisper will fail with '[WinError 2]' or similar errors without it.")
        safe_print("")
        safe_print("Please install FFmpeg manually and restart your terminal:")
        safe_print(installer._get_ffmpeg_manual_instructions(
            'windows' if installer.is_windows else ('macos' if installer.is_macos else 'linux')
        ))
        safe_print("="*60)
        return False
    else:
        safe_print("\n✓ FFmpeg is available")
    
    return True


def install_ffmpeg_only(password: str = None) -> bool:
    """Install only FFmpeg (useful for quick fix)."""
    installer = AutoInstaller()
    
    if installer.check_system_command('ffmpeg'):
        safe_print("✓ FFmpeg is already installed")
        # Show version
        try:
            result = subprocess.run(['ffmpeg', '-version'], capture_output=True, text=True, timeout=10)
            first_line = result.stdout.split('\n')[0] if result.stdout else "Unknown version"
            safe_print(f"  Version: {first_line}")
        except:
            pass
        return True
    
    safe_print("Installing FFmpeg...")
    success, message = installer.install_system_package('ffmpeg', password)
    
    if success:
        safe_print(f"✓ {message}")
    else:
        safe_print(f"✗ {message}")
    
    return success


def verify_installation() -> bool:
    """Comprehensive verification of all dependencies."""
    installer = AutoInstaller()
    all_ok = True
    
    safe_print("\n" + "="*60)
    safe_print("🔍 COMPREHENSIVE DEPENDENCY VERIFICATION")
    safe_print("="*60)
    
    # System Information
    safe_print("\n📋 System Information:")
    safe_print(f"  Platform: {platform.system()} ({installer.architecture})")
    safe_print(f"  Python: {platform.python_version()}")
    has_gpu, cuda_available = installer.detect_nvidia_gpu()
    safe_print(f"  NVIDIA GPU: {'Yes' if has_gpu else 'No'}")
    safe_print(f"  CUDA Available: {'Yes' if cuda_available else 'No'}")
    if installer.is_macos:
        safe_print(f"  Homebrew Prefix: {installer.homebrew_prefix}")
    
    # Check Python packages
    safe_print("\n📦 Python Packages:")
    for module_name, package_name in installer.required_packages.items():
        if installer.check_package(module_name):
            # Try to get version
            try:
                mod = importlib.import_module(module_name)
                version = getattr(mod, '__version__', 'unknown')
                safe_print(f"  ✓ {package_name} (v{version})")
            except:
                safe_print(f"  ✓ {package_name}")
        else:
            safe_print(f"  ✗ {package_name} - NOT INSTALLED")
            all_ok = False
    
    # Check PyAudio specifically
    safe_print("\n🎤 PyAudio Verification:")
    try:
        import pyaudio
        safe_print(f"  ✓ PyAudio imported successfully")
        # Try to list audio devices
        try:
            p = pyaudio.PyAudio()
            device_count = p.get_device_count()
            safe_print(f"  ✓ Audio devices found: {device_count}")
            p.terminate()
        except Exception as e:
            safe_print(f"  ⚠️  PyAudio device enumeration failed: {e}")
    except ImportError:
        safe_print("  ✗ PyAudio - NOT INSTALLED")
        all_ok = False
    except Exception as e:
        safe_print(f"  ⚠️  PyAudio error: {e}")
    
    # Check PyTorch and CUDA
    safe_print("\n🔥 PyTorch Verification:")
    try:
        import torch
        safe_print(f"  ✓ PyTorch {torch.__version__}")
        
        # Test tensor creation
        try:
            tensor = torch.zeros(2, 3)
            safe_print(f"  ✓ Tensor creation works")
        except Exception as e:
            safe_print(f"  ✗ Tensor creation failed: {e}")
            all_ok = False
        
        # Check CUDA
        if torch.cuda.is_available():
            safe_print(f"  ✓ CUDA available: {torch.cuda.get_device_name(0)}")
            safe_print(f"  ✓ CUDA version: {torch.version.cuda}")
        elif has_gpu:
            safe_print(f"  ⚠️  NVIDIA GPU detected but CUDA not available in PyTorch")
        else:
            safe_print(f"  ℹ️  CUDA not available (CPU-only installation)")
        
        # Check MPS (Apple Silicon)
        if installer.is_macos and hasattr(torch.backends, 'mps'):
            if torch.backends.mps.is_available():
                safe_print(f"  ✓ MPS (Metal) available for Apple Silicon")
            elif installer.is_arm64:
                safe_print(f"  ⚠️  Apple Silicon detected but MPS not available")
    except ImportError:
        safe_print("  ✗ PyTorch - NOT INSTALLED")
        all_ok = False
    except Exception as e:
        safe_print(f"  ✗ PyTorch error: {e}")
        all_ok = False
    
    # Check Torchaudio
    safe_print("\n🔊 Torchaudio Verification:")
    try:
        import torchaudio
        safe_print(f"  ✓ Torchaudio {torchaudio.__version__}")
    except ImportError:
        safe_print("  ✗ Torchaudio - NOT INSTALLED")
        all_ok = False
    except Exception as e:
        safe_print(f"  ✗ Torchaudio error: {e}")
        all_ok = False
    
    # Check FFmpeg
    safe_print("\n🎬 FFmpeg Verification:")
    if installer.check_system_command('ffmpeg'):
        safe_print("  ✓ FFmpeg found in PATH")
        try:
            result = subprocess.run(['ffmpeg', '-version'], capture_output=True, text=True, timeout=10)
            first_line = result.stdout.split('\n')[0] if result.stdout else ""
            if first_line:
                safe_print(f"    {first_line}")
        except:
            pass
    else:
        safe_print("  ✗ FFmpeg - NOT INSTALLED or not in PATH")
        all_ok = False
    
    # Check Whisper
    safe_print("\n🗣️ Whisper Verification:")
    try:
        import whisper
        safe_print(f"  ✓ Whisper imported successfully")
    except ImportError:
        safe_print("  ✗ Whisper - NOT INSTALLED")
        all_ok = False
    except Exception as e:
        safe_print(f"  ⚠️  Whisper import warning: {e}")
    
    # Check SpeechBrain
    safe_print("\n🧠 SpeechBrain Verification:")
    try:
        import speechbrain
        safe_print(f"  ✓ SpeechBrain {speechbrain.__version__}")
    except ImportError:
        safe_print("  ✗ SpeechBrain - NOT INSTALLED")
        all_ok = False
    except Exception as e:
        safe_print(f"  ⚠️  SpeechBrain import warning: {e}")
    
    # Summary
    safe_print("\n" + "="*60)
    if all_ok:
        safe_print("✓ All dependencies are properly installed!")
        safe_print("  Voice analysis is ready to use.")
    else:
        safe_print("⚠️  Some dependencies are missing or have issues.")
        safe_print("  Run: python auto_installer.py --force")
    safe_print("="*60)
    
    return all_ok


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Auto-installer for voice analysis dependencies (Windows, Linux, macOS)"
    )
    parser.add_argument(
        '--force', '-f',
        action='store_true',
        help='Force reinstall all packages'
    )
    parser.add_argument(
        '--verify', '-v',
        action='store_true',
        help='Only verify installation, do not install'
    )
    parser.add_argument(
        '--ffmpeg-only',
        action='store_true',
        help='Only install FFmpeg'
    )
    parser.add_argument(
        '--skip-requirements',
        action='store_true',
        help='Skip requirements.txt installation'
    )
    parser.add_argument(
        '--password', '-p',
        type=str,
        help='Sudo password for system package installation (Linux only)'
    )
    
    args = parser.parse_args()
    
    if args.verify:
        success = verify_installation()
    elif args.ffmpeg_only:
        success = install_ffmpeg_only(args.password)
    else:
        success = auto_install_dependencies(
            force_reinstall=args.force,
            skip_requirements=args.skip_requirements,
            password=args.password
        )
    
    sys.exit(0 if success else 1)
