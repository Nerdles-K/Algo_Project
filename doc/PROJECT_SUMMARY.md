# SynchPlay 项目总结文档（v2）

## 项目定位

SynchPlay 是一款基于社交图谱的视频推荐引擎，核心目标是解决算法导致的**信息茧房**（Echo Chamber）问题。通过分析用户的社交圈（好友看了什么），结合 BFS 多跳召回 + PageRank 热度评分，为每个用户生成个性化、多元化的视频推荐。

项目经历两个版本：

| 版本 | 目录 | 技术 | 状态 |
|------|------|------|------|
| **v1**（算法原型） | （已从工作区移除） | Java 22 裸 HTTP + SQLite + 原生 JS | 📦 见 v2 重写前的 git 历史；数据准备/算法原型 Python 脚本保留在 `scripts/` |
| **v2**（生产重写） | `backend-springboot/` + `frontend-vue/` | Spring Boot + PostgreSQL + Vue 3 + JWT | ✅ 当前主版本 |

---

## 项目文件结构

```
Algo_Project/
│
├── dev.sh                            ← 一键启动脚本（并行启动前后端）
│
├── backend-springboot/               ← v2 后端（Spring Boot 3.3.5 / Java 17）
│   ├── pom.xml
│   └── src/main/java/com/synchplay/
│       ├── SynchPlayApplication.java         # 启动入口
│       ├── domain/
│       │   ├── Node.java                     # 图节点
│       │   ├── Edge.java                     # 图边
│       │   └── Graph.java                    # 异构图 + 6个算法
│       ├── service/
│       │   ├── DataImportService.java         # CSV→PostgreSQL 一次性导入
│       │   ├── GraphService.java              # 内存图单例（从 Postgres 加载）
│       │   ├── FriendRecommendationService.java  # 好友推荐
│       │   └── DemoDataService.java           # 启动时播种 demo 账号
│       ├── auth/
│       │   ├── AppUser.java                   # JPA 实体（app_users 表）
│       │   ├── AppUserRepository.java         # Spring Data JPA
│       │   ├── JwtService.java                # JWT 签发 / 验证（jjwt）
│       │   ├── AuthController.java            # /api/auth/register|login|me
│       │   └── dto/                           # RegisterRequest / LoginRequest / AuthResponse
│       ├── config/
│       │   ├── SecurityConfig.java            # Spring Security + CORS
│       │   └── JwtAuthenticationFilter.java   # Bearer Token 过滤器
│       └── api/
│           ├── HealthController.java          # GET /api/health
│           ├── StatsController.java           # GET /api/stats
│           ├── UsersController.java           # GET /api/users
│           ├── RecommendController.java       # GET /api/recommend
│           ├── FriendsController.java         # GET/POST/DELETE /api/friends
│           ├── LccController.java             # GET /api/lcc (个人) + /api/lcc/admin (全局)
│           ├── PageRankController.java        # GET /api/pagerank
│           └── WatchHistoryController.java    # POST/GET /api/watch-history
│
├── frontend-vue/                     ← v2 前端（Vue 3 + Vite + Pinia）
│   ├── vite.config.js
│   ├── package.json
│   └── src/
│       ├── main.js                           # createApp + createPinia + router
│       ├── App.vue                           # 根组件（<router-view />）
│       ├── style.css                         # 全局暗色主题
│       ├── api/
│       │   └── client.js                     # axios 实例 + Bearer 拦截器 + 401 重定向
│       ├── stores/
│       │   └── auth.js                       # Pinia auth store（token + currentUser）
│       ├── router/
│       │   └── index.js                      # Vue Router 4 + 导航守卫
│       └── views/
│           ├── LoginPage.vue                 # 登录页
│           ├── RegisterPage.vue              # 注册页
│           ├── AppShell.vue                  # 主框架（顶栏 + 5个 Tab 导航）
│           ├── RecommendTab.vue              # 视频推荐（含缩略图 + 死链过滤）
│           ├── OverviewTab.vue               # 图统计概览
│           ├── FriendsTab.vue                # 好友推荐
│           ├── LccTab.vue                    # 茧房检测（LCC）
│           └── PageRankTab.vue               # PageRank 热度榜
│
├── scripts/                          ← 数据准备 + 算法原型 Python 脚本（v1 遗留，保留）
│
├── Dataset/                          ← 原始数据集
│   ├── archive/USvideos.csv          #   Kaggle YouTube Trending（美区）
│   └── com-youtube.top5000.cmty.txt  #   SNAP YouTube 社区数据
│
├── ProcessedData/                    ← 抽样后数据
│   ├── mini_nodes.csv                #   500 节点（100用户 + 400视频）
│   ├── mini_edges.csv                #   945 条边
│   └── synchplay.db                  #   v1 SQLite 数据库（v2 用 PostgreSQL）
│
└── doc/                              ← 项目文档
    ├── PROJECT_SUMMARY.md            #   本文件
    ├── REQUIREMENTS.md               #   需求文档（v2）
    ├── PROGRESS.md                   #   进度控制文档
    ├── ARCHITECTURE.md               #   架构文档
    └── WORKFLOW.md                   #   Git 工作流规范
```

---

## 技术架构（v2）

```
┌──────────────────────────────────────────────────────────────┐
│  浏览器 http://localhost:5173                                  │
│  Vue 3 + Vite + Vue Router + Pinia                           │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌────────────┐  ┌──────────┐  │
│  │ /login   │  │/register │  │/app/recomm │  │/app/lcc  │  │
│  └──────────┘  └──────────┘  └────────────┘  └──────────┘  │
│                                                              │
│  axios client — 自动注入 Bearer Token；401 → /login          │
│  Pinia auth store — token + currentUser 持久化到 localStorage│
└───────────────────────────┬──────────────────────────────────┘
                            │  HTTP REST + CORS (Authorization: Bearer <JWT>)
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Spring Boot 3.3.5  :8080                                    │
│                                                              │
│  JwtAuthenticationFilter → SecurityConfig                    │
│  （所有 /api/** 需 Token，/api/auth/** 和 /api/health 公开）  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  AuthController  /api/auth/register|login|me           │  │
│  │  HealthController    /api/health      （公开）          │  │
│  │  StatsController     /api/stats       （需认证）        │  │
│  │  UsersController     /api/users       （需认证）        │  │
│  │  RecommendController /api/recommend   （需认证）        │  │
│  │  FriendsController   /api/friends     （需认证）        │  │
│  │  LccController       /api/lcc         （需认证）        │  │
│  │  PageRankController  /api/pagerank    （需认证）        │  │
│  └────────────────────────────────────────────────────────┘  │
│            │                      │                          │
│       GraphService             AppUserRepository             │
│    （内存 Graph 单例）          （JPA → app_users 表）        │
│            │                                                 │
│    DataImportService                                         │
│  （CSV → Postgres，启动时幂等导入）                           │
└──────────────────────────────────────────────────────────────┘
                            │
                            ▼
                   PostgreSQL 17  (synchplay DB)
                   ┌──────────┐  ┌───────┐  ┌──────────┐
                   │app_users │  │ nodes │  │  edges   │
                   └──────────┘  └───────┘  └──────────┘
                   （Flyway V1__init.sql 自动建表）
```

---

## 数据层

### 数据来源

| 来源 | 内容 | 规模 |
|------|------|------|
| SNAP YouTube 社区数据 | 用户及社交关系（community detection 分组） | 39,841 用户 |
| Kaggle YouTube Trending（美区） | 热门视频：标题、频道、播放量、点赞数 | 40,949 视频 |

**抽样**：`scripts/data_preparation.py`（seed=42，从仓库根目录运行）随机抽取 100 用户 + 400 视频，生成 945 条边，写入 `ProcessedData/mini_nodes.csv` 和 `mini_edges.csv`。

### 图数据统计

| 节点/边类型 | 数量 | 含义 |
|------------|------|------|
| 用户节点 | 100 | 从 39,841 人中抽样 |
| 视频节点 | 400（入库 383） | 从 40,949 热门视频中抽样（CSV 去重后 383） |
| social 边 | 72 | 用户↔用户社交连接（权重=1.0） |
| watch 边 | 755 | 用户→视频观看行为（权重=点赞数/播放量） |
| similar 边 | 118 | 同频道视频间相似连接（权重=0.5） |

### 数据库（v2）

- **PostgreSQL 17**（本地 brew 安装，DB: `synchplay`，user: `synchplay`）
- Flyway 管理 Schema，`V1__init.sql` 建立三张表：
  - `app_users`：注册用户账号（username, email, password_hash, graph_node_id）
  - `nodes`：图节点（483 行）
  - `edges`：图边（945 行）
- `DataImportService`（CommandLineRunner）启动时幂等地将 CSV 导入 `nodes`/`edges`；若已有数据则跳过
- `GraphService` 在 `ApplicationReadyEvent` 时将 Postgres 数据加载到内存 `Graph` 对象，耗时约 10 ms

---

## 用户账号系统（v2 新增）

### 注册与登录

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/auth/register` | POST | 注册：username + email + password → 返回 JWT（24h） |
| `/api/auth/login` | POST | 登录：username + password → 返回 JWT（24h） |
| `/api/auth/me` | GET | 获取当前已认证用户信息 |

- 密码用 **BCrypt**（cost=10）哈希后存库，明文不落地
- JWT 由 **jjwt** 库签发，使用 HMAC-SHA256，秘钥通过环境变量 `SYNCHPLAY_JWT_SECRET` 注入
- 所有 `/api/**` 端点（除 `/api/auth/**` 和 `/api/health`）需在请求头携带 `Authorization: Bearer <token>`

### 用户→图节点映射（FR-A.6）

每个注册用户被确定性地映射到数据集中的一个用户节点，算法：

```
idx = repo.count() % 100     # 当前注册人数对用户节点数取余
graphNodeId = sortedUserNodes[idx]  # 按 nodeId 排序后取第 idx 个
```

这保证不同账号对应不同图位置，因此推荐结果个性化不同。

### 预置 Demo 账号

`DemoDataService` 在首次启动时自动创建：

| 账号 | 密码 | 邮箱 |
|------|------|------|
| demo1 | demo123 | demo1@synchplay.dev |
| demo2 | demo123 | demo2@synchplay.dev |
| demo3 | demo123 | demo3@synchplay.dev |

三个账号映射到不同的图用户节点，推荐结果各不相同。

---

## 核心算法详解

所有 6 个算法均实现在 `backend-springboot/src/main/java/com/synchplay/domain/Graph.java`，由 `GraphService` 持有的内存图实例调用。

---

### 算法 1：BFS 多跳召回 —— 候选视频发现

**目的**：从海量视频中快速找出与目标用户"有关系"的候选视频。

**方法**：`Graph.findCandidateVideosByBFS(userId, maxDepth)`

**算法步骤：**
```
输入：用户 user_391585，最大深度 2
输出：候选视频集合

Step 0: 初始化
  visited = {user_391585}
  队列 = [(user_391585, depth=0)]

Step 1: 取出 (user_391585, 0)，depth < 2，继续
  遍历所有邻居（无向：出边+入边）：
    → video_aEM2kOrrNJI (watch边) → 是视频，加入候选集
    → video_rZQepOFnYi8 (watch边) → 是视频，加入候选集
    → user_278190 (social边)      → 是用户，入队 (depth=1)

Step 2: 取出 (user_278190, 1)，depth < 2，继续
  遍历 user_278190 的邻居：
    → video_FlsCjmMhFmw (watch边) → 加入候选集
    → user_105466 (social边)      → 入队 (depth=2)
    → video_aEM2kOrrNJI (已访问)  → 跳过

Step 3: 取出 (user_105466, 2)，depth == maxDepth，停止扩展

输出：2-hop 约 19 个候选，3-hop 约 112 个候选
```

**关键设计**：
- `visited` Set 防重复访问和环路
- **无向遍历**（同时检查出边和入边），不遗漏关系
- 深度 2–3：太浅候选少，太深引入噪音
- 时间复杂度 O(V + E)

---

### 算法 2：PageRank（全图）—— 视频全局热度

**目的**：模仿 Google PageRank，计算视频在整个异构图中的"权威性"分数。

**方法**：`Graph.computePageRank(iterations=20, dampingFactor=0.85)`

**算法步骤：**
```
输入：全图 483 节点，945 边
输出：每个节点的 PageRank 分数

Step 1: 初始化
  每个节点 PR = 1/483 ≈ 0.00207

Step 2: 迭代 20 轮，每轮：
  a) 保底分：每节点 += (1-0.85)/483 = 0.00031
  b) 传播：每节点将 PR×0.85 均分给出边邻居
  c) Sink 处理：出度=0 的节点将 PR 均分给全图

Step 3: 仅提取视频节点 PR 值，降序排列
```

**实测 Top 5（全图模式）：**
```
1. video__6lGaYh71g4  0.016561  WTF - $300 Toaster?!
2. video_aEM2kOrrNJI  0.015139  Jennifer Lopez - Dinero
3. video_FlsCjmMhFmw  0.014283  YouTube Rewind 2017
4. video_WCKG9J1d5iE  0.013417
5. video_jpH-8HiMCRI  0.013129
```

---

### 算法 3：Watch-Based PageRank —— 仅用观看行为

**目的**：与全图 PageRank 互补，仅聚焦 user→video 的观看行为，消除社交关系和相似边的影响。

**方法**：`Graph.computeWatchBasedPageRank(alpha=0.85, maxIter=50, tol=1e-6)`

**算法步骤：**
```
Step 1: 预计算每个用户→看过的视频列表

Step 2: 初始化每个视频 PR = 1/视频总数

Step 3: 迭代（收敛阈值 1e-6），每轮：
  a) 每个视频保底分 = (1-0.85)/n
  b) 每个用户将 0.85 均分给其看过的所有视频
  c) 归一化：所有分数之和=1.0
  d) 收敛检测：总变化 < 1e-6 则提前终止
```

**两种模式对比：**
```
全图模式 Top 3:                 Watch模式 Top 3:
1. WTF - $300 Toaster?!        1. WTF - $300 Toaster?!
2. Jennifer Lopez - Dinero     2. How I Became Fresh Prince
3. YouTube Rewind 2017         3. Jerry Rice Answers...
```

Watch 模式更聚焦"纯观看行为"定义的热度，全图模式则融合社交关系影响。

---

### 算法 4：综合打分公式 —— 最终推荐排序（v2 升级版）

**目的**：把"距离"、"全局影响力"、"原始热度"三种异质信号融合为一个最终分。

**方法**：`Graph.rankCandidatesByCompositeScore(nodeId, alpha, beta, gamma, prMode)`

**公式：**
```
最终分 = α · (1 / 加权距离)              ← 社交近度（Dijkstra）
       + β · 归一化PageRank              ← 全局影响力
       + γ · 归一化popularity             ← 原始 views + likes 热度

α + β + γ = 1（后端自动归一化，前端三个独立滑块）
默认 α=0.5, β=0.3, γ=0.2 —— 偏好"朋友看过 + 略带全局热度"
```

**Dijkstra 加权距离**（替代 v1 的 BFS 单位距离）使用真实边权：
- `watch` 边 = 0.1（最强信号 —— 朋友真的看了这个视频）
- `similar` 边 = 0.5
- `social` 边 = 1.0（最弱 —— 仅"是朋友"还不够）

距离越小越近，前端展示保留 3 位小数。

**Popularity 特征**（log 尺度，避免病毒视频独占）：
```
popularity(v) = 0.6 · log(1+views) / log(1+max_views)
              + 0.4 · log(1+likes) / log(1+max_likes)
```

**算法步骤（α=0.5, β=0.3, γ=0.2）：**
```
Step 1: Dijkstra 加权最短路径，cap=3.0
  → {video_A: d=0.1, video_B: d=1.1, video_C: d=2.1, ...}

Step 2: 计算 PageRank（full 或 watch 模式）+ 归一化

Step 3: 计算 popularity 子分（views/likes log-normalized）

Step 4: 归一化权重 α/β/γ（保证和=1）

Step 5: 最终分 = aN · (1/d) + bN · normPR + gN · popularity

Step 6: 降序 Top N
```

**v2 改造点**：
- 引入 **Dijkstra** 替换 BFS：满足"使用 edge weight"的承诺。
- 加入 **popularity 特征**：满足"考虑更多特征"的承诺。
- 前端三滑块（α/β/γ）+ 卡片新增 popularity 徽章，可视化每个子分对最终排序的贡献。

---

### 算法 5：LCC 局部聚类系数 —— 信息茧房检测

**目的**：量化用户社交圈的"封闭程度"，LCC 越高说明朋友们互相都认识，信息茧房风险越大。

**方法**：`Graph.computeLocalClusteringCoefficient(userNodeId)`（用户仅看到自己的 LCC；管理员可通过 `/api/lcc/admin` 查看全部用户）

**公式：**
```
LCC(u) = 2 × E_neighbors / (k × (k - 1))

k = u 的社交邻居数
E_neighbors = 这些邻居之间实际存在的 social 边数
```

**直观理解：**
```
情况A：开放社交圈（LCC ≈ 0，低风险）
  你的朋友小明、小红、小刚互不相识 → 来自不同圈子 → 多元信息

情况B：信息茧房（LCC = 1，高风险）
  小明↔小红↔小刚↔小明，三人互相认识 → 信息来源单一
```

**风险分级：**

| LCC 范围 | 风险等级 | 前端显示 |
|----------|---------|---------|
| ≥ 0.7 | 高风险 | 红色 |
| 0.4 – 0.7 | 中风险 | 黄色 |
| < 0.4 | 低风险 | 绿色 |

**当前数据实测：**

| 指标 | 数值 |
|------|------|
| 有社交连接的用户 | 22 人 |
| 平均 LCC | 0.8557 |
| 高风险（LCC ≥ 0.7） | 17 人 |
| 中风险（0.4–0.7） | 5 人 |
| 低风险（< 0.4） | 0 人 |

---

### 算法 6：好友推荐 —— 基于共同观看的协同过滤

**目的**：根据"看过相同视频"的行为信号，为用户推荐潜在好友。

**方法**：`FriendRecommendationService.recommend(nodeId)`

**算法步骤：**
```
输入：目标用户 user_391585（看了 9 个视频）
输出：推荐好友列表，按共同视频数降序

Step 1: 获取目标用户看过的所有视频
  [video_a, video_b, ..., video_i]

Step 2: 对每个视频，找出也看过它的其他用户
  video_a 的观看者 → {user_278190, user_74420, user_438558}
  video_b 的观看者 → {user_278190, user_105466}

Step 3: 统计共同视频数
  user_278190: 共同 2 个
  user_105466: 共同 2 个
  user_74420:  共同 1 个

Step 4: 按共同数降序排列，排除自己，返回 Top N
```

**时间复杂度**：O(user_out_degree × avg_video_in_degree)，500 节点规模即时完成。

**好友关系持久化**：`POST /api/friends` 创建 social 边（同时写入 DB 和内存图），`DELETE /api/friends` 删除边。前端好友页同时显示已关注好友和推荐好友。

---

## API 端点总览（v2）

### 认证端点（无需 Token）

| 端点 | 方法 | 请求体 | 响应 |
|------|------|--------|------|
| `/api/auth/register` | POST | `{username, email, password}` | `{token, user}` / 409 冲突 |
| `/api/auth/login` | POST | `{username, password}` | `{token, user}` / 401 |
| `/api/auth/me` | GET | — | `{id, username, email, graphNodeId}` |
| `/api/health` | GET | — | `{status, db, graph:{nodes,edges}}` |

### 业务端点（需 Authorization: Bearer \<token\>）

| 端点 | 方法 | 参数 | 对应算法 |
|------|------|------|---------|
| `/api/stats` | GET | — | 图统计（节点数、边数、密度等） |
| `/api/users` | GET | — | 全部 100 个用户列表 |
| `/api/recommend` | GET | `alpha`(0.5), `beta`(0.3), `gamma`(0.2), `prMode`(full\|watch), `top`(20) | Dijkstra + PageRank + popularity 综合打分；当前登录用户自动作为目标 |
| `/api/friends` | GET | `top`(10) | 好友推荐 + 已有好友 |
| `/api/friends` | POST | body: `{targetNodeId}` | 关注好友（创建 social 边） |
| `/api/friends` | DELETE | body: `{targetNodeId}` | 取消关注（删除 social 边） |
| `/api/lcc` | GET | — | 当前用户个人 LCC + 风险等级 |
| `/api/lcc/admin` | GET | — | [ADMIN] 全部用户 LCC |
| `/api/pagerank` | GET | `prMode`(full\|watch), `top`(15) | 视频 PageRank 热度排行 |
| `/api/watch-history` | GET | `limit`(50) | 当前用户观看历史 |
| `/api/watch-history` | POST | body: `{videoNodeId, videoId, title, channel}` | 记录一次观看；同时在图+`edges`表建 user→video `watch` 边（幂等），闭合反馈回路 |
| `/api/videos` | POST | body: `{youtubeUrl, title, channel?, views?, likes?}` | 创作者发布：建 `video` 节点 + `creator→video` `uploaded` 边（DB+内存图）；重复返回 409 |
| `/api/videos/upload` | POST | multipart: `file`, `thumb?`, `title`, `channel?`, `views?`, `likes?` | 原生上传：存视频文件+封面到 `./uploads`，建 `source='native'` 视频节点 + `uploaded` 边 |
| `/api/videos/mine` | GET | — | 当前用户已发布的视频列表 |
| `/media/**` | GET | — | 静态服务上传的视频/封面（支持 HTTP Range，供 `<video>` 流播放）；公开 |

---

## 前端功能说明（v2）

| 页面/Tab | 路由 | 功能 |
|---------|------|------|
| 登录页 | `/login` | username + password；登录后跳转 `/app/recommend` |
| 注册页 | `/register` | username + email + password（含确认）；注册后自动登录 |
| 视频推荐 | `/app/recommend` | α/β/γ 滑块调权重，prMode 切换，视频卡片含 YouTube 缩略图；点击自动记录观看历史 |
| 图统计 | `/app/overview` | 节点数、边数、平均度等 9 张统计卡片 |
| 好友 | `/app/friends` | 已有好友列表（Unfollow 按钮）+ 推荐好友列表（Follow 按钮） |
| 茧房检测 | `/app/lcc` | 个人茧房等级卡片 + 可视化条形图；管理员可加载全局用户表格 |
| PageRank | `/app/pagerank` | Top N 视频热度榜，含缩略图、频道、播放量、比例条形图；点击自动记录观看历史 |
| 观看历史 | `/app/watch-history` | 当前用户已观看视频列表（缩略图、标题、频道、时间） |

**安全机制**：
- 所有 `/app/*` 路由有导航守卫，未登录自动跳转 `/login`
- axios 拦截器为每个请求注入 Bearer Token；收到 401 自动清除 Token 并跳回 `/login`
- Token 和用户信息持久化到 `localStorage`，刷新页面不丢失登录态

---

## 运行方式（v2）

### 前提

- **PostgreSQL 17** 已启动，且 `synchplay` 数据库 / 用户已创建：
  ```bash
  brew services start postgresql@17
  psql postgres -c "CREATE USER synchplay WITH PASSWORD 'synchplay';"
  psql postgres -c "CREATE DATABASE synchplay OWNER synchplay;"
  ```
- **Java 17+** 和 **Node.js 18+** 已安装

### 一键启动

```bash
# 在项目根目录
bash dev.sh
```

- 后端在 `:8080` 启动（Spring Boot + Postgres），首次启动自动导入 CSV + 创建 demo 账号
- 前端在 `:5173` 启动（Vite dev server）
- `Ctrl+C` 同时关闭两个进程

### 手动启动（分开两个终端）

```bash
# 终端 1：后端
cd backend-springboot
mvn spring-boot:run

# 终端 2：前端
cd frontend-vue
npm run dev
```

### Demo 账号

首次启动后可直接用以下账号登录体验：

| 账号 | 密码 |
|------|------|
| demo1 | demo123 |
| demo2 | demo123 |
| demo3 | demo123 |

---

## 技术栈对比

| 层面 | v1（参考实现） | v2（当前版本） |
|------|-------------|-------------|
| 语言 | Java 22 | Java 17 |
| 后端框架 | `com.sun.net.httpserver`（JDK 内置） | Spring Boot 3.3.5 |
| 数据库 | SQLite + JDBC | PostgreSQL 17 + Spring Data JPA + Flyway |
| 认证 | 无 | JWT（jjwt）+ Spring Security + BCrypt |
| 前端 | 原生 HTML + CSS + JS（三文件） | Vue 3 + Vite + Vue Router 4 + Pinia + axios |
| 构建 | Shell 脚本（compile.sh / start.sh） | Maven（后端）+ npm（前端）+ dev.sh（并发启动） |
| 算法层 | 同一套 6 个算法 | 同一套 6 个算法（移植到新包 com.synchplay.domain） |

---

## 项目进度一览

| 模块 | 内容 | 状态 |
|------|------|------|
| 数据预处理 | Python 抽样 + CSV 生成 | ✅ |
| BFS 召回 | 2–3 层异构图遍历 | ✅ |
| PageRank（全图） | 迭代热度计算，20轮，d=0.85 | ✅ |
| PageRank（Watch） | 收敛式，仅 watch 边，tol=1e-6 | ✅ |
| 综合打分（升级版） | α·(1/Dijkstra距离) + β·PageRank + γ·popularity | ✅ |
| LCC 茧房检测 | 全用户 LCC + 风险分级 | ✅ |
| 好友推荐 | 共同观看协同过滤 | ✅ |
| PostgreSQL 数据库 | Flyway 建表 + 幂等导入 | ✅ |
| Spring Boot 后端 | 7 个 REST 端点 + CORS | ✅ |
| JWT 认证系统 | 注册/登录/Token 过滤 | ✅ |
| Vue 3 前端 | 5 个 Tab + 登录注册 + 路由守卫 | ✅ |
| 缩略图 + 死链过滤 | naturalWidth ≤ 120 检测不可用视频 | ✅ |
| Demo 账号预置 | demo1/2/3 启动时自动创建 | ✅ |
| 一键启动脚本 | dev.sh 并发启动前后端 | ✅ |
| README | 新克隆者快速上手指南 | ⏳ |
| 演示幻灯片 | 8–10 张（算法 + 架构 + Demo） | ⏳ |
