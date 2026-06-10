# Spring Job Application Tracker

A full-stack job application tracker built with a Spring Boot REST API, Spring Data JPA persistence, JWT authentication, and the same React/Vite frontend used by the original project.

This public version is cleaned for portfolio/resume use: no private `.env`, no personal H2 database snapshot, no temp scrape files, no IDE metadata, and no generated dependency/build folders committed.

## Features

- Kanban-style job tracking with boards, status lanes, and job cards
- Persistent local H2 database by default
- Spring Data JPA entity relationships for users, boards, columns, jobs, Gmail settings, and backup state
- JWT username/password authentication
- Saved job CRUD, status moves, and ordering
- Job link/text scraping workflows with provider-specific fallbacks
- Backup export/import endpoints
- Optional Google/Gmail, Google Sheets, cloud backup, and Workday autofill integrations
- Offline mode for disabling outbound integrations during local demos/tests
- React + Vite frontend for login, tracking, scraping, saved jobs, integrations, and backups

## Tech Stack

| Layer | Stack |
|---|---|
| Backend | Java 21, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database | H2 file database by default, PostgreSQL profile available |
| Frontend | React 18, Vite, TypeScript/TSX, Lucide icons, Tailwind CDN |
| Testing | JUnit 5, Spring Boot Test, MockMvc, H2 in-memory test DB |
| DevOps | Dockerfiles and Docker Compose for full-stack local deployment |

## Persistence

The default backend profile uses a file-backed H2 database:

```text
backend/data/jobtracker.mv.db
```

That file is created automatically when the backend starts and is intentionally ignored by Git. This gives the app real local persistence without publishing private or generated database contents.

## Run Locally

### Prerequisites

- Java 21
- Maven 3.9+
- Node.js 18+
- npm

### 1. Start the backend

```powershell
cd backend
Copy-Item .env.example .env -Force
mvn spring-boot:run
```

Backend URL: `http://localhost:8080`

Default demo credentials when `APP_SEED_DEMO_DATA=true` in `backend/.env`:

```text
username: demo
password: demo123
```

You can also register a new user from the frontend.

### 2. Start the frontend

Open a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Frontend URL: `http://localhost:5173`

## Useful Commands

Backend tests:

```powershell
cd backend
mvn test
```

Frontend production build:

```powershell
cd frontend
npm run build
```

Run with PostgreSQL instead of H2:

```powershell
cd backend
docker compose up -d postgres
$env:APP_DB_PROFILE = "postgres"
$env:DB_URL = "jdbc:postgresql://localhost:5432/job_tracker"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
mvn spring-boot:run
```

Run the full stack with Docker Compose from the repo root:

```powershell
Copy-Item .env.example .env -Force
docker compose up --build
```

## Project Structure

```text
spring-job-application-tracker-public/
|- backend/
|  |- src/main/java/com/example/jobtracker/
|  |  |- config/          # Security, JWT, CORS, OAuth wiring
|  |  |- core/            # Startup, offline mode, API exception handling
|  |  \- feature/
|  |     |- auth/         # Register/login/OAuth user workflows
|  |     |- tracking/     # Boards, lanes, jobs, scraping workflows
|  |     |- integration/  # Google/Gmail and Workday integration support
|  |     \- backup/       # JSON/cloud/sheets backup workflows
|  |- src/main/resources/ # Runtime profiles: H2, PostgreSQL, offline
|  \- src/test/           # Controller, service, integration tests
|- frontend/
|  |- app.tsx             # Main React SPA
|  |- app.css             # Styles
|  |- scripts/            # Local Workday autofill launcher
|  \- package.json
\- docker-compose.yml
```

## Verification Status

Verified locally before publishing setup:

- `mvn test`: 68 tests passing
- `npm run build`: production frontend build passing

## Resume Summary

Built a full-stack job application tracker using Java, Spring Boot, Spring Data JPA, Spring Security/JWT, React, and H2/PostgreSQL, with persistent local storage, Kanban workflows, job scraping, backup/import/export, integration toggles, and tested REST APIs.
