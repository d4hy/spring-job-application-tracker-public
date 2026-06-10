# Spring Job Application Tracker

Spring Boot application to track job applications in a board/column workflow, authenticate users with JWT, and scrape job data either by keyword query or direct job-post URL.

## Features

- JWT authentication (`register`, `login`)
- Optional OAuth2 login config (Google/GitHub)
- Kanban-style job board model (`Board -> Columns -> Job Applications`)
- CRUD operations for job applications
- Scrape job listings by query (`/api/jobs/scrape`)
- Scrape a single pasted link (`/api/jobs/scrape-link`)
- Best-effort LinkedIn job URL scraping (public/guest postings)
- Best-effort Workday job URL scraping (public postings)
- Auto-save scraped links to the selected board (`Internships > Applied` or `Full-Time Jobs > Applied`)
- React frontend with navigation bar (Login, Scrape, Saved Jobs, Integrations, Dark Mode, Logout)
- Spreadsheet-style table of saved jobs (title, company, location, salary, source, status column, link)
- Google OAuth integration page for Gmail status sync (manual sync + auto-sync)
- Local backup export/import (`/api/backups/export`, `/api/backups/import`)
- Optional cloud backup upload via configurable endpoint (`/api/backups/cloud-upload`)
- Optional Google Sheets sync for backup rows (`/api/backups/google-sheet-upload`)
- Profile-based database config (`h2` or `postgres`)
- PostgreSQL-ready local setup with Docker Compose

## Tech Stack

- Java 21
- Maven 3.9+
- Spring Boot 3.2.2
- Spring Security + JWT (`jjwt`)
- Spring Data JPA
- H2 (default local-file profile), PostgreSQL (persistent profile)
- Tailwind CSS (CDN) for frontend utility-first styling
- React 18 (Vite dev server in `../frontend`)

## Project Architecture

This project follows a feature-modular Spring Boot architecture:

1. `feature/auth` module
2. `feature/tracking` module
3. `feature/integration` module
4. `feature/backup` module
5. Shared `config` and `core` modules
6. Database layer (H2 local file for local dev, PostgreSQL optional)

Request flow:

1. React frontend (from `../frontend/`) sends HTTP request.
2. Controller receives request and validates input DTOs.
3. Service applies business logic and authorization checks.
4. Repository reads/writes JPA entities.
5. Controller returns response DTO JSON.

Security flow:

1. User logs in at `POST /api/auth/login`.
2. Server returns JWT.
3. Frontend stores JWT in `localStorage`.
4. Protected API calls send `Authorization: Bearer <token>`.
5. `JwtAuthenticationFilter` validates token before controller access.

### What Controllers Are

Controllers are the HTTP boundary of the backend. They:

- expose URL routes (`/api/auth`, `/api/jobs`, `/api/boards`),
- accept request bodies/query params/path params,
- validate and normalize incoming request data,
- call service methods (instead of writing business logic directly),
- return response DTOs and HTTP status codes.

In this project:

- `AuthController` handles login/register endpoints.
- `BoardController` handles board and column read endpoints.
- `JobApplicationController` handles CRUD + scraping endpoints.
- `BackupController` handles local export/import and cloud upload endpoints.

Location: `backend/src/main/java/com/example/jobtracker/feature/*/controller/`

### What Services Are

Services contain the business logic of the app. They:

- enforce rules (ownership checks, required fields, ordering logic),
- orchestrate workflows across repositories,
- perform non-trivial operations (JWT handling, scraping, seeding),
- keep controllers thin and easier to maintain/test.

In this project:

- `UserService` manages user registration and lookup.
- `JwtService` creates and validates JWTs.
- `BoardService` retrieves board/column data with ownership rules.
- `JobApplicationService` creates/moves/deletes job cards.
- `JobListingScraperService` normalizes scraped listing data.
- `BackupService` handles local backup export/import and optional cloud upload.
- `DataSeeder` can insert demo data at startup when `APP_SEED_DEMO_DATA=true`.

Location: `backend/src/main/java/com/example/jobtracker/feature/*/service/`

### What Repositories Are

Repositories are the data-access layer. They:

- extend Spring Data JPA interfaces (`JpaRepository`),
- define query methods by convention (`findByIdAndUser`, etc.),
- hide SQL/ORM details from controllers/services,
- provide CRUD operations for entities.

In this project:

- `UserRepository` for users,
- `BoardRepository` for boards,
- `BoardColumnRepository` for columns,
- `JobApplicationRepository` for job applications.

Location: `backend/src/main/java/com/example/jobtracker/feature/*/repository/`

### Why Board and Column Exist

The app uses a Kanban workflow model:

- A `Board` is one workspace/category (examples: `Internships`, `Full-Time Jobs`).
- A `Column` is a status stage inside that board (`Wish List`, `Applied`, `Interviewing`, `Offer`, `Rejected`).
- A `JobApplication` is a card inside one column.

This structure lets you:

- keep internship and full-time searches separated,
- move jobs between status stages without deleting/recreating records,
- support manual updates and Gmail auto-sync updates with the same model.

## Why Java 21

This project is configured with `java.version=21` in `pom.xml` and builds with Maven compiler `release 21`. Using Java 21 avoids class-version/build mismatch issues.

## Prerequisites

Install these before running:

1. Java 21 JDK
2. Maven 3.9+

### What Maven Is

Maven is the Java build tool used by this project. It:

- downloads and manages dependencies,
- runs build lifecycle phases (compile, test, package),
- runs Spring Boot tasks such as `spring-boot:run`.

`pom.xml` is the Maven project file where dependencies/plugins/Java version are defined.

### What Maven Wrapper Files Are

Wrapper files let a project run Maven without requiring a system-wide Maven install:

- `.mvn/wrapper/*`
- `mvnw` (Mac/Linux script)
- `mvnw.cmd` (Windows script)

If wrapper files exist, you can run:

- Windows: `.\mvnw.cmd clean spring-boot:run`
- Mac/Linux: `./mvnw clean spring-boot:run`

Current state in this repo:

- `backend/pom.xml` exists.
- Wrapper files are not currently present, so `mvn` must be installed globally.

### Windows Install Commands

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
```

If `winget` does not resolve Maven on your machine:

```powershell
choco install maven -y --no-progress
```

### Verify Install

```powershell
java -version
mvn -version
```

You should see Java 21 and a Maven version.

### If `mvn` or `JAVA_HOME` Is Still Missing (Windows)

Session-only fix for the current terminal:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:PATH="$env:JAVA_HOME\bin;C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.12\bin;$env:PATH"
```

Permanent user-level fix (open a new terminal after running):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-21.0.10", "User")
$existingPath = [Environment]::GetEnvironmentVariable("Path", "User")
$mavenBin = "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.12\bin"
if ($existingPath -notlike "*$mavenBin*") {
  [Environment]::SetEnvironmentVariable("Path", "$existingPath;$mavenBin", "User")
}
```

## Configuration

Main config file: `backend/src/main/resources/application.properties`

### Required

Set `JWT_SECRET` (minimum 32 chars) before starting the app.

PowerShell example:

```powershell
$env:JWT_SECRET = "replace-with-a-strong-random-secret-at-least-32-characters"
```

The app reads:

```properties
app.jwt.secret=${JWT_SECRET:change-me-please-change-32-char-minimum}
```

### Database Profile Selection

The app supports two DB profiles:

- `h2` (default, local file at `backend/data/`, persists across restarts)
- `postgres` (persistent, recommended for saved jobs)

Set profile with:

```powershell
$env:APP_DB_PROFILE="postgres"
```

For PostgreSQL profile also set:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/job_tracker"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"
```

### Optional OAuth2 Variables

Only needed if you want Google/GitHub login flows:

```powershell
$env:GOOGLE_CLIENT_ID="your-google-client-id"
$env:GOOGLE_CLIENT_SECRET="your-google-client-secret"
$env:GOOGLE_OAUTH_REDIRECT_URI="http://localhost:8080/api/integrations/google/callback"
$env:GITHUB_CLIENT_ID="your-github-client-id"
$env:GITHUB_CLIENT_SECRET="your-github-client-secret"
```

### Frontend Split Variables

When frontend runs separately (recommended):

```powershell
$env:FRONTEND_BASE_URL="http://localhost:5173"
$env:FRONTEND_AUTO_OPEN="true"
$env:APP_SEED_DEMO_DATA="false"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:*,http://127.0.0.1:*,https://localhost:*,https://127.0.0.1:*"
```

### Optional Cloud Backup Variables

Set these only if you want `Upload Backup to Cloud` enabled:

```powershell
$env:CLOUD_BACKUP_UPLOAD_URL="https://your-cloud-endpoint.example.com/job-tracker-backup"
$env:CLOUD_BACKUP_API_KEY="optional-bearer-token-for-upload-endpoint"
```

### Offline Mode (Disable Outbound Integrations)

Use this when you want local-only behavior and no outbound integration calls:

```powershell
$env:APP_OFFLINE_MODE="true"
```

When enabled, outbound features return HTTP `503`:

- job scraping endpoints
- Google OAuth/Gmail sync endpoints
- cloud backup upload
- Google Sheets backup sync

### Integrations Requirements (Google OAuth)

Gmail status sync + Google Sheets export use Google OAuth with `gmail.readonly` and `spreadsheets` scopes:

1. Create a Google OAuth client in Google Cloud Console.
2. Add your callback URL:
   - Local default: `http://localhost:8080/api/integrations/google/callback`
3. Set:
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - optional `GOOGLE_OAUTH_REDIRECT_URI` (if not using default)
4. In the app, open `Integrations` tab and click `Connect Google OAuth`.
5. If you previously connected before Sheets support was added, reconnect once to refresh scopes.

Optional sync cadence:

```powershell
$env:GMAIL_AUTO_SYNC_DELAY_MS="300000"
```

## Run the App

### Option A: Run with PostgreSQL (Recommended)

1. Start PostgreSQL from backend root:

```powershell
docker compose up -d postgres
```

2. Set runtime env vars:

```powershell
$env:APP_DB_PROFILE="postgres"
$env:DB_URL="jdbc:postgresql://localhost:5432/job_tracker"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"
$env:JWT_SECRET="replace-with-a-strong-random-secret-at-least-32-characters"
```

3. Start app:

```powershell
mvn clean spring-boot:run
```

### Option B: Run with H2 (Local File)

```powershell
$env:APP_DB_PROFILE="h2"
$env:JWT_SECRET="replace-with-a-strong-random-secret-at-least-32-characters"
$env:APP_SEED_DEMO_DATA="false"
mvn clean spring-boot:run
```

### Option C: Run in Offline Mode

```powershell
$env:APP_DB_PROFILE="h2"
$env:APP_OFFLINE_MODE="true"
$env:JWT_SECRET="replace-with-a-strong-random-secret-at-least-32-characters"
mvn clean spring-boot:run
```

Alternatively, use the bundled `offline` profile:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=h2,offline"
```

Default URLs (both profiles):

- App: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console` (H2 profile only)
- Frontend auto-opens at `FRONTEND_BASE_URL` when `FRONTEND_AUTO_OPEN=true`

If port `8080` is in use, start on another port:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

## Demo Login (Optional)

Demo credentials are only available if you enable seeding:

```powershell
$env:APP_SEED_DEMO_DATA="true"
```

- Username: `demo`
- Password: `demo123`

## Frontend Usage (Paste Link Scraper)

Open frontend at `http://localhost:5173` (with backend running on `http://localhost:8080`) and use:

1. Use the top navigation bar:
   - `Login` tab
   - `Scrape` tab (enabled after login)
   - `Saved Jobs` tab (enabled after login)
   - `Integrations` tab (enabled after login)
   - `Dark Mode` toggle
   - `Logout` button (when logged in)
2. Login with your own credentials (or enable demo seeding above).
3. Switch to `Scrape`, paste a job URL, and click `Scrape Link`.
4. Scraped links are auto-saved into the selected board, such as `Internships > Applied`.
5. Open `Saved Jobs` and use Progress control for final status:
   - Left-click `Set Progress` => `Rejected`
   - Right-click `Set Progress` => `Accepted` (stored in `Offer` column)
6. Open `Integrations`:
   - Click `Connect Google OAuth`.
   - Click `Sync Now` to process Gmail updates.
   - Toggle `Auto sync Gmail` to keep statuses updated automatically.
   - Click `Download Local Backup` to save tracker data as JSON.
   - Use file picker to import a local backup JSON.
   - Click `Upload Backup to Cloud` after setting cloud backup env vars.
7. Scrape result includes:
   - Title
   - Company
   - Location
   - Salary
   - Source
   - Original link
8. Theme preference is stored in browser local storage.

## API Overview

### Public Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`

### Protected Endpoints (Bearer JWT)

- `GET /api/boards`
- `GET /api/boards/{id}`
- `POST /api/jobs`
- `PUT /api/jobs/{id}?columnId=<id>&order=<position>`
- `POST /api/jobs/{id}/set-applied`
- `POST /api/jobs/{id}/set-status`
- `DELETE /api/jobs/{id}`
- `GET /api/jobs/scrape?query=<keywords>&limit=<1-100>`
- `POST /api/jobs/scrape-link`
- `POST /api/jobs/scrape-link-and-save`
- `GET /api/jobs/saved`
- `GET /api/integrations/google/status`
- `GET /api/integrations/google/authorize`
- `GET /api/integrations/google/callback` (redirect endpoint from Google)
- `POST /api/integrations/google/auto-sync`
- `POST /api/integrations/google/sync`
- `GET /api/backups/export`
- `POST /api/backups/import`
- `GET /api/backups/cloud-status`
- `POST /api/backups/cloud-upload`
- `POST /api/backups/google-sheet-upload`

## API Quickstart

### 1. Register (first time only)

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"your_user","password":"your_password"}'
```

### 2. Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"your_user","password":"your_password"}'
```

Response includes:

```json
{
  "token": "<jwt>",
  "username": "your_user"
}
```

### 3. Scrape by Query

```bash
curl "http://localhost:8080/api/jobs/scrape?query=software%20engineer&limit=10" \
  -H "Authorization: Bearer <jwt>"
```

### 4. Scrape by Link (paste-link feature)

```bash
curl -X POST http://localhost:8080/api/jobs/scrape-link \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/jobs/123"}'
```

For LinkedIn links, use a job-post URL such as:

```bash
curl -X POST http://localhost:8080/api/jobs/scrape-link \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.linkedin.com/jobs/view/1234567890/"}'
```

To scrape and immediately save to `Applied` in one call:

```bash
curl -X POST http://localhost:8080/api/jobs/scrape-link-and-save \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.linkedin.com/jobs/view/1234567890/","markApplied":true}'
```

Response shape:

```json
{
  "listing": {
    "title": "Senior Software Engineer",
    "company": "Example Co",
    "location": "Remote",
    "salary": "$120,000 - $160,000",
    "originalLink": "https://example.com/jobs/123",
    "source": "linkedin.com"
  },
  "saveStatus": "saved",
  "boardName": "Internships",
  "columnName": "Wish List",
  "jobId": 42
}
```

Get all saved jobs for the current user (table data source):

```bash
curl http://localhost:8080/api/jobs/saved \
  -H "Authorization: Bearer <jwt>"
```

Move a specific job row into `Applied`:

```bash
curl -X POST http://localhost:8080/api/jobs/42/set-applied \
  -H "Authorization: Bearer <jwt>"
```

Move a specific job row by explicit status (`applied`, `accepted`, `rejected`):

```bash
curl -X POST http://localhost:8080/api/jobs/42/set-status \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"status":"accepted"}'
```

Get Google integration status:

```bash
curl http://localhost:8080/api/integrations/google/status \
  -H "Authorization: Bearer <jwt>"
```

Start Google OAuth flow:

```bash
curl http://localhost:8080/api/integrations/google/authorize \
  -H "Authorization: Bearer <jwt>"
```

Open the returned `authorizationUrl` in your browser. Google will redirect back to:

`/api/integrations/google/callback`

Enable auto-sync:

```bash
curl -X POST http://localhost:8080/api/integrations/google/auto-sync \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"enabled":true}'
```

Run manual sync now:

```bash
curl -X POST http://localhost:8080/api/integrations/google/sync \
  -H "Authorization: Bearer <jwt>"
```

Export local backup JSON:

```bash
curl http://localhost:8080/api/backups/export \
  -H "Authorization: Bearer <jwt>"
```

Import local backup JSON:

```bash
curl -X POST http://localhost:8080/api/backups/import \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d @job-tracker-backup.json
```

Check cloud backup configuration:

```bash
curl http://localhost:8080/api/backups/cloud-status \
  -H "Authorization: Bearer <jwt>"
```

Upload latest backup snapshot to configured cloud endpoint:

```bash
curl -X POST http://localhost:8080/api/backups/cloud-upload \
  -H "Authorization: Bearer <jwt>"
```

Sync backup rows to a Google Sheet (URL or ID):

```bash
curl -X POST http://localhost:8080/api/backups/google-sheet-upload \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"spreadsheet":"https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit"}'
```

## Creating a Job Card From Scraped Data

After scraping, you can create a tracked application card with:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "boardId": 1,
    "columnId": 1,
    "company": "Example Co",
    "title": "Senior Software Engineer",
    "location": "Remote",
    "salary": "$120,000 - $160,000",
    "jobUrl": "https://example.com/jobs/123",
    "notes": "Imported from scraped link"
  }'
```

Required fields for `POST /api/jobs`:

- `boardId`
- `columnId`
- `company`
- `title`

## Build, Test, Package

Compile:

```powershell
mvn -DskipTests compile
```

Run tests:

```powershell
mvn test
```

Package JAR:

```powershell
mvn clean package
```

Run packaged JAR:

```powershell
java -jar target/spring-job-application-tracker-0.0.1-SNAPSHOT.jar
```

## Project Structure

```text
backend/
|- src/
|  |- main/
|  |  |- java/com/example/jobtracker/
|  |  |  |- SpringJobApplicationTrackerApplication.java
|  |  |  |- config/                                # Security/auth/CORS config
|  |  |  |- core/
|  |  |  |  \- startup/
|  |  |  |     \- DataSeeder.java                 # Startup seed logic
|  |  |  \- feature/
|  |  |     |- auth/                               # Login/JWT/OAuth user workflows
|  |  |     |- tracking/                           # Jobs/boards/scraping workflows
|  |  |     |- integration/                        # Google OAuth + Gmail sync workflows
|  |  |     \- backup/                             # Local backup + cloud upload workflows
|  |  \- resources/
|  |     |- application.properties
|  |     |- application-h2.properties
|  |     \- application-postgres.properties
|  \- test/                                        # Backend tests
|- pom.xml
|- docker-compose.yml
|- .env.example
|- SPRING_BOOT_NOTES.md
\- README.md
```

How to read this structure:

- `feature/<module>/controller` is the API surface for that module.
- `feature/<module>/service` contains business logic and orchestration.
- `feature/<module>/repository` handles data access with Spring Data JPA.
- `feature/<module>/model/entity` is the persistence model.
- `feature/<module>/model/dto` is the API contract model.
- `core/startup` holds shared startup initialization logic.
- Frontend is now in the separate `../frontend/` folder.

## Troubleshooting

- `mvn` not recognized:
  - Reopen terminal after install.
  - Check Maven `bin` is in PATH.
- Java version not 21:
  - Ensure `java -version` returns 21.
- App fails on startup with JWT errors:
  - Ensure `JWT_SECRET` is set and at least 32 chars.
- App fails to connect to PostgreSQL:
  - Confirm DB is running (`docker compose ps`).
  - Verify `APP_DB_PROFILE=postgres`.
  - Verify `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- Port already in use:
  - Run with another port via `--server.port=...`.
- Scrape endpoint returns limited data:
  - Some job pages do not publish full salary/company/location metadata.
  - LinkedIn may limit data for some postings depending on page privacy and anti-bot checks.

## Security Notes

- Do not commit real secrets.
- Keep OAuth credentials and JWT secret in environment variables.
- If using Integrations, keep Google OAuth client credentials private.
- Revoke OAuth app access in Google Account settings if needed.
- Protected endpoints require `Authorization: Bearer <token>`.

## License

Educational project.



