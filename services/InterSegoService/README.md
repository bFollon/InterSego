# InterSego Server

Lightweight boarding notification repository for the InterSego app.
Users tap "I'm on the bus" and this server stores the event so other users on the same route get confirmation and a live punctuality signal.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for design decisions and [`docs/API.md`](docs/API.md) for the full API reference.

## Requirements

- Node.js 20+
- npm

## Setup

```bash
cd server
npm install
cp .env.example .env
```

Edit `.env` and set `API_KEY` to a strong random secret:

```bash
openssl rand -hex 32
```

## Development

```bash
npm run dev
```

The server reloads automatically on file changes.

## Production (Raspberry Pi)

### Build and start

```bash
npm run build
npm start
```

### Run with pm2 (recommended)

```bash
npm install -g pm2
pm2 start dist/index.js --name intersego-server
pm2 save
pm2 startup   # follow the printed command to enable start-on-boot
```

### Expose via Cloudflare Tunnel

1. Install `cloudflared` on the Pi.
2. Authenticate and create a tunnel:
   ```bash
   cloudflared tunnel login
   cloudflared tunnel create intersego
   ```
3. Configure the tunnel to forward to `localhost:3000`.
4. Run the tunnel as a service:
   ```bash
   cloudflared service install
   ```

The `API_KEY` in `.env` protects the endpoints. Never expose the key publicly.

## Project structure

```
server/
├── src/
│   ├── index.ts              # Entry point
│   ├── types.ts              # Shared TypeScript types
│   ├── db/
│   │   └── index.ts          # lowdb setup (JSON file store)
│   ├── middleware/
│   │   └── auth.ts           # Bearer token enforcement
│   └── routes/
│       └── boardings.ts      # POST /boardings, GET /boardings
├── data/
│   └── boardings.json        # Runtime persistence (gitignored)
├── docs/
│   ├── ARCHITECTURE.md       # Design decisions and app integration guide
│   └── API.md                # Full API reference
├── .env.example
├── package.json
└── tsconfig.json
```

## License

GPL-3.0 — see source file headers.
