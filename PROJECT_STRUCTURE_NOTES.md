# PROJECT_STRUCTURE_NOTES

## 1. What this project is

This repo is a split app with:

- `backend/`: Spring Boot API (authentication, scraping, persistence, integrations)
- `frontend/`: React UI (login, scrape screen, saved jobs table, integrations screen)

The frontend sends HTTP requests to backend endpoints under `/api/**`.

## 2. Top-level layout

```text
spring-job-application-tracker/
|- .idea/
|- backend/
|  |- src/
|  |- pom.xml
|  |- docker-compose.yml
|  |- .env.example
|  |- SPRING_BOOT_NOTES.md
|  \- README.md
|- frontend/
|  |- index.html
|  |- scrape.html
|  |- app.tsx
|  |- app.css
|  \- README.md
|- .aiassistant/
|- .gitignore
\- README.md
```

### 2.1 IDE and Maven Wrapper Files (`.idea`, `.mvn`, `mvnw`, `mvnw.cmd`, `pom.xml`)

These are common Spring Boot project-root files/folders:

- Maven (what it is):
  - Maven is Java's build tool and dependency manager.
  - It handles compile, test, package, and dependency download.
  - In this project, Maven is used to run Spring Boot (`spring-boot:run`) and produce JARs.
- `pom.xml` (what it is):
  - `pom.xml` means Project Object Model.
  - It is the project build contract: dependencies, plugins, Java version, and build settings.
  - If you change libraries or Java target version, you usually update `pom.xml`.

- `.idea/`
  - IntelliJ project metadata (workspace and run-config XML files).
  - Usually not part of app runtime logic.
- `.mvn/`
  - Maven Wrapper support folder.
  - Important file is usually `.mvn/wrapper/maven-wrapper.properties`.
- `mvnw`
  - Maven Wrapper script for Mac/Linux.
- `mvnw.cmd`
  - Maven Wrapper script for Windows.

What wrapper files are for:

- Wrapper files (`.mvn`, `mvnw`, `mvnw.cmd`) let the project run Maven without requiring a global Maven install.
- They also lock the Maven version used by the project, which improves reproducibility across machines.
- Typical commands when wrapper exists:
  - Windows: `.\mvnw.cmd clean test`
  - Mac/Linux: `./mvnw clean test`

How that applies to this repo right now:

- `.idea/` exists at the repo root.
- `pom.xml` exists in `backend/pom.xml` (because backend is split out).
- `.mvn/`, `mvnw`, and `mvnw.cmd` are not currently present.
- Result: this repo currently expects Maven installed globally to run backend commands.

If you want wrapper-based commands later (so Maven install is optional), add wrapper in `backend/`:

```powershell
cd backend
mvn -N wrapper:wrapper
```

## 3. Backend architecture (feature-based)

Base package:

- `com.example.jobtracker`

Main backend folders:

```text
backend/src/main/java/com/example/jobtracker/
|- SpringJobApplicationTrackerApplication.java
|- config/
|- core/
|  \- startup/
\- feature/
   |- auth/
   |- tracking/
   |- integration/
   \- backup/
```

### 3.1 `config/` (cross-cutting setup)

Purpose: app-wide security/auth/cors wiring.

Key files:

- `config/SecurityConfig.java`
  - Declares protected vs public routes.
  - Enables JWT filter.
  - Enables CORS for frontend origins.
- `config/JwtAuthenticationFilter.java`
  - Reads `Authorization: Bearer <token>`.
  - Validates token and sets Spring Security context.
- `config/JwtProperties.java`
  - Maps JWT config values from properties.
- `config/CustomOAuth2UserService.java`
  - OAuth2 user info loading.
- `config/OAuth2LoginSuccessHandler.java`
  - Placeholder/custom OAuth success behavior notes.

### 3.2 `core/startup/`

Purpose: initialization at app startup.

Key file:

- `core/startup/DataSeeder.java`
  - Seeds demo user and sample board/jobs when DB is empty.

### 3.3 `feature/auth/`

Purpose: user authentication and token creation.

Structure:

- `controller/`
  - `AuthController.java`: `/api/auth/register`, `/api/auth/login`
  - `OAuth2Controller.java`: OAuth-related authenticated user endpoint
- `model/dto/`
  - `AuthRequest.java`, `AuthResponse.java`
- `model/entity/`
  - `User.java`
- `repository/`
  - `UserRepository.java`
- `service/`
  - `UserService.java`: user lookup/register/UserDetailsService
  - `JwtService.java`: token generation/validation

### 3.4 `feature/tracking/`

Purpose: boards, job applications, scraping, and status movement.

Structure:

- `controller/`
  - `BoardController.java`: board retrieval endpoints (`/api/boards`)
  - `JobApplicationController.java`: jobs CRUD, scrape, save, status endpoints (`/api/jobs`)
- `model/dto/`
  - request/response contracts for boards/jobs/scraping
- `model/entity/`
  - `Board.java`
  - `BoardColumn.java`
  - `JobApplication.java`
- `repository/`
  - `BoardRepository.java`
  - `BoardColumnRepository.java`
  - `JobApplicationRepository.java`
- `service/`
  - `BoardService.java`: board + default-column lifecycle
  - `JobApplicationService.java`: create/move/delete/status/save-from-scrape
  - `JobListingScraperService.java`: extracts job data from URLs/query

### 3.5 `feature/integration/`

Purpose: Google OAuth + Gmail-based status syncing.

Structure:

- `controller/`
  - `GoogleIntegrationController.java`: status/authorize/auto-sync/sync-now endpoints
  - `GoogleIntegrationCallbackController.java`: OAuth callback redirect back to frontend
- `model/dto/`
  - integration responses and requests
- `model/entity/`
  - `GmailIntegrationSettings.java`
  - `GoogleOAuthState.java`
- `repository/`
  - settings + oauth state persistence
- `service/`
  - `GoogleIntegrationService.java`: OAuth state/token flow
  - `GoogleOAuthClient.java`: HTTP calls to Google APIs
  - `GmailSyncService.java`: scans Gmail snippets and updates job statuses

### 3.6 `feature/backup/`

Purpose: local-first backup and optional cloud upload.

Structure:

- `controller/`
  - `BackupController.java`: export/import/cloud-status/cloud-upload endpoints
- `model/dto/`
  - backup payload and response DTOs
- `service/`
  - `BackupService.java`: serializes tracker data, imports data, and uploads snapshot to cloud endpoint

## 4. Backend layer responsibilities

### Controllers

Controllers are the HTTP entry points.

They should:

- accept request params/body
- call service methods
- return DTOs/HTTP responses
- avoid embedding business logic

### Services

Services contain business rules and workflows.

They should:

- enforce ownership/authorization assumptions
- orchestrate multiple repositories
- handle workflow logic (scrape-and-save, set-status, OAuth completion)

### Repositories

Repositories are the JPA data-access layer.

They should:

- define persistence queries
- avoid business branching logic
- be used by services, not controllers directly

### DTOs vs Entities

- DTOs (`model/dto`) are API contracts.
- Entities (`model/entity`) are DB models.
- Controllers should expose DTOs, not entities.

## 5. Frontend architecture

Current frontend is a CDN-style React app.

Files:

- `frontend/index.html`: entry page
- `frontend/scrape.html`: alias page
- `frontend/app.tsx`: all React state, routing-like tab handling, API calls
- `frontend/app.css`: supporting styles

Frontend behavior summary:

- Stores JWT in `localStorage`.
- Calls backend API (default `http://localhost:8080`).
- Tabs include Login, Scrape, Saved Jobs, Integrations.
- Uses left click/right click progress behavior for status updates.
- Supports light/dark mode toggle.

## 6. Data flow examples

### 6.1 Login flow

1. User submits username/password in frontend.
2. Frontend calls `POST /api/auth/login`.
3. Backend authenticates with Spring Security.
4. Backend returns JWT.
5. Frontend stores token and sends it in future `Authorization` headers.

### 6.2 Scrape-and-save flow

1. Frontend posts job URL to `POST /api/jobs/scrape-link-and-save`.
2. `JobListingScraperService` extracts title/company/location/salary/source/link.
3. `JobApplicationService` stores or deduplicates job in user board.
4. Frontend refreshes saved jobs table from `GET /api/jobs/saved`.

### 6.3 Status update flow

1. Frontend triggers `POST /api/jobs/{id}/set-status` with `accepted|rejected|applied`.
2. `JobApplicationService` maps status to target column.
3. Job row appears with updated status in saved jobs table.

### 6.4 Gmail integration flow

1. Frontend requests `GET /api/integrations/google/authorize`.
2. Backend creates OAuth state and returns Google auth URL.
3. User completes consent in Google.
4. Google calls backend callback `/api/integrations/google/callback`.
5. Backend stores integration settings and redirects to frontend integrations tab.
6. Frontend can run sync or toggle auto-sync.

### 6.5 Local backup and cloud upload flow

1. User opens `Integrations` tab.
2. Frontend calls `GET /api/backups/export` and downloads JSON backup locally.
3. User can import backup later via `POST /api/backups/import`.
4. If `CLOUD_BACKUP_UPLOAD_URL` is configured, frontend can call `POST /api/backups/cloud-upload`.
5. Backend sends exported payload JSON to configured cloud endpoint.

## 7. Configuration files

Backend resources:

- `backend/src/main/resources/application.properties`
  - common config
  - frontend base URL
  - CORS origins
- `backend/src/main/resources/application-h2.properties`
  - in-memory DB profile
- `backend/src/main/resources/application-postgres.properties`
  - PostgreSQL profile

Useful env vars:

- `JWT_SECRET`
- `APP_DB_PROFILE`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_OAUTH_REDIRECT_URI`
- `FRONTEND_BASE_URL`
- `APP_CORS_ALLOWED_ORIGINS`
- `CLOUD_BACKUP_UPLOAD_URL`
- `CLOUD_BACKUP_API_KEY`

## 8. Tests currently present

```text
backend/src/test/java/com/example/jobtracker/feature/backup/controller/BackupControllerTest.java
backend/src/test/java/com/example/jobtracker/feature/integration/controller/GmailControllerStatusTest.java
backend/src/test/java/com/example/jobtracker/feature/tracking/controller/JobApplicationControllerSavedJobsTest.java
backend/src/test/java/com/example/jobtracker/feature/tracking/controller/JobApplicationSetAppliedTest.java
```

## 9. Where to add new code

- New auth behavior: `feature/auth/service` + `feature/auth/controller`
- New job-tracking behavior: `feature/tracking/service`
- New external integrations: `feature/integration/*`
- New backup/export/sync logic: `feature/backup/*`
- Shared startup/init logic: `core/startup`
- Shared app-wide wiring/security/CORS: `config`
- UI changes: `frontend/app.tsx` + `frontend/index.html`

## 10. Practical rule of thumb

When adding features, follow this order:

1. Define DTOs and endpoint contract.
2. Add/modify service logic.
3. Add repository query if needed.
4. Keep controller thin and map response DTOs.
5. Update frontend API call and UI state handling.
6. Add/update tests in matching feature module.
