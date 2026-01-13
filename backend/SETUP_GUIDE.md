# Voice Analysis Backend - Setup Guide

Comprehensive guide for setting up the voice analysis backend with all dependencies across Windows, Linux, and macOS.

## Table of Contents

- [Quick Start](#quick-start)
- [System Requirements](#system-requirements)
- [Automatic Installation](#automatic-installation)
- [Manual Installation](#manual-installation)
- [Platform-Specific Notes](#platform-specific-notes)
- [GPU/CUDA Setup](#gpucuda-setup)
- [Troubleshooting](#troubleshooting)
- [Verification](#verification)

---

## Quick Start

### One-Command Installation

**Windows:**
```batch
cd backend
auto_install.bat
```

**Linux/macOS:**
```bash
cd backend
chmod +x auto_install.sh
./auto_install.sh
```

**Python Script (All Platforms):**
```bash
cd backend
python auto_installer.py
```

---

## System Requirements

### Minimum Requirements

| Component | Requirement |
|-----------|-------------|
| Python | 3.8 or higher |
| RAM | 4 GB minimum, 8 GB recommended |
| Disk Space | 5 GB for dependencies |
| OS | Windows 10+, Ubuntu 20.04+, macOS 11+ |

### Required System Dependencies

| Dependency | Purpose |
|------------|---------|
| FFmpeg | Audio processing for Whisper |
| PortAudio | Audio I/O for PyAudio |
| libsndfile | Audio file reading |

---

## Automatic Installation

The auto-installer handles everything automatically:

### Basic Usage

```bash
# Standard installation
python auto_installer.py

# Non-interactive (CI/CD)
python auto_installer.py --yes

# Force reinstall all packages
python auto_installer.py --force

# Install only FFmpeg
python auto_installer.py --ffmpeg-only

# Verify installation
python auto_installer.py --verify
```

### What the Installer Does

1. **Detects Platform**: Windows, Linux (Debian/RHEL/Arch), macOS (Intel/Apple Silicon)
2. **Detects GPU**: NVIDIA GPU and CUDA availability
3. **Installs System Dependencies**: FFmpeg, PortAudio, libsndfile
4. **Installs PyTorch**: With automatic CUDA/CPU/MPS selection
5. **Installs PyAudio**: With platform-specific build flags
6. **Installs Python Packages**: From requirements.txt
7. **Verifies Installation**: Tests all components

### PyTorch Version Selection

The installer automatically selects the appropriate PyTorch version:

| Platform | GPU | PyTorch Version |
|----------|-----|-----------------|
| Linux/Windows | NVIDIA + CUDA | `torch==2.2.0+cu121` |
| Linux/Windows | No GPU | `torch==2.2.0+cpu` |
| macOS Intel | - | `torch==2.2.0` |
| macOS Apple Silicon | - | `torch==2.2.0` (MPS enabled) |

---

## Manual Installation

If automatic installation fails, follow these steps:

### Step 1: System Dependencies

#### Windows

```powershell
# Option 1: Using winget (recommended)
winget install ffmpeg

# Option 2: Using Chocolatey
choco install ffmpeg

# Option 3: Manual download
# Download from https://www.gyan.dev/ffmpeg/builds/
# Extract to C:\ffmpeg and add C:\ffmpeg\bin to PATH
```

#### Linux (Ubuntu/Debian)

```bash
sudo apt-get update
sudo apt-get install -y \
    ffmpeg \
    portaudio19-dev \
    python3-dev \
    build-essential \
    libsndfile1-dev
```

#### Linux (Fedora/RHEL)

```bash
sudo dnf install -y \
    ffmpeg \
    portaudio-devel \
    python3-devel \
    gcc gcc-c++ \
    libsndfile-devel
```

#### Linux (Arch)

```bash
sudo pacman -S ffmpeg portaudio libsndfile base-devel
```

#### macOS

```bash
# Install Homebrew if not present
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install dependencies
brew install portaudio ffmpeg libsndfile
```

### Step 2: Create Virtual Environment

```bash
cd backend
python -m venv venv

# Activate (Windows)
venv\Scripts\activate

# Activate (Linux/macOS)
source venv/bin/activate
```

### Step 3: Install PyTorch

Choose based on your system:

```bash
# CPU only (Linux/Windows)
pip install torch==2.2.0 torchaudio==2.2.0 --index-url https://download.pytorch.org/whl/cpu

# CUDA 12.1 (Linux/Windows with NVIDIA GPU)
pip install torch==2.2.0 torchaudio==2.2.0 --index-url https://download.pytorch.org/whl/cu121

# macOS (Intel or Apple Silicon)
pip install torch==2.2.0 torchaudio==2.2.0
```

### Step 4: Install PyAudio

```bash
# Windows (pre-built wheel)
pip install pyaudio

# Linux (after installing portaudio19-dev)
pip install pyaudio

# macOS Intel
LDFLAGS="-L/usr/local/lib" CPPFLAGS="-I/usr/local/include" pip install pyaudio

# macOS Apple Silicon
LDFLAGS="-L/opt/homebrew/lib" CPPFLAGS="-I/opt/homebrew/include" pip install pyaudio
```

### Step 5: Install Remaining Dependencies

```bash
pip install -r requirements.txt
```

---

## Platform-Specific Notes

### Windows

- **FFmpeg PATH**: After installing FFmpeg, restart your terminal for PATH changes
- **PyAudio**: Pre-built wheels are available; no compilation needed
- **Long Paths**: Enable long paths if you encounter path issues:
  ```powershell
  # Run as Administrator
  reg add "HKLM\SYSTEM\CurrentControlSet\Control\FileSystem" /v LongPathsEnabled /t REG_DWORD /d 1 /f
  ```

### Linux

- **Shared Filesystems**: If using VMware/VirtualBox shared folders, the installer uses `--copies` flag for venv
- **Sudo Access**: System package installation requires sudo
- **CI/CD**: Set `DEBIAN_FRONTEND=noninteractive` for unattended installation

### macOS

- **Apple Silicon (M1/M2/M3)**: 
  - Homebrew installs to `/opt/homebrew`
  - MPS (Metal Performance Shaders) is automatically available
  - No CUDA support (use MPS instead)
  
- **Intel Macs**:
  - Homebrew installs to `/usr/local`
  - CPU-only PyTorch

- **Xcode Command Line Tools**: May be required for compilation:
  ```bash
  xcode-select --install
  ```

---

## GPU/CUDA Setup

### Detecting Your GPU

```bash
# Windows
nvidia-smi

# Linux
nvidia-smi
# or
lspci | grep -i nvidia

# Check in Python
python -c "import torch; print(torch.cuda.is_available())"
```

### NVIDIA Driver Installation

#### Windows
Download from: https://www.nvidia.com/Download/index.aspx

#### Linux (Ubuntu)
```bash
# Recommended: Use ubuntu-drivers
sudo ubuntu-drivers autoinstall
sudo reboot

# Or install specific version
sudo apt install nvidia-driver-535
```

### CUDA Toolkit (Optional)

The PyTorch CUDA wheels include CUDA runtime, but for development:

```bash
# Ubuntu
sudo apt install nvidia-cuda-toolkit

# Or download from NVIDIA:
# https://developer.nvidia.com/cuda-downloads
```

### Verifying CUDA

```python
import torch
print(f"CUDA available: {torch.cuda.is_available()}")
print(f"CUDA version: {torch.version.cuda}")
print(f"GPU: {torch.cuda.get_device_name(0)}")

# Test CUDA tensor
x = torch.zeros(3, 3, device='cuda')
print(f"CUDA tensor created successfully")
```

---

## Troubleshooting

### Common Issues

#### FFmpeg Not Found

**Symptom**: `[WinError 2] The system cannot find the file specified`

**Solution**:
```bash
# Verify installation
ffmpeg -version

# If not found, install and restart terminal
# Windows: winget install ffmpeg
# Linux: sudo apt install ffmpeg
# macOS: brew install ffmpeg
```

#### PyAudio Build Fails

**Symptom**: `portaudio.h not found`

**Solution**:
```bash
# Linux
sudo apt install portaudio19-dev

# macOS (with correct prefix)
brew install portaudio
export LDFLAGS="-L$(brew --prefix)/lib"
export CPPFLAGS="-I$(brew --prefix)/include"
pip install pyaudio
```

#### CUDA Not Detected

**Symptom**: `torch.cuda.is_available()` returns `False`

**Solutions**:
1. Verify NVIDIA driver: `nvidia-smi`
2. Reinstall PyTorch with CUDA:
   ```bash
   pip uninstall torch torchaudio
   pip install torch==2.2.0 torchaudio==2.2.0 --index-url https://download.pytorch.org/whl/cu121
   ```
3. Check CUDA version compatibility

#### Import Errors After Installation

**Symptom**: `ModuleNotFoundError` even after installation

**Solutions**:
1. Ensure virtual environment is activated
2. Check pip is using correct Python:
   ```bash
   which pip  # Should be in venv
   pip list   # Verify packages
   ```

#### SpeechBrain/Whisper Crashes

**Symptom**: Crashes when processing audio

**Solutions**:
1. Ensure FFmpeg is installed and in PATH
2. Check available memory (voice models need ~2GB)
3. Try smaller Whisper model: `whisper.load_model("tiny")`

---

## Verification

### Quick Verification

```bash
python test_installation.py --quick
```

### Full Verification

```bash
python test_installation.py
```

### Manual Verification

```python
# Test all components
print("Testing imports...")
import torch
import torchaudio
import whisper
import speechbrain
import pyaudio
import numpy
import scipy
print("All imports successful!")

# Test PyTorch
print(f"\nPyTorch: {torch.__version__}")
print(f"CUDA: {torch.cuda.is_available()}")
tensor = torch.zeros(3, 3)
print("Tensor operations: OK")

# Test PyAudio
p = pyaudio.PyAudio()
print(f"\nAudio devices: {p.get_device_count()}")
p.terminate()

# Test FFmpeg
import subprocess
result = subprocess.run(['ffmpeg', '-version'], capture_output=True, text=True)
print(f"\nFFmpeg: {'OK' if result.returncode == 0 else 'FAILED'}")

print("\n✓ All verifications passed!")
```

---

## Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `DEBIAN_FRONTEND` | Non-interactive apt | `noninteractive` |
| `LDFLAGS` | Library paths for compilation | `-L/opt/homebrew/lib` |
| `CPPFLAGS` | Include paths for compilation | `-I/opt/homebrew/include` |
| `CUDA_HOME` | CUDA toolkit location | `/usr/local/cuda` |

---

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Install Dependencies
  run: |
    cd backend
    python -m venv venv
    source venv/bin/activate
    python auto_installer.py --yes
    python test_installation.py --quick
```

### Docker

```dockerfile
FROM python:3.10-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    ffmpeg \
    portaudio19-dev \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY backend/ .

RUN pip install --no-cache-dir -r requirements.txt

CMD ["python", "main.py"]
```

---

## Support

If you encounter issues:

1. Run `python test_installation.py` and share the output
2. Check the [Troubleshooting](#troubleshooting) section
3. Ensure all system dependencies are installed
4. Try `python auto_installer.py --force` to reinstall everything
