# llm-online

五子棋多人联机对战平台 - 基于Vue3和Java21，支持AI对战

## 项目结构

```
game-lobby/
├── backend/                 # 后端目录
│   ├── src/                # 源代码目录（预留）
│   ├── target/             # 构建产物目录
│   │   ├── *.class        # 编译后的Java类文件
│   │   ├── game_lobby.db  # SQLite数据库
│   │   ├── sqlite-jdbc.jar # JDBC驱动
│   │   ├── ai-config.properties # AI配置
│   │   └── server.log     # 服务器日志
│   ├── *.java             # Java源代码
│   ├── build.sh           # 构建脚本
│   ├── start.sh           # 启动脚本
│   └── stop.sh            # 停止脚本
│
└── frontend/               # 前端目录
    ├── src/                # 源代码目录
    │   ├── App.vue        # 主应用组件
    │   ├── main.js        # 入口文件
    │   └── components/    # 组件目录
    ├── target/             # 构建产物目录
    │   ├── index.html     # 入口HTML
    │   └── assets/        # 静态资源
    ├── public/             # 公共资源
    ├── package.json       # 依赖配置
    └── vite.config.js     # Vite配置
```

## 快速开始

### 后端

```bash
cd backend

# 构建并启动
./start.sh

# 停止
./stop.sh
```

### 前端

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建
npm run build
```

## 访问地址

- 本地: http://localhost:80
- 远程: http://<服务器IP>:80

## 测试账号

| 用户名 | 密码 | 昵称 |
|--------|------|------|
| 1 | 1 | 玩家1 |
| player001 | 38472916 | 战神阿瑞斯 |
| ai-bot | 00000000 | AI助手 |

## AI配置

编辑 `backend/ai-config.properties`:

```properties
ai.api.url=https://api.openai.com/v1
ai.api.key=your-api-key
ai.model=gpt-3.5-turbo
ai.enabled=true
```

## 功能特性

- 五子棋多人联机对战
- AI对战（支持OpenAI兼容API）
- 实时聊天
- 对局记录
- 单设备登录限制
- 响应式设计（支持手机端）
