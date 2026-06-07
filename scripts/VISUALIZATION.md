# 图可视化（节点与边）

把 `ProcessedData/` 的节点/边画成图，用于展示。提供两种方式：

| 方式 | 适合 | 是否需安装 |
|------|------|-----------|
| **A. 独立 HTML（推荐）** | 快速展示、拷给别人 | 无，双击即开 |
| B. Neo4j | 深入图查询、跑 Cypher | 要装 Neo4j |

> 两种方式都**不需要启动后端或数据库**，直接读 CSV 快照。

数据规模：100 User + 383 Video（共 483 节点），945 条边。

---

## A. 独立 HTML（推荐）

生成一个自包含的交互式网页，双击用浏览器打开即可，无需任何安装/联网/数据库。

```bash
python3 scripts/make_graph_html.py     # 生成 scripts/graph.html
open scripts/graph.html                # macOS；其他系统双击打开
```

特性：
- 拖动 / 滚轮缩放 / 悬停看节点详情（视频显示频道、播放量、点赞）
- 颜色区分：🔵 User，🔴 Video
- 左上角面板可单独开关 `social 关注` / `watch 观看` / `similar 相似` 三类边
- 打开后图会动画铺开，约 5 秒后自动定住并缩放到全图

> 脚本会把 vis-network 库内嵌进 `graph.html`，所以生成出来的文件可单独拷给别人。
> 首次运行若本地缺 `scripts/vis-network.min.js`，脚本会自动下载（需联网一次）。
> CSV 改了重跑 `make_graph_html.py` 即可刷新。

### 数据映射
| CSV | 图元素 |
|-----|--------|
| node_type=`user`  | User 节点（蓝） |
| node_type=`video` | Video 节点（红） |
| edge_type=`social`  | User → User（关注） |
| edge_type=`watch`   | User → Video（观看） |
| edge_type=`similar` | Video → Video（相似） |

---

## B. Neo4j

适合想写 Cypher 做图查询的场景。

### 1. 装 Neo4j
最省事用 [Neo4j Desktop](https://neo4j.com/download/)：新建 Local DBMS，设密码，Start。

### 2. 把 CSV 放进 import 目录
`LOAD CSV` 默认只读 DBMS 的 `import/` 文件夹。
- Neo4j Desktop：该 DBMS 右侧 `...` → Open folder → Import，把
  `ProcessedData/mini_nodes.csv` 和 `mini_edges.csv` 复制进去。
- 命令行/Docker：复制到 `$NEO4J_HOME/import/`。

### 3. 跑导入脚本
在 Neo4j Browser 里粘贴 `scripts/neo4j_import.cypher` 执行，或：
```bash
cypher-shell -u neo4j -p 你的密码 -f scripts/neo4j_import.cypher
```
校验应返回：User 100 / Video 383 / FOLLOWS 72 / WATCHED 755 / SIMILAR_TO 118。

### 4. 画图（在 Neo4j Browser 里）
```cypher
// 某用户看过的视频 + 这些视频的相似视频
MATCH p=(u:User {id:'user_4295'})-[:WATCHED]->(:Video)-[:SIMILAR_TO]-(:Video)
RETURN p;

// 社交子图
MATCH p=(:User)-[:FOLLOWS]->(:User) RETURN p;
```

不想复制文件可在 `neo4j.conf` 加 `dbms.security.allow_csv_import_from_file_urls=true`，
再把脚本里 `'file:///mini_nodes.csv'` 换成绝对路径。
