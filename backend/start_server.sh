#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Activate virtual environment if available
if [ -f "$SCRIPT_DIR/venv/bin/activate" ]; then
    source "$SCRIPT_DIR/venv/bin/activate"
elif [ -f "$SCRIPT_DIR/../.venv/bin/activate" ]; then
    source "$SCRIPT_DIR/../.venv/bin/activate"
fi

echo "Starting AI Backend Server..."
python main.py


