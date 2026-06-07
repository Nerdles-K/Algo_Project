#!/usr/bin/env bash
# 一键生成并打开交互式关系图可视化（scripts/graph.html）。
# 不需要启动后端。
#
# 用法:
#   ./scripts/show_graph.sh          # 读数据库实时数据(默认)；连不上则退回 CSV 快照
#   ./scripts/show_graph.sh --csv    # 只用 CSV 快照(零依赖，不需要数据库)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
HTML="$SCRIPT_DIR/graph.html"

# Homebrew 装的 psql 常不在 PATH 里，补进来
for d in /opt/homebrew/opt/postgresql@*/bin /usr/local/opt/postgresql@*/bin; do
  [ -d "$d" ] && PATH="$d:$PATH"
done

cd "$ROOT"

if [ "$1" = "--csv" ]; then
  echo "Generating graph from CSV snapshot..."
  python3 scripts/make_graph_html.py
else
  echo "Generating graph from database (--db)..."
  if ! python3 scripts/make_graph_html.py --db; then
    echo "⚠ Database mode failed (is PostgreSQL running?). Falling back to CSV snapshot."
    python3 scripts/make_graph_html.py
  fi
fi

# 在默认浏览器打开（macOS: open，Linux: xdg-open）
if command -v open >/dev/null 2>&1; then
  open "$HTML"
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$HTML"
else
  echo "Open it manually in a browser: $HTML"
fi
