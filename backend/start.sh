#!/bin/bash
# 后端构建并启动脚本

echo "===================================="
echo "  游戏大厅服务器 - 构建并启动"
echo "===================================="

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 1. 构建项目
echo ""
echo "[1/3] 构建项目..."
./build.sh
if [ $? -ne 0 ]; then
    echo "✗ 构建失败，启动终止"
    exit 1
fi

# 2. 停止旧服务
echo ""
echo "[2/3] 检查并停止旧服务..."
PID=$(ps aux | grep GameLobbyServer | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
    echo "检测到服务器正在运行 (PID: $PID)"
    kill -9 $PID
    sleep 1
    echo "✓ 旧服务已停止"
else
    echo "✓ 无运行中的服务"
fi

# 3. 启动新服务
echo ""
echo "[3/3] 启动服务器..."
cd target

nohup java -cp ".:sqlite-jdbc.jar" GameLobbyServer > server.log 2>&1 &

sleep 2

# 检查是否启动成功
if netstat -tlnp 2>/dev/null | grep -q ":80 "; then
    echo ""
    echo "===================================="
    echo "✓ 服务器启动成功！"
    echo "✓ 访问地址: http://localhost:80"
    echo "✓ 日志文件: target/server.log"
    echo "===================================="
else
    echo ""
    echo "✗ 服务器启动失败，请查看日志: target/server.log"
fi
