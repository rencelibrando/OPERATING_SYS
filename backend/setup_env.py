#!/usr/bin/env python3
"""
Helper script to create or update .env file in the backend directory.
This script helps set up the backend .env file with Supabase credentials.
"""
import os
from pathlib import Path

def main():
    backend_dir = Path(__file__).parent
    env_file = backend_dir / ".env"
    
    print("Backend .env Setup")
    print("=" * 60)
    print(f"Backend directory: {backend_dir}")
    print(f".env file location: {env_file}")
    print()
    
    # Check if .env already exists
    if env_file.exists():
        print("⚠️  .env file already exists!")
        response = input("Do you want to overwrite it? (y/N): ")
        if response.lower() != 'y':
            print("Cancelled.")
            return
    
    # Get values from the user or environment
    print("\nEnter Supabase credentials:")
    print("(Press Enter to use system environment variables if available)")
    print()
    
    supabase_url = input("SUPABASE_URL: ").strip()
    if not supabase_url:
        supabase_url = os.getenv("SUPABASE_URL", "")
    
    supabase_key = input("SUPABASE_KEY (or SUPABASE_ANON_KEY): ").strip()
    if not supabase_key:
        supabase_key = os.getenv("SUPABASE_KEY") or os.getenv("SUPABASE_ANON_KEY", "")
    
    supabase_service_role_key = input("SUPABASE_SERVICE_ROLE_KEY (optional): ").strip()
    if not supabase_service_role_key:
        supabase_service_role_key = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")
    
    gemini_api_key = input("GEMINI_API_KEY (optional): ").strip()
    if not gemini_api_key:
        gemini_api_key = os.getenv("GEMINI_API_KEY", "")
    
    # Write .env file
    env_content = f"""# Supabase Configuration
SUPABASE_URL={supabase_url}
SUPABASE_KEY={supabase_key}
SUPABASE_ANON_KEY={supabase_key}
SUPABASE_SERVICE_ROLE_KEY={supabase_service_role_key}

# AI Provider API Keys
GEMINI_API_KEY={gemini_api_key}

# Server Configuration
HOST=0.0.0.0
PORT=8000
ENVIRONMENT=development
"""
    
    try:
        env_file.write_text(env_content)
        print(f"\n✅ Successfully created .env file at: {env_file}")
        print("\nYou can now start the backend server with: python main.py")
    except Exception as e:
        print(f"\n❌ Error creating .env file: {e}")

if __name__ == "__main__":
    main()

