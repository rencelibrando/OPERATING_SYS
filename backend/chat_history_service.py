
from typing import List, Dict, Any, Optional
import logging
from datetime import datetime
import base64

from supabase_client import get_supabase
from compression_utils import (
    compress_chat_history,
    decompress_chat_history,
    prepare_messages_for_compression,
    restore_messages_from_compressed,
    get_compression_ratio,
    CompressionType
)
from models import ChatMessage, MessageRole
from config import settings

logger = logging.getLogger(__name__)


class ChatHistoryService:

    def __init__(self):
        self.compression_type = CompressionType(settings.compression_type)
    
    async def save_chat_history(
        self,
        session_id: str,
        messages: List[ChatMessage],
        user_id: Optional[str] = None
    ) -> Dict[str, Any]:
        try:
            supabase = get_supabase()
            if not supabase:
                raise Exception("Supabase not configured")
            
            if not messages:
                raise ValueError("No messages to save")
            
            logger.info(f"Saving {len(messages)} messages for session {session_id}")

            message_dicts = [msg.dict() for msg in messages]
            optimized = prepare_messages_for_compression(message_dicts)
            compressed_data, original_size, compressed_size = compress_chat_history(
                optimized,
                self.compression_type
            )
            
            # Calculate statistics
            ratio = get_compression_ratio(original_size, compressed_size)
            first_msg_time = messages[0].timestamp if messages[0].timestamp else None
            last_msg_time = messages[-1].timestamp if messages[-1].timestamp else None
            
            # Check if history already exists for this session
            existing = supabase.table('chat_session_history')\
                .select('id')\
                .eq('session_id', session_id)\
                .execute()
            
            # Encode compressed data to base64 for JSON serialization
            compressed_data_b64 = base64.b64encode(compressed_data).decode('utf-8')
            
            # Prepare data for storage
            history_data = {
                'session_id': session_id,
                'compressed_messages': compressed_data_b64,
                'compression_type': self.compression_type.value,
                'original_size': original_size,
                'compressed_size': compressed_size,
                'message_count': len(messages),
                'first_message_at': datetime.fromtimestamp(first_msg_time / 1000).isoformat() if first_msg_time else None,
                'last_message_at': datetime.fromtimestamp(last_msg_time / 1000).isoformat() if last_msg_time else None,
            }
            
            # Insert or update
            if existing.data:
                # Update existing record
                result = supabase.table('chat_session_history')\
                    .update(history_data)\
                    .eq('session_id', session_id)\
                    .execute()
                logger.info(f"Updated chat history for session {session_id}")
            else:
                # Insert new record
                result = supabase.table('chat_session_history')\
                    .insert(history_data)\
                    .execute()
                logger.info(f"Created chat history for session {session_id}")
            
            # Update session message count
            supabase.table('chat_sessions')\
                .update({'message_count': len(messages)})\
                .eq('id', session_id)\
                .execute()
            
            return {
                'success': True,
                'session_id': session_id,
                'message_count': len(messages),
                'original_size': original_size,
                'compressed_size': compressed_size,
                'compression_ratio': ratio,
                'compression_type': self.compression_type.value,
            }
            
        except Exception as e:
            logger.error(f"Failed to save chat history: {e}")
            raise Exception(f"Failed to save chat history: {str(e)}")
    
    async def load_chat_history(
        self,
        session_id: str,
        user_id: Optional[str] = None
    ) -> Dict[str, Any]:
        try:
            supabase = get_supabase()
            if not supabase:
                raise Exception("Supabase not configured")
            
            logger.info(f"Loading chat history for session {session_id}")

            result = supabase.table('chat_session_history')\
                .select('*')\
                .eq('session_id', session_id)\
                .execute()
            
            if not result.data:
                logger.info(f"No chat history found for session {session_id}")
                return {
                    'success': True,
                    'session_id': session_id,
                    'messages': [],
                    'message_count': 0,
                    'original_size': 0,
                    'compressed_size': 0,
                    'compression_ratio': 0.0,
                }
            
            history = result.data[0]

            compressed_data_raw = history['compressed_messages']
            if isinstance(compressed_data_raw, bytes):
                compressed_data = compressed_data_raw
            else:
                compressed_data = base64.b64decode(compressed_data_raw)

            compression_type = CompressionType(history['compression_type'])
            
            decompressed = decompress_chat_history(compressed_data, compression_type)
            messages_dict = restore_messages_from_compressed(decompressed)

            messages = [
                ChatMessage(
                    role=MessageRole(msg['role']),
                    content=msg['content'],
                    timestamp=msg.get('timestamp'),
                )
                for msg in messages_dict
            ]
            
            logger.info(f"Loaded {len(messages)} messages for session {session_id}")
            
            return {
                'success': True,
                'session_id': session_id,
                'messages': messages,
                'message_count': history['message_count'],
                'original_size': history['original_size'],
                'compressed_size': history['compressed_size'],
                'compression_ratio': float(history.get('compression_ratio', 0)),
            }
            
        except Exception as e:
            logger.error(f"Failed to load chat history: {e}")
            raise Exception(f"Failed to load chat history: {str(e)}")
    
    async def delete_chat_history(
        self,
        session_id: str,
        user_id: Optional[str] = None
    ) -> bool:
        try:
            supabase = get_supabase()
            if not supabase:
                raise Exception("Supabase not configured")
            
            logger.info(f"Deleting chat session and history for: {session_id}")

            session_result = supabase.table('chat_sessions')\
                .delete()\
                .eq('id', session_id)\
                .execute()
            
            logger.info(f"Deleted chat session (and cascaded history): {session_id}")

            try:
                history_result = supabase.table('chat_session_history')\
                    .delete()\
                    .eq('session_id', session_id)\
                    .execute()
                logger.info(f"Explicitly deleted chat history: {session_id}")
            except Exception as e:
                logger.info(f"Chat history already deleted (cascade): {e}")
            
            return True
            
        except Exception as e:
            logger.error(f"Failed to delete chat history: {e}")
            raise Exception(f"Failed to delete chat history: {str(e)}")
    
    async def get_user_chat_sessions(
        self,
        user_id: str,
        limit: int = 50
    ) -> List[Dict[str, Any]]:
        try:
            supabase = get_supabase()
            if not supabase:
                raise Exception("Supabase not configured")
            
            # Fetch sessions with history data
            result = supabase.table('chat_sessions')\
                .select('*, chat_session_history(*)')\
                .eq('user_id', user_id)\
                .order('updated_at', desc=True)\
                .limit(limit)\
                .execute()
            
            return result.data if result.data else []
            
        except Exception as e:
            logger.error(f"Failed to get user chat sessions: {e}")
            return []

