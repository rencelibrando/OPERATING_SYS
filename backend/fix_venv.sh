#!/bin/bash
# Fix corrupted virtual environment

echo "Cleaning up corrupted PyTorch installation..."

# Remove corrupted torch directories
rm -rf venv/lib/python3.12/site-packages/~orch
rm -rf venv/lib/python3.12/site-packages/torch*
rm -rf venv/lib/python3.12/site-packages/torchaudio*

# Reinstall torch and torchaudio
source venv/bin/activate
echo "Reinstalling PyTorch..."
pip install torch==2.2.0 torchaudio==2.2.0 --index-url https://download.pytorch.org/whl/cpu --no-cache-dir

echo "Testing basic imports..."
python -c "import torch; print('PyTorch:', torch.__version__)"
python -c "import fastapi; print('FastAPI: OK')"
python -c "import uvicorn; print('Uvicorn: OK')"

echo "Starting test server..."
python test_server.py
