"""
Project-specific Python startup customizations.

This module is automatically imported by Python (via site.py) whenever
the backend's virtual environment is activated. We use it to keep the
runtime compatible with upstream dependency changes.
"""

from __future__ import annotations

import logging
from typing import List
from functools import wraps
import inspect

logger = logging.getLogger("wordbridge.sitecustomize")


def _detect_available_backends() -> List[str]:
    """Best-effort detection of usable torchaudio backends."""
    backends: List[str] = ["sox_io"]

    try:
        import soundfile  # type: ignore[import]  # noqa: F401
    except Exception:
        pass
    else:
        backends.append("soundfile")

    # Preserve order while removing duplicates.
    seen = set()
    deduped: List[str] = []
    for name in backends:
        if name not in seen:
            seen.add(name)
            deduped.append(name)
    return deduped


def _patch_torchaudio_audio_backend_api() -> None:
    """
    Re-introduce torchaudio audio backend helpers removed in newer releases.

    SpeechBrain (used by the local voice analysis flow) still calls the
    legacy helper methods such as `torchaudio.list_audio_backends()`. When
    those APIs are missing the import fails before we can surface a helpful
    message. The shim below recreates the minimal behaviour SpeechBrain
    expects so that the rest of the subsystem can continue to operate.
    """

    try:
        import torchaudio  # type: ignore[import]
    except Exception:
        return

    available_backends = _detect_available_backends()
    state = {"backend": available_backends[0]}
    patched = False

    if not hasattr(torchaudio, "list_audio_backends"):
        def _list_audio_backends() -> List[str]:
            return list(available_backends)

        torchaudio.list_audio_backends = _list_audio_backends  # type: ignore[attr-defined]
        patched = True

    if hasattr(torchaudio, "get_audio_backend"):
        try:
            state["backend"] = torchaudio.get_audio_backend()  # type: ignore[attr-defined]
        except Exception:
            state["backend"] = available_backends[0]
    else:
        def _get_audio_backend() -> str:
            return state["backend"]

        torchaudio.get_audio_backend = _get_audio_backend  # type: ignore[attr-defined]
        patched = True

    if not hasattr(torchaudio, "set_audio_backend"):
        def _set_audio_backend(name: str) -> None:
            if name not in available_backends:
                raise ValueError(
                    f"Unsupported audio backend '{name}'. "
                    f"Available backends: {', '.join(available_backends)}."
                )
            state["backend"] = name

        torchaudio.set_audio_backend = _set_audio_backend  # type: ignore[attr-defined]
        patched = True

    if patched:
        logger.info(
            "Applied torchaudio audio backend compatibility shim (available=%s)",
            available_backends,
        )


_patch_torchaudio_audio_backend_api()


def _patch_huggingface_hub_download() -> None:
    """
    Newer versions of huggingface_hub removed the `use_auth_token`
    keyword argument. SpeechBrain (and several other libraries)
    still call hf_hub_download(..., use_auth_token=token).
    We shim the API to preserve compatibility by mapping the old
    keyword to the new `token` argument when necessary.
    """

    try:
        import huggingface_hub  # type: ignore[import]
    except Exception:
        return

    download_fn = getattr(huggingface_hub, "hf_hub_download", None)
    if not callable(download_fn):
        return

    try:
        sig = inspect.signature(download_fn)
    except (ValueError, TypeError):
        sig = None

    # If the function already supports the legacy kwarg, nothing to do.
    if sig and "use_auth_token" in sig.parameters:
        return

    # Avoid double-wrapping.
    if getattr(download_fn, "__dict__", {}).get("_wordbridge_patched"):
        return

    @wraps(download_fn)
    def _wrapped_hf_hub_download(*args, **kwargs):
        if "use_auth_token" in kwargs:
            token = kwargs.pop("use_auth_token")
            # HuggingFace renamed the parameter to `token`.
            kwargs.setdefault("token", token)
        return download_fn(*args, **kwargs)

    _wrapped_hf_hub_download._wordbridge_patched = True  # type: ignore[attr-defined]
    huggingface_hub.hf_hub_download = _wrapped_hf_hub_download  # type: ignore[assignment]
    logger.info("Applied huggingface_hub hf_hub_download compatibility shim")


_patch_huggingface_hub_download()
