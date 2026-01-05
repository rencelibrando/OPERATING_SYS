"""
Speaking Recording Routes for conversation playback feature.
Handles full conversation recording storage and retrieval.
"""
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import StreamingResponse
from typing import Optional, List
import json
import uuid
from datetime import datetime
import io

from supabase_client import SupabaseManager
from pydantic import BaseModel, Field


router = APIRouter(prefix="/recordings", tags=["speaking-recordings"])
supabase_manager = SupabaseManager()


class RecordingCreateRequest(BaseModel):
    """Request to create a speaking recording."""
    session_id: Optional[str] = Field(None, description="Associated voice session ID")
    agent_session_id: Optional[str] = Field(None, description="Associated agent session ID")
    user_id: str = Field(..., description="User identifier")
    recording_type: str = Field(..., description="Type: practice, conversation, or lesson")
    duration: float = Field(..., description="Recording duration in seconds")
    transcript: Optional[str] = Field(None, description="Full conversation transcript")
    metadata: Optional[dict] = Field(default_factory=dict, description="Additional metadata")


class RecordingResponse(BaseModel):
    """Response for recording operations."""
    success: bool
    recording_id: Optional[str] = None
    message: str
    audio_url: Optional[str] = None


class RecordingListResponse(BaseModel):
    """Response for listing recordings."""
    success: bool
    recordings: List[dict]
    total_count: int


@router.post("/create", response_model=RecordingResponse)
async def create_recording(request: RecordingCreateRequest):
    """
    Create a new speaking recording entry.

    This creates the database record. Audio file should be uploaded separately.
    """
    try:
        recording_id = str(uuid.uuid4())

        recording_data = {
            "id": recording_id,
            "session_id": request.session_id,
            "agent_session_id": request.agent_session_id,
            "user_id": request.user_id,
            "recording_type": request.recording_type,
            "duration": request.duration,
            "transcript": request.transcript,
            "metadata": request.metadata,
            "created_at": datetime.utcnow().isoformat()
        }

        supabase_manager.client.table("speaking_recordings").insert(recording_data).execute()

        return RecordingResponse(
            success=True,
            recording_id=recording_id,
            message="Recording entry created successfully"
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to create recording: {str(e)}")


@router.post("/upload-audio/{recording_id}", response_model=RecordingResponse)
async def upload_recording_audio(
    recording_id: str,
    audio_file: UploadFile = File(...),
    user_id: str = Form(...)
):
    """
    Upload audio file for an existing recording.

    Args:
        recording_id: The recording ID to attach audio to
        audio_file: Audio file (WAV, MP3, etc.)
        user_id: User identifier for verification
    """
    try:
        # Verify recording exists and belongs to user
        response = supabase_manager.client.table("speaking_recordings").select("*").eq(
            "id", recording_id
        ).eq("user_id", user_id).execute()

        if not response.data:
            raise HTTPException(status_code=404, detail="Recording not found or access denied")

        # Generate unique filename
        timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        file_extension = audio_file.filename.split(".")[-1] if "." in audio_file.filename else "wav"
        filename = f"recordings/{user_id}/{recording_id}_{timestamp}.{file_extension}"

        # Upload to Supabase storage
        file_data = await audio_file.read()

        # Ensure bucket exists
        try:
            supabase_manager.client.storage.create_bucket(
                "speaking-recordings",
                options={"public": False}
            )
        except:
            pass  # Bucket might already exist

        # Upload file
        result = supabase_manager.client.storage.from_("speaking-recordings").upload(
            path=filename,
            file=file_data,
            file_options={"content-type": audio_file.content_type or "audio/wav"}
        )

        if result.data is None:
            raise HTTPException(status_code=500, detail="Failed to upload audio file")

        # Get public URL
        audio_url = supabase_manager.client.storage.from_("speaking-recordings").get_public_url(filename)

        # Update recording with audio URL
        supabase_manager.client.table("speaking_recordings").update({
            "audio_url": audio_url
        }).eq("id", recording_id).execute()

        return RecordingResponse(
            success=True,
            recording_id=recording_id,
            message="Audio uploaded successfully",
            audio_url=audio_url
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to upload audio: {str(e)}")


@router.post("/save-chunks/{recording_id}")
async def save_audio_chunks(
    recording_id: str,
    chunks_metadata: str = Form(...),
    user_id: str = Form(...)
):
    """
    Save audio chunk metadata for a recording.

    This is useful for recordings that were captured in chunks during a conversation.

    Args:
        recording_id: The recording ID
        chunks_metadata: JSON array of chunk metadata
        user_id: User identifier
    """
    try:
        # Parse chunks metadata
        chunks = json.loads(chunks_metadata)

        # Verify recording exists
        response = supabase_manager.client.table("speaking_recordings").select("*").eq(
            "id", recording_id
        ).eq("user_id", user_id).execute()

        if not response.data:
            raise HTTPException(status_code=404, detail="Recording not found")

        # Update recording with chunks
        supabase_manager.client.table("speaking_recordings").update({
            "audio_chunks": chunks
        }).eq("id", recording_id).execute()

        return {"success": True, "message": "Audio chunks metadata saved"}

    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid chunks metadata JSON")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to save chunks: {str(e)}")


@router.get("/user/{user_id}", response_model=RecordingListResponse)
async def get_user_recordings(
    user_id: str,
    recording_type: Optional[str] = None,
    limit: int = 20,
    offset: int = 0
):
    """
    Get all recordings for a user.

    Args:
        user_id: User identifier
        recording_type: Optional filter by type (practice, conversation, lesson)
        limit: Maximum number of recordings to return
        offset: Offset for pagination
    """
    try:
        query = supabase_manager.client.table("speaking_recordings").select(
            "*"
        ).eq("user_id", user_id).order("created_at", desc=True).range(offset, offset + limit - 1)

        if recording_type:
            query = query.eq("recording_type", recording_type)

        response = query.execute()
        recordings = response.data

        return RecordingListResponse(
            success=True,
            recordings=recordings,
            total_count=len(recordings)
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get recordings: {str(e)}")


@router.get("/{recording_id}", response_model=dict)
async def get_recording(recording_id: str, user_id: str):
    """
    Get a specific recording by ID.

    Args:
        recording_id: Recording identifier
        user_id: User identifier for verification
    """
    try:
        response = supabase_manager.client.table("speaking_recordings").select("*").eq(
            "id", recording_id
        ).eq("user_id", user_id).execute()

        if not response.data:
            raise HTTPException(status_code=404, detail="Recording not found")

        recording = response.data[0]

        return {
            "success": True,
            "recording": recording
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get recording: {str(e)}")


@router.delete("/{recording_id}", response_model=dict)
async def delete_recording(recording_id: str, user_id: str):
    """
    Delete a recording.

    Args:
        recording_id: Recording identifier
        user_id: User identifier for verification
    """
    try:
        # Verify recording exists
        response = supabase_manager.client.table("speaking_recordings").select("audio_url").eq(
            "id", recording_id
        ).eq("user_id", user_id).execute()

        if not response.data:
            raise HTTPException(status_code=404, detail="Recording not found")

        audio_url = response.data[0].get("audio_url")

        # Delete audio file from storage if exists
        if audio_url:
            try:
                # Extract filename from URL
                filename = audio_url.split("speaking-recordings/")[-1]
                supabase_manager.client.storage.from_("speaking-recordings").remove([filename])
            except Exception as e:
                print(f"Warning: Failed to delete audio file: {str(e)}")

        # Delete database record
        supabase_manager.client.table("speaking_recordings").delete().eq(
            "id", recording_id
        ).execute()

        return {
            "success": True,
            "message": "Recording deleted successfully"
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete recording: {str(e)}")


@router.get("/session/{session_id}/recordings", response_model=RecordingListResponse)
async def get_session_recordings(session_id: str, user_id: str):
    """
    Get all recordings for a specific session.

    Args:
        session_id: Voice session ID or agent session ID
        user_id: User identifier for verification
    """
    try:
        # Try both session_id and agent_session_id
        response = supabase_manager.client.table("speaking_recordings").select("*").eq(
            "user_id", user_id
        ).or_(f"session_id.eq.{session_id},agent_session_id.eq.{session_id}").execute()

        recordings = response.data

        return RecordingListResponse(
            success=True,
            recordings=recordings,
            total_count=len(recordings)
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get session recordings: {str(e)}")


@router.get("/stats/{user_id}")
async def get_recording_stats(user_id: str):
    """
    Get recording statistics for a user.

    Args:
        user_id: User identifier
    """
    try:
        response = supabase_manager.client.table("speaking_recordings").select("*").eq(
            "user_id", user_id
        ).execute()

        recordings = response.data

        if not recordings:
            return {
                "success": True,
                "total_recordings": 0,
                "total_duration": 0,
                "by_type": {},
                "recent_count": 0
            }

        # Calculate stats
        total_recordings = len(recordings)
        total_duration = sum(r.get("duration", 0) for r in recordings)

        by_type = {}
        for r in recordings:
            r_type = r.get("recording_type", "unknown")
            by_type[r_type] = by_type.get(r_type, 0) + 1

        # Count recent (last 7 days)
        recent_threshold = datetime.utcnow().timestamp() - (7 * 24 * 60 * 60)
        recent_count = sum(
            1 for r in recordings
            if datetime.fromisoformat(r.get("created_at", "")).timestamp() > recent_threshold
        )

        return {
            "success": True,
            "total_recordings": total_recordings,
            "total_duration": total_duration,
            "by_type": by_type,
            "recent_count": recent_count
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get stats: {str(e)}")
