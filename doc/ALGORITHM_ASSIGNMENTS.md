# SynchPlay — 算法层任务分配(给组员 B / C)

> 目标:在不影响现有推荐核心的前提下,各自独立完成一条算法工作线。两条线 **additive、互不冲突**,都要配单元测试(沿用 JUnit 5;当前 47 个测试全绿,合并后不能让任何已有测试变红)。

## 0. 已经做好的算法(别重复造)

| 算法 | 位置 |
|------|------|
| BFS 多跳候选召回 | `Graph.findCandidateVideosByBFS` |
| 全图 PageRank / Watch-PageRank | `Graph.computePageRank` / `computeWatchBasedPageRank` |
| Dijkstra 加权距离 + 综合打分(α·1/d + β·PR + γ·popularity) | `Graph.rankCandidatesByCompositeScore` |
| 局部聚类系数 LCC(回音室) | `Graph.computeLocalClusteringCoefficient` / `computeAllUserLCC` |
| 已看排除 + 观看反馈闭环 | `RecommendController` + `WatchHistoryController` |
| 好友推荐(朴素:共同观看视频数) | `FriendRecommendationService.recommend` |

---

# 🧑‍💻 组员 B —「推荐质量评估 + 多样性」

主题:先造"尺子"量化推荐好坏,再用尺子证明一个反茧房改进。改动集中在 **新建 `EvaluationService`** + 在推荐结果上包一层重排,不碰 `Graph` 核心。

## 票 B1 — 离线评估框架  `[复杂度:中]`
**目标**:能跑出一组推荐质量指标。
**做法**
- 划分 train/test:对每个用户,**藏掉一部分 `watch` 边**(随机或按时间留最后 N 个),用剩下的图跑推荐,看 Top-K 是否命中被藏的。
- 指标:`Precision@K`、`Recall@K`、`MAP`、`NDCG@K`,外加反茧房关键的 **Catalog Coverage**(被推到的视频占全库比例)、**Intra-list Diversity**(列表内两两不相似度)、**Novelty**(− log 流行度均值)。
**落点**:新建 `service/EvaluationService.java`;入口可做成离线 `main` 或 `GET /api/eval?k=10`(临时、仅本地)。
**验收**:`EvaluationServiceTest` 用一个小手工图验证每个指标公式正确(给定固定推荐+真值,Precision@K / NDCG 等算出已知数值)。
**演示点**:一张指标表(全图 PR vs watch PR、不同 α/β/γ 的对比)。

## 票 B2 — MMR 多样性重排  `[复杂度:中]`
**目标**:用数据证明"牺牲一点精度换更多样性"。
**做法**:在 `rankCandidatesByCompositeScore` 的结果上做 **Maximal Marginal Relevance** 重排:
`MMR = λ·rel(v) − (1−λ)·max_{已选 u} sim(v,u)`,其中 `sim` 用视频间是否有 `similar` 边 / 共同观看者 / 频道相同来定义。
**落点**:新建 `service/DiversityReranker.java`;`RecommendController` 加一个 `&diversify=true&lambda=0.7` 开关。
**验收**:`DiversityRerankerTest` 验证 λ=1 时等于原排序、λ 降低时 intra-list diversity 单调上升。
**演示点**:用 B1 的框架画"λ 从 1→0,diversity↑ / Precision↓"的权衡曲线。

> B1 先做(没它 B2 无法量化)。两票都只读图 + 包装结果,和 C 零冲突。

---

# 🧑‍💻 组员 C —「图结构算法 + 更强召回」

主题:写**新的图算法**。改动集中在 **新建 `CommunityService`** + 重写 `FriendRecommendationService`,和 B 完全分开。

## 票 C1 — 社区发现  `[复杂度:中→中高]`
**目标**:把用户划进社区,用于回音室分析和"跨社区"推荐。
**做法**:实现 **Label Propagation**(简单、迭代,先做)或 **Louvain**(模块度优化,进阶)。在社交子图(`social` 边)上跑,输出每个用户的 `communityId`。
**落点**:新建 `service/CommunityService.java` + `domain` 里加社区划分方法;`GET /api/community` 返回社区数/规模分布,Echo Chamber 标签页可加"你属于社区 X,封闭度 …"。
**用途加分**:给推荐加一个 **跨社区信号**——专门推你所在社区**之外**的热门视频(最硬核的反茧房做法)。
**验收**:`CommunityServiceTest` 用一个"两个明显团簇 + 几条桥边"的手工图,断言算法把两团正确分开。
**演示点**:社区数量 + 最大社区占比;开/关跨社区信号后推荐结果的差异。

## 票 C2 — 好友推荐升级为链路预测 + 物品协同过滤  `[复杂度:中]`
**目标**:把"共同观看数"这种朴素打分换成经典算法并对比。
**做法**
- 链路预测打分,四选多并对比:**Common Neighbors / Jaccard / Adamic-Adar / Preferential Attachment**。
- 额外做 **item-based 协同过滤**:对 user×video 观看矩阵算视频间余弦相似度,作为推荐的第二路召回,与现有图距离召回融合。
**落点**:重写 `service/FriendRecommendationService`(保留 `recommend` 签名,内部换算法 + 可选 `method` 参数);CF 可新建 `service/ItemCFService.java`。
**验收**:`FriendRecommendationServiceTest`(已存在 5 个)扩展:在手工图上验证 Adamic-Adar 对"低度共同邻居"加权更高等性质。
**演示点**:同一用户,四种打分给出的"可能认识的人"差异;CF 召回带来的新候选视频。

> C1 先做或 C1/C2 并行都行,互不依赖。

---

## 备选 backlog(想换方向从这里挑)
- **Personalized PageRank / 带重启随机游走**:以用户为根的个性化,替代现在的距离启发式。
- **冷启动内容召回**:为新上传视频按频道/标签相似度**自动建 `similar` 边**,让它不至于隐形(配合已上线的上传功能)。
- **时间衰减**:`watch` 边权重按观看时间指数衰减。
- **中心性对比**:betweenness / closeness vs PageRank。

## 完成标准(两人通用)
1. 新算法都配 JUnit 5 单元测试,用小手工图验证正确性。
2. `mvn test` 全绿(不破坏现有 47 个测试)。
3. 新端点若仅用于演示/评估,标注为本地用途。
4. 在 `doc/REQUIREMENTS.md` 标上对应 FR 状态,`doc/PROGRESS.md` 更新分工。

## 协调点
- B 和 C 都会**读** `Graph.java`,但都是**新增类**,合并风险低;若要往 `Graph` 加公共方法(如 C1 的社区划分),各自加各自的,避免改同一方法。
- 推荐管线入口 `RecommendController` 两人都可能加查询参数(B 加 `diversify`,C 加跨社区信号)——约定**各加各的参数**,不互相覆盖。
