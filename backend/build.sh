#!/bin/bash
# 后端构建脚本

echo "开始构建后端..."

# 创建target目录
mkdir -p target

# 编译Java文件
echo "编译Java文件..."
javac -cp ".:sqlite-jdbc.jar" -d target *.java

# 复制依赖文件
echo "复制依赖文件..."
cp sqlite-jdbc.jar target/
cp game_lobby.db target/
cp ai-config.properties target/ 2>/dev/null || true

echo "后端构建完成！"
echo "构建产物位于: target/"
