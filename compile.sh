#!/bin/bash
# SynchPlay 项目编译脚本

echo "====== SynchPlay 编译脚本 ======"
echo ""

# 创建输出目录
mkdir -p bin

# 编译参数 (JDK 22+ 需要显式引入 httpserver 模块)
JAVAC_OPTS="-d bin --add-modules jdk.httpserver"
CP="bin:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"

echo "[1/4] 编译模型类..."
javac $JAVAC_OPTS src/com/synchplay/model/*.java 2>&1
if [ $? -ne 0 ]; then echo "✗ 模型类编译失败"; exit 1; fi
echo "✓ 模型类编译成功"

echo "[2/4] 编译加载器类..."
javac $JAVAC_OPTS -cp "$CP" src/com/synchplay/loader/*.java 2>&1
if [ $? -ne 0 ]; then echo "✗ 加载器类编译失败"; exit 1; fi
echo "✓ 加载器类编译成功"

echo "[3/4] 编译数据库类..."
javac $JAVAC_OPTS -cp "$CP" src/com/synchplay/db/*.java 2>&1
if [ $? -ne 0 ]; then echo "✗ 数据库类编译失败"; exit 1; fi
echo "✓ 数据库类编译成功"

echo "[4/4] 编译服务器和主程序..."
javac $JAVAC_OPTS -cp "$CP" src/com/synchplay/server/*.java src/com/synchplay/*.java 2>&1
if [ $? -ne 0 ]; then echo "✗ 服务器编译失败"; exit 1; fi
echo "✓ 服务器编译成功"

echo ""
echo "✅ 编译完成！"
echo ""
echo "控制台 Demo:  java --add-modules jdk.httpserver -cp bin com.synchplay.Main"
echo "Web 服务:      bash start.sh"
