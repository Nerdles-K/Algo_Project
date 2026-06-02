# SynchPlay — Project Architecture Document (v2)

> **2026-05-11 (W9):** Project re-scoped from "algorithm-course MVP" (v1) to "production-style full-stack" (v2). v1 implementation is preserved under `backend/` + `frontend/` as a reference. This document describes the **v2 target architecture**; v1 layout is documented in §11.

## 1. Overview

SynchPlay is a social-graph-based video recommendation engine. It addresses the "information cocoon" problem by analyzing a user's social circle to surface videos watched by friends, rather than relying solely on popularity-based ranking.

**Architecture style (v2):**
- SPA frontend (Vue 3) → REST API (Spring Boot 3) → PostgreSQL 16
- Stateless JWT authentication
- Algorithms run on an in-memory Graph hydrated from Postgres at startup

---

## 2. System Architecture Diagram (v2)

```
┌────────────────────────────────────────────────────────────┐
│  Frontend SPA (frontend-vue/) — Vite dev server :5173      │
│  Vue 3 + Vue Router + Pinia + axios                        │
│                                                            │
│  Routes:                                                   │
│    /login            (public)                              │
│    /register         (public)                              │
│    /app/recommend    (guarded)  ─┐                         │
│    /app/friends      (guarded)   │                         │
│    /app/overview     (guarded)   │ All call API with       │
│    /app/lcc          (guarded)   │ Authorization: Bearer   │
│    /app/pagerank     (guarded)  ─┘                         │
└──────────────┬─────────────────────────────────────────────┘
               │  fetch + JWT
               ▼
┌────────────────────────────────────────────────────────────┐
│  Backend (backend-springboot/) — Spring Boot 3 :8080       │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  SecurityFilterChain (JwtAuthenticationFilter)       │  │
│  │   permit: /api/auth/**, /api/health                  │  │
│  │   require: every other /api/**                       │  │
│  └─────────────────────────────────────────────────────┘  │
│                            │                               │
│         ┌──────────────────┼─────────────────────┐        │
│         ▼                  ▼                     ▼        │
│  AuthController     RecommendController    StatsController│
│  /register /login   /api/recommend         /api/stats     │
│  /me                /api/friends           /api/health    │
│                     /api/lcc                              │
│                     /api/pagerank                         │
│                            │                               │
│            ┌───────────────┴────────────────┐             │
│            ▼                                ▼             │
│      GraphService                     UserService          │
│      (in-mem Graph)                   (JPA: AppUser repo)  │
│            │                                │             │
│            └────────────┬───────────────────┘             │
│                         ▼                                  │
│                   Spring Data JPA + HikariCP               │
└────────────────────────────┼───────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────┐
│  PostgreSQL 16  (synchplay database)                       │
│    app_users(id, username, email, password_hash, ...)       │
│    nodes(node_id, node_type, ...)                          │
│    edges(src, dst, edge_type, weight)                      │
│  Managed via Flyway migrations                             │
└────────────────────────────────────────────────────────────┘
```

---

## 3. Directory Structure (v2)

```
Algo_Project/
├── backend-springboot/             # NEW: Spring Boot rewrite
│   ├── pom.xml
│   ├── src/main/java/com/synchplay/
│   │   ├── SynchPlayApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java       # SecurityFilterChain, CORS
│   │   │   └── JwtAuthenticationFilter.java
│   │   ├── auth/
│   │   │   ├── AppUser.java              # JPA entity
│   │   │   ├── AppUserRepository.java
│   │   │   ├── AuthController.java       # /api/auth/register, /login, /me
│   │   │   ├── JwtService.java
│   │   │   └── dto/                      # RegisterRequest, LoginRequest, TokenResponse
│   │   ├── domain/                       # Ported from v1 model/
│   │   │   ├── Node.java
│   │   │   ├── Edge.java
│   │   │   └── Graph.java
│   │   ├── service/
│   │   │   ├── GraphService.java         # In-memory graph singleton bean
│   │   │   ├── DataImportService.java    # CSV → Postgres on first boot
│   │   │   └── FriendRecommendationService.java
│   │   └── api/
│   │       ├── RecommendController.java
│   │       ├── FriendsController.java
│   │       ├── LccController.java
│   │       ├── PageRankController.java
│   │       └── StatsController.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   │       └── V1__init.sql              # Flyway schema
│   └── src/test/java/                    # Future: integration tests
│
├── frontend-vue/                   # NEW: Vue 3 rewrite
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── main.js
│       ├── App.vue
│       ├── router/index.js               # Routes + auth guard
│       ├── stores/auth.js                # Pinia auth store
│       ├── api/client.js                 # axios instance + interceptors
│       ├── pages/
│       │   ├── LoginPage.vue
│       │   ├── RegisterPage.vue
│       │   └── AppShell.vue              # Top nav + <router-view>
│       └── components/
│           ├── RecommendTab.vue
│           ├── FriendsTab.vue
│           ├── OverviewTab.vue
│           ├── LccTab.vue
│           └── PageRankTab.vue
│
├── backend/                        # PRESERVED: v1 reference impl (do not modify)
├── frontend/                       # PRESERVED: v1 reference impl (do not modify)
├── ProcessedData/                  # Shared: CSV + initial DB seed
├── Dataset/                        # Raw data
├── doc/                            # Engineering docs (this folder)
├── docs/                           # Original project summary
└── dev.sh                          # NEW: parallel start backend + frontend
```

---

## 4. Component Design (v2)

### 4.1 Data Layer (PostgreSQL)

```sql
-- V1__init.sql (managed by Flyway)

CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(72)  NOT NULL,         -- BCrypt
    graph_node_id VARCHAR(64)  NOT NULL,         -- FK-like ref to nodes.node_id (user_xxxxx)
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE nodes (
    node_id      VARCHAR(64) PRIMARY KEY,
    node_type    VARCHAR(16) NOT NULL,    -- 'user' | 'video'
    original_id  VARCHAR(64),
    display_name TEXT,
    channel      TEXT,
    views        BIGINT,
    likes        BIGINT
);

CREATE TABLE edges (
    id        BIGSERIAL PRIMARY KEY,
    src       VARCHAR(64) NOT NULL REFERENCES nodes(node_id),
    dst       VARCHAR(64) NOT NULL REFERENCES nodes(node_id),
    edge_type VARCHAR(16) NOT NULL,        -- 'community' | 'watch' | 'similar'
    weight    DOUBLE PRECISION NOT NULL DEFAULT 1.0
);

CREATE INDEX idx_edges_src       ON edges(src);
CREATE INDEX idx_edges_dst       ON edges(dst);
CREATE INDEX idx_edges_type      ON edges(edge_type);
CREATE INDEX idx_nodes_type      ON nodes(node_type);
```

### 4.2 Domain Layer (Graph in memory)

Same as v1 — `Graph.java` ported verbatim (no algorithm changes). Held by `GraphService` Spring bean and hydrated from Postgres in `@PostConstruct`. Re-uses adjacency list + reverse adjacency list.

**Why hydrate to memory?** Algorithms (BFS, PageRank, LCC) touch every node/edge multiple times. Running them via SQL each request would be 10–100× slower. Postgres = source of truth, memory = compute layer.

### 4.3 Algorithm Layer

| # | Algorithm | Method | Purpose |
|---|-----------|--------|---------|
| 1 | BFS Multi-Hop Recall | `Graph.findCandidateVideosByBFS(userId, depth)` | Candidate discovery |
| 2 | Full-Graph PageRank | `Graph.computePageRank(20, 0.85)` | Global video authority |
| 3 | Watch-Based PageRank | `Graph.computeWatchBasedPageRank(0.85, 50, 1e-6)` | User-centric PR |
| 4 | Composite Scoring | `Graph.rankCandidatesByCompositeScore(uid, α, β, prMode)` | finalScore = α×(1/dist) + β×normPR |
| 5 | LCC Echo Chamber | `Graph.computeLocalClusteringCoefficient(uid)` | Echo-chamber risk |
| 6 | Friend Recommendation | `FriendRecommendationService.recommend(uid)` | Co-watch CF |

### 4.4 Auth Layer

```
POST /api/auth/register
  Body: { username, email, password }
  Logic:
    1. Validate uniqueness on username, email
    2. password_hash = BCrypt.hashpw(password)
    3. graph_node_id = pickGraphUserNode()   // round-robin from the 100 dataset users
    4. INSERT app_users
    5. Generate JWT, return { token, user }

POST /api/auth/login
  Body: { username, password }
  Logic:
    1. Load AppUser by username
    2. BCrypt.checkpw(password, user.password_hash)
    3. Generate JWT, return { token, user }

GET /api/auth/me
  Header: Authorization: Bearer <token>
  Returns: { id, username, email, graphNodeId }
```

**JWT claims:** `sub` = username, `uid` = app_users.id, `gnid` = graph_node_id, `exp` = +24h.

### 4.5 API Layer (target endpoints, v2)

| Endpoint | Method | Auth | Notes |
|----------|--------|:---:|-------|
| `/api/health` | GET | ❌ | Public; for liveness |
| `/api/auth/register` | POST | ❌ | Body: RegisterRequest |
| `/api/auth/login` | POST | ❌ | Body: LoginRequest |
| `/api/auth/me` | GET | ✅ | Current user info |
| `/api/stats` | GET | ✅ | Graph + DB stats |
| `/api/users` | GET | ✅ | All graph users (for friend lookup) |
| `/api/recommend` | GET | ✅ | **No `userId` param** — uses JWT current user; supports `alpha`, `beta`, `prMode` |
| `/api/friends` | GET | ✅ | Existing friends + recommendations for current user |
| `/api/friends` | POST | ✅ | Create a social edge (body: `{targetNodeId}`) |
| `/api/friends` | DELETE | ✅ | Remove a social edge (body: `{targetNodeId}`) |
| `/api/lcc` | GET | ✅ | Current user's personal LCC only |
| `/api/lcc/admin` | GET | 🔒 ADMIN | All users' LCC (admin only) |
| `/api/pagerank` | GET | ✅ | top= param |
| `/api/watch-history` | GET | ✅ | Current user's watch history |
| `/api/watch-history` | POST | ✅ | Record a watch event (body: `{videoNodeId, videoId, title, channel}`) |

### 4.6 Frontend Layer (Vue 3 SPA)

| Route | Component | Notes |
|-------|-----------|-------|
| `/login` | `LoginPage.vue` | Public |
| `/register` | `RegisterPage.vue` | Public |
| `/app` | `AppShell.vue` | Guarded; wraps top nav + `<router-view>` |
| `/app/recommend` | `RecommendTab.vue` | Default after login; sliders + prMode toggle + clickable cards |
| `/app/friends` | `FriendsTab.vue` | Existing friends + Follow/Unfollow buttons + recommendations |
| `/app/overview` | `OverviewTab.vue` | Stat cards |
| `/app/lcc` | `LccTab.vue` | Personal echo chamber card + admin all-users view |
| `/app/pagerank` | `PageRankTab.vue` | Clickable leaderboard (records watch history) |
| `/app/watch-history` | `WatchHistoryTab.vue` | Table of watched videos |

**State management (Pinia):**
- `useAuthStore()` — `token`, `currentUser`, `login()`, `register()`, `logout()`, persisted to localStorage

**API client (axios):**
- Request interceptor adds `Authorization: Bearer ${token}`
- Response interceptor: on 401 → clear store, push to `/login`

---

## 5. Data Flow (v2)

```
1. INITIAL STARTUP (one-time per DB)
   Postgres empty  →  Flyway runs V1__init.sql (schema)
                  →  DataImportService scans app_users — if 0 nodes table empty:
                          CSV (ProcessedData/mini_*.csv) → JPA batch insert
                  →  GraphService.@PostConstruct loads nodes+edges from Postgres → Graph

2. REGISTER FLOW
   Browser /register → POST /api/auth/register
                     → BCrypt hash, pickGraphUserNode (round-robin),
                       INSERT app_users, sign JWT
                     → Response 201 { token, user }
                     → Vue: store in Pinia + localStorage, navigate to /app/recommend

3. RECOMMEND REQUEST
   Browser GET /api/recommend (Bearer token)
       → JwtAuthFilter parses token → SecurityContext set
       → RecommendController reads @AuthenticationPrincipal → graphNodeId
       → GraphService.runRecommend(graphNodeId, alpha, beta, prMode)
       → Returns ranked list → JSON response → Vue renders cards
```

---

## 6. Key Design Decisions (v2)

| Decision | Rationale |
|----------|-----------|
| Spring Boot 3 + Java 17 | Mainstream; tooling/ecosystem; Pinned to 17 since Spring Boot 3.3 supports 17 best. Java 22 also OK but 17 is safer |
| PostgreSQL over MySQL | Better SQL standard support, recursive CTEs (future use), JSON support |
| Flyway over Liquibase | Simpler config; SQL-based migrations are easier to review |
| JWT over session cookies | Stateless backend; simpler frontend (no CSRF issues with cookie-less auth); standard for SPAs |
| BCrypt over Argon2 / scrypt | Built into Spring Security; adequate for course demo |
| Vue 3 `<script setup>` over Options API | Less boilerplate; first-class Composition API support |
| Pinia over Vuex | Official successor; simpler API |
| Vite over webpack | Fast HMR; default for Vue 3 |
| Hydrate Graph in memory at startup | Algorithm performance: BFS/PR/LCC must scan many nodes/edges per request |
| Round-robin map app user → graph user | Lets every registered user immediately see meaningful recommendations against the dataset; alternative would be empty-state UX |
| Preserve v1 codebase | Reference impl; useful if v2 stalls — fall back to demoing v1 |

---

## 7. External Dependencies (v2)

| Dependency | Version | Purpose |
|------------|---------|---------|
| Java JDK | 17 (or 22) | Runtime |
| Maven | 3.9+ | Build |
| Spring Boot | 3.3.x | Web framework |
| Spring Security | bundled | Auth filter chain |
| spring-boot-starter-data-jpa | bundled | ORM |
| postgresql | 42.7+ | JDBC driver |
| flyway-core | bundled (Spring Boot manages) | Migrations |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12+ | JWT signing |
| BCrypt | bundled (Spring Security) | Password hashing |
| PostgreSQL | 16 | Database server |
| Node.js | 20+ | Frontend dev |
| Vue | 3.4+ | SPA framework |
| Vite | 5+ | Build tool |
| vue-router | 4+ | Routing |
| pinia | 2+ | State |
| axios | 1+ | HTTP client |

---

## 8. Configuration

`backend-springboot/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url:  ${DB_URL:jdbc:postgresql://localhost:5432/synchplay}
    username: ${DB_USER:synchplay}
    password: ${DB_PASSWORD:synchplay_dev}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

synchplay:
  data:
    nodes-csv: ${NODES_CSV:../ProcessedData/mini_nodes.csv}
    edges-csv: ${EDGES_CSV:../ProcessedData/mini_edges.csv}
  jwt:
    secret: ${JWT_SECRET:CHANGE_ME_DEV_ONLY_NOT_FOR_PROD_xxxxxxxx}
    expiry-seconds: 86400
  cors:
    allowed-origin: ${FRONTEND_ORIGIN:http://localhost:5173}

server.port: 8080
```

---

## 9. Security Notes (v2)

| Concern | Approach |
|---------|----------|
| Password storage | BCrypt (Spring Security's default `PasswordEncoder`) |
| Token tampering | JWT HMAC-SHA256 signature with externalized secret |
| Token replay | Short-ish expiry (24h); no refresh token in v1 of v2 |
| CORS | Whitelist Vite origin only; credentials false (JWT in header) |
| SQL injection | JPA parameterized queries; no string concatenation |
| Mass assignment | DTOs at API boundary, never expose JPA entities directly |
| Demo secret in repo | `application.yml` ships with a placeholder; real secret via env var |

---

## 10. Out-of-Scope Acknowledgments

- HTTPS / production TLS (localhost demo only)
- Refresh tokens
- Email verification
- Password reset
- Rate limiting
- CSRF protection (not needed for header-based JWT)
- Multi-region deployment

---

## 11. v1 Reference Architecture (Preserved)

The v1 implementation under `backend/` (com.sun.net.httpserver, SQLite) and `frontend/` (vanilla HTML/CSS/JS) is **preserved unmodified** as a reference. It demonstrates the same 6 algorithms in a zero-dependency form. See git history before commit `<v2-start>` for the original architecture document. Run v1 with:

```bash
cd backend && bash start.sh        # localhost:8080
open ../frontend/index.html
```
