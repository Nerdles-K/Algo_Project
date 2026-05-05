#!/bin/bash
# SynchPlay 后端 API 服务启动脚本
# 前端是独立的静态文件，用浏览器直接打开 ../frontend/index.html 即可

CP="bin:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"
PORT="${1:-8080}"

echo "========================================"
echo "  SynchPlay 后端 API 服务"
echo "========================================"
echo ""

# 检查数据文件
if [ ! -f "../ProcessedData/mini_nodes.csv" ] || [ ! -f "../ProcessedData/mini_edges.csv" ]; then
    echo "未找到数据文件，正在运行数据准备..."
    python3 scripts/data_preparation.py
fi

# 检查编译
if [ ! -f "bin/com/synchplay/ServerMain.class" ]; then
    echo "未找到编译文件，正在编译..."
    bash compile.sh
fi

echo "启动后端 API 服务 (端口: $PORT)..."
echo ""
echo "启动后请用浏览器打开:  ../frontend/index.html"
echo ""

java --add-modules jdk.httpserver -cp "$CP" com.synchplay.ServerMain "$PORT"
