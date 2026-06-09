import pandas as pd

# -----------------------------
# 路徑設定
# -----------------------------
NODES_PATH = "ProcessedData/mini_nodes.csv"
EDGES_PATH = "ProcessedData/mini_edges.csv"

OUTPUT_USER = "user.csv"
OUTPUT_VIDEO = "video.csv"
OUTPUT_EDGE = "edge.csv"

# -----------------------------
# 讀取資料
# -----------------------------
print("Loading CSV files...")
nodes = pd.read_csv(NODES_PATH)
edges = pd.read_csv(EDGES_PATH)

print(f"Nodes loaded: {len(nodes)} rows")
print(f"Edges loaded: {len(edges)} rows")

# -----------------------------
# 轉換 user.csv
# -----------------------------
print("\nConverting users...")

users = nodes[nodes["node_type"] == "user"].copy()

users_db = users[["original_id", "display_name"]].rename(
    columns={
        "original_id": "id",
        "display_name": "name"
    }
)

# user.id 是整數
users_db["id"] = users_db["id"].astype(int)

users_db.to_csv(OUTPUT_USER, index=False)
print(f"✓ user.csv generated ({len(users_db)} users)")

# -----------------------------
# 轉換 video.csv
# -----------------------------
print("\nConverting videos...")

videos = nodes[nodes["node_type"] == "video"].copy()

videos_db = videos[
    ["original_id", "original_id", "display_name", "channel", "views", "likes", "tags"]
]

videos_db.columns = [
    "id",           # YouTube video ID（字串）
    "original_id",  # 同上
    "display_name",
    "channel",
    "views",
    "likes",
    "tags"
]

videos_db.to_csv(OUTPUT_VIDEO, index=False)
print(f"✓ video.csv generated ({len(videos_db)} videos)")

# -----------------------------
# 轉換 edge.csv
# -----------------------------
print("\nConverting edges...")

edges_db = edges.copy()

# 使用正確欄位名稱：source / target
edges_db["src"] = edges_db["source"].str.replace("user_", "").str.replace("video_", "")
edges_db["dst"] = edges_db["target"].str.replace("user_", "").str.replace("video_", "")

# 保留 src/dst（字串即可）
edges_db = edges_db[["src", "dst"]]

edges_db.to_csv(OUTPUT_EDGE, index=False)
print(f"✓ edge.csv generated ({len(edges_db)} edges)")

# -----------------------------
# 完成
# -----------------------------
print("\nAll files generated successfully:")
print(f"- {OUTPUT_USER}")
print(f"- {OUTPUT_VIDEO}")
print(f"- {OUTPUT_EDGE}")
