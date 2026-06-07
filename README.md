# SynchPlay — Algorithm Project

A full-stack video recommendation platform that applies graph algorithms (BFS, PageRank, LCC, Dijkstra-style scoring) to a YouTube social network dataset.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3.5, Java 17, Spring Security (JWT) |
| Database | PostgreSQL 17 (Flyway migrations) |
| Frontend | Vue 3, Vite, Pinia, Vue Router, Axios |
| Algorithms | In-memory graph: BFS, PageRank, LCC, Composite Scoring |

---

## Prerequisites

- **Java 17+** (`java -version`)
- **Maven 3.8+** (`mvn -version`)
- **Node.js 18+** (`node -version`)
- **PostgreSQL 17** running locally

---

## Quick Start

### 1. Set up PostgreSQL

```bash
# Create the database and user (run once)
psql postgres -c "CREATE USER synchplay WITH PASSWORD 'synchplay_dev';"
psql postgres -c "CREATE DATABASE synchplay OWNER synchplay;"
```

### 2. Start everything

```bash
./dev.sh
```

This script starts the Spring Boot backend on **:8080** and the Vite frontend on **:5173** in parallel. Press `Ctrl+C` to stop both.

### 3. Open the app

Visit **http://localhost:5173**

---

## Demo Accounts

Three accounts are seeded automatically on first boot:

| Username | Password | Notes |
|----------|----------|-------|
| `demo1` | `demo123` | Maps to graph node A · **ADMIN** (can view all-users echo-chamber table) |
| `demo2` | `demo123` | Maps to graph node B |
| `demo3` | `demo123` | Maps to graph node C |

Each demo user is assigned a different graph node so their recommendations differ. You can also register your own account.

---

## Features

| Tab | Description |
|-----|-------------|
| **Recommend** | Personalized recommendations via composite scoring (α·distance + β·PageRank + γ·popularity); already-watched videos excluded |
| **Friends** | Friend suggestions ranked by shared connections; Follow/Unfollow; expand a friend for their recommendations |
| **Overview** | Graph statistics: node count, edge count, avg degree, density, etc. |
| **Echo Chamber** | Local Clustering Coefficient — per-user filter-bubble risk (admins see all users) |
| **PageRank** | Top-N videos ranked by PageRank score (full-graph or watch-graph mode) |
| **History** | Your watch history; each watch also feeds back into the graph (closes the recommendation loop) |
| **Upload** | Publish a video into the graph — paste a YouTube link **or upload a real file** (hosted + played in-app) |

---

## Project Structure

```
Algo_Project/
├── dev.sh                        # One-command launcher
├── backend-springboot/           # Spring Boot v2 backend
│   └── src/main/java/com/synchplay/
│       ├── api/                  # REST controllers
│       ├── auth/                 # JWT service, security config
│       ├── domain/               # Graph, Node, Edge entities
│       └── service/              # GraphService, DemoDataService, etc.
├── frontend-vue/                 # Vue 3 v2 frontend
│   └── src/
│       ├── views/                # Tab components (7 tabs + login/register)
│       ├── components/           # VideoThumb, VideoModal (in-app player)
│       ├── utils/                # video.js (native/YouTube helpers, thumbnail capture)
│       ├── stores/               # Pinia auth store
│       ├── router/               # Vue Router with auth guards
│       └── api/                  # Axios client with Bearer interceptor
├── scripts/                      # Data prep + algorithm prototypes (Python) + graph viz
│   ├── make_graph_html.py        #   → standalone interactive graph.html (no backend/DB)
│   ├── neo4j_import.cypher        #   Cypher import for Neo4j
│   └── VISUALIZATION.md           #   How to visualize the nodes/edges
├── ProcessedData/
│   ├── mini_nodes.csv            # 483 nodes (100 users + 383 videos)
│   └── mini_edges.csv            # 945 edges
└── doc/                          # Architecture, progress, and summary docs
```

---

## API Overview

All business endpoints require `Authorization: Bearer <token>`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Login → JWT |
| GET | `/api/auth/me` | Current user info |
| GET | `/api/stats` | Graph statistics |
| GET | `/api/recommend` | Personalized recommendations |
| GET | `/api/friends` | Friend suggestions |
| GET | `/api/lcc` | Echo chamber / LCC analysis (`/api/lcc/admin` = all users, ADMIN only) |
| GET | `/api/pagerank` | Top-N videos by PageRank |
| GET/POST | `/api/watch-history` | List / record a watch (recording also adds a `watch` edge) |
| POST | `/api/videos` | Publish a video node from a YouTube link |
| POST | `/api/videos/upload` | Upload a real video file (multipart) → native video node |
| GET | `/api/videos/mine` | Videos the current user published |
| GET | `/media/**` | Serves uploaded files/thumbnails (public, HTTP Range) |

---

## Environment Variables (optional overrides)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/synchplay` | JDBC URL |
| `DB_USER` | `synchplay` | DB username |
| `DB_PASSWORD` | `synchplay_dev` | DB password |
| `JWT_SECRET` | dev-only placeholder | Min 32-char secret — **change in production** |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | CORS allowed origin |

---

## Visualize the Graph

To **see the nodes and edges** (no backend or database needed — reads the CSV snapshot):

```bash
python3 scripts/make_graph_html.py   # generates a standalone scripts/graph.html
open scripts/graph.html              # double-click to open in any browser
```

The page is self-contained and interactive (drag/zoom/hover, toggle edge types). A Neo4j import path is also available — see [scripts/VISUALIZATION.md](scripts/VISUALIZATION.md).

---

## Documentation

- [doc/PROJECT_SUMMARY.md](doc/PROJECT_SUMMARY.md) — Full architecture, algorithm explanations, API reference
- [scripts/VISUALIZATION.md](scripts/VISUALIZATION.md) — Visualize the graph (standalone HTML / Neo4j)
- [doc/PROGRESS.md](doc/PROGRESS.md) — Phase 4 task breakdown and milestone tracking
