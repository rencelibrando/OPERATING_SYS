#!/usr/bin/env python3
"""
Standalone script to auto-install voice analysis dependencies.
Run this script to install all required packages for local voice analysis.

Usage:
    python auto_install_dependencies.py           # Interactive install
    python auto_install_dependencies.py --yes     # Non-interactive install
    python auto_install_dependencies.py --verify  # Verify installation
    python auto_install_dependencies.py --ffmpeg  # Install only FFmpeg
"""

import sys
import os

# Add current directory to path to import auto_installer
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from auto_installer import auto_install_dependencies, verify_installation, install_ffmpeg_only


def main():
    """Main function to run auto-installation."""
    
    # Check for command-line arguments
    args = sys.argv[1:]
    
    # Handle --verify flag
    if '--verify' in args or '-v' in args:
        success = verify_installation()
        return 0 if success else 1
    
    # Handle --ffmpeg flag
    if '--ffmpeg' in args or '--ffmpeg-only' in args:
        success = install_ffmpeg_only()
        return 0 if success else 1
    
    # Handle --force flag
    force_reinstall = '--force' in args or '-f' in args
    
    # Handle --yes flag (skip confirmation)
    skip_confirmation = '--yes' in args or '-y' in args
    
    print("🔧 Auto-installing voice analysis dependencies...")
    print("This will install: Whisper, SpeechBrain, PyTorch, FFmpeg, etc.")
    print()
    
    # Ask for confirmation unless --yes flag is provided
    if not skip_confirmation:
        try:
            response = input("Continue? (y/N): ").strip().lower()
            if response not in ['y', 'yes']:
                print("Installation cancelled.")
                return 0
        except KeyboardInterrupt:
            print("\nInstallation cancelled.")
            return 0
    
    # Run auto-installation
    success = auto_install_dependencies(force_reinstall=force_reinstall)
    
    if success:
        print("\n Auto-installation completed successfully!")
        print("You can now start the backend server: python main.py")
        return 0
    else:
        print("\n Auto-installation completed with issues.")
        print("Please check the warnings above and install missing dependencies manually.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
