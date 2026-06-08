# Deploying SynchPlay to the cloud

The whole app ships as **one Docker image**: the Vue frontend is built and bundled
into the Spring Boot jar, so the backend serves both the API and the website from a
single origin (no CORS, no hard-coded URLs). You only need to attach a **managed
PostgreSQL** database.

```
┌─────────────────────────────┐        ┌──────────────────┐
│  Docker image (one service) │ ─────► │ Managed Postgres │
│  Spring Boot :PORT          │        └──────────────────┘
│   ├─ /            → Vue SPA  │
│   ├─ /api/**      → REST     │
│   └─ /media/**    → uploads  │
└─────────────────────────────┘
```

---

## Required environment variables (set on the platform)

| Variable | Example | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://HOST:5432/synchplay` | JDBC URL of the managed DB |
| `DB_USER` | `synchplay` | DB user |
| `DB_PASSWORD` | `••••••` | DB password |
| `JWT_SECRET` | a random 32+ char string | **required** in prod; sign tokens |
| `FRONTEND_ORIGIN` | `https://your-app.up.railway.app` | the app's own public URL |
| `SPRING_PROFILES_ACTIVE` | `prod` | already defaulted in the image |
| `PORT` | (auto) | injected by the platform; the app honors it |

> The image already sets `SPRING_PROFILES_ACTIVE=prod`, `NODES_CSV`, `EDGES_CSV`.
> On first boot the backend runs Flyway migrations and imports the graph CSVs.

---

## Option A — Railway (recommended, simplest)

1. Push this repo to GitHub.
2. On [railway.app](https://railway.app): **New Project → Deploy from GitHub repo** → pick this repo. Railway detects the `Dockerfile` and builds it.
3. In the same project: **New → Database → PostgreSQL** (one click).
4. Open your **service → Variables** and add (use Railway's reference syntax to pull from the Postgres service):
   ```
   DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DB_USER=${{Postgres.PGUSER}}
   DB_PASSWORD=${{Postgres.PGPASSWORD}}
   JWT_SECRET=<paste a 32+ char random string>
   FRONTEND_ORIGIN=<your service's public URL, e.g. https://xxx.up.railway.app>
   ```
5. **Settings → Networking → Generate Domain** to get the public URL (put it in `FRONTEND_ORIGIN`, then redeploy).
6. Open the URL → the SynchPlay login page loads. Demo accounts `demo1/2/3` (password `demo123`) are seeded on first boot.

## Option B — Render

1. Push to GitHub.
2. **New → PostgreSQL** → create the DB. Note its host, port, database, user, password.
3. **New → Web Service** → connect the repo → Environment: **Docker**.
4. Add the env vars from the table above (build `DB_URL` as `jdbc:postgresql://HOST:PORT/DB` from the Render DB's connection info).
5. Deploy → open the `.onrender.com` URL.

---

## Test the image locally (optional, needs Docker + a Postgres)

```bash
docker build -t synchplay .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/synchplay \
  -e DB_USER=synchplay -e DB_PASSWORD=synchplay_dev \
  -e JWT_SECRET=dev-secret-please-change-32-chars-min \
  -e FRONTEND_ORIGIN=http://localhost:8080 \
  synchplay
# open http://localhost:8080
```

---

## Notes & limitations

- **Uploaded video files are ephemeral.** They live on the container's local disk and are
  wiped on every redeploy. For a demo this is fine; for persistence, attach a volume
  (Railway Volume / Render Disk) and point `UPLOADS_DIR` at it.
- **First boot is slower** (Flyway + CSV import). Later boots skip the import.
- **Local dev is unchanged**: `./dev.sh` still runs frontend (:5173, proxying to :8080)
  and backend (:8080) separately. The single-image bundling only happens in Docker.
