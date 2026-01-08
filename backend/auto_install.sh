#!/bin/bash

# ============================================
# AUTO-INSTALLER FOR VOICE ANALYSIS
# Works on Linux and macOS
# ============================================

set -e

echo ""
echo "============================================"
echo "  AUTO-INSTALLER FOR VOICE ANALYSIS"
echo "  Linux / macOS Compatible"
echo "============================================"
echo ""
echo "This will automatically install:"
echo "  - Python packages from requirements.txt"
echo "  - OpenAI Whisper (speech-to-text)"
echo "  - SpeechBrain (speaker analysis)"
echo "  - PyTorch (deep learning framework)"
echo "  - FFmpeg (audio processing) <-- CRITICAL"
echo "  - Other required packages"
echo ""
echo "============================================"
echo ""

# Detect OS
OS_TYPE="unknown"
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS_TYPE="linux"
    echo "Detected: Linux"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS_TYPE="macos"
    echo "Detected: macOS"
else
    echo "Warning: Unknown OS type: $OSTYPE"
    OS_TYPE="linux"  # Default to Linux-like behavior
fi

# Check Python installation
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python 3 is not installed"
    if [[ "$OS_TYPE" == "macos" ]]; then
        echo "Install with: brew install python3"
    else
        echo "Install with: sudo apt-get install python3 python3-pip python3-venv"
    fi
    exit 1
fi

echo "Python found: $(python3 --version)"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo ""
    echo "Creating virtual environment..."
    python3 -m venv venv
    echo "Virtual environment created successfully."
fi

# Activate virtual environment
echo ""
echo "Activating virtual environment..."
source venv/bin/activate

# Upgrade pip
echo ""
echo "Upgrading pip..."
pip install --upgrade pip

# Run the auto-installer
echo ""
echo "============================================"
echo "Running Auto-Installer..."
echo "============================================"
echo ""

python auto_installer.py "$@"
INSTALL_RESULT=$?

echo ""
echo "============================================"
if [ $INSTALL_RESULT -eq 0 ]; then
    echo "  Installation Complete!"
    echo "============================================"
    echo ""
    echo "All dependencies installed successfully."
    echo ""
    echo "To start the server:"
    echo "  source venv/bin/activate"
    echo "  python main.py"
else
    echo "  Installation Had Issues"
    echo "============================================"
    echo ""
    echo "Some dependencies may not have installed correctly."
    echo "Please review the output above and follow any instructions."
    echo ""
    echo "Common fixes:"
    if [[ "$OS_TYPE" == "macos" ]]; then
        echo "  - For FFmpeg: brew install ffmpeg"
        echo "  - Install Homebrew first if needed:"
        echo '    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
    else
        echo "  - For FFmpeg: sudo apt-get install -y ffmpeg"
        echo "  - For PyAudio: sudo apt-get install -y portaudio19-dev"
    fi
fi
echo ""
echo "============================================"

exit $INSTALL_RESULT
