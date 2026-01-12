#!/bin/bash

# ============================================
# AUTO-INSTALLER FOR VOICE ANALYSIS
# Works on Linux and macOS
# Handles shared filesystem symlink issues
# ============================================

set -e

echo ""
echo "============================================"
echo "  AUTO-INSTALLER FOR VOICE ANALYSIS"
echo "  Linux / macOS Compatible"
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
    OS_TYPE="linux"
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

# Check if we're on a shared filesystem (VMware, VirtualBox, etc.)
USE_COPIES=""
if [[ "$SCRIPT_DIR" == /mnt/hgfs/* ]] || [[ "$SCRIPT_DIR" == /media/sf_* ]]; then
    echo ""
    echo "Shared filesystem detected - using file copies instead of symlinks"
    USE_COPIES="--copies"
fi

# Function to test symlink support
test_symlink_support() {
    local test_dir="$SCRIPT_DIR/.symlink_test_$$"
    local test_link="$SCRIPT_DIR/.symlink_test_link_$$"
    mkdir -p "$test_dir" 2>/dev/null || return 1
    if ln -s "$test_dir" "$test_link" 2>/dev/null; then
        rm -f "$test_link" 2>/dev/null
        rmdir "$test_dir" 2>/dev/null
        return 0
    else
        rmdir "$test_dir" 2>/dev/null
        return 1
    fi
}

# Test symlink support if not already determined
if [ -z "$USE_COPIES" ]; then
    if ! test_symlink_support; then
        echo ""
        echo "Symlinks not supported on this filesystem - using file copies"
        USE_COPIES="--copies"
    fi
fi

# Create virtual environment if it doesn't exist or is from wrong OS
NEED_VENV=false
if [ ! -d "venv" ]; then
    NEED_VENV=true
elif [ ! -f "venv/bin/activate" ]; then
    echo ""
    echo "Existing venv is not Linux-compatible (likely created on Windows)"
    echo "Removing and recreating..."
    rm -rf venv
    NEED_VENV=true
fi

if [ "$NEED_VENV" = true ]; then
    echo ""
    echo "Creating virtual environment..."
    if [ -n "$USE_COPIES" ]; then
        python3 -m venv $USE_COPIES venv
    else
        python3 -m venv venv
    fi
    if [ $? -eq 0 ]; then
        echo "Virtual environment created successfully."
    else
        echo "ERROR: Failed to create virtual environment"
        exit 1
    fi
fi

# Activate virtual environment
echo ""
echo "Activating virtual environment..."
source venv/bin/activate

# Upgrade pip
echo ""
echo "Upgrading pip..."
pip install --upgrade pip

# Install requirements
echo ""
echo "============================================"
echo "Installing dependencies from requirements.txt..."
echo "============================================"
echo ""
pip install -r requirements.txt

# Run the auto-installer for additional dependencies
echo ""
echo "============================================"
echo "Running Auto-Installer for additional dependencies..."
echo "============================================"
echo ""

python auto_installer.py "$@"
INSTALL_RESULT=$?

# Verify installation
echo ""
echo "============================================"
echo "Verifying installation..."
echo "============================================"
echo ""

python -c "import fastapi; print(f'  [OK] FastAPI: {fastapi.__version__}')" 2>/dev/null || echo "  [FAIL] FastAPI"
python -c "import uvicorn; print(f'  [OK] Uvicorn: {uvicorn.__version__}')" 2>/dev/null || echo "  [FAIL] Uvicorn"
python -c "import pydantic; print(f'  [OK] Pydantic: {pydantic.__version__}')" 2>/dev/null || echo "  [FAIL] Pydantic"
python -c "import numpy; print(f'  [OK] NumPy: {numpy.__version__}')" 2>/dev/null || echo "  [FAIL] NumPy"

echo ""
echo "============================================"
if [ $INSTALL_RESULT -eq 0 ]; then
    echo "  Installation Complete!"
    echo "============================================"
    echo ""
    echo "To start the server:"
    echo "  source venv/bin/activate"
    echo "  python main.py"
else
    echo "  Installation Had Issues"
    echo "============================================"
    echo ""
    echo "Some dependencies may not have installed correctly."
    echo "Common fixes:"
    if [[ "$OS_TYPE" == "macos" ]]; then
        echo "  - For FFmpeg: brew install ffmpeg"
    else
        echo "  - For FFmpeg: sudo apt-get install -y ffmpeg"
        echo "  - For PyAudio: sudo apt-get install -y portaudio19-dev"
    fi
fi
echo ""
echo "============================================"

exit $INSTALL_RESULT
