import asyncio
from supabase_client import get_supabase

async def apply_migration():
    migration_sql = """
ALTER TABLE public.chat_session_history 
    ALTER COLUMN compressed_messages TYPE TEXT
    USING encode(compressed_messages, 'base64');
"""
    
    supabase = get_supabase()
    if not supabase:
        print("Supabase not configured")
        return False
    
    try:
        result = supabase.rpc('exec_sql', {'sql': migration_sql}).execute()
        print("Migration applied successfully!")
        print("Changed compressed_messages from BYTEA to TEXT")
        return True
    except Exception as e:
        print(f"Failed to apply migration: {e}")
        print("\nPlease apply the migration manually through Supabase dashboard:")
        print("1. Go to your Supabase project SQL Editor")
        print("2. Run the following SQL:")
        print(migration_sql)
        return False

if __name__ == "__main__":
    asyncio.run(apply_migration())

