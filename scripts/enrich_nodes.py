#!/usr/bin/env python3
"""
就地增强 ProcessedData/mini_nodes.csv：为每个视频节点补两列
  - category      （由 USvideos.csv 的 category_id 经 US_category_id.json 映射成类别名，如 Music/Sports）
  - published_at  （USvideos.csv 的 publish_time，ISO8601）
不重新采样、不改动任何已有行/列，只在末尾追加两列，从而保留现有图与 demo 账号。

另外输出 /tmp/node_enrich.csv (node_id, category, published_at) 供回填已有数据库使用。

用法： python3 scripts/enrich_nodes.py
"""
import csv
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NODES_CSV = os.path.join(ROOT, "ProcessedData", "mini_nodes.csv")
US_VIDEOS = os.path.join(ROOT, "Dataset", "archive", "USvideos.csv")
US_CATEGORIES = os.path.join(ROOT, "Dataset", "archive", "US_category_id.json")
ENRICH_OUT = "/tmp/node_enrich.csv"

# 1. category_id -> 类别名
with open(US_CATEGORIES, encoding="utf-8") as f:
    cat_map = {item["id"]: item["snippet"]["title"] for item in json.load(f)["items"]}

# 2. video_id -> (category, publish_time)，重复行取首次出现
video_info = {}
with open(US_VIDEOS, encoding="utf-8") as f:
    for row in csv.DictReader(f):
        vid = row["video_id"]
        if vid in video_info:
            continue
        category = cat_map.get(row.get("category_id", ""), "Unknown")
        video_info[vid] = (category, row.get("publish_time", ""))

# 3. 读现有节点，追加两列
with open(NODES_CSV, encoding="utf-8") as f:
    reader = csv.DictReader(f)
    base_fields = reader.fieldnames
    rows = list(reader)

new_fields = base_fields + [c for c in ("category", "published_at") if c not in base_fields]

enriched = 0
for r in rows:
    if r["node_type"] == "video":
        cat, pub = video_info.get(r["original_id"], ("", ""))
        r["category"] = cat
        r["published_at"] = pub
        if cat:
            enriched += 1
    else:
        r.setdefault("category", "")
        r.setdefault("published_at", "")

# 4. 备份并写回
shutil.copy(NODES_CSV, NODES_CSV + ".pre_category.bak")
with open(NODES_CSV, "w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=new_fields, restval="")
    writer.writeheader()
    writer.writerows(rows)

# 5. 输出 DB 回填用的精简文件
with open(ENRICH_OUT, "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(["node_id", "category", "published_at"])
    for r in rows:
        if r["node_type"] == "video":
            writer.writerow([r["node_id"], r.get("category", ""), r.get("published_at", "")])

print(f"✓ 已为 {enriched} 个视频补充 category/published_at")
print(f"✓ mini_nodes.csv 列：{new_fields}")
print(f"✓ DB 回填文件：{ENRICH_OUT}")
