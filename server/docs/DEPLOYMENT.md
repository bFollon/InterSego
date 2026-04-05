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

### `intersego-server.service`

Systemd unit at `/etc/systemd/system/intersego-server.service`.

- Runs as user `bruno.follon`
- Working directory: `/home/bruno.follon/services/InterSego/server`
- Reads environment from `.env` (port, host, API key)
- Enabled at boot, restarts automatically on failure

```bash
# Start / stop / restart
sudo systemctl start intersego-server
sudo systemctl stop intersego-server
sudo systemctl restart intersego-server

# Logs
journalctl -u intersego-server -f
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
npm run build
sudo systemctl restart intersego-server
```

## Environment variables

| Variable | Description |
|---|---|
| `API_KEY` | Bearer token all clients must include in `Authorization` header |
| `PORT` | Port to listen on (set to `3700`) |
| `HOST` | Interface to bind to (set to `127.0.0.1`) |
