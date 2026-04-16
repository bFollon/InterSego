# Deployment

The InterSego server runs on the home Raspberry Pi (`homeserver`) and is exposed to the internet via a Cloudflare tunnel.

## Overview

```
Mobile apps
    │  HTTPS (Bearer token required)
    ▼
intersego.bfollon.dev  (Cloudflare edge, TLS terminated)
    │  HTTP
    ▼
cloudflared tunnel (ea881d41-92d6-4a17-80b5-5fe69ced9e41 / signoz-tunnel)
    │  HTTP
    ▼
127.0.0.1:3700  ←  intersego-server.service (Node.js)
```

The server only binds to `127.0.0.1`, so it is not reachable from the LAN directly — all traffic must go through the tunnel.

## Services

### `intersego-server` (pm2)

Managed by pm2, running as user `bruno.follon`.

- Working directory: `/home/bruno.follon/services/InterSego/server`
- Reads environment from `.env` (port, host, API key)
- Persisted via `pm2 save` + `pm2 startup`, restarts automatically on failure and reboot

```bash
# Start / stop / restart
pm2 start dist/index.js --name intersego-server
pm2 stop intersego-server
pm2 restart intersego-server

# Logs
pm2 logs intersego-server
pm2 logs intersego-server --lines 100
```

### `cloudflared.service`

Existing tunnel service at `/etc/systemd/system/cloudflared.service`. Config at `/etc/cloudflared/config.yml`.

The `intersego.bfollon.dev` ingress rule was added alongside the existing `signoz` and `otlp` rules:

```yaml
- hostname: intersego.bfollon.dev
  service: http://localhost:3700
```

```bash
sudo systemctl restart cloudflared
```

## DNS

The CNAME record for `intersego.bfollon.dev` was registered via:

```bash
cloudflared tunnel route dns signoz-tunnel intersego.bfollon.dev
```

This pointed the subdomain at the existing tunnel — no Cloudflare dashboard changes were needed.

## Rebuilding after code changes

```bash
cd /home/bruno.follon/services/InterSego/server
git pull
npm install
npm run build
pm2 restart intersego-server
```

## App integration

The server URL is `https://intersego.bfollon.dev`. The API key is in `.env` on the Pi at `/home/bruno.follon/services/InterSego/server/.env`.

### Android

In `android/app/build.gradle.kts`, update the two `buildConfigField` lines:

```kotlin
buildConfigField("String", "BOARDING_SERVER_URL", "\"https://intersego.bfollon.dev\"")
buildConfigField("String", "BOARDING_API_KEY", "\"<API_KEY>\"")
```

`BoardingService` reads these via `BuildConfig.BOARDING_SERVER_URL` and `BuildConfig.BOARDING_API_KEY` — no other changes needed.

### iOS

In `iOS/InterSego/Config/AppConfig.swift`, update the two constants:

```swift
static let boardingServerURL = "https://intersego.bfollon.dev"
static let boardingAPIKey = "<API_KEY>"
```

`BoardingService` reads these via `AppConfig.boardingServerURL` and `AppConfig.boardingAPIKey` — no other changes needed.

---

## Environment variables

| Variable | Description |
|---|---|
| `API_KEY` | Bearer token all clients must include in `Authorization` header |
| `PORT` | Port to listen on (set to `3700`) |
| `HOST` | Interface to bind to (set to `127.0.0.1`) |
