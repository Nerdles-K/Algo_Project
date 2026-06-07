#!/usr/bin/env python3
"""
生成 Neo4j 风格的【局部关系图】可视化 HTML（节点带名字、边带关系类型、点击展开邻域）。

两种数据源：
  默认       读 ProcessedData 的 CSV 静态快照
  --db       读 PostgreSQL 实时数据（含新注册用户/观看/上传），并把 app_users
             账号绑定的节点高亮标注（如 demo1 / demo2 / demo3）

用法:
    python3 scripts/make_graph_html.py          # CSV 快照
    python3 scripts/make_graph_html.py --db      # 连数据库

输出:
    scripts/graph.html   （自包含单文件，双击即可在浏览器打开）

--db 模式需要：PostgreSQL 正在运行 + psql 可用。连接参数取自环境变量
（默认与 README 一致）：DB_HOST=localhost DB_PORT=5432 DB_NAME=synchplay
DB_USER=synchplay DB_PASSWORD=synchplay_dev
"""
import csv
import glob
import io
import os
import shutil
import subprocess
import sys
import json
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NODES_CSV = os.path.join(ROOT, "ProcessedData", "mini_nodes.csv")
EDGES_CSV = os.path.join(ROOT, "ProcessedData", "mini_edges.csv")
OUT_HTML = os.path.join(HERE, "graph.html")
VIS_LIB = os.path.join(HERE, "vis-network.min.js")
VIS_URL = "https://unpkg.com/vis-network@9.1.9/standalone/umd/vis-network.min.js"

DEFAULT_ANCHOR = "user_4295"   # CSV 模式默认中心；--db 模式优先选 demo1

USER_COLOR = "#4f8cff"    # 蓝 = 普通用户
VIDEO_COLOR = "#ff6b6b"   # 红 = 视频
ACCOUNT_COLOR = "#b388ff" # 紫 = 绑定了登录账号的用户（demo/注册）
ADMIN_BORDER = "#ffd166"  # 金边 = 管理员账号
# 关系类型 → 显示标签 + 颜色
REL = {
    "social":   ("FOLLOWS",    "#9aa7b8"),
    "watch":    ("WATCHED",    "#7bd389"),
    "similar":  ("SIMILAR_TO", "#ffc857"),
    "uploaded": ("UPLOADED",   "#c084fc"),
}


def ensure_vis_lib():
    if os.path.exists(VIS_LIB):
        return
    import urllib.request
    print(f"… 本地缺 vis 库，正在下载 {VIS_URL}")
    urllib.request.urlretrieve(VIS_URL, VIS_LIB)
    print(f"✓ 已下载到 {VIS_LIB}")


# ───────────────────────── 数据源：CSV ─────────────────────────
def read_csv_source():
    node_rows = list(csv.DictReader(open(NODES_CSV, newline="", encoding="utf-8")))
    edge_rows = list(csv.DictReader(open(EDGES_CSV, newline="", encoding="utf-8")))
    return node_rows, edge_rows, {}   # CSV 无账号信息


# ───────────────────────── 数据源：PostgreSQL ─────────────────────────
def _find_psql():
    p = shutil.which("psql")
    if p:
        return p
    for pat in ("/opt/homebrew/opt/postgresql@*/bin/psql",
                "/usr/local/opt/postgresql@*/bin/psql",
                "/Applications/Postgres.app/Contents/Versions/*/bin/psql"):
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[-1]
    sys.exit("✗ 找不到 psql，请安装/加入 PATH 后重试")


def _psql_csv(psql, conn, query):
    """跑一条 COPY ... TO STDOUT CSV，返回解析后的 dict 列表。"""
    out = subprocess.run(
        [psql, "-h", conn["host"], "-p", conn["port"], "-U", conn["user"],
         "-d", conn["db"], "-tAc", f"COPY ({query}) TO STDOUT WITH CSV HEADER"],
        capture_output=True, text=True,
        env={**os.environ, "PGPASSWORD": conn["password"]})
    if out.returncode != 0:
        sys.exit(f"✗ psql 执行失败:\n{out.stderr.strip()}")
    return list(csv.DictReader(io.StringIO(out.stdout)))


def read_db_source():
    psql = _find_psql()
    conn = {
        "host": os.environ.get("DB_HOST", "localhost"),
        "port": os.environ.get("DB_PORT", "5432"),
        "db":   os.environ.get("DB_NAME", "synchplay"),
        "user": os.environ.get("DB_USER", "synchplay"),
        "password": os.environ.get("DB_PASSWORD", "synchplay_dev"),
    }
    node_rows = _psql_csv(psql, conn,
        "SELECT node_id, node_type, original_id, display_name, channel, views, likes, tags FROM nodes")
    edge_rows = _psql_csv(psql, conn,
        "SELECT src AS source, dst AS target, edge_type, weight FROM edges")
    accounts = _psql_csv(psql, conn,
        "SELECT username, graph_node_id, role FROM app_users")
    acc_map = {a["graph_node_id"]: {"username": a["username"], "role": a["role"]}
               for a in accounts}
    return node_rows, edge_rows, acc_map


# ───────────────────────── 构建图数据 ─────────────────────────
def build_nodes(node_rows, acc_map):
    nodes = {}
    for row in node_rows:
        nid = row["node_id"]
        if nid in nodes:                       # 去重（CSV 可能含重复 node_id）
            continue
        is_user = row["node_type"] == "user"
        name = row.get("display_name") or nid
        acc = acc_map.get(nid)
        if acc:                                # bound to a login account → highlight
            label = acc["username"]
            border = ADMIN_BORDER if acc["role"] == "ADMIN" else "#ffffff"
            color = {"background": ACCOUNT_COLOR, "border": border}
            title = (f"<b>Account: {acc['username']}</b> ({acc['role']})<br>"
                     f"Graph node: {nid}")
            nodes[nid] = {"id": nid, "label": label, "title": title,
                          "group": "account", "color": color,
                          "borderWidth": 4, "account": True}
        elif is_user:
            nodes[nid] = {"id": nid, "label": name,
                          "title": f"<b>User</b><br>{name}<br><i>{nid}</i>",
                          "group": "user", "color": USER_COLOR}
        else:
            short = name[:22] + ("…" if len(name) > 22 else "")
            tags = (row.get("tags") or "").replace("|", ", ")
            title = (f"<b>Video</b><br>{name}<br>Channel: {row.get('channel','')}"
                     f"<br>Views: {row.get('views','')} · Likes: {row.get('likes','')}")
            if tags:
                title += f"<br>Tags: {tags}"
            nodes[nid] = {"id": nid, "label": short, "title": title,
                          "group": "video", "color": VIDEO_COLOR}
    return nodes


def build_edges(valid_ids, edge_rows):
    edges = []
    for row in edge_rows:
        s, t, et = row["source"], row["target"], row["edge_type"]
        if s not in valid_ids or t not in valid_ids:
            continue
        rel_label, rel_color = REL.get(et, (et.upper(), "#ccc"))
        edges.append({"from": s, "to": t, "type": et,
                      "label": rel_label, "color": rel_color})
    return edges


def main():
    use_db = "--db" in sys.argv[1:]
    ensure_vis_lib()

    node_rows, edge_rows, acc_map = read_db_source() if use_db else read_csv_source()
    nodes = build_nodes(node_rows, acc_map)
    edges = build_edges(set(nodes), edge_rows)

    # 度数：节点大小 + 选择器排序
    deg = defaultdict(int)
    sim_deg = defaultdict(int)          # 仅 similar 边的度（用于视频选择器）
    for e in edges:
        deg[e["from"]] += 1
        deg[e["to"]] += 1
        if e["type"] == "similar":
            sim_deg[e["from"]] += 1
            sim_deg[e["to"]] += 1
    for nid, n in nodes.items():
        d = deg.get(nid, 0)
        base = 16 if n.get("account") else 12
        n["size"] = base + min(d, 30) * 0.9
        n["title"] += f"<br>Degree: {d}"

    # 选择器：账号置顶 → 普通用户(按度) → 有相似边的视频(按相似度)
    accounts = [n for n in nodes.values() if n.get("account")]
    accounts.sort(key=lambda n: -deg.get(n["id"], 0))
    plain = sorted((n for n in nodes.values() if n["group"] == "user"),
                   key=lambda n: -deg.get(n["id"], 0))
    sim_videos = sorted((n for n in nodes.values()
                         if n["group"] == "video" and sim_deg.get(n["id"], 0) > 0),
                        key=lambda n: -sim_deg.get(n["id"], 0))
    picker = ([{"id": n["id"], "label": f'👤 {n["label"]}  (deg {deg.get(n["id"],0)})'} for n in accounts] +
              [{"id": n["id"], "label": f'{n["label"]}  (deg {deg.get(n["id"],0)})'} for n in plain] +
              [{"id": n["id"], "label": f'🎬 {n["label"]}  (similar {sim_deg.get(n["id"],0)})'} for n in sim_videos])

    # 默认中心：--db 优先 demo1，否则第一个账号/默认锚点
    anchor = DEFAULT_ANCHOR if DEFAULT_ANCHOR in nodes else None
    if use_db and accounts:
        demo1 = next((n for n in accounts if n["label"] == "demo1"), accounts[0])
        anchor = demo1["id"]
    if anchor is None or anchor not in nodes:
        anchor = picker[0]["id"]

    with open(VIS_LIB, encoding="utf-8") as f:
        vis_js = f.read()
    html = (TEMPLATE
            .replace("__VISLIB__", vis_js)
            .replace("__NODES__", json.dumps(nodes, ensure_ascii=False))
            .replace("__EDGES__", json.dumps(edges, ensure_ascii=False))
            .replace("__PICKER__", json.dumps(picker, ensure_ascii=False))
            .replace("__ANCHOR__", json.dumps(anchor)))
    with open(OUT_HTML, "w", encoding="utf-8") as f:
        f.write(html)

    nu = sum(1 for n in nodes.values() if n["group"] in ("user", "account"))
    src = "数据库(--db)" if use_db else "CSV 快照"
    print(f"✓ 写出 {OUT_HTML}   [数据源: {src}]")
    print(f"  {len(nodes)} 节点 (user {nu} / video {len(nodes)-nu}) · {len(edges)} 边 · 账号节点 {len(accounts)}")
    print(f"  默认中心: {anchor} · 打开: open {OUT_HTML}")


TEMPLATE = r"""<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8" />
<title>SynchPlay — Local Relationship Graph</title>
<script>__VISLIB__</script>
<style>
  html,body{margin:0;height:100%;font-family:-apple-system,"PingFang SC",sans-serif;background:#0e1116;color:#e6e6e6;overflow:hidden}
  #net{width:100%;height:100vh}
  #panel{position:fixed;top:14px;left:14px;width:250px;z-index:10;background:rgba(18,23,30,.94);
         padding:14px 16px;border-radius:12px;font-size:13px;line-height:1.6;
         box-shadow:0 8px 30px rgba(0,0,0,.45);border:1px solid #232b36}
  #panel b{font-size:15px}
  select,button{width:100%;box-sizing:border-box;margin-top:6px;padding:7px 8px;border-radius:8px;
         border:1px solid #2c3744;background:#0e1116;color:#e6e6e6;font-size:13px}
  button{cursor:pointer;background:#1c2530}
  button:hover{background:#26323f}
  .dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:6px;vertical-align:middle}
  .ln{display:inline-block;width:16px;height:0;border-top:3px solid;margin-right:6px;vertical-align:middle}
  hr{border:none;border-top:1px solid #232b36;margin:11px 0}
  label.sw{cursor:pointer;user-select:none;display:block;margin:3px 0}
  .hint{color:#7d8896;font-size:12px;margin-top:4px}
  #sel{margin-top:6px;color:#aeb9c6;font-size:12px;min-height:16px}
</style>
</head>
<body>
<div id="panel">
  <b>SynchPlay Graph</b>
  <div class="hint">Click a node to expand its relationships</div>
  <hr>
  <div>Center node</div>
  <select id="anchor"></select>
  <button id="reset">↺ Reset to center</button>
  <button id="more">＋ Expand one more layer</button>
  <div id="sel"></div>
  <hr>
  <div><span class="dot" style="background:#b388ff"></span>Account (login)</div>
  <div><span class="dot" style="background:#4f8cff"></span>User</div>
  <div><span class="dot" style="background:#ff6b6b"></span>Video</div>
  <div style="margin-top:6px">
    <label class="sw"><input type="checkbox" data-e="social"  checked><span class="ln" style="border-color:#9aa7b8"></span>FOLLOWS</label>
    <label class="sw"><input type="checkbox" data-e="watch"   checked><span class="ln" style="border-color:#7bd389"></span>WATCHED</label>
    <label class="sw"><input type="checkbox" data-e="similar" checked><span class="ln" style="border-color:#ffc857"></span>SIMILAR</label>
    <label class="sw"><input type="checkbox" data-e="uploaded" checked><span class="ln" style="border-color:#c084fc"></span>UPLOADED</label>
  </div>
  <hr>
  <label class="sw"><input type="checkbox" id="phys" checked> Physics animation</label>
</div>
<div id="net"></div>
<script>
  const NODE_DB = __NODES__;
  const EDGE_DB = __EDGES__;
  const PICKER  = __PICKER__;
  let   anchor  = __ANCHOR__;

  const adj = {};
  EDGE_DB.forEach((e, i) => {
    (adj[e.from] = adj[e.from] || []).push(i);
    (adj[e.to]   = adj[e.to]   || []).push(i);
  });

  const nodes = new vis.DataSet();
  const edges = new vis.DataSet();
  const network = new vis.Network(document.getElementById('net'), {nodes, edges}, {
    nodes: { shape:'dot', borderWidth:2, color:{border:'#0e1116'},
             font:{color:'#dfe7f0', size:14, strokeWidth:4, strokeColor:'#0e1116'} },
    edges: { arrows:{to:{enabled:true, scaleFactor:0.5}}, width:1.4,
             font:{color:'#8a94a3', size:11, strokeWidth:3, strokeColor:'#0e1116', align:'middle'},
             smooth:{type:'dynamic'} },
    physics:{ stabilization:{enabled:false},
              barnesHut:{gravitationalConstant:-9000, springLength:150, springConstant:0.04, damping:0.5} },
    interaction:{ hover:true, tooltipDelay:120, multiselect:false }
  });

  function activeTypes(){
    return new Set([...document.querySelectorAll('input[data-e]:checked')].map(x=>x.dataset.e));
  }
  // tooltip 以 HTML 元素传入，否则 vis 会把 <br>/<b> 当纯文本原样显示
  function htmlTitle(html){
    const d = document.createElement('div');
    d.style.maxWidth = '320px';
    d.style.whiteSpace = 'normal';
    d.innerHTML = html;
    return d;
  }
  function addNode(id){
    if (NODE_DB[id] && !nodes.get(id)){
      const n = Object.assign({}, NODE_DB[id]);
      n.title = htmlTitle(n.title);
      nodes.add(n);
    }
  }
  function addEdge(i){
    const e = EDGE_DB[i];
    addNode(e.from); addNode(e.to);
    if (!edges.get('e'+i))
      edges.add({id:'e'+i, from:e.from, to:e.to, label:e.label,
                 color:{color:e.color, opacity:0.7}, type:e.type});
  }
  function expand(id){
    const types = activeTypes();
    addNode(id);
    (adj[id]||[]).forEach(i=>{ if (types.has(EDGE_DB[i].type)) addEdge(i); });
  }
  // 只展开某视频的 similar 边（用于"用户→看过的视频→相似视频"链路）
  function expandSimilar(id){
    if (!activeTypes().has('similar')) return;
    (adj[id]||[]).forEach(i=>{ if (EDGE_DB[i].type==='similar') addEdge(i); });
  }
  function focusOn(id){
    nodes.clear(); edges.clear();
    anchor = id;
    document.getElementById('anchor').value = id;
    expand(id);
    // 中心是用户/账号时，额外把它"看过的视频"的相似边也带出来
    const n = NODE_DB[id];
    if (n && (n.group==='user' || n.group==='account')){
      nodes.getIds().forEach(nid=>{
        if (NODE_DB[nid] && NODE_DB[nid].group==='video') expandSimilar(nid);
      });
    }
    document.getElementById('sel').textContent = 'Center: ' + (NODE_DB[id]?.label||id);
    setTimeout(()=>network.fit({animation:{duration:500}}), 400);
  }

  const sel = document.getElementById('anchor');
  PICKER.forEach(p=>{ const o=document.createElement('option'); o.value=p.id; o.textContent=p.label; sel.appendChild(o); });

  network.on('click', p=>{
    if (p.nodes.length){
      const id=p.nodes[0];
      expand(id);
      document.getElementById('sel').textContent = 'Expanded: ' + (NODE_DB[id]?.label||id);
    }
  });
  sel.addEventListener('change', e=> focusOn(e.target.value));
  document.getElementById('reset').addEventListener('click', ()=> focusOn(anchor));
  document.getElementById('more').addEventListener('click', ()=> nodes.getIds().forEach(expand));
  document.querySelectorAll('input[data-e]').forEach(cb=>
    cb.addEventListener('change', ()=> focusOn(anchor)));
  document.getElementById('phys').addEventListener('change', e=>
    network.setOptions({physics:{enabled:e.target.checked}}));

  focusOn(anchor);
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
