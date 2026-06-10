# Frontend

React + Vite UI for the job tracker.

## Files

- `index.html` - main entry page
- `scrape.html` - legacy alias entry page
- `app.tsx` - React app logic and API calls
- `app.css` - legacy stylesheet (current UI styling is mostly Tailwind classes)
- `package.json` - npm scripts and frontend dependencies
- `vite.config.ts` - Vite dev server settings

## Backup Features

From the `Integrations` tab you can:

- Download a local JSON backup of your tracker data.
- Import a previously downloaded backup file.
- Upload the current backup payload to cloud endpoint if configured in backend.
- Sync current backup rows into a Google Sheet URL using connected Google OAuth.

## Job Form Autofill (Local MVP)

From the `Integrations` tab you can:

- Save a reusable autofill profile (name/contact/address/links/work authorization).
- Paste a job application URL and launch local autofill in a browser session.
- Autofill common text fields such as name, email, phone, address, LinkedIn, website, and work authorization.
- Attach a resume for upload fields when the page exposes a compatible file input.
- Review and submit manually in the opened browser window.

Local setup for autofill:

```powershell
cd frontend
npm install
npm run autofill:install-browsers
```

## Run

Install dependencies:

```powershell
cd frontend
npm install
```

Start React dev server (auto-opens browser) on port `5173`:

```powershell
npm run dev
```

Build production bundle:

```powershell
npm run build
```

Open:

- `http://localhost:5173`

Legacy static server (optional):

```powershell
python start_client.py --port 5174
python start_client.py --no-open
```

## Backend API URL

By default, frontend calls:

- `http://localhost:8080`

You can override the API base URL from browser console:

```js
localStorage.setItem("jobTracker.apiBaseUrl", "http://localhost:8080");
```
