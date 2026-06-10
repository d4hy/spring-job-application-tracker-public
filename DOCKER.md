# Docker

This repo has two Compose entry points:

- `docker-compose.yml` at the repo root runs the full stack: PostgreSQL, Spring Boot backend, and Nginx-served frontend.
- `backend/docker-compose.yml` remains a Postgres-only helper for running the backend directly with Maven.

## Full Stack

From the repo root:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Open:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Postgres: `localhost:5432`

For local development, the default `.env.example` values are enough to boot the stack. Replace `JWT_SECRET` before using this anywhere other than local development.

## Common Commands

```powershell
docker compose up --build
docker compose up -d --build
docker compose logs -f backend
docker compose down
```

Reset the Docker Postgres database:

```powershell
docker compose down -v
```

## Configuration

Compose reads root `.env` automatically. Important variables:

- `JWT_SECRET`: JWT signing secret, must be 32+ characters.
- `BACKEND_PORT`: host port for the backend, default `8080`.
- `FRONTEND_PORT`: host port for the frontend, default `5173`.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: database settings.
- `APP_SEED_DEMO_DATA`: set to `true` to seed demo data.
- `APP_OFFLINE_MODE`: set to `true` to disable outbound integrations.

If you change `BACKEND_PORT`, also update `APP_GOOGLE_OAUTH_REDIRECT_URI` and clear any browser-local API override:

```js
localStorage.removeItem("jobTracker.apiBaseUrl");
```

## Notes

- The full-stack Compose file uses the backend `postgres` profile and connects to Postgres over Docker's internal network at `postgres:5432`.
- `FRONTEND_AUTO_OPEN=false` prevents the backend container from trying to open a browser.
- `APP_WORKDAY_AUTOFILL_ENABLED=false` by default because the autofill launcher is designed to open and control a browser session on the host machine, which is not a clean container boundary.
- The frontend image runs `npm ci --ignore-scripts` so the production web build does not download Playwright browsers.
