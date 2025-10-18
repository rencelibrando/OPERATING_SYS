
import gzip
import zlib
import json
from typing import List, Dict, Any, Tuple
from enum import Enum


class CompressionType(str, Enum):
    GZIP = "gzip"
    ZLIB = "zlib"
    NONE = "none"


def compress_chat_history(
    messages: List[Dict[str, Any]], 
    compression_type: CompressionType = CompressionType.GZIP
) -> Tuple[bytes, int, int]:
    json_str = json.dumps(messages, separators=(',', ':'), ensure_ascii=False)
    original_data = json_str.encode('utf-8')
    original_size = len(original_data)

    if compression_type == CompressionType.GZIP:
        compressed_data = gzip.compress(original_data, compresslevel=9)
    elif compression_type == CompressionType.ZLIB:
        compressed_data = zlib.compress(original_data, level=9)
    else:
        compressed_data = original_data
    
    compressed_size = len(compressed_data)
    
    return compressed_data, original_size, compressed_size


def decompress_chat_history(
    compressed_data: bytes,
    compression_type: CompressionType = CompressionType.GZIP
) -> List[Dict[str, Any]]:

    if compression_type == CompressionType.GZIP:
        decompressed_data = gzip.decompress(compressed_data)
    elif compression_type == CompressionType.ZLIB:
        decompressed_data = zlib.decompress(compressed_data)
    else:
        decompressed_data = compressed_data

    json_str = decompressed_data.decode('utf-8')
    messages = json.loads(json_str)
    
    return messages


def get_compression_ratio(original_size: int, compressed_size: int) -> float:
    if original_size == 0:
        return 0.0
    ratio = (1 - (compressed_size / original_size)) * 100
    return round(ratio, 2)


def format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.2f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def prepare_messages_for_compression(messages: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    optimized = []
    
    for msg in messages:
        optimized_msg = {
            "r": msg.get("role", "user")[:1],
            "c": msg.get("content", ""),
        }

        if "timestamp" in msg:
            optimized_msg["t"] = msg["timestamp"]
        
        # Add metadata only if present and not empty
        if "metadata" in msg and msg["metadata"]:
            optimized_msg["m"] = msg["metadata"]
        
        optimized.append(optimized_msg)
    
    return optimized


def restore_messages_from_compressed(messages: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    restored = []
    
    for msg in messages:
        role_map = {"u": "user", "a": "assistant", "s": "system"}
        restored_msg = {
            "role": role_map.get(msg.get("r", "u"), "user"),
            "content": msg.get("c", ""),
        }

        if "t" in msg:
            restored_msg["timestamp"] = msg["t"]
        
        if "m" in msg:
            restored_msg["metadata"] = msg["m"]
        
        restored.append(restored_msg)
    
    return restored

if __name__ == "__main__":
    test_messages = [
        {
            "role": "user",
            "content": "Hello, how are you?",
            "timestamp": 1729123456789,
        },
        {
            "role": "assistant",
            "content": "I'm doing well, thank you! How can I help you today?",
            "timestamp": 1729123457000,
            "metadata": {"model": "gemini-2.0-flash", "tokens": 15}
        }
    ] * 50
    
    print("Testing compression utilities...")
    print(f"Test messages count: {len(test_messages)}")
    

    optimized = prepare_messages_for_compression(test_messages)
    compressed, orig_size, comp_size = compress_chat_history(optimized, CompressionType.GZIP)
    
    print(f"\nOriginal size: {format_size(orig_size)}")
    print(f"Compressed size: {format_size(comp_size)}")
    print(f"Compression ratio: {get_compression_ratio(orig_size, comp_size):.2f}%")
    

    decompressed = decompress_chat_history(compressed, CompressionType.GZIP)
    restored = restore_messages_from_compressed(decompressed)
    
    print(f"\nDecompression successful: {len(restored) == len(test_messages)}")
    print(f"Data integrity: {restored[0]['content'] == test_messages[0]['content']}")

