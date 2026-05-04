# SynchPlay 项目总结文档

## 项目定位

SynchPlay 是一款基于社交图谱的视频推荐引擎，核心目标是解决算法导致的"信息茧房"问题——通过分析用户的社交圈（好友看了什么）来提供更具社交共鸣的视频推荐。

---

## 技术架构

项目采用 **前后端分离** 架构，前端和后端独立运行，通过 HTTP REST API 通信。

```
┌─────────────────────────────────────────────────────────┐
│  前端 (frontend/index.html)                              │
│  原生 HTML/CSS/JS，无框架依赖                            │
│  可独立部署：浏览器直接打开 / 任意静态文件服务器           │
│  配置 API_BASE 变量指向后端地址即可切换后端               │
└────────────┬────────────────────────────────────────────┘
             │  HTTP GET (CORS)
             ▼
┌─────────────────────────────────────────────────────────┐
│  后端 (SynchPlayServer.java :8080)                       │
│  Java HttpServer，仅提供 REST API，不返回 HTML            │
│  ┌─────────────────────────────────────────────────┐    │
│  │  GET /api/health     健康检查 + 运行信息          │    │
│  │  GET /api/stats      图统计信息                  │    │
│  │  GET /api/users      用户列表                    │    │
│  │  GET /api/recommend  综合打分视频推荐             │    │
│  │  GET /api/lcc        LCC茧房检测报告              │    │
│  │  GET /api/pagerank   PageRank排行榜              │    │
│  └─────────────────────────────────────────────────┘    │
│            │                                             │
│     ┌──────┴──────┐                                      │
│     ▼              ▼                                      │
│  Graph (内存)   DatabaseManager                          │
│  算法引擎        SQLite JDBC                             │
│  BFS/PageRank   nodes表 + edges表                        │
│  /LCC/综合打分                                            │
└─────────────────────────────────────────────────────────┘
```

### 数据层
- **原始数据**：SNAP YouTube 社区数据（39,841用户）+ Kaggle YouTube Trending（40,949视频）
- **Python 抽样**：`data_preparation.py` 随机抽样 100用户 + 400视频，生成 945 条边
- **数据库**：SQLite（通过 JDBC 连接），包含 `nodes` 和 `edges` 两张表，含索引

### 后端 API（纯 REST，前后端分离）

| 端点 | 功能 |
|------|------|
| `GET /api/health` | 健康检查（状态、运行时长、节点/边/用户/视频数） |
| `GET /api/stats` | 图统计 + 数据库统计 + 边类型分布 |
| `GET /api/users` | 所有用户列表 |
| `GET /api/recommend?userId=X&alpha=0.4&beta=0.6` | 综合打分视频推荐 |
| `GET /api/lcc` | 所有用户 LCC 茧房检测报告（含风险等级） |
| `GET /api/pagerank?top=N` | 视频 PageRank 热度排行榜 |

所有端点返回 JSON，设置 CORS 头允许跨域访问。后端不提供任何 HTML 页面。

### 前端界面（独立部署，4个Tab页）

| Tab | 内容 |
|-----|------|
| **视频推荐**（首页） | 下拉选用户 → 视频卡片网格（YouTube缩略图+标题+频道+观看量+距离/PageRank/最终分）。α/β 权重滑块实时调整排序 |
| **概览** | 图统计卡片（节点/边/密度/度数）+ 边类型分布 |
| **茧房检测** | LCC 排行榜 + 高/中/低风险分级统计 |
| **PageRank** | 视频热度排行榜 |

前端顶部显示后端连接状态，通过修改 `API_BASE` 变量即可切换后端地址。

---

## 图结构详解

### 一、什么是异构图？

这个项目的核心数据结构是一个**图（Graph）**。图由**节点**和**边**组成。

但与普通图不同，我们的图里有两种不同类型的节点，所以叫 **异构图（Heterogeneous Graph）**。

**两种节点（类比：社交网络）：**

```
  [用户节点]                     [视频节点]
  代表一个"人"                   代表一个"YouTube 视频"

  例如：                         例如：
  User_391585                    video_aEM2kOrrNJI
  (用户ID: 391585)               (标题: Jennifer Lopez - Dinero...)
```

**三种边（类比：人与人、人与物之间的关系）：**

```
  关系1: 社交关系 (social)       关系2: 观看关系 (watch)        关系3: 相似关系 (similar)
  用户 ←→ 用户                   用户  →  视频                  视频 ←→ 视频

  例如：                         例如：                         例如：
  小明和小红是同一个              小明看了"Jennifer Lopez       视频A和视频B都是
  YouTube社区的成员              的MV"这个视频                  同一个频道发布的
```

**数据集实际规模：**

| 节点/边 | 数量 | 说明 |
|---------|------|------|
| 用户节点 | 100 个 | 从 39,841 人中随机抽样 |
| 视频节点 | 400 个 | 从 40,949 个热门视频中随机抽样 |
| social 边 | 72 条 | 用户之间的社交连接（权重=1.0） |
| watch 边 | 755 条 | 用户观看视频的记录（权重=点赞/播放量） |
| similar 边 | 118 条 | 同频道视频之间的相似连接（权重=0.5） |

---

### 二、图在 Java 代码中如何存储？

代码中使用 **邻接表（Adjacency List）** 存储图，简单说就是：**每个节点都维护一个"我连接到了谁"的列表**。

```
Graph.java 中的三个核心数据结构:

1. nodeMap（节点字典）
   "user_391585"  ──→  Node对象 {类型=user, 名称=User_391585}
   "user_4295"    ──→  Node对象 {类型=user, 名称=User_4295}
   "video_aEM..." ──→  Node对象 {类型=video, 标题=Jennifer Lopez...}
   ...共 483 个节点

2. adjacencyList（邻接表 = "我指向谁"）
   "user_391585"  ──→  [ Edge(user_391585 → video_abc, type=watch),
                          Edge(user_391585 → video_def, type=watch),
                           ... 共 9 条出边 ]

3. reverseAdjacencyList（反向邻接表 = "谁指向我"）
   "video_abc"    ──→  [ Edge(user_391585 → video_abc, type=watch),
                          Edge(user_222 → video_abc, type=watch),
                          ... 共 3 条入边 ]
```

**为什么用邻接表而不是矩阵？** 因为图中有 483 个节点但只有 945 条边，这是一个**稀疏图**。如果用矩阵存储，需要 483×483=233,289 个格子，其中绝大部分都是空的，浪费内存。邻接表只存实际存在的边，节省空间。

---

### 三、BFS 召回算法是如何工作的？

BFS（广度优先搜索）是推荐系统的**第一步：从海量视频中捞出一批候选视频**。后面再用 PageRank 和打分公式对这些候选视频进行排序。

**算法步骤（以用户 User_A 为例）：**

```
Step 0: 从 User_A 出发，把它加入待探索队列

        队列: [User_A]

Step 1 (第1跳): 探索 User_A 的所有邻居
        User_A 有这些连接：
          --[watch]--> Video_1   (User_A 看过 Video_1)
          --[watch]--> Video_2   (User_A 看过 Video_2)
          --[social]--> User_B   (User_A 和 User_B 是社区好友)

        发现视频: Video_1, Video_2  ← 加入候选集
        发现用户: User_B           ← 加入队列继续探索
        
        队列: [User_B]

Step 2 (第2跳): 探索 User_B 的所有邻居
        User_B 有这些连接：
          --[watch]--> Video_2   (User_B 也看过 Video_2)
          --[watch]--> Video_3   (User_B 看过 Video_3)
          --[social]--> User_C   (User_B 和 User_C 是社区好友)

        发现视频: Video_3         ← 加入候选集
        (Video_2 已经发现过，跳过)

        队列: [User_C]

Step 3: User_C 已经达到最大深度（2跳），不再继续探索。

最终候选集: {Video_1, Video_2, Video_3}
```

**关键设计点：**
- 使用 `visited set` 记录已访问的节点，**防止重复访问和死循环**
- BFS 以**无向方式**遍历（同时检查出边和入边），确保不会遗漏关系
- 深度限制为 2-3 层：太浅则候选太少，太深则引入噪音

**实际运行结果（用户 user_391585）：**
- 2-hop BFS 找到了 **19 个候选视频**
- 3-hop BFS 找到了 **112 个候选视频**

---

### 四、LCC 茧房检测是如何工作的？

LCC（Local Clustering Coefficient，局部聚类系数）用于回答一个问题：**我的朋友们互相认识吗？**

**直观理解：**

```
情况A：开放社交圈（LCC = 0.0，低风险）
  你的朋友有: 小明、小红、小刚
  小明、小红、小刚 三人之间 → 全都不认识
  
  解释：你的朋友来自不同圈子，你能接触到多元信息


情况B：封闭社交圈 / 茧房（LCC = 1.0，高风险）
  你的朋友有: 小明、小红、小刚
  小明 ←→ 小红 ←→ 小刚 ←→ 小明 （三人互相都认识）
  
  解释：你的朋友全在一个小圈子里，大家看的东西都一样
        你很难接触到圈子以外的信息 → "信息茧房"
```

**计算公式：**

```
LCC = (你的朋友们之间实际存在的连接数) / (朋友们之间最多可能有多少连接)

假设你有 k 个朋友：
  最多可能连接数 = k × (k - 1) / 2
  LCC = 实际连接数 / 最多可能连接数

例子：你有 3 个朋友 {A, B, C}
  最多可能连接: 3×2/2 = 3 条  (A-B, B-C, A-C)
  
  如果 A-B 和 B-C 有连接，但 A-C 没有：
  LCC = 2/3 = 0.667  ← 中风险
  
  如果 A、B、C 两两都有连接：
  LCC = 3/3 = 1.000  ← 高风险！
```

**在我们的数据中实际检测结果：**

| 指标 | 数值 | 说明 |
|------|------|------|
| 有社交连接的用户 | 22 人（共 100 人） | 大部分用户缺少社交关系数据 |
| 平均 LCC | 0.8557 | 在有社交连接的用户中，圈子非常封闭 |
| 高风险用户（LCC>0.7） | 5 人 | 这些用户的朋友圈几乎完全互通 |
| 中风险用户（0.3-0.7） | 17 人 | - |
| 低风险用户（<0.3） | 0 人 | 没有完全开放的社交圈 |

**为什么 LCC 能检测茧房？**

如果 LCC 很高 → 你的朋友们互相都认识 → 他们很可能看同样的视频、有同样的兴趣 → 你从他们那里得到的推荐都是同质化的 → 你被困在了信息茧房里。

PageRank 排序会加剧这个问题：热门视频排名越来越高，冷门视频越来越不可见。所以 PageRank 负责推荐"热门的"，LCC 负责提醒你"你可能被关在茧房里了"。

---

## 核心算法实现

### 1. BFS 多跳召回（Graph.java `findCandidateVideosByBFS`）
- 从目标用户出发，在异构图中进行 2-3 层无向 BFS 遍历
- 收集路径上遇到的所有视频节点作为候选集
- 使用 `visited set` 防止重复访问和死循环
- 时间复杂度 O(V + E)

### 2. PageRank 全局热度（Graph.java `computePageRank`）
- 迭代式 PageRank 算法（默认 20 轮迭代，阻尼因子 0.85）
- 处理 sink nodes（出度为0的节点将分数均分给所有节点）
- 输出所有视频节点的全局热度排名

### 3. 综合打分公式（Graph.java `rankCandidatesByCompositeScore`）
```
最终分 = α × (1 / 路径距离) + β × 归一化PageRank
```
- α 控制"社交邻近度"权重，β 控制"视频热度"权重
- 可调节参数适配不同推荐策略

### 4. LCC 局部聚类系数——茧房检测（Graph.java `computeLocalClusteringCoefficient`）
```
LCC = 2 × 邻居间实际边数 / (k × (k - 1))
```
- 计算用户社交邻居之间的互连程度
- LCC ∈ [0, 1]，越高表示社交圈越封闭，茧房效应越强
- 按风险分级：高风险 (>0.7)、中风险 (0.3-0.7)、低风险 (<0.3)

---

## 项目进度对照

| 模块 | 计划内容 | 状态 | 说明 |
|------|---------|------|------|
| 数据基建 | Python抽样 + Node/Graph/DataLoader | ✅ 100% | 500节点 / 945边 |
| BFS 召回 | 2-3层图遍历候选集 | ✅ 已实现 | `findCandidateVideosByBFS` |
| PageRank | 全图迭代热度计算 | ✅ 已实现 | `computePageRank` |
| 综合打分 | α×距离 + β×热度 | ✅ 已实现 | `rankCandidatesByCompositeScore` |
| LCC 茧房检测 | 局部聚类系数 | ✅ 已实现 | `computeLocalClusteringCoefficient` |
| 数据库 | SQLite 持久化存储 | ✅ 已实现 | JDBC + 建表 + CSV导入 |
| 后端 API | RESTful HTTP 服务 | ✅ 已实现 | 6个端点 + CORS 跨域 + 健康检查 |
| 前端界面 | 交互式推荐展示 | ✅ 已实现 | 4个Tab页 + 视频卡片网格 |
| 前后端分离 | 独立部署、HTTP通信 | ✅ 已实现 | 前端可独立部署到任意静态服务器 |
| 代码质量 | 资源管理、静默加载 | ✅ 已完善 | try-with-resources + 服务端静默模式 |
| Dijkstra | 最短路径权重算法 | ⏳ 后续 | 优先级低于 LCC |
| 实验报告 | 性能评估与对比 | ⏳ 阶段3 | - |

---

## 项目文件结构

```
Algo_Project/
├── data_preparation.py              # Python 数据抽样脚本
├── compile.sh                       # Java 编译脚本
├── start.sh                         # Web 服务一键启动
├── Dataset/                         # 原始数据
├── ProcessedData/
│   ├── mini_nodes.csv               # 500节点
│   ├── mini_edges.csv               # 945边
│   ├── synchplay.db                 # SQLite 数据库
│   └── data_statistics.txt          # 统计报告
├── lib/
│   ├── sqlite-jdbc.jar              # SQLite JDBC 驱动
│   ├── slf4j-api.jar                # 日志接口
│   └── slf4j-nop.jar                # 日志实现
├── src/com/synchplay/
│   ├── Main.java                    # 控制台 Demo 入口
│   ├── ServerMain.java              # Web 服务入口
│   ├── model/
│   │   ├── Node.java                # 图节点（用户/视频）
│   │   ├── Edge.java                # 图边（社交/观看/相似）
│   │   └── Graph.java               # 异构图 + 全部算法（含内部类VideoScore）
│   ├── loader/
│   │   └── DataLoader.java          # CSV 数据加载器 + loadGraphSilently()
│   ├── db/
│   │   └── DatabaseManager.java     # SQLite 数据库管理（try-with-resources）
│   └── server/
│       └── SynchPlayServer.java     # HTTP 服务器 + 6个REST API端点
├── frontend/
│   └── index.html                   # 前端单页应用
└── bin/                             # 编译输出 (.class)
```

---

## 运行方式

前后端分离运行：

```bash
# 终端1：启动后端 API 服务
bash start.sh
# 输出: SynchPlay API 服务已启动: http://localhost:8080

# 终端2：打开前端（任选一种方式）
open frontend/index.html                                # macOS 直接打开
# 或 python3 -m http.server 3000 -d frontend             # 本地静态服务器
# 或部署到任意静态托管（GitHub Pages / Netlify 等）
```

控制台 Demo（无需前端，直接验证算法输出）：

```bash
bash compile.sh
java --add-modules jdk.httpserver -cp bin com.synchplay.Main
```

前端默认连接 `http://localhost:8080`，如需修改后端地址，编辑 `frontend/index.html` 顶部的 `API_BASE` 变量。

---

## 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 数据预处理 | Python 3 (标准库) | CSV 抽样与生成 |
| 核心算法 | Java 22 | BFS / PageRank / LCC / 综合打分 |
| 数据库 | SQLite + JDBC | 嵌入式关系型数据库 |
| 后端 | `com.sun.net.httpserver` | JDK 内置 HTTP 服务器 |
| 前端 | 原生 HTML/CSS/JS | 无框架依赖，独立于后端部署 |
| 构建 | Shell 脚本 | `compile.sh` / `start.sh` |
