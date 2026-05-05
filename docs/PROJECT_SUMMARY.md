# SynchPlay 项目总结文档

## 项目定位

SynchPlay 是一款基于社交图谱的视频推荐引擎，核心目标是解决算法导致的"信息茧房"问题——通过分析用户的社交圈（好友看了什么）来提供更具社交共鸣的视频推荐。

---

## 项目文件结构

```
Algo_Project/
│
├── backend/                          ← 后端（Java 全套）
│   ├── compile.sh                    #   编译脚本（5步）
│   ├── start.sh                      #   启动脚本（一键运行）
│   ├── src/com/synchplay/
│   │   ├── Main.java                 #   控制台 Demo
│   │   ├── ServerMain.java           #   Web 服务入口
│   │   ├── model/
│   │   │   ├── Node.java             #   图节点
│   │   │   ├── Edge.java             #   图边
│   │   │   └── Graph.java            #   异构图 + 6个算法
│   │   ├── loader/
│   │   │   └── DataLoader.java       #   CSV 加载器
│   │   ├── db/
│   │   │   └── DatabaseManager.java  #   SQLite 管理
│   │   ├── service/
│   │   │   └── FriendRecommendation.java  # 好友推荐
│   │   └── server/
│   │       └── SynchPlayServer.java  #   HTTP 服务器 + 7个API
│   ├── lib/                          #   JAR 依赖
│   │   ├── sqlite-jdbc.jar
│   │   ├── slf4j-api.jar
│   │   └── slf4j-nop.jar
│   ├── scripts/                      #   Python 脚本
│   │   ├── data_preparation.py       #   数据抽样
│   │   ├── pagerank.py               #   组员 PageRank 参考
│   │   ├── lcc.py                    #   组员 LCC 参考
│   │   └── ranking.py                #   组员 综合打分 参考
│   └── bin/                          #   编译输出
│
├── frontend/                         ← 前端（独立部署）
│   ├── index.html                    #   页面结构（5个Tab）
│   ├── style.css                     #   样式
│   └── app.js                        #   逻辑（API调用）
│
├── docs/                             ← 文档
│   └── PROJECT_SUMMARY.md            #   本文件
│
├── Dataset/                          ← 原始数据
└── ProcessedData/                    ← 处理后数据 + SQLite
```

**前后端分离说明：**
- `frontend/` 可独立部署到任意静态文件服务器，修改 `app.js` 中的 `API_BASE` 即可切换后端
- `backend/` 是纯后端，`cd backend && bash start.sh` 启动 `:8080`，提供 REST API + 前端静态文件
- 两者通过 HTTP + CORS 通信

---

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│  前端 (frontend/)                                        │
│  index.html  +  style.css  +  app.js                     │
│  原生三件套，无框架依赖，5个Tab页                          │
│  可独立部署到任意静态文件服务器                            │
└────────────┬────────────────────────────────────────────┘
             │  HTTP GET (CORS)
             ▼
┌─────────────────────────────────────────────────────────┐
│  后端 (SynchPlayServer.java :8080)                       │
│  Java 22 + com.sun.net.httpserver，7个REST API           │
│  ┌─────────────────────────────────────────────────┐    │
│  │  GET /api/health     健康检查 + 运行信息          │    │
│  │  GET /api/stats      图统计信息                  │    │
│  │  GET /api/users      用户列表                    │    │
│  │  GET /api/recommend  综合打分视频推荐             │    │
│  │  GET /api/friends    好友推荐（共同观看）          │    │
│  │  GET /api/lcc        LCC茧房检测报告              │    │
│  │  GET /api/pagerank   PageRank排行榜              │    │
│  └─────────────────────────────────────────────────┘    │
│            │                                             │
│     ┌──────┴──────┬──────────────┐                      │
│     ▼              ▼              ▼                       │
│  Graph (内存)   DatabaseManager  FriendRecommendation   │
│  算法引擎        SQLite JDBC      协同过滤推荐            │
└─────────────────────────────────────────────────────────┘
```

---

## 数据层

### 数据来源
- **SNAP YouTube 社区数据**：39,841 个用户，通过社区检测算法分组。同社区内用户两两建立 social 边。
- **Kaggle YouTube Trending**：40,949 个热门视频（美国区），含标题、频道、播放量、点赞数等。
- **抽样**：`backend/scripts/data_preparation.py` 随机抽样 100 用户 + 400 视频（seed=42），生成 945 条边。

### 数据集统计

| 节点/边 | 数量 | 说明 |
|---------|------|------|
| 用户节点 | 100 | 从 39,841 人中随机抽样 |
| 视频节点 | 400 | 从 40,949 热门视频中随机抽样 |
| social 边 | 72 | 用户之间的社交连接（权重=1.0） |
| watch 边 | 755 | 用户→视频的观看关系（权重=点赞/播放量） |
| similar 边 | 118 | 同频道视频之间的相似连接（权重=0.5） |

### 数据库
- **SQLite**（通过 JDBC 连接），文件位于 `ProcessedData/synchplay.db`
- 两张表：`nodes`（7列）和 `edges`（5列），含 3 个索引
- `DatabaseManager.java` 全部使用 try-with-resources 确保连接安全释放

---

## 核心算法详解

### 算法 1：BFS 多跳召回 —— 候选视频发现

**目的**：从海量视频中快速找出与目标用户"有关系"的候选视频。

**Java 实现**：[Graph.java](../backend/src/com/synchplay/model/Graph.java)  `findCandidateVideosByBFS(userId, maxDepth)`

**算法步骤：**
```
输入：用户 user_391585，最大深度 2
输出：候选视频集合

Step 0: 初始化
  visited = {user_391585}
  队列 = [(user_391585, depth=0)]

Step 1: 取出 (user_391585, 0)，depth < 2，继续
  遍历 user_391585 的所有邻居（无向：出边+入边）：
    → video_aEM2kOrrNJI (watch边) → 是视频，加入候选集
    → video_rZQepOFnYi8 (watch边) → 是视频，加入候选集
    → user_278190 (social边)      → 是用户，入队 (depth=1)

Step 2: 取出 (user_278190, 1)，depth < 2，继续
  遍历 user_278190 的所有邻居：
    → video_FlsCjmMhFmw (watch边) → 是视频，加入候选集
    → user_105466 (social边)      → 是用户，入队 (depth=2)
    → video_aEM2kOrrNJI (已访问)  → 跳过

Step 3: 取出 (user_105466, 2)，depth >= 2，停止扩展

输出：候选集 = {video_aEM2kOrrNJI, video_rZQepOFnYi8, video_FlsCjmMhFmw, ...}
      2-hop 找到 19 个候选视频，3-hop 找到 112 个候选视频
```

**关键设计**：
- **visited set** 防止重复访问和死循环
- **无向遍历**（同时检查出边和入边），确保不遗漏关系
- 深度限制 2-3 层：太浅候选太少，太深引入噪音
- 时间复杂度 O(V + E)

**操作方式**：
- 控制台：`java --add-modules jdk.httpserver -cp bin com.synchplay.Main` → 自动展示
- API：推荐接口 `GET /api/recommend` 内部自动调用 BFS 召回

---

### 算法 2：PageRank（全图）—— 视频全局热度

**目的**：模仿 Google 网页排名思想，计算每个视频在图中的"权威性"分数。被更多用户观看、与热门视频相似的视频得分更高。

**Java 实现**：[Graph.java](../backend/src/com/synchplay/model/Graph.java)  `computePageRank(iterations=20, dampingFactor=0.85)`

**算法步骤：**
```
输入：全图 483 个节点，945 条边
输出：每个节点的 PageRank 分数

Step 1: 初始化
  每个节点的初始 PR = 1/483 ≈ 0.00207

Step 2: 迭代 20 轮，每轮执行：
  a) 基础分：每个节点得到 (1-0.85)/483 = 0.00031 的保底分
  b) 沿边传播：每个节点将自己的 PR×0.85 均分给出边邻居
     例如：user_391585 的 PR=0.005，有 10 条出边
     则每条出边分发 0.005×0.85/10 = 0.000425 给目标节点
  c) Sink处理：出度为0的节点将PR均分给全图所有节点
  d) 更新所有节点的PR值为本轮计算值

Step 3: 仅提取视频节点的PR值，降序排列
```

**实际运行结果：**
```
Top 5 视频 PageRank:
  1. video__6lGaYh71g4  score=0.016561  (WTF - $300 Toaster?!)
  2. video_aEM2kOrrNJI  score=0.015139  (Jennifer Lopez - Dinero)
  3. video_FlsCjmMhFmw  score=0.014283  (YouTube Rewind 2017)
  4. video_WCKG9J1d5iE  score=0.013417
  5. video_jpH-8HiMCRI  score=0.013129
```

**操作方式：**
- 控制台：自动展示 Top 5
- API：`GET /api/pagerank?top=10`
- 前端：PageRank 排行榜 Tab

---

### 算法 3：Watch-Based PageRank —— 组员算法（仅用观看边）

**目的**：与全图 PageRank 互补。全图模式利用所有边类型（social + watch + similar），watch 模式仅聚焦 user→video 的观看行为，消除社交关系和频道相似性的影响。

**对应 Python**：[scripts/pagerank.py](../backend/scripts/pagerank.py)

**Java 实现**：[Graph.java](../backend/src/com/synchplay/model/Graph.java)  `computeWatchBasedPageRank(alpha=0.85, maxIter=50, tol=1e-6)`

**算法步骤：**
```
输入：所有视频节点 + 所有 watch 边
输出：每个视频的 watch-based PR 分数

Step 1: 预计算
  为每个用户建立"看了哪些视频"的映射：
  user_391585 → [video_a, video_b, video_c, ...]  (共9个)
  user_278190 → [video_d, video_e, ...]

Step 2: 初始化
  每个视频的 PR = 1/视频总数 = 1/383 ≈ 0.00261

Step 3: 迭代（最多50轮，收敛阈值 1e-6），每轮：
  a) 基础分：每个视频得到 (1-0.85)/n
  b) 用户投票：每个用户将 0.85 均分给自己看过的视频
     例如：user_391585 看了 9 个视频
     每个视频从该用户获得 0.85/9 ≈ 0.0944
  c) 归一化：所有分数除以总和，使总分 = 1.0
  d) 检查收敛：如果两轮之间的总变化 < 1e-6，提前终止

Step 4: 返回视频PR降序排列
```

**两种 PageRank 的对比：**
```
全图模式 Top 3:                 Watch模式 Top 3:
1. WTF - $300 Toaster?!        1. WTF - $300 Toaster?!
2. Jennifer Lopez - Dinero     2. How I Became Fresh Prince
3. YouTube Rewind 2017         3. Jerry Rice Answers...

Watch 模式更偏向"纯粹被观看行为"定义的权威性（收敛式迭代）
```

**操作方式：**
- API：`GET /api/recommend?userId=user_391585&prMode=watch`
- 控制台：自动展示双模式 Top 5 对比

---

### 算法 4：综合打分公式 —— 最终推荐排序

**目的**：将 BFS 召回的距离信号和 PageRank 的热度信号融合为最终分数，兼顾"社交邻近度"和"视频质量"。

**Java 实现**：[Graph.java](../backend/src/com/synchplay/model/Graph.java)  `rankCandidatesByCompositeScore(userId, alpha=0.4, beta=0.6, prMode="full")`

**公式：**
```
最终分 = α × (1 / 路径距离) + β × 归一化PageRank

其中：
  α = 距离权重（默认 0.4），值越大越偏好"朋友直接看的视频"
  β = 热度权重（默认 0.6），值越大越偏好"全局热门视频"
  归一化PageRank = 原始PR分 / 最大PR分（压缩到[0,1]区间）
```

**算法步骤（以 user_391585 为例）：**
```
Step 1: BFS 3-hop 获取候选视频及距离
  {video_A: dist=1, video_B: dist=1, video_C: dist=3, ...}
  共 112 个候选

Step 2: 计算 PageRank（全图模式 或 watch模式）
  {video_A: 0.015139, video_B: 0.001858, video_C: 0.014283, ...}

Step 3: 归一化 PR
  最大PR = 0.016561 (来自全局最高分视频)
  video_A 归一化: 0.015139/0.016561 = 0.914
  video_C 归一化: 0.014283/0.016561 = 0.862

Step 4: 计算最终分 (α=0.4, β=0.6)
  video_A (dist=1): 0.4×(1/1) + 0.6×0.914 = 0.948  ← Top 1
  video_C (dist=3): 0.4×(1/3) + 0.6×0.862 = 0.651  ← Top 2
  video_B (dist=1): 0.4×(1/1) + 0.6×0.112 = 0.467  ← Top 3

  虽然 video_C 的 PR 很高，但因为距离远(3跳)，被距离近的 video_A 超越

Step 5: 按最终分降序输出
```

**操作方式：**
- 控制台：自动展示 Top 10
- API：`GET /api/recommend?userId=X&alpha=0.4&beta=0.6&prMode=full`
- 前端：视频推荐 Tab（首页），α/β 滑块可实时调整权重

---

### 算法 5：LCC 局部聚类系数 —— 信息茧房检测

**目的**：量化用户的社交圈有多"封闭"。如果用户的朋友们互相都认识，说明用户处于信息茧房中，接触到多元信息的可能性低。

**对应 Python**：[scripts/lcc.py](../backend/scripts/lcc.py)

**Java 实现**：[Graph.java](../backend/src/com/synchplay/model/Graph.java)  `computeLocalClusteringCoefficient(userId)`

**直观理解：**
```
情况A：开放社交圈（LCC = 0.0，低风险）
  你的朋友：小明、小红、小刚
  小明不认识小红，小红不认识小刚，小刚不认识小明
  → 你的朋友来自不同圈子，你能接触多元信息

情况B：封闭社交圈 / 茧房（LCC = 1.0，高风险）
  你的朋友：小明、小红、小刚
  小明 ↔ 小红 ↔ 小刚 ↔ 小明（三人互相都认识）
  → 大家都在一个小圈子里，看的东西一样 → 信息茧房
```

**公式与算法步骤：**
```
LCC(u) = 2 × E / (k × (k - 1))

其中：
  k = 用户 u 通过 social 边连接的其他用户数（社交邻居）
  E = 这些社交邻居之间实际存在的 social 边数

Step 1: 获取目标用户的社交邻居
  遍历所有 social 边（出边+入边），收集邻居用户
  例如：user_A 的社交邻居 = {user_B, user_C, user_D}，k = 3

Step 2: 统计邻居间存在的边数 E
  检查所有邻居对（共 k×(k-1)/2 = 3 对）：
    user_B ↔ user_C: 有 social 边 ✓ → E += 1
    user_B ↔ user_D: 无 social 边 ✗
    user_C ↔ user_D: 有 social 边 ✓ → E += 1
  结果：E = 2

Step 3: 计算 LCC
  LCC = 2×2 / (3×2) = 4/6 = 0.667  → 中风险
```

**风险分级：**
| LCC 范围 | 风险等级 | 含义 |
|----------|---------|------|
| > 0.7 | 高风险 | 社交圈非常封闭，茧房效应严重 |
| 0.3 - 0.7 | 中风险 | 有一定封闭性 |
| < 0.3 | 低风险 | 社交圈较开放 |

**当前数据实测：**
| 指标 | 数值 |
|------|------|
| 有社交连接的用户 | 22 人（共 100 人） |
| 平均 LCC | 0.8557 |
| 高风险用户（LCC>0.7） | 17 人 |
| 中风险用户（0.3-0.7） | 5 人 |
| 低风险用户（<0.3） | 0 人 |

**操作方式：**
- API：`GET /api/lcc` → 返回全部 100 个用户的 LCC 值 + 风险等级
- 前端：茧房检测 Tab（含高/中/低风险统计卡片 + Top 20 排行表）

---

### 算法 6：好友推荐 —— 基于共同观看的协同过滤（组员实现）

**目的**：根据"看过相同视频"这一行为信号，为用户推荐潜在好友。

**Java 实现**：[FriendRecommendation.java](../backend/src/com/synchplay/service/FriendRecommendation.java)  `recommend(userId)`

**算法步骤：**
```
输入：目标用户 user_391585
输出：推荐好友列表（按共同视频数降序）

Step 1: 获取目标用户看过的所有视频
  遍历 user_391585 的 watch 出边：
  → [video_a, video_b, video_c, video_d, video_e, video_f, video_g, video_h, video_i]
  共 9 个视频

Step 2: 为每个视频，找出也看过它的其他用户
  video_a 的入边（谁看了这个视频）→ [user_278190, user_74420, user_438558]
  video_b 的入边 → [user_278190, user_105466]
  video_c 的入边 → [user_160414, user_105466]
  ...

Step 3: 统计每个用户与目标用户的共同视频数
  user_278190: 看了 video_a, video_b → 2个共同视频
  user_105466: 看了 video_b, video_c → 2个共同视频
  user_74420:  看了 video_a          → 1个共同视频
  ...

Step 4: 按共同视频数降序排列，排除目标用户自己
  #1 user_278190 (2个共同视频)
  #2 user_105466 (2个共同视频)
  #3 user_160414 (1个共同视频)
  ...

Step 5: 返回推荐结果（当前为 user_391585 找到了 15 个推荐好友）
```

**时间复杂度**：O(user_out_degree × avg_video_in_degree)，对 500 节点规模即时完成。

**操作方式：**
- 控制台：自动展示 Top 10 推荐好友
- API：`GET /api/friends?userId=X`
- 前端：好友推荐 Tab

---

## API 端点总览

| 端点 | 参数 | 功能 | 对应算法 |
|------|------|------|---------|
| `GET /api/health` | - | 健康检查（状态、运行时长、节点/边统计） | - |
| `GET /api/stats` | - | 图统计 + 数据库统计 + 边类型分布 | - |
| `GET /api/users` | - | 所有 100 个用户列表 | - |
| `GET /api/recommend` | `userId`, `alpha`(0.4), `beta`(0.6), `prMode`(full) | 综合打分视频推荐 | BFS + PageRank×2 + 综合打分 |
| `GET /api/friends` | `userId` | 好友推荐（共同观看协同过滤） | 协同过滤 |
| `GET /api/lcc` | - | 全部用户 LCC 茧房检测（含 riskLevel） | LCC |
| `GET /api/pagerank` | `top`(10) | 视频 PageRank 热度排行榜 | 全图 PageRank |

所有端点返回 JSON，设置 CORS 头（`Access-Control-Allow-Origin: *`）。

---

## 项目进度对照

| 模块 | 内容 | 状态 | 实现位置 |
|------|------|------|---------|
| 数据基建 | Python抽样 + Node/Graph/DataLoader | ✅ | `backend/scripts/`, `model/`, `loader/` |
| BFS 召回 | 2-3层异构图案遍历 | ✅ | `Graph.findCandidateVideosByBFS()` |
| PageRank（全图） | 全图迭代热度计算（20轮） | ✅ | `Graph.computePageRank()` |
| PageRank（Watch） | 组员pagerank.py融合（收敛式） | ✅ | `Graph.computeWatchBasedPageRank()` |
| 综合打分 | α×距离 + β×热度（双PR模式） | ✅ | `Graph.rankCandidatesByCompositeScore()` |
| LCC 茧房检测 | 局部聚类系数 + 风险分级 | ✅ | `Graph.computeLocalClusteringCoefficient()` |
| 好友推荐 | 组员协同过滤算法 | ✅ | `FriendRecommendation.recommend()` |
| 数据库 | SQLite 持久化 | ✅ | `DatabaseManager`（try-with-resources） |
| 后端 API | 7个REST端点 + CORS | ✅ | `SynchPlayServer` |
| 前端界面 | 5个Tab + 视频卡片 + 关注点分离 | ✅ | `frontend/`（HTML+CSS+JS 三文件） |
| 前后端分离 | 独立部署、HTTP通信 | ✅ | 前端可部署到静态服务器 |
| Dijkstra | 最短路径权重算法 | ⏳ 后续 | - |

---

## 运行方式

所有后端命令从 `backend/` 目录执行：

```bash
cd backend

# 一键启动（编译 + 后端 API，浏览器打开 localhost:8080）
bash start.sh

# 或分步：
bash compile.sh                                    # 编译（5步）
java --add-modules jdk.httpserver \
  -cp bin:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar \
  com.synchplay.ServerMain 8080                    # 启动后端

# 控制台 Demo（算法验证）
java --add-modules jdk.httpserver -cp bin com.synchplay.Main

# 前端独立部署
open ../frontend/index.html                        # 浏览器直接打开
```

---

## 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 数据预处理 | Python 3（标准库） | CSV 抽样与生成 |
| 核心算法 | Java 22 | BFS / PageRank×2 / LCC / 综合打分 / 协同过滤 |
| 数据库 | SQLite + JDBC | 嵌入式关系型数据库 |
| 后端框架 | `com.sun.net.httpserver` | JDK 内置 HTTP 服务器，零额外依赖 |
| 前端 | HTML + CSS + JS（原生） | 三文件分离，无框架，响应式布局 |
| 构建 | Shell 脚本 | `compile.sh`（5步编译）/ `start.sh`（一键启动） |
