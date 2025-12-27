"""
Dependency installer module for handling special dependencies like PyAudio.
This module ensures that all required dependencies are properly installed,
with special handling for packages that may need system-level installation.
"""

import subprocess
import sys
import logging
import platform
from typing import List, Optional

logger = logging.getLogger(__name__)

def run_pip_command(command: List[str]) -> bool:
    """Run a pip command and return True if successful."""
    try:
        result = subprocess.run(
            [sys.executable, "-m", "pip"] + command,
            capture_output=True,
            text=True,
            check=True
        )
        logger.info(f"Successfully ran: pip {' '.join(command)}")
        return True
    except subprocess.CalledProcessError as e:
        logger.error(f"Failed to run pip {' '.join(command)}: {e}")
        logger.error(f"stderr: {e.stderr}")
        return False

def install_pyaudio() -> bool:
    """Install PyAudio with platform-specific handling."""
    system = platform.system().lower()
    
    if system == "windows":
        # On Windows, try to install PyAudio directly first
        if run_pip_command(["install", "pyaudio>=0.2.14"]):
            return True
        
        # If that fails, try installing from wheel files
        logger.info("Attempting to install PyAudio from alternative sources...")
        
        # Try installing without version constraint
        return run_pip_command(["install", "pyaudio"])
    
    elif system == "linux":
        # On Linux, may need to install system dependencies first
        logger.info("On Linux, PyAudio may require portaudio19-dev")
        logger.info("If installation fails, run: sudo apt-get install portaudio19-dev")
        return run_pip_command(["install", "pyaudio>=0.2.14"])
    
    elif system == "darwin":  # macOS
        # On macOS, may need to install portaudio first
        logger.info("On macOS, PyAudio may require portaudio")
        logger.info("If installation fails, run: brew install portaudio")
        return run_pip_command(["install", "pyaudio>=0.2.14"])
    
    else:
        # Default installation attempt
        return run_pip_command(["install", "pyaudio>=0.2.14"])

def ensure_dependencies() -> None:
    """
    Ensure all required dependencies are installed.
    This function checks and installs missing dependencies with special handling for problematic packages.
    """
    logger.info("Checking and installing dependencies...")
    
    # List of dependencies to check/install
    dependencies = [
        "fastapi>=0.110.0",
        "uvicorn[standard]>=0.27.0", 
        "google-generativeai>=0.8.0",
        "pydantic>=2.6.0",
        "pydantic-settings>=2.2.0",
        "python-dotenv>=1.0.0",
        "httpx>=0.27.0",
        "supabase>=2.3.0",
        "postgrest>=0.16.0",
        "gtts>=2.5.0",
        "numpy>=1.24.0",
        "scipy>=1.11.0",
        "pydub>=0.25.1"
    ]
    
    # Install regular dependencies
    for dep in dependencies:
        logger.info(f"Installing/Checking {dep}...")
        if not run_pip_command(["install", dep]):
            logger.warning(f"Failed to install {dep}")
    
    # Special handling for PyAudio
    logger.info("Installing PyAudio (may require special handling)...")
    if not install_pyaudio():
        logger.warning("Failed to install PyAudio. Audio features may be limited.")
    
    # Upgrade pip to latest version for better compatibility
    logger.info("Upgrading pip...")
    run_pip_command(["install", "--upgrade", "pip"])
    
    logger.info("Dependency installation process completed")

def check_python_version() -> bool:
    """Check if Python version is compatible."""
    version = sys.version_info
    if version.major < 3 or (version.major == 3 and version.minor < 8):
        logger.error(f"Python {version.major}.{version.minor} is not supported. Please use Python 3.8 or higher.")
        return False
    logger.info(f"Python {version.major}.{version.minor}.{version.micro} is compatible")
    return True

def main():
    """Main function to run dependency installation."""
    if not check_python_version():
        sys.exit(1)
    
    try:
        ensure_dependencies()
        logger.info("All dependencies have been processed successfully")
    except Exception as e:
        logger.error(f"Error during dependency installation: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
