# Spring Job Tracker Learning Path

## Goal
Learn the project quickly by tracing one user action end-to-end:

`Frontend click -> API request -> Controller -> Service -> Repository -> Entity -> DB`

## 1) Start At The Entry Points

### Frontend Entry
- File: `frontend/index.html`
- Focus:
- `#root` mount node
- script import (`/app.tsx`)
- theme + Tailwind setup

### Frontend App Root
- File: `frontend/app.tsx`
- Focus:
- API base setup/helpers
- fetch wrappers to `/api/jobs/*` and integrations
- main app state (`auth`, `view`, scrape/saved jobs)
- Saved Jobs table rendering

### Backend Entry
- File: `backend/src/main/java/com/example/jobtracker/SpringJobApplicationTrackerApplication.java`
- Focus:
- Spring Boot startup flow
- `@EnableScheduling`

## 2) Read Config Before Deep Debugging
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-h2.properties`
- `backend/src/main/resources/application-offline.properties`
- `backend/src/main/resources/application-postgres.properties`
- `backend/src/main/java/com/example/jobtracker/config/SecurityConfig.java`

What to understand first:
- active profile and port
- CORS origin rules
- offline-mode behavior
- JWT + OAuth config
- which routes are public vs protected

## 3) Core Feature Flow To Trace

Use this order for most features:

1. `frontend/app.tsx`
2. `backend/src/main/java/com/example/jobtracker/feature/tracking/controller/JobApplicationController.java`
3. `backend/src/main/java/com/example/jobtracker/feature/tracking/service/JobApplicationService.java`
4. `backend/src/main/java/com/example/jobtracker/feature/tracking/repository/JobApplicationRepository.java`
5. `backend/src/main/java/com/example/jobtracker/feature/tracking/model/entity/JobApplication.java`

## 4) Scraping-Specific Path
- Main scraper:
- `backend/src/main/java/com/example/jobtracker/feature/tracking/service/JobListingScraperService.java`

When reading it, focus on:
- source host detection
- provider-specific parsing branches (Handshake/Ashby/UptimeCrew/etc.)
- fallback behavior for JS-heavy or login-protected pages
- error messages returned to frontend

Related DTOs:
- `backend/src/main/java/com/example/jobtracker/feature/tracking/model/dto/`

## 5) Auth + User Path

Controllers:
- `backend/src/main/java/com/example/jobtracker/feature/auth/controller/AuthController.java`
- `backend/src/main/java/com/example/jobtracker/feature/auth/controller/OAuth2Controller.java`

Services:
- `backend/src/main/java/com/example/jobtracker/feature/auth/service/UserService.java`
- `backend/src/main/java/com/example/jobtracker/feature/auth/service/JwtService.java`

Repository:
- `backend/src/main/java/com/example/jobtracker/feature/auth/repository/UserRepository.java`

## 6) 90-Minute Practical Plan

### 0-15 min
- Read root `README.md`, then `backend/README.md`, then `frontend/README.md`
- Start backend and frontend locally

### 15-35 min
- Trace the "Scrape Link" action in `frontend/app.tsx`
- Write down exact request payload and endpoint

### 35-60 min
- Follow that endpoint in controller -> service -> DTO mapping
- Confirm where validation and parsing happen

### 60-75 min
- Trace Saved Jobs load path (`/api/jobs/saved`) end-to-end

### 75-90 min
- Add breakpoints/logging around one scrape request
- Verify what fields are parsed, normalized, and persisted

## 7) Debug Checklist
1. Is backend running on expected port (default `8080`)?
2. Is frontend calling correct API base?
3. Is JWT present/valid?
4. Is CORS allowing frontend origin?
5. Did scraper receive a login/challenge page instead of content?
6. Did parsed fields fail validation or get dropped by fallback logic?
7. Is data saved correctly but visually truncated by table layout?

## 8) Good Next Files After Basics
- `backend/src/main/java/com/example/jobtracker/feature/tracking/controller/BoardController.java`
- `backend/src/main/java/com/example/jobtracker/feature/tracking/service/BoardService.java`
- `backend/src/main/java/com/example/jobtracker/feature/integration/controller/GoogleIntegrationController.java`
- `backend/src/main/java/com/example/jobtracker/feature/integration/service/GmailSyncService.java`
- `frontend/app.css`
