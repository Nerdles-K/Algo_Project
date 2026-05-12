# SynchPlay — Presentation Slide Outline
# Target: 8–10 slides, ~8 min demo + 2 min Q&A

---

## Slide 1 — Title

**SynchPlay: Graph-Powered Video Recommendations**
*Escaping the Echo Chamber with BFS + PageRank*

- Course: Algorithm Design
- Team: Member A · Member B · Member C
- Date: June 1, 2026

---

## Slide 2 — Problem & Motivation

**The Echo Chamber Problem**

- YouTube/TikTok recommendation algorithms trap users in filter bubbles
- Users only see content their immediate network already likes
- Result: narrowing perspectives, declining content diversity

**Our Goal:**
> Given a social graph of users and videos, recommend content that is *socially close* (BFS) but *globally influential* (PageRank) — balancing personalization with diversity.

---

## Slide 3 — Dataset & Graph Structure

**Heterogeneous Graph: 500 nodes, 945 edges**

| Node Type | Count | Attributes |
|-----------|-------|------------|
| User | ~50 | userId |
| Video | ~450 | videoId, title, channel, views, likes, tags |

| Edge Type | Meaning |
|-----------|---------|
| user → user | social follow |
| user → video | watch history |
| video → video | related video |

*Data sourced from YouTube API, preprocessed to CSV, loaded into PostgreSQL on startup.*

---

## Slide 4 — Algorithm 1: BFS (Friend & Video Discovery)

**Breadth-First Search — Multi-hop Traversal**

```
start: logged-in user's graph node
hop 1: direct friends (user→user edges)
hop 2: friends-of-friends
hop 3: their watched videos (user→video edges)
```

**Output:** candidate video set reachable within K hops

**Key insight:** BFS provides the *social distance* score — how many hops from you to a video through your network.

*Time complexity: O(V + E) = O(1 445) — sub-millisecond on our dataset*

---

## Slide 5 — Algorithm 2: PageRank (Video Influence)

**PageRank — Global Importance Score**

Standard iterative formula:
```
PR(v) = (1-d)/N + d · Σ PR(u)/out(u)   [d = 0.85, converges in ~30 iters]
```

**Two modes:**
- **Full-graph PageRank** — all edges (social + watch + related)
- **Watch-graph PageRank** — only user→video and video→video edges (reflects actual viewing behaviour)

**Output:** a score for every video node indicating global influence

---

## Slide 6 — Algorithm 3: Composite Scoring & Recommendation

**Combining 3 weighted features:**

```
finalScore(v) = α · (1 / Dijkstra_dist(u,v))          ← social closeness
              + β · normalizedPageRank(v)              ← global influence
              + γ · normalizedPopularity(v)            ← raw views + likes
                α + β + γ = 1   (auto-normalized from 3 sliders)
```

**Dijkstra weighted distance** uses real edge weights:
- watch edges = 0.1  (strong signal — actual viewing)
- similar edges = 0.5
- social edges = 1.0  (weaker signal)

**Popularity feature** (log-scaled to avoid viral domination):
```
popularity(v) = 0.6 · log(1+views)/log(1+maxViews)
              + 0.4 · log(1+likes)/log(1+maxLikes)
```

**Defaults:** α=0.5, β=0.3, γ=0.2 (privileges social closeness)

**Filtering:**
- Videos already watched by the user are excluded
- Videos with unavailable thumbnails (deleted/private) are hidden

**Result:** ranked list of N recommendations unique to each user's graph position

---

## Slide 7 — Algorithm 4 & 5: LCC + Friend Recommendation

**Largest Connected Component (LCC) — Echo Chamber Detection**

- Union-Find identifies connected components
- LCC size relative to total = echo chamber risk score
- Frontend shows: component count, LCC %, risk level (Low / Medium / High)

**Friend Recommendation (BFS variant)**

- For each candidate user node reachable within 2 hops
- Score = number of shared neighbors (Jaccard-style)
- Returns ranked list: "People you may know"

---

## Slide 8 — System Architecture

```
┌──────────────────────────────────────────────────┐
│  Vue 3 Frontend  (Vite :5173)                    │
│  Login · Register · 5 algorithm tabs             │
│  Pinia auth store · axios Bearer interceptor     │
└────────────────┬─────────────────────────────────┘
                 │ HTTPS (JWT Bearer token)
┌────────────────▼─────────────────────────────────┐
│  Spring Boot 3.3 Backend  (:8080)                │
│  Spring Security + JWT · 7 REST endpoints        │
│  In-memory Graph (500 nodes, 945 edges)          │
└──────────┬──────────────────────────┬────────────┘
           │ JPA (Flyway migrations)  │ CSV import (boot)
┌──────────▼──────────────────────────▼────────────┐
│  PostgreSQL  (synchplay DB)                      │
│  app_users · nodes · edges                       │
└──────────────────────────────────────────────────┘
```

**v2 vs v1:** Spring Boot + PostgreSQL + JWT replaced raw Java HTTP server + SQLite

---

## Slide 9 — Live Demo Flow (~8 min)

1. `./dev.sh` — start backend + frontend in one command
2. **Register** a new account → see JWT auth in action
3. **Overview tab** — 9 live graph statistics
4. **Recommend tab** — adjust α/β slider, toggle PageRank mode, see personalized cards
5. **Friends tab** — BFS-based "people you may know"
6. **Echo Chamber tab** — LCC risk assessment
7. **PageRank tab** — global top videos
8. Login as `demo2` — show different recommendations for different graph position

---

## Slide 10 — Results & Reflection

**What we built:**
- Full-stack app: Spring Boot + PostgreSQL + Vue 3 + JWT in 3 weeks
- 6 algorithms: BFS, PageRank (×2 modes), Composite Scoring, LCC, Friend BFS
- All running live in < 50 ms per request on a 500-node graph

**Lessons learned:**
- Heterogeneous graphs (user + video nodes) require careful BFS scoping
- PageRank mode choice matters: watch-graph scores differ significantly from full-graph
- Production concerns (auth, dead-video filtering, CORS) add ~30% to implementation time

**Future directions:**
- Larger dataset (10 000+ nodes)
- Collaborative filtering to complement graph-distance scoring
- Temporal decay: recent watches weighted higher

---

*Appendix: API reference and full architecture in `doc/PROJECT_SUMMARY.md`*
