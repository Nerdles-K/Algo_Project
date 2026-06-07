#!/usr/bin/env python3
"""
基于【标签重叠 Jaccard】重算 similar 边（video↔video），替换掉原来"同频道"的版本。

只针对【现有】的视频节点（读 ProcessedData/mini_nodes.csv），用它们在原始
Dataset/archive/USvideos.csv 里的 tags 计算两两 Jaccard 相似度。不重新采样，
所以 user/video 节点、social/watch 边都保持不变，只替换 similar 边。

规则：两视频共享标签数 ≥ MIN_SHARED 且 Jaccard ≥ THRESHOLD 才连边；每个视频最多
保留 TOP_K 条最相似的边（安全上限）；边权 = Jaccard 分数（有梯度）。

用法:  python3 scripts/regen_similar.py
输出:  改写 ProcessedData/mini_edges.csv（similar 边换成新版，其余边不动）
       备份原文件到 mini_edges.csv.pre_jaccard.bak
"""
import csv
import itertools
import os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NODES_CSV = os.path.join(ROOT, "ProcessedData", "mini_nodes.csv")
EDGES_CSV = os.path.join(ROOT, "ProcessedData", "mini_edges.csv")
RAW_CSV   = os.path.join(ROOT, "Dataset", "archive", "USvideos.csv")

THRESHOLD = 0.05    # Jaccard 下限
MIN_SHARED = 2      # 至少共享多少个标签
TOP_K = 10          # 每个视频最多保留的相似边（安全上限）


def parse_tags(t):
    if not t or t == "[none]":
        return set()
    return {x.strip().strip('"').lower() for x in t.split("|") if x.strip().strip('"')}


def main():
    # 1) 现有视频节点: original_id(video_id) -> node_id
    vid_node = {}
    for r in csv.DictReader(open(NODES_CSV, newline="", encoding="utf-8")):
        if r["node_type"] == "video":
            vid_node[r["original_id"]] = r["node_id"]

    # 2) 从原始集取 tags（按 video_id 去重，多天 trending 取首条）
    tags = {}
    for r in csv.DictReader(open(RAW_CSV, newline="", encoding="utf-8")):
        vid = r["video_id"]
        if vid in vid_node and vid not in tags:
            tags[vid] = parse_tags(r.get("tags", ""))
    vids = [v for v in vid_node if tags.get(v)]

    # 3) 两两 Jaccard，收集候选
    cand = defaultdict(list)   # video_id -> [(score, other_video_id)]
    for a, b in itertools.combinations(vids, 2):
        ta, tb = tags[a], tags[b]
        shared = len(ta & tb)
        if shared < MIN_SHARED:
            continue
        j = shared / len(ta | tb)
        if j < THRESHOLD:
            continue
        cand[a].append((j, b))
        cand[b].append((j, a))

    # 4) 每个视频取 TOP_K，合并成无向边（去重）
    chosen = {}   # frozenset({nidA, nidB}) -> weight
    for v in vids:
        for j, o in sorted(cand[v], reverse=True)[:TOP_K]:
            key = frozenset((vid_node[v], vid_node[o]))
            chosen[key] = max(chosen.get(key, 0), round(j, 4))

    # 5) 读现有边，保留非 similar，拼上新 similar
    rows = list(csv.DictReader(open(EDGES_CSV, newline="", encoding="utf-8")))
    kept = [r for r in rows if r["edge_type"] != "similar"]
    new_similar = []
    for key, w in chosen.items():
        a, b = tuple(key)
        new_similar.append({"source": a, "target": b, "edge_type": "similar", "weight": w})

    # 6) 备份 + 写回
    bak = EDGES_CSV + ".pre_jaccard.bak"
    if not os.path.exists(bak):
        os.replace(EDGES_CSV, bak)         # 首次运行才备份原始版
    else:
        pass
    with open(EDGES_CSV, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["source", "target", "edge_type", "weight"])
        w.writeheader()
        w.writerows(kept)
        w.writerows(new_similar)

    covered = set()
    for key in chosen:
        covered |= set(key)
    print(f"✓ 重算 similar 边: {len(new_similar)} 条 (旧版同频道是 114)")
    print(f"  覆盖视频 {len(covered)}/{len(vids)} · 权重=Jaccard(梯度)")
    print(f"  其余边保留: social/watch 共 {len(kept)} 条")
    print(f"  备份: {bak if os.path.exists(bak) else '(已存在,未覆盖)'}")
    print(f"  mini_edges.csv 现在共 {len(kept)+len(new_similar)} 条边")


if __name__ == "__main__":
    main()
