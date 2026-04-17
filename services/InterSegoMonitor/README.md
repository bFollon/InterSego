# InterSego Monitor

Watches the [Linecar website](https://www.linecar.es/metropolitano/segovia/) for PDF timetable changes. Runs twice a day (08:00 and 20:00), downloads all route PDFs (M1–M8), compares their SHA-256 checksums against the last known state, and sends an email notification if anything changed.

Useful because the app uses static parsers: if Linecar publishes an updated PDF, users won't see the new times until the parser is manually updated. This service catches that.

## Requirements

- Node.js >= 20
- pm2 (`npm install -g pm2`)

## Environment variables

Copy `.env.example` to `.env` and fill in the values.

| Variable | Required | Default | Description |
|---|---|---|---|
| `API_KEY` | Yes | — | Bearer token required for `POST /check`. Generate with `openssl rand -hex 32`. |
| `SMTP_USER` | Yes | — | iCloud sender address (`follon.bruno@icloud.com`). Used as the SMTP username. |
| `SMTP_PASS` | Yes | — | App-specific password for iCloud SMTP. **Not your Apple ID password.** Generate at [appleid.apple.com](https://appleid.apple.com) → Sign-In & Security → App-Specific Passwords. |
| `NOTIFY_EMAIL` | Yes | — | Address to receive change notifications (`bfollon.dev@icloud.com`). |
| `PORT` | No | `3701` | Port to listen on. |
| `HOST` | No | `0.0.0.0` | Interface to bind to. Use `127.0.0.1` to restrict to localhost. |

## Deploy

```bash
# 1. Clone / pull the repo on the Pi, then:
cd /home/bruno.follon/services/InterSego/monitor

# 2. Install dependencies and build
npm install
npm run build

# 3. Create and fill in .env
cp .env.example .env
nano .env

# 4. Start with pm2
pm2 start dist/index.js --name intersego-monitor

# 5. Persist across reboots (run once)
pm2 save
pm2 startup   # follow the printed instructions if not already set up
```

## Rebuild after code changes

```bash
cd /home/bruno.follon/services/InterSego/monitor
git pull
npm install
npm run build
pm2 restart intersego-monitor
```

## pm2 commands

```bash
pm2 status                          # check process is running
pm2 logs intersego-monitor          # tail logs
pm2 logs intersego-monitor --lines 100
pm2 restart intersego-monitor
pm2 stop intersego-monitor
```

## Endpoints

All endpoints except `/health` require `Authorization: Bearer <API_KEY>`.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | No | Liveness check. Returns `{ status: "ok" }`. |
| `GET` | `/status` | Yes | Last check timestamp and per-route state (URL, SHA-256 prefix, lastChecked, lastChanged). |
| `POST` | `/check` | Yes | Triggers an immediate check. Returns 202 immediately; check runs in the background. |

## How it works

1. On startup, verifies SMTP credentials, then runs an initial check.
2. Cron fires at `0 8,20 * * *` (08:00 and 20:00 server time).
3. Each check:
   - Scrapes the Linecar page for current PDF links.
   - Downloads each PDF and computes a SHA-256 hash.
   - Compares against the stored state in `data/checksums.json`.
   - If any URL or hash changed, sends one summary email listing the affected routes.
4. State survives restarts via `data/checksums.json`. The first run after deployment establishes the baseline — no email is sent on first run.

## Notes

- Linecar's SSL certificate chain is incomplete. The HTTP client skips certificate verification for `linecar.es` only (scoped to a dedicated undici Agent, not a global override).
- iCloud SMTP requires an **app-specific password**, not your regular Apple ID password.
- The service runs on port `3701` by default to avoid conflicting with `intersego-server` (port `3700`).
