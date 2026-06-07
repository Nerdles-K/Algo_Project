#!/usr/bin/env python3
"""
标签清洗 + 基于【清洗后标签的 Jaccard】重算 similar 边（video↔video）。

清洗（去掉创作者噪声、保留题材标签）：
  1) 丢掉只被 1 个频道用过的标签（频道独占 → 创作者噪声，只能连同频道视频）
  2) 丢掉含频道名的标签
相似度：两视频共享清洗后标签 ≥ MIN_SHARED 且 Jaccard ≥ THRESHOLD 才连边；
每视频最多 TOP_K 条；边权 = Jaccard。

只针对【现有】视频节点（不重采样）。产出两处改动：
  - ProcessedData/mini_edges.csv  similar 边换成清洗版（social/watch 不动）
  - ProcessedData/mini_nodes.csv  新增 tags 列（视频=清洗后题材标签, 用户=空）
原文件首次运行时备份为 *.pre_jaccard.bak / *.pre_tags.bak。

用法:  python3 scripts/regen_similar.py
之后用 psql 同步到数据库（见 scripts/VISUALIZATION.md）。
"""
import csv
import itertools
import os
import re
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
NODES_CSV = os.path.join(ROOT, "ProcessedData", "mini_nodes.csv")
EDGES_CSV = os.path.join(ROOT, "ProcessedData", "mini_edges.csv")
RAW_CSV   = os.path.join(ROOT, "Dataset", "archive", "USvideos.csv")

THRESHOLD = 0.1     # Jaccard 下限
MIN_SHARED = 2      # 至少共享的(清洗后)标签数
TOP_K = 8           # 每个视频最多保留的相似边
MIN_CHANNELS = 2    # 标签至少被多少个不同频道使用才保留


def parse_tags(t):
    if not t or t == "[none]":
        return set()
    return {x.strip().strip('"').lower() for x in t.split("|") if x.strip().strip('"')}


def main():
    # 1) 现有视频节点
    node_rows = list(csv.DictReader(open(NODES_CSV, newline="", encoding="utf-8")))
    vid_node, channel = {}, {}
    for r in node_rows:
        if r["node_type"] == "video":
            vid_node[r["original_id"]] = r["node_id"]
            channel[r["original_id"]] = r.get("channel", "")

    # 2) 原始 tags（按 video_id 去重）
    raw = {}
    for r in csv.DictReader(open(RAW_CSV, newline="", encoding="utf-8")):
        vid = r["video_id"]
        if vid in vid_node and vid not in raw:
            raw[vid] = parse_tags(r.get("tags", ""))

    # 3) 标签清洗：频道频次过滤 + 去频道名
    tag_channels = defaultdict(set)
    for vid, ts in raw.items():
        for t in ts:
            tag_channels[t].add(channel[vid])

    def clean(tags, ch):
        ch_l = ch.lower(); ch_ns = ch_l.replace(" ", "")
        ch_tok = {w for w in re.split(r"\W+", ch_l) if len(w) >= 3}
        out = set()
        for t in tags:
            if len(tag_channels[t]) < MIN_CHANNELS:
                continue                                  # 频道独占标签
            if ch_ns and ch_ns in t.replace(" ", ""):
                continue                                  # 含频道全名
            if {w for w in re.split(r"\W+", t) if w} & ch_tok:
                continue                                  # 含频道名词
            out.add(t)
        return out

    cleaned = {vid: clean(raw[vid], channel[vid]) for vid in raw}

    # 4) 清洗后标签的 Jaccard
    cand = defaultdict(list)
    vids = [v for v in cleaned if cleaned[v]]
    for a, b in itertools.combinations(vids, 2):
        ta, tb = cleaned[a], cleaned[b]
        shared = len(ta & tb)
        if shared < MIN_SHARED:
            continue
        j = shared / len(ta | tb)
        if j < THRESHOLD:
            continue
        cand[a].append((j, b)); cand[b].append((j, a))

    chosen = {}
    for v in vids:
        for j, o in sorted(cand[v], reverse=True)[:TOP_K]:
            chosen[frozenset((vid_node[v], vid_node[o]))] = max(
                chosen.get(frozenset((vid_node[v], vid_node[o])), 0), round(j, 4))

    # 5) 改写 edges：保留非 similar，拼新 similar
    edge_rows = list(csv.DictReader(open(EDGES_CSV, newline="", encoding="utf-8")))
    kept = [r for r in edge_rows if r["edge_type"] != "similar"]
    bak_e = EDGES_CSV + ".pre_jaccard.bak"
    if not os.path.exists(bak_e):
        os.replace(EDGES_CSV, bak_e)
    with open(EDGES_CSV, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["source", "target", "edge_type", "weight"])
        w.writeheader()
        w.writerows(kept)
        for key, wt in chosen.items():
            a, b = tuple(key)
            w.writerow({"source": a, "target": b, "edge_type": "similar", "weight": wt})

    # 6) 改写 nodes：新增 tags 列（视频=清洗后标签 | 连接, 用户=空）
    bak_n = NODES_CSV + ".pre_tags.bak"
    if not os.path.exists(bak_n):
        import shutil
        shutil.copy(NODES_CSV, bak_n)
    fields = ["node_id", "node_type", "original_id", "display_name", "channel", "views", "likes", "tags"]
    with open(NODES_CSV, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for r in node_rows:
            r = {k: r.get(k, "") for k in fields}
            if r["node_type"] == "video":
                r["tags"] = "|".join(sorted(cleaned.get(r["original_id"], set())))
            else:
                r["tags"] = ""
            w.writerow(r)

    covered = set()
    for key in chosen:
        covered |= set(key)
    print(f"✓ similar 边: {len(chosen)} 条 · 覆盖视频 {len(covered)}/{len(vids)}")
    print(f"  标签清洗: 中位 {sorted(len(cleaned[v]) for v in vids)[len(vids)//2]} 个/视频 (原始中位~19)")
    print(f"  nodes.csv 新增 tags 列 · edges.csv similar 已更新")
    print(f"  备份: {os.path.basename(bak_e)} / {os.path.basename(bak_n)}")


if __name__ == "__main__":
    main()
