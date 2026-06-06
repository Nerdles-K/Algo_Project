# SynchPlay — Development Workflow Document (v2)

> **2026-05-11 (W9):** Workflow rewritten for the Spring Boot + Vue + PostgreSQL stack. v1 workflow (vanilla JS / com.sun.net.httpserver) preserved under §9 for reference.

## 1. Development Environment

### Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| Java JDK | 17 (or 22) | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20+ | `node -v` |
| npm | 10+ | `npm -v` |
| PostgreSQL | 16 or 17 | `psql --version` (this machine: 17.5 via brew) |
| Git | any | `git --version` |

### One-time setup

```bash
# 1. PostgreSQL — local install (or Docker fallback, see §1.1)
brew install postgresql@17       # 16 or 17 both fine
brew services start postgresql@17

# Path note: brew installs psql under /opt/homebrew/opt/postgresql@17/bin/
# Add to PATH or use the full path. Example below uses an alias.
PSQL=/opt/homebrew/opt/postgresql@17/bin/psql

# Create dev database + user
$PSQL -d postgres -c "CREATE USER synchplay WITH PASSWORD 'synchplay_dev';"
$PSQL -d postgres -c "CREATE DATABASE synchplay OWNER synchplay;"
$PSQL -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE synchplay TO synchplay;"

# Verify
$PSQL -d synchplay -U synchplay -c "SELECT current_user, current_database();"

# 2. Backend
cd backend-springboot
mvn -DskipTests package        # one full compile to warm caches

# 3. Frontend
cd ../frontend-vue
npm install
```

### 1.1 Docker fallback (if local Postgres install fails)

```bash
docker run --name synchplay-pg \
  -e POSTGRES_USER=synchplay \
  -e POSTGRES_PASSWORD=synchplay_dev \
  -e POSTGRES_DB=synchplay \
  -p 5432:5432 -d postgres:16-alpine
```

---

## 2. Daily Dev Workflow

### 2.1 Backend (Spring Boot)

**Run dev server:**
```bash
cd backend-springboot
mvn spring-boot:run
# Hot reload: use `mvn spring-boot:run` with spring-boot-devtools dependency
```

**Step-by-step for adding a new feature:**

1. **Design** — sketch the endpoint signature and DTO shape; update `doc/REQUIREMENTS.md` if it's a new FR
2. **JPA entity / migration** — if schema changes, write a Flyway migration `V<N>__<desc>.sql`
3. **Service** — add business logic in a `@Service`
4. **DTO** — create request/response records (`record`s, immutable)
5. **Controller** — add `@RestController` method; use `@AuthenticationPrincipal AppUser user` to get current user
6. **Security** — if endpoint should be public, add to `permitAll()` in `SecurityConfig`
7. **Test** — `curl` with/without token; expect correct status codes
8. **Update docs** — mark the FR done in `REQUIREMENTS.md`; update `PROGRESS.md`

**Compilation:**
```bash
mvn compile          # only compile
mvn test             # run tests (40 unit tests, no DB needed)
mvn package          # build executable JAR
```

**Spring profiles (FR-E.2):** the backend defaults to the `dev` profile
(verbose logging + SQL echo + baked-in JWT secret). For a deployment run:
```bash
SPRING_PROFILES_ACTIVE=prod \
JWT_SECRET=<32+ byte secret> \
FRONTEND_ORIGIN=https://your.app \
  mvn spring-boot:run
```
`prod` quiets logging to INFO and has **no** insecure defaults — if
`JWT_SECRET` or `FRONTEND_ORIGIN` are missing the app fails fast on boot.

### 2.2 Frontend (Vue 3 + Vite)

```bash
cd frontend-vue
npm run dev          # Vite dev server :5173 with HMR
npm run build        # production bundle to dist/
npm run preview      # serve dist/ locally
```

**File layout reminders:**
| What | Where |
|------|-------|
| New page (full route) | `src/pages/<Name>.vue` + register in `router/index.js` |
| Reusable widget | `src/components/<Name>.vue` |
| API call | `src/api/<resource>.js` (use shared `client.js` axios instance) |
| Global state | `src/stores/<name>.js` (Pinia) |
| Style overrides | Scoped `<style>` in SFC; global theme in `src/assets/main.css` |

### 2.3 One-command dev startup (after Phase 4 W11)

```bash
./dev.sh             # runs backend + frontend in parallel; Ctrl+C kills both
```

---

## 3. Testing Workflow

### 3.1 Backend API (curl)

```bash
# Public health
curl -s http://localhost:8080/api/health

# Register
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"a@x.io","password":"secret123"}' | jq -r .token)

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}' | jq -r .token)

# Authenticated calls
curl -s http://localhost:8080/api/auth/me        -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/stats          -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/recommend?alpha=0.4&beta=0.6&prMode=full" \
       -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/friends        -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/lcc            -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/pagerank?top=5" -H "Authorization: Bearer $TOKEN"

# Expect 401 without token
curl -i http://localhost:8080/api/stats          # → 401
```

### 3.2 Frontend manual checklist (per change)

```
[ ] npm run dev starts without console errors
[ ] /login renders; submitting bad creds shows error
[ ] /register works; auto-logs in
[ ] After login, JWT visible in DevTools → Application → localStorage
[ ] All 5 tabs reachable from top nav; no 401s in Network tab
[ ] Logout clears token and routes back to /login
[ ] Refresh on /app/recommend stays logged in (token persisted)
[ ] Refresh with expired/missing token → redirected to /login
```

### 3.3 Database

```bash
# Inspect schema
psql -U synchplay -d synchplay -c "\dt"

# Count rows
psql -U synchplay -d synchplay -c "SELECT COUNT(*) FROM nodes; SELECT COUNT(*) FROM edges; SELECT COUNT(*) FROM app_users;"

# Reset everything (DESTRUCTIVE)
psql -U synchplay -d synchplay -c "DROP TABLE IF EXISTS app_users, edges, nodes CASCADE;"
# Then restart backend → Flyway recreates schema + DataImportService reseeds
```

---

## 4. Git Workflow

### Branching
- `main` — always deployable v1; updated only after v2 milestones merge
- `feature/v2-<area>` — e.g., `feature/v2-auth`, `feature/v2-recommend-port`

### Commit Message Convention
```
<type>: <short description>

feat: add JWT auth + register/login endpoints
feat(v2): port BFS to GraphService
fix(v2): JwtFilter NPE when Authorization header missing
chore: bump spring-boot 3.3.2 → 3.3.3
docs: update REQUIREMENTS.md FR-A.6 to done
refactor(v2): extract DTO records
```

### Before Committing
```
[ ] mvn compile passes (0 errors)
[ ] npm run build passes (frontend)
[ ] curl smoke test passes (at least /api/health + /api/auth/login)
[ ] No secrets committed (JWT secret in application.yml is placeholder only)
[ ] doc/*.md updated if behavior changed
```

---

## 5. Common Tasks

### Add a new authenticated endpoint
```
1. Add method to a @RestController:

   @GetMapping("/api/something")
   public SomethingDto get(@AuthenticationPrincipal AppUser user) { ... }

2. If new service logic needed, add to a @Service
3. Restart backend (or rely on spring-boot-devtools auto-restart)
4. curl with Bearer token
5. Mark the FR done in REQUIREMENTS.md
```

### Add a new Vue page/tab
```
1. Create src/components/SomeTab.vue (if it's a tab under /app)
   or src/pages/SomePage.vue (if it's a full route)
2. Register in src/router/index.js
3. Add link in AppShell.vue (top nav)
4. Add API call in src/api/<resource>.js using shared client
5. npm run dev → manual test
```

### Schema change
```
1. Write src/main/resources/db/migration/V<N>__<description>.sql
2. Update JPA entity to match
3. Restart backend → Flyway auto-applies
4. If migration breaks existing data, document in REQUIREMENTS.md or PROGRESS.md
```

### Debug 401 / token issues
```
1. DevTools Network tab → click failing request → check Authorization header
2. Copy token to https://jwt.io → verify expiry, claims
3. Check application.yml JWT secret matches what signed the token
4. If secret rotated → all tokens invalid → frontend logout + login again
```

### Reset everything (DESTRUCTIVE)
```bash
# Backend
cd backend-springboot
mvn clean
psql -U synchplay -d synchplay -c "DROP TABLE IF EXISTS app_users, edges, nodes, flyway_schema_history CASCADE;"

# Frontend
cd ../frontend-vue
rm -rf node_modules dist
npm install

# Start fresh
cd ../backend-springboot && mvn spring-boot:run &
cd ../frontend-vue && npm run dev
```

---

## 6. Production Readiness Gaps (acknowledged)

| Gap | Why Not Done | When to Address |
|-----|-------------|-----------------|
| No unit / integration tests | Demo first, tests second; tight 3-week budget | If continuing past course |
| HTTPS / TLS | Localhost only | Out of scope |
| Email verification / password reset | No SMTP infra in demo | Out of scope |
| Refresh tokens | Single 24h JWT sufficient | Out of scope |
| Rate limiting | Single-user demo | Out of scope |
| Monitoring (Prometheus / actuator detailed) | `/actuator/health` is enough | Out of scope |
| Docker Compose for full stack | Manual scripts suffice | Nice-to-have if time |

---

## 7. Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Connection refused` to :5432 | Postgres not running | `brew services start postgresql@16` or `docker start synchplay-pg` |
| `password authentication failed for user "synchplay"` | DB user wrong/missing | Re-run the `CREATE USER` snippet in §1 |
| Flyway: `Validate failed` | Migration changed after applied | Don't edit committed migrations; add a new V<N+1> |
| Spring Boot fails to start with `java.lang.UnsupportedClassVersionError` | JDK version mismatch | Run with Java 17 (set `JAVA_HOME` or use `sdk use java 17`) |
| `mvn` not found after brew install | brew bottle didn't symlink due to openjdk dep | Use `brew install --ignore-dependencies maven` (we already have Java 17/22 installed) |
| Flyway: `PostgreSQL 17.5 is newer than this version of Flyway` | Flyway core warns but still works on Postgres 17 | Ignore; or upgrade flyway-database-postgresql when available |
| Frontend CORS error | `application.yml` `cors.allowed-origin` mismatch | Set to `http://localhost:5173` (or wherever Vite serves) |
| 401 even with valid token | JWT secret rotated, or expired | Login again; check `jwt.expiry-seconds` |
| `npm run dev` port :5173 already in use | Another Vite instance | `lsof -ti:5173 \| xargs kill` |

---

## 8. Documentation Discipline

Per project rule: **update doc files after every small completion.**

| When you... | Update |
|------------|--------|
| Implement an FR | mark it ✅ in `REQUIREMENTS.md` |
| Complete a Phase 4 task | check it off in `PROGRESS.md` |
| Change a tech decision | update §6 of `ARCHITECTURE.md` |
| Change a workflow step | update relevant section of this file |
| Hit a non-obvious gotcha | add row to `WORKFLOW.md §7 Troubleshooting` |

---

## 9. v1 Workflow (Removed)

The v1 codebase (`backend/` + `frontend/`) has been removed from the working tree. To inspect or run it, check out a commit before the v2 rewrite from git history (it built via `backend/compile.sh` + `backend/start.sh`, auto-importing CSV → SQLite). The v1-era Python scripts (data prep + algorithm prototypes) are kept at the repo root under `scripts/`.
