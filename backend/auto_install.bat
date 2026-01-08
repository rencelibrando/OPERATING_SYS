@echo off
setlocal EnableDelayedExpansion

echo.
echo ============================================
echo   AUTO-INSTALLER FOR VOICE ANALYSIS
echo   Windows / Linux / macOS Compatible
echo ============================================
echo.
echo This will automatically install:
echo   - Python packages from requirements.txt
echo   - OpenAI Whisper (speech-to-text)
echo   - SpeechBrain (speaker analysis)
echo   - PyTorch (deep learning framework)
echo   - FFmpeg (audio processing) ^<-- CRITICAL
echo   - Other required packages
echo.
echo ============================================
echo.

REM Check Python installation
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.8+ from https://www.python.org/
    pause
    exit /b 1
)

echo Python found:
python --version

REM Check if virtual environment exists
if not exist "venv" (
    echo.
    echo Creating virtual environment...
    python -m venv venv
    if errorlevel 1 (
        echo ERROR: Failed to create virtual environment
        pause
        exit /b 1
    )
    echo Virtual environment created successfully.
)

REM Activate virtual environment
echo.
echo Activating virtual environment...
call venv\Scripts\activate.bat

REM Upgrade pip first
echo.
echo Upgrading pip...
python -m pip install --upgrade pip

echo.
echo ============================================
echo Running Auto-Installer...
echo ============================================
echo.
python auto_installer.py %*

set INSTALL_RESULT=%errorlevel%

echo.
echo ============================================
if %INSTALL_RESULT% equ 0 (
    echo   Installation Complete!
    echo ============================================
    echo.
    echo All dependencies installed successfully.
    echo.
    echo IMPORTANT: If FFmpeg was just installed, you need to:
    echo   1. Close this terminal
    echo   2. Open a new terminal
    echo   3. Run this script again OR start the server
    echo.
    echo To start the server: python main.py
) else (
    echo   Installation Had Issues
    echo ============================================
    echo.
    echo Some dependencies may not have installed correctly.
    echo Please review the output above and follow any instructions.
    echo.
    echo Common fixes:
    echo   - For FFmpeg: winget install ffmpeg
    echo   - Restart terminal after installing FFmpeg
    echo   - Run this script again after fixing issues
)
echo.
echo ============================================

pause
exit /b %INSTALL_RESULT%
