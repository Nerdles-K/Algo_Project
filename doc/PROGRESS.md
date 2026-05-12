# SynchPlay — Progress Control Document

## 1. Schedule Overview

| Phase | Weeks | Theme | Key Deliverables | Status |
|-------|-------|-------|-----------------|--------|
| Phase 1: Setup | W1–W3 | Environment & Data | Python sampling, VS Code config, CSV export | ✅ **Complete** |
| Phase 2: Algorithm MVP (v1) | W4–W8 | Algorithm Implementation | BFS, PageRank, LCC, Scoring, demo API + frontend | ✅ **Complete** |
| Phase 3 (Original): Experiment Report | W9–W11 | Precision@K evaluation | — | ❌ **Cancelled (2026-05-11)** — replaced by Phase 4 rewrite |
| **Phase 4: Production Rewrite (v2)** | W9–W12 | Spring Boot + Vue + Postgres + Auth | Full-stack rewrite | 🚀 **Active** |

**Current date:** 2026-05-11 (Week 9). Project scope shifted from "algorithm-course MVP" to "production-style full-stack rewrite". v1 codebase preserved under `backend/` + `frontend/`; v2 built fresh under `backend-springboot/` + `frontend-vue/`.

---

## 2. Phase 4 Task Breakdown

### Week 9 (5/11–5/17) — Backend Foundation

| Task | Assignee | Deliverable | Status |
|------|----------|-------------|--------|
| Update REQUIREMENTS / ARCHITECTURE / PROGRESS / WORKFLOW docs | All | doc/*.md v2 | 🔄 In progress |
| Install PostgreSQL locally; create `synchplay` database | Member A | psql connection works (Postgres 17.5 via brew; user=synchplay, db=synchplay) | ✅ 2026-05-11 |
| Scaffold `backend-springboot/` (Maven, Spring Boot 3.3.5, Java 17) | Member A | `mvn compile` succeeds; `mvn spring-boot:run` starts in 2.5s on :8080 | ✅ 2026-05-11 |
| Flyway migration V1__init.sql (app_users, nodes, edges) | Member A | Schema auto-applied on boot; 3 business tables created | ✅ 2026-05-11 |
| Port `Graph.java`, `Node.java`, `Edge.java` to `com.synchplay.domain` | Member B | Algorithms compile under Spring; package updated, no logic changes | ✅ 2026-05-11 |
| CSV → Postgres importer (CommandLineRunner, idempotent) | Member A | First startup imports 483 nodes + 945 edges; later boots skip | ✅ 2026-05-11 |
| `GraphService` Spring bean wraps in-memory Graph | Member B | Hydrates on `ApplicationReadyEvent` in ~10 ms; ordering of import-then-hydrate verified | ✅ 2026-05-11 |
| JWT auth: AppUser entity, JwtService, SecurityConfig, AuthController | Member C | register/login/me all return correct status; bad pw → 401, dup → 409, missing token → 401 | ✅ 2026-05-11 |
| Port `/api/health`, `/api/stats`, `/api/users` to RestController | Member A | All three return JSON matching v1; stats numbers identical (avgDegree=1.96, density=0.0041) | ✅ 2026-05-11 |

### Week 10 (5/18–5/24) — Backend Finish + Frontend Foundation

| Task | Assignee | Deliverable | Status |
|------|----------|-------------|--------|
| Port `/api/recommend` (uses `@AuthenticationPrincipal`) | Member B | Authenticated user gets personalized recs | ✅ 2026-05-11 |
| Port `/api/friends`, `/api/lcc`, `/api/pagerank` | Member C | All v1 endpoints reach feature parity | ✅ 2026-05-11 |
| Scaffold `frontend-vue/` (Vite, Vue Router, Pinia, axios) | Member A | `npm run dev` serves on :5173 | ✅ 2026-05-11 |
| Login + Register pages | Member A | Forms post to /api/auth/*, error display | ✅ 2026-05-11 |
| Pinia auth store + axios interceptor (Bearer token + 401 redirect) | Member A | Login persists across refresh; expired token → /login | ✅ 2026-05-11 |
| Vue Router with guards (`/app/*` requires token) | Member A | Unauthenticated → /login | ✅ 2026-05-11 |
| Recommend tab (port from v1 app.js) | Member B | Sliders, prMode toggle, clickable cards work | ✅ 2026-05-11 |
| Overview tab | Member B | 9 stat cards | ✅ 2026-05-11 |

### Week 11 (5/25–5/31) — Polish + Integration

| Task | Assignee | Deliverable | Status |
|------|----------|-------------|--------|
| Friends tab | Member C | User selector + ranked list | ✅ 2026-05-11 |
| Echo Chamber (LCC) tab | Member C | Risk cards + top-20 table | ✅ 2026-05-11 |
| PageRank tab | Member C | Top-15 video table | ✅ 2026-05-11 |
| End-to-end smoke test (register → login → all 5 tabs) | All | Manual checklist passes | ⏳ |
| Demo data: pre-seed 3 demo accounts mapped to interesting graph nodes | Member A | Login as `demo1/demo2/demo3` shows distinct recs | ✅ 2026-05-12 (DemoDataService seeds on boot; password: demo123) |
| `dev.sh` orchestration script | Member A | One command starts backend + frontend | ✅ 2026-05-12 |
| Bug fixes + UI polish | All | — | ✅ 2026-05-12 (thumbnails added to Recommend/PageRank; dead videos filtered via img @error) |

### Week 12 (6/1) — Final Presentation

| Task | Assignee | Deliverable | Status |
|------|----------|-------------|--------|
| Slide deck (6 algorithms + system architecture + live demo plan) | All | 8–10 slides | ⏳ |
| Live demo rehearsal | All | <10 min demo flow | ⏳ |
| README.md at repo root explaining how to run | Member A | New cloner can `./dev.sh` | ⏳ |
| Final presentation | All | — | ⏳ |

---

## 3. Member Assignment Matrix (Phase 4)

| Module | Member A (Infra) | Member B (Algo+Recs) | Member C (Auth+Aux) |
|--------|:---:|:---:|:---:|
| Postgres install + schema | ● | | |
| Spring Boot scaffold | ● | | |
| CSV importer + Graph bean | ● | ○ | |
| Algorithm port (Graph/BFS/PR/LCC/Scoring) | | ● | |
| Recommend / Stats endpoints | ○ | ● | |
| JWT auth + AuthController | | | ● |
| Friends / LCC / PageRank endpoints | | | ● |
| Vue project scaffold + router + pinia | ● | | |
| Login / Register UI | ● | | |
| Recommend / Overview tabs | | ● | |
| Friends / LCC / PageRank tabs | | | ● |
| Demo prep + slides | ○ | ○ | ○ |

● = Primary  ○ = Supporting

---

## 4. Milestone Tracking

| Milestone | Target Date | Actual | Status |
|-----------|------------|--------|--------|
| v1 MVP complete (Phase 2) | W8 (May 4) | W8 | ✅ |
| Mid-project presentation | W8 (May 5) | May 5 | ✅ |
| v2 scope decision | W9 (May 11) | May 11 | ✅ |
| v2 docs updated | W9 (May 11) | — | 🔄 |
| Backend skeleton runs (Spring Boot + Postgres + auth) | W9 end (May 17) | 2026-05-11 | ✅ Achieved 6 days early |
| All v1 endpoints ported to v2 | W10 (May 24) | 2026-05-11 | ✅ Achieved 13 days early |
| Frontend skeleton runs (Vue + login) | W10 (May 24) | 2026-05-11 | ✅ Achieved 13 days early |
| All 5 tabs working in v2 | W11 (May 31) | 2026-05-11 | ✅ Achieved 20 days early |
| Final presentation | W12 (Jun 1) | — | ⏳ |

---

## 5. Risk Register (Phase 4)

| Risk | Probability | Impact | Mitigation | Status |
|------|:---:|:---:|------------|--------|
| 3-week rewrite is too aggressive | **High** | High | Minimum viable demo = auth + Recommend tab + Overview. If W11 ends without Friends/LCC/PageRank in Vue, expose them as iframes to v1 frontend | 🔄 Active |
| Spring Boot + Java 22 incompat | Low | Medium | Pin Java 17 in pom.xml; downgrade local JDK if needed | 🔄 |
| Postgres install issues on macOS | Medium | Low | Fallback: `docker run -p 5432:5432 postgres:16-alpine` | 🔄 |
| Team unfamiliar with Vue 3 / Spring Security | High | Medium | Reference official docs; member A scaffolds, others fill in incrementally | 🔄 |
| Demo data: all demo users see identical recs (FR-A.6 mapping bug) | Medium | High | Test with 3+ demo users early in W11 | ⏳ |

---

## 6. Next Actions (Priority Order, W9)

1. **Install Postgres 16 + create `synchplay` DB** (~15 min)
2. **Scaffold `backend-springboot/`** with Spring Initializr (web, data-jpa, security, flyway, postgresql) (~20 min)
3. **Write Flyway V1__init.sql** (~20 min)
4. **Port `Node.java`, `Edge.java`, `Graph.java` to new package** (~30 min)
5. **Implement CSV importer + GraphService bean** (~45 min)
6. **AppUser entity + JwtService + SecurityConfig + AuthController** (~2 hours)
7. **Port `/api/health` + `/api/stats` as first secured endpoints; verify with curl** (~30 min)
