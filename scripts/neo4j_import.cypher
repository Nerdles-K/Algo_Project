// ============================================================
// SynchPlay → Neo4j 导入脚本
// 数据来源: ProcessedData/mini_nodes.csv, mini_edges.csv
// 用法见 scripts/NEO4J.md
// ============================================================

// 0) 清空（重新导入时使用，谨慎）
MATCH (n) DETACH DELETE n;

// 1) 唯一性约束（也会自动建索引，加速后续 MATCH）
CREATE CONSTRAINT user_id  IF NOT EXISTS FOR (u:User)  REQUIRE u.id IS UNIQUE;
CREATE CONSTRAINT video_id IF NOT EXISTS FOR (v:Video) REQUIRE v.id IS UNIQUE;

// 2) 导入 User 节点
LOAD CSV WITH HEADERS FROM 'file:///mini_nodes.csv' AS row
WITH row WHERE row.node_type = 'user'
CREATE (:User {
  id:    row.node_id,
  name:  row.display_name
});

// 3) 导入 Video 节点
LOAD CSV WITH HEADERS FROM 'file:///mini_nodes.csv' AS row
WITH row WHERE row.node_type = 'video'
CREATE (:Video {
  id:      row.node_id,
  name:    row.display_name,
  channel: row.channel,
  views:   toInteger(row.views),
  likes:   toInteger(row.likes)
});

// 4) social 边: User -[:FOLLOWS]-> User
LOAD CSV WITH HEADERS FROM 'file:///mini_edges.csv' AS row
WITH row WHERE row.edge_type = 'social'
MATCH (a:User {id: row.source})
MATCH (b:User {id: row.target})
CREATE (a)-[:FOLLOWS {weight: toFloat(row.weight)}]->(b);

// 5) watch 边: User -[:WATCHED]-> Video
LOAD CSV WITH HEADERS FROM 'file:///mini_edges.csv' AS row
WITH row WHERE row.edge_type = 'watch'
MATCH (u:User  {id: row.source})
MATCH (v:Video {id: row.target})
CREATE (u)-[:WATCHED {weight: toFloat(row.weight)}]->(v);

// 6) similar 边: Video -[:SIMILAR_TO]-> Video
LOAD CSV WITH HEADERS FROM 'file:///mini_edges.csv' AS row
WITH row WHERE row.edge_type = 'similar'
MATCH (a:Video {id: row.source})
MATCH (b:Video {id: row.target})
CREATE (a)-[:SIMILAR_TO {weight: toFloat(row.weight)}]->(b);

// 7) 校验
MATCH (n) RETURN labels(n)[0] AS label, count(*) AS cnt
UNION ALL
MATCH ()-[r]->() RETURN type(r) AS label, count(*) AS cnt;
