#!/usr/bin/env python3
"""Simple test server to check if FastAPI can start and respond"""

from fastapi import FastAPI
import uvicorn

app = FastAPI()

@app.get("/")
async def root():
    return {"status": "ok", "message": "Test server running"}

@app.get("/ping")
async def ping():
    return {"status": "ok", "message": "pong"}

if __name__ == "__main__":
    print("Starting test server on http://localhost:8001")
    uvicorn.run(app, host="0.0.0.0", port=8001, reload=False)
