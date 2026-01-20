```markdown
# WordBridge — AI-powered Language Learning (OPERATING_SYS)

WordBridge is a cross-platform desktop language-learning project with an AI backend, speaking practice, 
pronunciation feedback tools, and admin utilities for managing lesson topics. 
The repository contains a Kotlin/Compose desktop frontend, Python-based backend services (API, audio comparison, agent routes), and web documentation.

This README gives an overview, developer quickstart, architecture notes, and where to find important pieces in the repo.

## Project status
- Actively developed (features visible in UI components, backend routes, and audio feedback).
- Components:
  - Desktop app: Kotlin + Jetpack Compose (composeApp)
  - Admin app: separate Compose-based admin for lesson/topic management
  - Backend: FastAPI-based Python services for AI chat, providers, chat history, and agent routes
  - Audio tools: pronunciation/audio comparison CLI and helpers
  - Docs: static web pages in `docs/`

## Key features
- AI-powered personalized lessons and conversational AI chat
- Speaking practice with pronunciation feedback and audio comparison
- Vocabulary and lesson progress tracking, achievements
- Admin application for seeding/modifying lesson topics (separate app & storage)
- Pluggable AI provider support and voice/tutor selection

## Repo layout (high level)
- KotlinProject/composeApp/… — Desktop and admin Compose applications, UI, domain models
- backend/ — FastAPI app, agent routes, audio compare utilities, CLI entrypoints
  - backend/main.py — API routing and server entry
  - backend/agent_routes.py — voice models and agent endpoints
  - backend/compare_audio.py — audio comparison / feedback code + CLI
- docs/ — Static web pages (index.html) for companion web UI
- Other supporting modules and resources for packaging and distribution

## Quickstart — Development

Prerequisites
- JDK 11+ (or the JDK used by the project)
- Gradle (wrapper included: use ./gradlew)
- Python 3.12 for compatibility and pip
- (Optional) Supabase/Postgres or other configured remote DB for topics and user data
- AI provider credentials if you want AI features gemini, deepseek, elevenlabs

Backend (local)
1. Create and activate Python virtualenv:
   - python3 -m venv .venv
   - source .venv/bin/activate  (macOS/Linux)
   - .venv\Scripts\activate     (Windows)
2. Install requirements:
   - pip install -r backend/requirements.txt
3. Configure environment variables (example):
   -read the .env example in backend folder and composeApp
4. Run the API:
   - cd backend
   - uvicorn main:app --reload --host 127.0.0.1 --port 8000
5. Health and routes:
   - GET /health
   - POST /chat, /chat/history/save, /chat/history/load, etc. (see backend/main.py for tags & routes)

Desktop app (Kotlin Compose)
1. From repo root, run via Gradle wrapper:
   - ./gradlew :composeApp:run          (macOS/Linux)
   - .\gradlew.bat :composeApp:run     (Windows)
2. Admin app (lesson topics manager):
   - Run from IDE: run `org.example.project.admin.AdminMainKt`
   - CLI:
     - ./gradlew :composeApp:runAdmin
   - Packaging (examples):
     - ./gradlew :composeApp:packageReleaseAdminDmg  (macOS)
     - ./gradlew :composeApp:packageReleaseAdminDeb  (Linux)
     - .\gradlew.bat :composeApp:packageReleaseAdminMsi (Windows)

Audio tools
- The audio comparison CLI is at `backend/compare_audio.py`.
- Try: python backend/compare_audio.py --help
- This module contains feedback generation and summary utilities used for pronunciation scoring.

Docs / Web
- Open `docs/index.html` in a browser to view the static companion pages and feature overview.

## Configuration & environment
- The backend reads settings / environment variables for host/port, environment (development/production), AI provider keys, and database connectors. Inspect `backend/` settings and the top of `backend/main.py` to see the expected variables.
- The Compose app displays a message if Python is not found; the app expects a system Python 3.12+ for certain features. If you see a "Python not found" UI card, install Python and ensure it is on PATH.

## Development notes
- Domain models, UI components, and sample/demo data live under `KotlinProject/composeApp/src/jvmMain/kotlin/org/example/project/`.
- Backend APIs are defined in `backend/main.py` and `backend/agent_routes.py`.
- Look for README fragments (e.g., `KotlinProject/composeApp/.../admin/README.md`) for admin-specific instructions.
- Tests: (If present) follow the project's test runner instructions; otherwise add tests under appropriate language test frameworks (pytest for Python, JUnit for Kotlin).

## Contributing
- Fork the repository and create feature branches.
- Keep backend and frontend changes separated where possible (API contract compatibility).
- Document new environment variables and configuration steps in this README or in the relevant subfolder.
- Add unit/integration tests for backend routes and audio scoring logic when modifying behavior.

## Troubleshooting
- Backend: check logs printed by uvicorn; confirm environment variables and installed Python packages.
- Desktop: if AI features report errors, confirm backend is running and AI keys are correctly configured.
- Packaging: use the Gradle packaging tasks listed above; ensure platform-specific prerequisites (codesigning, packaging dependencies) are met.

## Where to look next
- UI: KotlinProject/composeApp/src/jvmMain/kotlin/org/example/project/
- Backend: backend/main.py, backend/agent_routes.py, backend/compare_audio.py
- Admin docs: KotlinProject/composeApp/.../admin/README.md
- Docs site: docs/index.html

## Contact
Maintainer: rencelibrando (see repository owner)
