# Cross-Platform Python Backend Setup

This document explains how the backend manager handles Python installation across different platforms.

## Supported Platforms

- **Linux (Ubuntu/Debian, CentOS/RHEL, Fedora, Arch, openSUSE)**
- **Windows (10/11)**
- **macOS (Intel/Apple Silicon)**

## Fully Automatic Installation Process

The backend manager will automatically handle **ALL** dependency installation across all platforms:

### 1. **Python Detection & Installation**
- Detects existing Python 3.12 installation
- Validates Python version and required modules (pip, venv)
- Installs missing components automatically using multiple methods

### 2. **System Dependencies**
- **Automatically installs audio libraries** (portaudio, ALSA, etc.)
- **Automatically installs development tools** (build-essential, Windows SDK, Xcode tools)
- **Automatically installs package managers** (Homebrew on macOS if missing)

### 3. **Error Recovery**
- Detects compilation errors for audio libraries
- Automatically installs missing system dependencies
- Retries package installation with system dependencies
- Provides platform-specific manual instructions only as last resort

## Platform-Specific Automatic Handling

### Ubuntu/Debian Linux
```bash
# Automatically handled:
1. python3.12 -m ensurepip --default-pip --upgrade
2. sudo apt update && sudo apt install -y python3-pip python3-venv
3. sudo apt install -y python3.12-pip python3.12-venv
4. sudo apt install -y pip python3-venv

# Audio dependencies (automatically installed when needed):
sudo apt install -y portaudio19-dev libasound2-dev libportaudio2 libportaudiocpp0
sudo apt install -y python3-dev build-essential libsndfile1-dev
```

### Windows
```bash
# Automatically handled:
1. py -3.12 -m ensurepip --default-pip --upgrade
2. winget install Python.pip --silent --accept-package-agreements
3. choco install pip --yes (if Chocolatey available)

# Audio dependencies (automatically installed when needed):
winget install Microsoft.WindowsSDK --silent --accept-package-agreements
winget install Microsoft.VisualStudio.2022.BuildTools --silent --accept-package-agreements --add Microsoft.VisualStudio.Workload.VCTools
```

### macOS
```bash
# Automatically handled:
1. python3.12 -m ensurepip --default-pip --upgrade
2. brew install python-pip --quiet
3. Automatic Homebrew installation if missing

# Audio dependencies (automatically installed when needed):
brew install portaudio libsndfile --quiet
```

## Audio Library Issues (Fully Automatic)

### Common Errors
```bash
# Linux:
src/pyaudio/device_api.c:9:10: fatal error: portaudio.h: No such file or directory

# Windows:
error: Microsoft Visual C++ 14.0 or greater is required

# macOS:
portaudio.h: No such file or directory
```

### Automatic Fix Process
The backend manager automatically detects these errors and:

1. **Identifies the platform** (Linux/Windows/macOS)
2. **Installs required system dependencies** automatically
3. **Retries the pip installation** with system dependencies
4. **Continues with backend startup** if successful
5. **Provides manual instructions only if all automatic methods fail**

### Expected Automatic Behavior
```bash
[Backend] 🎵 Audio library installation detected!
[Backend] This is common on all systems. Installing system dependencies...
[Backend] Installing audio system dependencies...
[Backend] Installing: sudo apt install -y portaudio19-dev libasound2-dev libportaudio2 libportaudiocpp0
[Backend] ✓ All audio system dependencies installed successfully
[Backend] ✓ Audio system dependencies installed, retrying pip install...
[Backend] ✓ Dependencies installed successfully after adding system deps!
```

## Manual Installation (Never Required)

The system is designed to be **fully automatic**. Manual installation is only needed as a last resort if all automatic methods fail.

### Linux Manual (Last Resort)
```bash
sudo apt install portaudio19-dev libasound2-dev python3-dev build-essential
pip install -r requirements.txt
```

### Windows Manual (Last Resort)
```bash
winget install Microsoft.WindowsSDK --silent
winget install Microsoft.VisualStudio.2022.BuildTools --silent --add Microsoft.VisualStudio.Workload.VCTools
pip install -r requirements.txt
```

### macOS Manual (Last Resort)
```bash
brew install portaudio libsndfile
pip install -r requirements.txt
```

## Environment Variables

The backend reads configuration from `.env` file in the backend directory:

```bash
# Required API Keys
GEMINI_API_KEY=your_gemini_key
DEEPSEEK_API_KEY=your_deepseek_key
DEEPGRAM_API_KEY=your_deepgram_key
ELEVEN_LABS_API_KEY=your_elevenlabs_key
GLADIA_API_KEY=your_gladia_key

# Supabase Configuration
SUPABASE_URL=your_supabase_url
SUPABASE_KEY=your_supabase_key

# Server Configuration
HOST=0.0.0.0
PORT=8000
ENVIRONMENT=development
```

## Virtual Environment

The backend automatically creates and uses a Python virtual environment:

```bash
# Virtual environment location: backend/venv/
# Dependencies installed from: backend/requirements.txt
# All system dependencies handled automatically
```

## Logs and Debugging

The backend manager provides detailed logs during setup:

```bash
[Backend] Python found: python3.12
[Backend]   Detected Python version: 3.12
[Backend]   ✓ Python version is compatible
[Backend]   ✗ pip module not found
[Backend] Python modules missing, attempting to install missing components...
[Backend] Attempting to install missing Python modules on Linux...
[Backend] Trying ensurepip to bootstrap pip...
[Backend] ✓ pip installed via ensurepip
[Backend]   ✓ pip found: pip 24.0 from /usr/lib/python3.12/site-packages/pip (python 3.12)
[Backend]   ✓ venv module available
[Backend] ✓ Python modules installed successfully

# Audio error handling (fully automatic):
[Backend] 🎵 Audio library installation detected!
[Backend] This is common on all systems. Installing system dependencies...
[Backend] Installing audio system dependencies...
[Backend] ✓ All audio system dependencies installed successfully
[Backend] ✓ Dependencies installed successfully after adding system deps!
```

## Health Check

The backend includes a health endpoint:

```bash
curl http://localhost:8000/health
```

Should return: `{"status": "healthy"}`

## Common Issues and Solutions (All Automatic)

### 1. Python 3.12 not found
- **Automatic**: System attempts to install Python 3.12 automatically
- **Manual**: Install from https://www.python.org/downloads/ (rarely needed)

### 2. pip module not found
- **Automatic**: Auto-fixed via `ensurepip` or package managers
- **Manual**: `python3.12 -m ensurepip --default-pip` (rarely needed)

### 3. Audio library compilation fails
- **Automatic**: System detects and installs required dependencies automatically
- **Manual**: Platform-specific commands (rarely needed)

### 4. Package manager not available
- **Automatic**: System installs missing package managers (Homebrew on macOS)
- **Manual**: Install package manager manually (rarely needed)

### 5. Backend won't start
- **Check logs**: Look for specific error messages
- **Verify dependencies**: System handles this automatically
- **Check ports**: Make sure port 8000 is not in use

## Summary

The backend manager is designed to be **100% automatic** across all platforms:

✅ **No manual installation required**  
✅ **No manual dependency management**  
✅ **No platform-specific setup needed**  
✅ **Automatic error detection and recovery**  
✅ **Cross-platform compatibility**  

Simply run the application and it will handle everything automatically.
