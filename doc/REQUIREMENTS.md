# SynchPlay — Requirements Document (v2)

> **Scope shift (2026-05-11, W9):** The project has been re-scoped from "algorithm-course MVP" (v1, completed) to "production-style full-stack rewrite" (v2). The v1 implementation under `backend/` (com.sun.net.httpserver) and `frontend/` (vanilla JS) is preserved as a reference; v2 is built fresh under new directories (`backend-springboot/`, `frontend-vue/`). Phase 3 experiment report is **dropped** to free up time for the rewrite.

## 1. Project Vision

Build a production-style video recommendation platform that leverages social graph analysis to combat the "information cocoon" (echo chamber) effect. The system must include a real user account model, a relational database, a REST API with authentication, and a modern SPA frontend.

---

## 2. Tech Stack (v2 Target)

| Layer | Choice | Notes |
|-------|--------|-------|
| Frontend | **Vue 3 + Vite + Vue Router + Pinia** | SPA with client-side routing; Pinia for auth/user state |
| Backend | **Spring Boot 3.x + Maven + Java 17** | REST API, JPA, Spring Security |
| Database | **PostgreSQL 17** | JDBC via Spring Data JPA / JdbcTemplate |
| Auth | **JWT (jjwt library)** | Stateless `Authorization: Bearer <token>` header |
| Algorithms | Ported from v1 `Graph.java` into a `GraphService` Spring bean | All 6 algorithms preserved |
| Build/Run | `mvn spring-boot:run` (backend) + `npm run dev` (frontend) | |

---

## 3. Functional Requirements (v2)

### FR-A: User Account System (NEW)

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| FR-A.1 | **User registration**: `POST /api/auth/register` with `{username, email, password}`; password hashed with BCrypt; uniqueness on username/email | High | ✅ |
| FR-A.2 | **User login**: `POST /api/auth/login` returns JWT (24h expiry) on valid credentials | High | ✅ |
| FR-A.3 | **JWT filter**: All `/api/**` except `/api/auth/**` and `/api/health` require valid Bearer token | High | ✅ |
| FR-A.4 | **Current user**: `GET /api/auth/me` returns the authenticated user profile | Medium | ✅ |
| FR-A.5 | **Logout**: Frontend clears localStorage token; backend stateless (no server-side action) | Low | ✅ |
| FR-A.6 | **Link app user → graph user**: On registration, deterministically map new account to one of the 100 dataset user nodes (round-robin or by hash) so recommendations work immediately | Medium | ✅ (round-robin by `repo.count() % 100`, sorted by nodeId for stability) |
| FR-A.7 | **Watch history tracking**: `POST /api/watch-history` records a watch event; `GET /api/watch-history` returns the authenticated user's watch history; stored in `watch_history` table | High | ✅ (2026-05-18) |
| FR-A.9 | **Watch feedback loop**: recording a watch also creates a `user→video` `watch` edge (weight 0.1) in the `edges` table + in-memory graph (idempotent), so real viewing behaviour feeds back into Dijkstra distance and watch-based PageRank | High | ✅ (2026-06-06) |
| FR-A.8 | **User roles**: `app_users.role` column (USER/ADMIN); demo1 is ADMIN; role embedded in JWT and used for authorization | Medium | ✅ (2026-05-18) |

### FR-B: Database (NEW / MIGRATED)

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| FR-B.1 | **PostgreSQL schema** with tables: `app_users`, `nodes`, `edges`, indexed on FK columns | High | ✅ (Postgres 17.5; schema in V1__init.sql) |
| FR-B.2 | **One-time CSV → Postgres import** via Spring CommandLineRunner on first startup (idempotent: skip if tables non-empty) | High | ✅ (DataImportService; verified 500→483 dedup + 945 edges import) |
| FR-B.3 | **JPA entities**: `AppUser`, `GraphNode`, `GraphEdge` with appropriate relationships | High | ✅ partially: `AppUser` JPA; nodes/edges use `JdbcTemplate` directly (lighter, no per-row entity overhead for read-heavy graph load) |
| FR-B.4 | **Flyway migration scripts** for schema versioning (V1 base → V6: watch_history, role, native-video columns, tags, category/published_at) | Medium | ✅ |
| FR-B.5 | **HikariCP pool** with size 10 (Spring Boot default) | Low | ✅ (default) |

### FR-C: Recommendation Engine (PORTED)

All 6 algorithms preserved from v1, exposed via authenticated REST endpoints. The `Graph` is loaded into memory once at startup from Postgres (not CSV).

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| FR-C.1 | BFS Candidate Recall (2/3-hop, undirected, visited HashSet) | High | ✅ |
| FR-C.2 | Full-Graph PageRank (d=0.85, 20 iter, sink handling) | High | ✅ |
| FR-C.3 | Watch-Based PageRank (convergence-based, tol=1e-6) | High | ✅ |
| FR-C.4 | Composite Scoring `α×(1/dist) + β×normPR + γ×popularity` (Dijkstra distance) with dual `prMode` | High | ✅ |
| FR-C.5 | `GET /api/recommend?alpha=&beta=&gamma=&prMode=&mode=` — uses **current authenticated user** as target (no need to pass userId) | High | ✅ |
| FR-C.6 | LCC computation + Echo Chamber endpoint | Medium | ✅ |
| FR-C.7 | Friend Recommendation (collaborative filtering) | Medium | ✅ |
| FR-C.8 | **Friend edge creation/deletion**: `POST /api/friends` creates a social edge (persisted to DB + in-memory graph); `DELETE /api/friends` removes it | Medium | ✅ (2026-05-18) |
| FR-C.9 | **Exclude already-watched**: `/api/recommend` and `/api/friends/{id}/recommend` drop videos the target has a direct `watch` edge to, so watched videos never re-surface and the feed shifts as you watch | High | ✅ (2026-06-06) |
| FR-C.10 | **Creator publish**: `POST /api/videos` registers a new `video` node + `creator→video` "uploaded" edge (DB + in-memory graph) from a real YouTube link; `GET /api/videos/mine` lists the user's published videos. New nodes start cold (reachable only via the creator's edges) and accrue reach/PageRank organically | Medium | ✅ (2026-06-06) |
| FR-C.11 | **Native video upload**: `POST /api/videos/upload` (multipart) stores a real video file + browser-captured thumbnail under `./uploads`, served at `/media/**` with HTTP Range; node carries `source='native'` and is played in-app via an HTML5 `<video>` modal. Native + YouTube videos coexist in the graph and all tabs | Medium | ✅ (2026-06-06) |
| FR-C.12 | **Content-diversity signal**: video `category` (from USvideos `category_id` via `US_category_id.json`) + `published_at` stored on nodes (Flyway V5 `tags`, V6 `category`/`published_at`; `DataImportService` backfills existing DBs) | Medium | ✅ (2026-06-08) |
| FR-C.13 | **Composite cocoon score**: `computeCocoonScore` = 0.5×LCC (social closure) + 0.5×(1−watch-topic-entropy) (content concentration); only signals with data are counted and re-normalised; `/api/lcc` returns score + breakdown + level | Medium | ✅ (2026-06-08) |
| FR-C.14 | **Explore (break-the-cocoon) mode**: `GET /api/recommend?mode=explore` re-ranks the same reachable candidates by category novelty (0.7×novelty + 0.3×composite), surfacing topics the user rarely watches; `mode=foryou` is the default relevance ranking | Medium | ✅ (2026-06-09) |

### FR-D: Frontend (REWRITE)

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| FR-D.1 | **Vue 3 + Vite project scaffold** with `<script setup>` SFC syntax | High | ✅ |
| FR-D.2 | **Vue Router**: `/login`, `/register`, `/app` (guarded), `/app/recommend`, `/app/friends`, `/app/overview` (admin-only), `/app/lcc`, `/app/pagerank`, `/app/watch-history`, `/app/upload` | High | ✅ |
| FR-D.3 | **Pinia auth store**: `token`, `currentUser`; persists to localStorage | High | ✅ |
| FR-D.4 | **API client (axios)** with interceptor: auto-inject Bearer token, redirect to `/login` on 401 | High | ✅ |
| FR-D.5 | **Login page**: username + password, "register" link, error handling | High | ✅ |
| FR-D.6 | **Register page**: username + email + password + confirm-password, client-side validation | High | ✅ |
| FR-D.7 | **All 5 v1 tabs reimplemented as Vue components** (Recommend, Friends, Overview, LCC, PageRank), preserving feature parity (sliders, prMode toggle, clickable video cards, YouTube links) | High | ✅ |
| FR-D.8 | **Top nav** shows logged-in username + logout button | Medium | ✅ |
| FR-D.9 | **Dark theme** preserved from v1 | Low | ✅ |
| FR-D.10 | **Watch History tab**: `/app/watch-history` shows the authenticated user's watch history in a table (thumbnail, title, channel, timestamp) | Medium | ✅ (2026-05-18) |
| FR-D.11 | **LCC personal view**: Each user sees only their own echo chamber level; admin users can load the all-users table via `/api/lcc/admin` | Medium | ✅ (2026-05-18) |
| FR-D.12 | **Friends tab with Follow/Unfollow**: Existing friends shown with Unfollow button; recommended friends with Follow button; API calls persist edges | Medium | ✅ (2026-05-18) |
| FR-D.13 | **Upload tab**: `/app/upload` — creator form (YouTube link, title, channel, initial views/likes) posts to `/api/videos`; below it a "My Videos" grid loaded from `/api/videos/mine` | Medium | ✅ (2026-06-06) |
| FR-D.14 | **Echo Chamber cocoon view**: LCC tab shows the composite cocoon score with its social-closure / content-concentration breakdown bars | Medium | ✅ (2026-06-08) |
| FR-D.15 | **For You / Explore toggle**: RecommendTab lets the user switch between relevance (`foryou`) and break-the-cocoon (`explore`) ranking | Medium | ✅ (2026-06-09) |

### FR-E: System Operations

| ID | Requirement | Priority | Status |
|----|------------|----------|--------|
| FR-E.1 | `GET /api/health` returns `{status, db: ok/error, graph: {nodes, edges}}` — public | High | ✅ |
| FR-E.2 | Spring profiles: `dev` (verbose logs), `prod` (info+) | Medium | ✅ (`application.yml` multi-doc; defaults to `dev`; `prod` quiets logs + requires `JWT_SECRET`/`FRONTEND_ORIGIN` env, no insecure defaults) |
| FR-E.3 | `application.yml` with externalized config (DB URL, JWT secret) | High | ✅ (all values overridable via env vars) |
| FR-E.4 | CORS allow `http://localhost:5173` (Vite dev) and configurable origin | High | ✅ (`synchplay.cors.allowed-origin`) |
| FR-E.5 | One-command dev startup: `bash dev.sh` (parallel `mvn spring-boot:run` + `npm run dev`) | Medium | ✅ |

---

## 4. Non-Functional Requirements

| ID | Requirement | Target |
|----|------------|--------|
| NFR-1 | **Performance**: All API endpoints respond within 500ms for 500-node graph | Inherits from v1 |
| NFR-2 | **Security**: Passwords hashed with BCrypt (cost 10+); JWT secret externalized via env var | New |
| NFR-3 | **Separation of Concerns**: Frontend and backend independently deployable | Preserved |
| NFR-4 | **Reproducibility**: Data import idempotent, Flyway version-controlled migrations | New |
| NFR-5 | **Type Safety**: All Spring controllers use DTOs (not raw Map/JSON strings) | New |
| NFR-6 | **Authentication on protected endpoints**: 401 if no token; 403 if token invalid | New |

---

## 5. Out of Scope (v2)

| Item | Reason |
|------|--------|
| Password reset / email verification | Demo-only environment, no SMTP infrastructure |
| OAuth / social login | Course scope |
| Multi-tenancy / org accounts | Single-tenant demo |
| Production deployment (HTTPS, domain, prod DB) | Localhost demo only |
| Experiment report / Precision@K evaluation | Dropped to free time for the rewrite (see PROGRESS.md) |

---

## 6. Use Case Summary (v2)

```
Primary Flow: New user signs up and gets recommendations
  1. User opens http://localhost:5173 → redirected to /login
  2. Clicks "Register" → fills form → POST /api/auth/register → auto-login (JWT returned)
  3. Redirected to /app/recommend → frontend calls GET /api/recommend
  4. Backend: JWT filter resolves current user → maps to dataset graph node → runs BFS+PR+Score
  5. Frontend renders ranked video cards → user clicks → opens youtube.com in new tab

Secondary Flow: Returning user logs in
  1. User opens http://localhost:5173 → /login
  2. Enters credentials → POST /api/auth/login → JWT stored in localStorage
  3. Auto-navigates to last visited tab via Vue Router

Supporting Flow: Echo chamber check
  1. User clicks "Echo Chamber" → GET /api/lcc with Bearer token
  2. Renders 100 users with LCC scores + risk levels
```

---

## 7. Acceptance Criteria (v2)

| Scenario | Criteria |
|----------|----------|
| Backend starts | `mvn spring-boot:run` from `backend-springboot/`; Postgres connection succeeds; CSV imported (or skipped if data exists) |
| Frontend starts | `npm run dev` from `frontend-vue/`; opens on `:5173` |
| Registration works | POST /api/auth/register with new username returns 201 + JWT; duplicate returns 409 |
| Login works | POST /api/auth/login with valid creds returns 200 + JWT; bad creds return 401 |
| Protected endpoint requires auth | GET /api/recommend without token returns 401 |
| All tabs render | Logged-in user can navigate to all 7 tabs without errors |
| Recommendations personalized | Different registered users get different recommendation orderings (mapped to different graph nodes) |
| Logout works | Clicking logout clears token, redirects to /login, subsequent API calls return 401 |

---

## 8. Migration Plan (v1 → v2)

| Step | What | When |
|------|------|------|
| 1 | Keep v1 code under `backend/` and `frontend/` during the rewrite (later removed from the tree once v2 was stable; recoverable from git history) | W9 (done) |
| 2 | Scaffold `backend-springboot/` (pom.xml, application.yml, Postgres schema) | W9 |
| 3 | Port `Graph.java`, `Node.java`, `Edge.java`, `DataLoader.java` into Spring services | W9 |
| 4 | Implement auth (entities, JwtService, SecurityConfig, controllers) | W9–W10 |
| 5 | Port REST endpoints into `@RestController`s with `@AuthenticationPrincipal` for current user | W10 |
| 6 | Scaffold `frontend-vue/` (vite, router, pinia, axios) | W10 |
| 7 | Build Login/Register pages | W10 |
| 8 | Port 5 tabs to Vue components | W10–W11 |
| 9 | Integration testing, polish, demo prep | W11 |
| 10 | Final presentation | W12 |

---

## 9. Risk Register (v2 Scope)

| Risk | Probability | Impact | Mitigation |
|------|:---:|:---:|------------|
| 3-week rewrite is too aggressive | **High** | High | Reduce scope: cut Friends or PageRank tabs from frontend if running out of time; auth + Recommend + Overview is minimum viable demo |
| PostgreSQL setup friction on macOS | Medium | Low | Use Docker Postgres image as backup if local install fails |
| Java 22 incompat with Spring Boot 3 | Low | Medium | Pin Spring Boot 3.3.x + Java 17 in pom.xml (set source/target/release) |
| Team members new to Spring/Vue | High | Medium | Member A leads scaffolding; Members B/C focus on porting algorithms and building tabs once scaffold is ready |
| Vue + Vite build issues | Medium | Low | Use `npm create vue@latest` official scaffolder, well-documented |
