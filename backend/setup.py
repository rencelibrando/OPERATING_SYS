#!/usr/bin/env python3
"""
Cross-platform setup script for the backend.
Works on Windows, Linux (including Ubuntu), and macOS.
Handles shared filesystem symlink issues (VMware, VirtualBox, WSL).
"""
import subprocess
import sys
import os
import platform
import shutil
from pathlib import Path


def get_python_cmd():
    """Get the appropriate python command for the current platform."""
    if sys.platform == 'win32':
        return 'python'
    return 'python3'


def get_pip_cmd(venv_path):
    """Get the pip command for the virtual environment."""
    if sys.platform == 'win32':
        return str(venv_path / 'Scripts' / 'pip.exe')
    return str(venv_path / 'bin' / 'pip')


def get_python_in_venv(venv_path):
    """Get the python executable in the virtual environment."""
    if sys.platform == 'win32':
        return str(venv_path / 'Scripts' / 'python.exe')
    return str(venv_path / 'bin' / 'python')


def is_shared_filesystem(path):
    """
    Detect if path is on a shared filesystem that doesn't support symlinks.
    Common cases: VMware shared folders, VirtualBox shared folders, some network drives.
    """
    path_str = str(path).lower()
    
    # VMware shared folders
    if '/mnt/hgfs/' in path_str or '\\mnt\\hgfs\\' in path_str:
        return True
    
    # VirtualBox shared folders
    if '/media/sf_' in path_str or '\\media\\sf_' in path_str:
        return True
    
    # Try to detect by attempting a symlink test
    if sys.platform != 'win32':
        test_dir = path / '.symlink_test'
        test_link = path / '.symlink_test_link'
        try:
            test_dir.mkdir(exist_ok=True)
            if test_link.exists() or test_link.is_symlink():
                test_link.unlink()
            os.symlink(str(test_dir), str(test_link))
            test_link.unlink()
            test_dir.rmdir()
            return False
        except OSError as e:
            # Clean up
            try:
                if test_link.exists() or test_link.is_symlink():
                    test_link.unlink()
                if test_dir.exists():
                    test_dir.rmdir()
            except:
                pass
            # Error 95 = Operation not supported (symlinks not supported)
            if e.errno == 95 or 'not supported' in str(e).lower():
                return True
    
    return False


def create_venv(venv_path, use_copies=False):
    """Create a virtual environment with optional --copies flag."""
    print(f"\n{'='*50}")
    print("Creating virtual environment...")
    print(f"{'='*50}\n")
    
    cmd = [sys.executable, '-m', 'venv']
    
    if use_copies:
        print("Using --copies flag (shared filesystem detected)")
        cmd.append('--copies')
    
    cmd.append(str(venv_path))
    
    try:
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            print(f"Error creating venv: {result.stderr}")
            return False
        print("Virtual environment created successfully!")
        return True
    except Exception as e:
        print(f"Exception creating venv: {e}")
        return False


def upgrade_pip(venv_path):
    """Upgrade pip in the virtual environment."""
    print("\nUpgrading pip...")
    pip_cmd = get_pip_cmd(venv_path)
    python_cmd = get_python_in_venv(venv_path)
    
    try:
        result = subprocess.run(
            [python_cmd, '-m', 'pip', 'install', '--upgrade', 'pip'],
            capture_output=True,
            text=True
        )
        if result.returncode == 0:
            print("pip upgraded successfully!")
            return True
        else:
            print(f"Warning: pip upgrade failed: {result.stderr}")
            return True  # Continue anyway
    except Exception as e:
        print(f"Warning: pip upgrade failed: {e}")
        return True  # Continue anyway


def install_requirements(venv_path, requirements_file):
    """Install requirements from requirements.txt."""
    print(f"\n{'='*50}")
    print("Installing dependencies from requirements.txt...")
    print(f"{'='*50}\n")
    
    python_cmd = get_python_in_venv(venv_path)
    
    try:
        result = subprocess.run(
            [python_cmd, '-m', 'pip', 'install', '-r', str(requirements_file)],
            capture_output=False,  # Show output in real-time
            text=True
        )
        return result.returncode == 0
    except Exception as e:
        print(f"Error installing requirements: {e}")
        return False


def run_auto_installer(venv_path):
    """Run the auto_installer.py for additional setup."""
    print(f"\n{'='*50}")
    print("Running auto-installer for additional dependencies...")
    print(f"{'='*50}\n")
    
    python_cmd = get_python_in_venv(venv_path)
    script_dir = Path(__file__).parent
    auto_installer = script_dir / 'auto_installer.py'
    
    if not auto_installer.exists():
        print("auto_installer.py not found, skipping...")
        return True
    
    try:
        result = subprocess.run(
            [python_cmd, str(auto_installer)],
            cwd=str(script_dir),
            capture_output=False
        )
        return result.returncode == 0
    except Exception as e:
        print(f"Error running auto_installer: {e}")
        return False


def verify_installation(venv_path):
    """Verify that key packages are installed correctly."""
    print(f"\n{'='*50}")
    print("Verifying installation...")
    print(f"{'='*50}\n")
    
    python_cmd = get_python_in_venv(venv_path)
    
    packages_to_check = [
        ('fastapi', 'FastAPI'),
        ('uvicorn', 'Uvicorn'),
        ('pydantic', 'Pydantic'),
        ('httpx', 'HTTPX'),
        ('numpy', 'NumPy'),
        ('scipy', 'SciPy'),
    ]
    
    all_ok = True
    for import_name, display_name in packages_to_check:
        try:
            result = subprocess.run(
                [python_cmd, '-c', f'import {import_name}; print({import_name}.__version__ if hasattr({import_name}, "__version__") else "OK")'],
                capture_output=True,
                text=True,
                timeout=30
            )
            if result.returncode == 0:
                version = result.stdout.strip()
                print(f"  [OK] {display_name}: {version}")
            else:
                print(f"  [FAIL] {display_name}: {result.stderr.strip()}")
                all_ok = False
        except Exception as e:
            print(f"  [FAIL] {display_name}: {e}")
            all_ok = False
    
    return all_ok


def print_activation_instructions(venv_path):
    """Print instructions for activating the virtual environment."""
    print(f"\n{'='*50}")
    print("Setup Complete!")
    print(f"{'='*50}\n")
    
    print("To activate the virtual environment:\n")
    
    if sys.platform == 'win32':
        print("  Windows (PowerShell):")
        print(f"    .\\venv\\Scripts\\Activate.ps1")
        print("\n  Windows (Command Prompt):")
        print(f"    .\\venv\\Scripts\\activate.bat")
    else:
        print("  Linux/macOS:")
        print(f"    source venv/bin/activate")
    
    print("\nTo start the server:")
    print("    python main.py")
    print("")


def main():
    """Main entry point."""
    print(f"\n{'='*50}")
    print("  Cross-Platform Backend Setup")
    print(f"  OS: {platform.system()} {platform.release()}")
    print(f"  Python: {sys.version.split()[0]}")
    print(f"{'='*50}\n")
    
    script_dir = Path(__file__).parent.resolve()
    venv_path = script_dir / 'venv'
    requirements_file = script_dir / 'requirements.txt'
    
    # Check if requirements.txt exists
    if not requirements_file.exists():
        print(f"ERROR: requirements.txt not found at {requirements_file}")
        return 1
    
    # Check for existing venv
    if venv_path.exists():
        print(f"Existing virtual environment found at: {venv_path}")
        response = input("Delete and recreate? [y/N]: ").strip().lower()
        if response == 'y':
            print("Removing existing virtual environment...")
            shutil.rmtree(venv_path)
        else:
            print("Keeping existing virtual environment.")
            # Just verify and exit
            if verify_installation(venv_path):
                print_activation_instructions(venv_path)
                return 0
            else:
                print("\nSome packages are missing. Reinstalling...")
                if not install_requirements(venv_path, requirements_file):
                    return 1
                verify_installation(venv_path)
                print_activation_instructions(venv_path)
                return 0
    
    # Detect if we need to use --copies (for shared filesystems)
    use_copies = is_shared_filesystem(script_dir)
    if use_copies:
        print("Shared filesystem detected - will use file copies instead of symlinks")
    
    # Create virtual environment
    if not create_venv(venv_path, use_copies=use_copies):
        print("\nERROR: Failed to create virtual environment")
        
        # Retry with --copies if first attempt failed
        if not use_copies:
            print("Retrying with --copies flag...")
            if not create_venv(venv_path, use_copies=True):
                print("\nERROR: Failed to create virtual environment even with --copies")
                return 1
        else:
            return 1
    
    # Upgrade pip
    upgrade_pip(venv_path)
    
    # Install requirements
    if not install_requirements(venv_path, requirements_file):
        print("\nWARNING: Some requirements may have failed to install")
        print("Continuing with auto-installer...")
    
    # Run auto-installer for additional setup (FFmpeg, Whisper, etc.)
    run_auto_installer(venv_path)
    
    # Verify installation
    verify_installation(venv_path)
    
    # Print activation instructions
    print_activation_instructions(venv_path)
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
