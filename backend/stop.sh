#!/bin/bash
# 后端停止脚本

echo "停止游戏大厅服务器..."

# 查找并停止服务器进程
PID=$(ps aux | grep GameLobbyServer | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
    kill -9 $PID
    echo "✓ 服务器已停止 (PID: $PID)"
else
    echo "服务器未运行"
fi
