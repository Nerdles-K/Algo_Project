#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据准备与处理脚本
用途：从YouTube数据集中抽取100个用户和400个视频，生成mini_nodes.csv和mini_edges.csv

流程：
1. 从社区检测文件中提取用户关系
2. 从视频CSV中提取视频信息
3. 生成节点和边的CSV文件
"""

import csv
import json
import random
import os
from collections import defaultdict

# 设置随机种子，保证可复现
random.seed(42)

# 配置路径
DATASET_DIR = "Dataset"
CMTY_FILE = os.path.join(DATASET_DIR, "com-youtube.top5000.cmty.txt")
VIDEO_FILE = os.path.join(DATASET_DIR, "archive/USvideos.csv")
CATEGORY_FILE = os.path.join(DATASET_DIR, "archive/US_category_id.json")
OUTPUT_DIR = "ProcessedData"

# category_id -> 类别名（如 10 -> Music）
with open(CATEGORY_FILE, encoding="utf-8") as _cf:
    CATEGORY_MAP = {it["id"]: it["snippet"]["title"] for it in json.load(_cf)["items"]}

# 创建输出目录
os.makedirs(OUTPUT_DIR, exist_ok=True)

MINI_NODES_FILE = os.path.join(OUTPUT_DIR, "mini_nodes.csv")
MINI_EDGES_FILE = os.path.join(OUTPUT_DIR, "mini_edges.csv")

print("=" * 60)
print("SynchPlay 数据准备脚本")
print("=" * 60)

# ============================================================================
# 第一步：提取用户社区关系，生成用户节点和边
# ============================================================================
print("\n[第1步] 读取用户社区关系...")

users = set()
user_edges = []  # (user1, user2)，表示用户在同一社区

try:
    with open(CMTY_FILE, 'r') as f:
        for line_idx, line in enumerate(f):
            community_users = list(map(int, line.strip().split()))
            users.update(community_users)
            
            # 同一社区内的用户相互连接
            for i in range(len(community_users)):
                for j in range(i + 1, len(community_users)):
                    user_edges.append((community_users[i], community_users[j]))
    
    print(f"✓ 社区文件中共发现 {len(users)} 个用户")
    print(f"✓ 共发现 {len(user_edges)} 条用户关系")
    
except FileNotFoundError:
    print(f"✗ 找不到文件: {CMTY_FILE}")
    exit(1)

# 从所有用户中随机采样100个
sampled_users = random.sample(list(users), min(100, len(users)))
sampled_users_set = set(sampled_users)
print(f"✓ 采样 {len(sampled_users)} 个用户")

# 过滤用户边：只保留采样用户之间的连接
filtered_user_edges = [
    (u1, u2) for u1, u2 in user_edges 
    if u1 in sampled_users_set and u2 in sampled_users_set
]
print(f"✓ 过滤后的用户关系：{len(filtered_user_edges)} 条")

# ============================================================================
# 第二步：提取视频信息
# ============================================================================
print("\n[第2步] 读取视频数据...")

videos = []
video_dict = {}  # video_id -> {title, channel, views, likes, ...}

try:
    with open(VIDEO_FILE, 'r', encoding='utf-8') as f:
        csv_reader = csv.DictReader(f)
        for row in csv_reader:
            video_id = row['video_id']
            videos.append({
                'video_id': video_id,
                'title': row['title'][:50],  # 截断标题
                'channel': row['channel_title'][:30],
                'views': int(row['views']),
                'likes': int(row['likes']),
                'dislikes': int(row['dislikes']),
                'comment_count': int(row['comment_count']),
                'publish_time': row['publish_time'],
                'category': CATEGORY_MAP.get(row.get('category_id', ''), 'Unknown'),
                'tags': row.get('tags', ''),   # 用于标签相似度
            })
    
    print(f"✓ 共读取 {len(videos)} 个视频")
    
except FileNotFoundError:
    print(f"✗ 找不到文件: {VIDEO_FILE}")
    exit(1)

# 从所有视频中随机采样400个
sampled_videos = random.sample(videos, min(400, len(videos)))
sampled_video_ids = set(v['video_id'] for v in sampled_videos)
print(f"✓ 采样 {len(sampled_videos)} 个视频")

# ============================================================================
# 第三步：生成用户-视频交互边
# 说明：这里我们模拟用户看过的视频。实际应用中需要真实的观看数据
# ============================================================================
print("\n[第3步] 生成用户-视频交互关系...")

user_video_edges = []

# 假设每个用户平均看过5-10个视频
for user_id in sampled_users:
    num_videos = random.randint(5, 10)
    watched_videos = random.sample(sampled_videos, min(num_videos, len(sampled_videos)))
    for video in watched_videos:
        # 权重基于视频的热度（点赞数）
        weight = max(0.1, video['likes'] / max(1, video['views']))
        user_video_edges.append((user_id, video['video_id'], weight))

print(f"✓ 生成 {len(user_video_edges)} 条用户-视频交互关系")

# ============================================================================
# 第四步：生成视频-视频相似度边（清洗后标签的 Jaccard）
# 标签清洗：丢掉只被 1 个频道用过的标签（创作者噪声）+ 含频道名的标签，只留题材标签。
# 相似度：共享清洗后标签 ≥ MIN_SHARED 且 Jaccard ≥ SIM_THRESHOLD 才连边；
# 每视频最多 SIM_TOP_K 条；边权 = Jaccard（有梯度）。比"同频道"更能反映内容相似。
# ============================================================================
print("\n[第4步] 生成视频-视频相似度关系（标签 Jaccard）...")

import re as _re
SIM_THRESHOLD = 0.1    # Jaccard 下限
MIN_SHARED = 2         # 至少共享的(清洗后)标签数
SIM_TOP_K = 8          # 每个视频最多保留的相似边
MIN_CHANNELS = 2       # 标签至少被多少个不同频道使用才保留

def _parse_tags(t):
    if not t or t == '[none]':
        return set()
    return {x.strip().strip('"').lower() for x in t.split('|') if x.strip().strip('"')}

_raw_tags = {v['video_id']: _parse_tags(v['tags']) for v in sampled_videos}
_vid_channel = {v['video_id']: v['channel'] for v in sampled_videos}

# 每个标签被多少个不同频道使用
_tag_channels = defaultdict(set)
for _vid, _ts in _raw_tags.items():
    for _t in _ts:
        _tag_channels[_t].add(_vid_channel[_vid])

def _clean_tags(tags, channel):
    ch = channel.lower(); ch_ns = ch.replace(' ', '')
    ch_tok = {w for w in _re.split(r'\W+', ch) if len(w) >= 3}
    out = set()
    for t in tags:
        if len(_tag_channels[t]) < MIN_CHANNELS:      # 频道独占 → 创作者噪声
            continue
        if ch_ns and ch_ns in t.replace(' ', ''):     # 含频道全名
            continue
        if {w for w in _re.split(r'\W+', t) if w} & ch_tok:  # 含频道名词
            continue
        out.add(t)
    return out

video_tags = {vid: _clean_tags(_raw_tags[vid], _vid_channel[vid]) for vid in _raw_tags}
tagged = [vid for vid, tg in video_tags.items() if tg]

sim_cand = defaultdict(list)   # video_id -> [(score, other)]
for i in range(len(tagged)):
    for j in range(i + 1, len(tagged)):
        a, b = tagged[i], tagged[j]
        ta, tb = video_tags[a], video_tags[b]
        shared = len(ta & tb)
        if shared < MIN_SHARED:
            continue
        jac = shared / len(ta | tb)
        if jac < SIM_THRESHOLD:
            continue
        sim_cand[a].append((jac, b))
        sim_cand[b].append((jac, a))

_chosen = {}   # frozenset(pair) -> weight
for vid in tagged:
    for jac, other in sorted(sim_cand[vid], reverse=True)[:SIM_TOP_K]:
        key = frozenset((vid, other))
        _chosen[key] = max(_chosen.get(key, 0), round(jac, 4))

video_video_edges = [(a, b, w) for (a, b), w in ((tuple(k), w) for k, w in _chosen.items())]

print(f"✓ 生成 {len(video_video_edges)} 条视频-视频相似度关系")

# ============================================================================
# 第五步：生成节点CSV
# ============================================================================
print("\n[第5步] 生成节点文件...")

nodes = []

# 添加用户节点
for user_id in sampled_users:
    nodes.append({
        'node_id': f"user_{user_id}",
        'node_type': 'user',
        'original_id': user_id,
        'display_name': f"User_{user_id}",
    })

# 添加视频节点
for video in sampled_videos:
    nodes.append({
        'node_id': f"video_{video['video_id']}",
        'node_type': 'video',
        'original_id': video['video_id'],
        'display_name': video['title'],
        'channel': video['channel'],
        'views': video['views'],
        'likes': video['likes'],
        'tags': '|'.join(sorted(video_tags.get(video['video_id'], set()))),
        'category': video['category'],
        'published_at': video['publish_time'],
    })

# 写入节点CSV
with open(MINI_NODES_FILE, 'w', newline='', encoding='utf-8') as f:
    # 先获取所有可能的字段名
    fieldnames = ['node_id', 'node_type', 'original_id', 'display_name', 'channel', 'views', 'likes', 'tags', 'category', 'published_at']
    
    writer = csv.DictWriter(f, fieldnames=fieldnames, restval='')
    writer.writeheader()
    writer.writerows(nodes)

print(f"✓ 节点文件已生成：{MINI_NODES_FILE}")
print(f"  - 用户节点：{len(sampled_users)} 个")
print(f"  - 视频节点：{len(sampled_videos)} 个")
print(f"  - 总计：{len(nodes)} 个节点")

# ============================================================================
# 第六步：生成边CSV
# ============================================================================
print("\n[第6步] 生成边文件...")

edges = []

# 添加用户-用户边（社交关系）
for u1, u2 in filtered_user_edges:
    edges.append({
        'source': f"user_{u1}",
        'target': f"user_{u2}",
        'edge_type': 'social',
        'weight': 1.0,
    })

# 添加用户-视频边（观看行为）
for user_id, video_id, weight in user_video_edges:
    edges.append({
        'source': f"user_{user_id}",
        'target': f"video_{video_id}",
        'edge_type': 'watch',
        'weight': weight,
    })

# 添加视频-视频边（频道相似度）
for video_id1, video_id2, weight in video_video_edges:
    edges.append({
        'source': f"video_{video_id1}",
        'target': f"video_{video_id2}",
        'edge_type': 'similar',
        'weight': weight,
    })

# 写入边CSV
with open(MINI_EDGES_FILE, 'w', newline='', encoding='utf-8') as f:
    fieldnames = ['source', 'target', 'edge_type', 'weight']
    
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(edges)

print(f"✓ 边文件已生成：{MINI_EDGES_FILE}")
print(f"  - 用户-用户边（社交）：{len(filtered_user_edges)} 条")
print(f"  - 用户-视频边（观看）：{len(user_video_edges)} 条")
print(f"  - 视频-视频边（相似度）：{len(video_video_edges)} 条")
print(f"  - 总计：{len(edges)} 条边")

# ============================================================================
# 第七步：生成数据统计报告
# ============================================================================
print("\n[第7步] 数据统计分析...")

# 计算图的基本统计
avg_user_connections = len(filtered_user_edges) / len(sampled_users) if sampled_users else 0
avg_user_videos = len(user_video_edges) / len(sampled_users) if sampled_users else 0
avg_video_popularity = sum(v['views'] for v in sampled_videos) / len(sampled_videos) if sampled_videos else 0

print(f"\n📊 图的基本统计：")
print(f"  - 节点总数：{len(nodes)}")
print(f"  - 边总数：{len(edges)}")
print(f"  - 图密度：{len(edges) / (len(nodes) * (len(nodes) - 1) / 2):.4f}")
print(f"  - 平均用户度：{avg_user_connections:.2f}")
print(f"  - 平均用户观看视频数：{avg_user_videos:.2f}")
print(f"  - 平均视频观看量：{avg_video_popularity:.0f}")

# 生成统计报告文件
stats_file = os.path.join(OUTPUT_DIR, "data_statistics.txt")
with open(stats_file, 'w', encoding='utf-8') as f:
    f.write("=" * 60 + "\n")
    f.write("SynchPlay 数据集统计报告\n")
    f.write("=" * 60 + "\n\n")
    
    f.write("【节点统计】\n")
    f.write(f"  用户节点数：{len(sampled_users)}\n")
    f.write(f"  视频节点数：{len(sampled_videos)}\n")
    f.write(f"  总节点数：{len(nodes)}\n\n")
    
    f.write("【边统计】\n")
    f.write(f"  用户-用户边（社交）：{len(filtered_user_edges)}\n")
    f.write(f"  用户-视频边（观看）：{len(user_video_edges)}\n")
    f.write(f"  视频-视频边（相似度）：{len(video_video_edges)}\n")
    f.write(f"  总边数：{len(edges)}\n\n")
    
    f.write("【图指标】\n")
    f.write(f"  图密度：{len(edges) / (len(nodes) * (len(nodes) - 1) / 2):.4f}\n")
    f.write(f"  平均用户度：{avg_user_connections:.2f}\n")
    f.write(f"  平均用户观看视频数：{avg_user_videos:.2f}\n")
    f.write(f"  平均视频观看量：{avg_video_popularity:.0f}\n\n")
    
    f.write("【文件清单】\n")
    f.write(f"  - {MINI_NODES_FILE}\n")
    f.write(f"  - {MINI_EDGES_FILE}\n")

print(f"✓ 统计报告：{stats_file}\n")

print("=" * 60)
print("✅ 数据准备完成！")
print("=" * 60)
print(f"\n生成的文件：")
print(f"  📄 {MINI_NODES_FILE}")
print(f"  📄 {MINI_EDGES_FILE}")
print(f"  📄 {stats_file}")
