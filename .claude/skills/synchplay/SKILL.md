---
name: synchplay
description: "Use when working on the SynchPlay project — a full-stack social-graph video recommendation engine (Spring Boot 3 + Vue 3 + PostgreSQL). Triggers: SynchPlay codebase, Spring Boot backend, Vue frontend, graph algorithms (BFS/PageRank/LCC/scoring), JWT auth, PostgreSQL schema, or project documentation questions."
---

# SynchPlay Project Skill

## What This Project Is

SynchPlay is a video recommendation engine that uses **social graph analysis** to combat the "information cocoon" problem. Instead of popularity-only ranking, it analyzes what a user's friends watched and surfaces diverse recommendations via BFS multi-hop recall + PageRank + popularity scoring.

**Architecture:** Vue 3 SPA → REST API (Spring Boot 3, port 8080) → PostgreSQL (port 5432)
**Auth:** Stateless JWT (Bearer token, 24h expiry), BCrypt password hashing
**Graph:** In-memory graph singleton hydrated from Postgres `nodes`/`edges` tables at startup

## Core Principles

1. **Read docs before acting.** The `doc/` folder has authoritative information. When unsure, read the relevant doc before guessing. The docs are always up to date because they were rewritten for v2.
2. **Backend port 8080, frontend dev server on 5173 (or 5174 if busy).** CORS now accepts both.
3. **Never expose secrets.** DB password, JWT secret — all have environment variable overrides. Defaults in `application.yml` are local dev only.
4. **Graph is read-only after startup.** Algorithms run on the in-memory `Graph` singleton, not on the database. Schema changes go through Flyway migrations.

## Project Layout Quick Reference

```
backend-springboot/src/main/java/com/synchplay/
├── config/         SecurityConfig (CORS + filter chain), JwtAuthenticationFilter
├── auth/           AppUser entity, JwtService, AuthController, login/register DTOs
├── domain/         Node, Edge, Graph (ported from v1, ~800 lines for Graph.java)
├── service/        GraphService (hydrates in-memory graph), DataImportService (CSV→DB),
│                   FriendRecommendationService, DemoDataService (seeds demo1/2/3)
└── api/            RecommendController, FriendsController, LccController,
                    PageRankController, StatsController, HealthController

frontend-vue/src/
├── views/          LoginPage, RegisterPage, RecommendPage, FriendsPage,
│                   OverviewPage, LccPage, PageRankPage
├── stores/         auth.js (Pinia: token + currentUser, localStorage persistence)
├── router/         index.js (Vue Router 4: beforeEach guard checks auth)
├── api/            client.js (axios instance with Bearer interceptor + 401 redirect)
└── components/     NavBar, VideoCard, etc.
```

## Database (PostgreSQL 17, database: synchplay)

Three tables, two with DB-enforced foreign keys:

| Table | Rows | PK | Purpose |
|-------|------|-----|--------|
| `nodes` | 483 | `node_id` (VARCHAR 64) | YouTube users + videos |
| `edges` | 945 | `id` (BIGSERIAL) | Relationships: `src`/`dst` FK → `nodes.node_id` |
| `app_users` | 3-∞ | `id` (BIGSERIAL) | Registered accounts; `graph_node_id` logically links to `nodes` but is NOT an FK |

**Key gotcha:** `app_users.graph_node_id` looks like a foreign key but was not declared with `REFERENCES` in Flyway V1__init.sql. There's only a regular index on it. The relationship is enforced by application code only.

Demo accounts (seeded on first boot by DemoDataService):
- `demo1` / `demo2` / `demo3`, password `demo123`
- Each mapped to a different graph user node (round-robin assignment)

## Key Configuration

```yaml
# application.yml defaults — overridable via env vars:
DB_URL:       jdbc:postgresql://localhost:5432/synchplay
DB_USER:      synchplay
DB_PASSWORD:  synchplay_dev
FRONTEND_ORIGIN: http://localhost:5173  # CORS also allows 5174 automatically
JWT_SECRET:   (32+ char secret)
JWT_EXPIRY:   86400  # 24 hours in seconds
```

## Common Commands

```bash
# Start everything
./dev.sh

# Backend only
cd backend-springboot && mvn spring-boot:run

# Frontend only
cd frontend-vue && npm run dev

# Run tests (29 tests)
cd backend-springboot && mvn test

# Smoke test all API endpoints
bash doc/smoke_test.sh

# Connect to database
psql -U synchplay -d synchplay
```

## 6 Graph Algorithms

1. **BFS** (2-3 hop, undirected) — candidate recall from user's social circle
2. **Full-Graph PageRank** (d=0.85, 20 iterations, sink handling)
3. **Watch-Based PageRank** (convergence-based, tol=1e-6)
4. **Composite Scoring**: `score = α×(1/distance) + β×normalizedPR` — dual `prMode` toggle
5. **LCC** (Local Clustering Coefficient) — echo chamber risk metrics per user
6. **Friend Recommendation** — collaborative filtering + Dijkstra + PageRank + popularity

## Gotchas & Recurring Issues

- **@Order on @EventListener**: Must be on the method, not the class. Spring ignores class-level @Order for @EventListener methods.
- **DemoDataService runs after GraphService** (Order 2 → Order 3) — if GraphService hasn't hydrated yet, demo seed skips silently with a WARN log.
- **Login page MUST use raw axios**, not the configured `client` instance. The `client` has a 401 interceptor that calls `store.logout()` + `router.push('/login')` before the error is displayed, destroying the error message.
- **CORS accepts both :5173 and :5174** — Vite auto-switches to :5174 if :5173 is taken.
- **app_users.graph_node_id is NOT a foreign key** — JOINs in queries must be explicit, and orphan references are possible.
- **Stale localStorage token** causes silent redirect loops. Clear browser localStorage if login seems broken despite correct credentials.

## When Things Go Wrong

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Login returns 401 with correct password | DB user doesn't exist or password hash mismatch | Check `psql -c "SELECT * FROM app_users;"` |
| "No graph user nodes found" warning | GraphService didn't hydrate before DemoDataService | Verify @Order on methods, restart backend |
| CORS error in browser | Port mismatch (5174 vs 5173) | Fixed — SecurityConfig now allows both |
| Frontend login shows no error | Using `client` instead of raw `axios` for login | Verify LoginPage imports `axios` directly |
| Token works in curl but not browser | Stale token in localStorage | Clear localStorage or use incognito |
| `mvn` fails with JAVA_HOME | Shell doesn't have JAVA_HOME set | `export JAVA_HOME=$(/usr/libexec/java_home)` |

## Documentation Map

| For this question | Read this doc |
|------------------|--------------|
| What is this project / algorithm descriptions | `doc/PROJECT_SUMMARY.md` |
| What features are planned / built | `doc/REQUIREMENTS.md` |
| How is the system designed | `doc/ARCHITECTURE.md` |
| How do I run / deploy / troubleshoot | `doc/WORKFLOW.md` (also `README.md`) |
| What's the current status / who does what | `doc/PROGRESS.md` |
| Are the APIs working right now | `bash doc/smoke_test.sh` |

## Development Status

All v2 features are **complete** (ahead of schedule — finished W11 by W9). Only remaining task: **final presentation (Jun 1)**. Presentation outline in `doc/SLIDES_OUTLINE.md` needs transfer to slide software for the W12 presentation.
