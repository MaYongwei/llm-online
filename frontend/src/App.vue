<script setup>
import { ref, onMounted, computed } from 'vue'

// 页面状态
const currentPage = ref('lobby') // lobby, game, records

// 登录状态
const isLoggedIn = ref(false)
const showLoginModal = ref(false)
const currentUser = ref(null)
const loginForm = ref({ username: '', password: '' })
const loginError = ref('')

// 游戏状态
const gameMode = ref('') // create, join, quick
const roomCode = ref('')
const joinRoomCode = ref('')
const gameBoard = ref(Array(15).fill(null).map(() => Array(15).fill(0)))
const currentTurn = ref(1)
const myColor = ref(0) // 1=黑棋, 2=白棋
const opponent = ref(null)
const gameStatus = ref('waiting') // waiting, playing, ended
const winner = ref(null)
const isMyTurn = computed(() => currentTurn.value === myColor.value)

// 对局记录
const gameRecords = ref([])

// 游戏房间数据
const rooms = ref([
  { id: 1, name: '王者对决', players: 2, maxPlayers: 4, status: 'waiting', level: '高级', icon: '⚔️' },
  { id: 2, name: '极速竞赛', players: 3, maxPlayers: 4, status: 'playing', level: '中级', icon: '🏎️' },
  { id: 3, name: '策略战场', players: 1, maxPlayers: 6, status: 'waiting', level: '专家', icon: '🎯' }
])

// 匹配状态
const isMatching = ref(false)
const matchTime = ref(0)
let matchInterval = null

// 轮询定时器
let pollInterval = null

// AI相关状态
const aiEnabled = ref(false)
const isAIGame = ref(false)
const showAIChat = ref(false)
const chatMessages = ref([])
const chatInput = ref('')
const chatSessionId = ref('')
const aiTestResult = ref('')
const isTestingAI = ref(false)

// 检查登录状态
const checkLogin = async () => {
  const token = localStorage.getItem('token')
  if (!token) return

  try {
    const res = await fetch('/api/check-login', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    const data = await res.json()
    if (data.loggedIn) {
      isLoggedIn.value = true
      currentUser.value = data.user
    }
  } catch (e) {
    console.error('检查登录失败:', e)
  }
}

// 检查AI状态
const checkAIStatus = async () => {
  try {
    const res = await fetch('/api/ai/status')
    const data = await res.json()
    if (data.success) {
      aiEnabled.value = data.enabled
    }
  } catch (e) {
    console.error('检查AI状态失败:', e)
  }
}

// 添加AI-Bot
const addAIBot = async () => {
  if (!roomCode.value) {
    alert('请先创建房间')
    return
  }
  
  try {
    const res = await fetch('/api/ai/add-bot', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ roomCode: roomCode.value })
    })
    const data = await res.json()
    
    if (data.success) {
      isAIGame.value = true
      gameStatus.value = 'playing'
      myColor.value = 1
      chatSessionId.value = roomCode.value
      alert('AI-Bot已加入对局！')
    } else {
      alert(data.message)
    }
  } catch (e) {
    alert('添加AI-Bot失败')
  }
}

// 发送聊天消息
const sendChatMessage = async () => {
  if (!chatInput.value.trim()) return
  
  const userMsg = chatInput.value
  chatMessages.value.push({ role: 'user', content: userMsg })
  chatInput.value = ''
  
  try {
    const res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: userMsg,
        sessionId: chatSessionId.value
      })
    })
    const data = await res.json()
    
    if (data.success) {
      chatMessages.value.push({ role: 'assistant', content: data.response })
    } else {
      chatMessages.value.push({ role: 'assistant', content: '抱歉，我遇到了一些问题。' })
    }
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '网络错误，请重试。' })
  }
}

// 测试AI连接
const testAIConnection = async () => {
  isTestingAI.value = true
  aiTestResult.value = '测试中...'
  
  try {
    const res = await fetch('/api/ai/test')
    const data = await res.json()
    
    if (data.success) {
      aiTestResult.value = `✓ ${data.message}\nAI回复: ${data.response}`
    } else {
      aiTestResult.value = `✗ ${data.message}`
    }
  } catch (e) {
    aiTestResult.value = `✗ 测试失败: ${e.message}`
  }
  
  isTestingAI.value = false
}

// 与AI对战
const playWithAI = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  
  try {
    // 创建房间
    const res = await fetch('/api/game/create', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    const data = await res.json()
    
    if (data.success) {
      roomCode.value = data.roomCode
      myColor.value = 1
      gameMode.value = 'ai'
      currentPage.value = 'game'
      gameStatus.value = 'waiting'
      chatSessionId.value = data.roomCode
      startPolling()
      
      // 自动添加AI-Bot
      setTimeout(async () => {
        const botRes = await fetch('/api/ai/add-bot', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ roomCode: roomCode.value })
        })
        const botData = await botRes.json()
        
        if (botData.success) {
          isAIGame.value = true
          gameStatus.value = 'playing'
        }
      }, 500)
    } else {
      alert(data.message)
    }
  } catch (e) {
    alert('创建AI对战失败')
  }
}

// 登录
const handleLogin = async () => {
  loginError.value = ''
  try {
    const res = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(loginForm.value)
    })
    const data = await res.json()
    
    if (data.success) {
      localStorage.setItem('token', data.token)
      isLoggedIn.value = true
      currentUser.value = data.user
      showLoginModal.value = false
      loginForm.value = { username: '', password: '' }
    } else {
      loginError.value = data.message || '登录失败'
    }
  } catch (e) {
    loginError.value = '网络错误'
  }
}

// 登出
const handleLogout = async () => {
  const token = localStorage.getItem('token')
  try {
    await fetch('/api/logout', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    })
  } catch (e) {
    console.error('登出失败:', e)
  }
  localStorage.removeItem('token')
  isLoggedIn.value = false
  currentUser.value = null
  currentPage.value = 'lobby'
}

// 创建游戏房间
const createGame = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  
  try {
    const res = await fetch('/api/game/create', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    const data = await res.json()
    
    if (data.success) {
      roomCode.value = data.roomCode
      myColor.value = 1
      gameMode.value = 'create'
      currentPage.value = 'game'
      gameStatus.value = 'waiting'
      startPolling()
    } else {
      alert(data.message)
    }
  } catch (e) {
    alert('创建房间失败')
  }
}

// 加入游戏房间
const joinGame = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  
  if (!joinRoomCode.value) {
    alert('请输入房间号')
    return
  }
  
  try {
    const res = await fetch('/api/game/join', {
      method: 'POST',
      headers: { 
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ roomCode: joinRoomCode.value })
    })
    const data = await res.json()
    
    if (data.success) {
      roomCode.value = joinRoomCode.value
      myColor.value = 2
      gameMode.value = 'join'
      currentPage.value = 'game'
      gameStatus.value = 'playing'
      startPolling()
    } else {
      alert(data.message)
    }
  } catch (e) {
    alert('加入房间失败')
  }
}

// 快速匹配
const quickMatch = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  
  try {
    const res = await fetch('/api/game/quick-match', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    const data = await res.json()
    
    if (data.success) {
      if (data.matched) {
        roomCode.value = data.roomCode
        myColor.value = 2
        gameMode.value = 'quick'
        currentPage.value = 'game'
        gameStatus.value = 'playing'
        initWebSocket()
      } else {
        isMatching.value = true
        matchTime.value = 0
        matchInterval = setInterval(async () => {
          matchTime.value++
          // 每3秒检查一次匹配状态
          if (matchTime.value % 3 === 0) {
            const checkRes = await fetch('/api/game/quick-match', {
              method: 'POST',
              headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
            })
            const checkData = await checkRes.json()
            if (checkData.matched) {
              clearInterval(matchInterval)
              isMatching.value = false
              roomCode.value = checkData.roomCode
              myColor.value = 2
              gameMode.value = 'quick'
              currentPage.value = 'game'
              gameStatus.value = 'playing'
              startPolling()
            }
          }
          if (matchTime.value >= 60) {
            clearInterval(matchInterval)
            isMatching.value = false
            alert('匹配超时，请重试')
          }
        }, 1000)
      }
    }
  } catch (e) {
    alert('匹配失败')
  }
}

// 取消匹配
const cancelMatch = () => {
  isMatching.value = false
  clearInterval(matchInterval)
}

// 开始轮询
const startPolling = () => {
  if (pollInterval) clearInterval(pollInterval)
  
  pollInterval = setInterval(async () => {
    try {
      const res = await fetch('/api/game/poll', {
        headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
      })
      const data = await res.json()
      
      if (data.success && data.messages) {
        data.messages.forEach(msg => handlePollMessage(msg))
      }
    } catch (e) {
      console.error('轮询失败:', e)
    }
  }, 500) // 每500ms轮询一次
}

// 处理轮询消息
const handlePollMessage = (data) => {
  if (data.type === 'gameStart') {
    gameStatus.value = 'playing'
    opponent.value = data.player1 === currentUser.value.id ? data.player2 : data.player1
  } else if (data.type === 'move') {
    gameBoard.value[data.x][data.y] = data.player
    currentTurn.value = data.nextTurn
    if (data.winner) {
      gameStatus.value = 'ended'
      winner.value = data.winner
    }
  }
}

// 下棋
const makeMove = async (x, y) => {
  if (gameStatus.value !== 'playing') return
  if (!isMyTurn.value) {
    alert('不是你的回合')
    return
  }
  if (gameBoard.value[x][y] !== 0) {
    return
  }
  
  // 本地更新
  gameBoard.value[x][y] = myColor.value
  currentTurn.value = myColor.value === 1 ? 2 : 1
  
  // 发送到服务器
  try {
    await fetch('/api/game/move', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        roomCode: roomCode.value,
        x: x,
        y: y
      })
    })
  } catch (e) {
    console.error('下棋失败:', e)
  }
  
  // 检查胜利（简化版，实际由服务器判断）
  if (checkWin(x, y, myColor.value)) {
    gameStatus.value = 'ended'
    winner.value = currentUser.value.id
  }
}

// 检查胜利
const checkWin = (x, y, player) => {
  const directions = [[1,0], [0,1], [1,1], [1,-1]]
  
  for (const [dx, dy] of directions) {
    let count = 1
    for (let i = 1; i < 5; i++) {
      const nx = x + dx * i
      const ny = y + dy * i
      if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && gameBoard.value[nx][ny] === player) {
        count++
      } else break
    }
    for (let i = 1; i < 5; i++) {
      const nx = x - dx * i
      const ny = y - dy * i
      if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && gameBoard.value[nx][ny] === player) {
        count++
      } else break
    }
    if (count >= 5) return true
  }
  return false
}

// 返回大厅
const backToLobby = () => {
  currentPage.value = 'lobby'
  gameBoard.value = Array(15).fill(null).map(() => Array(15).fill(0))
  currentTurn.value = 1
  myColor.value = 0
  roomCode.value = ''
  gameStatus.value = 'waiting'
  winner.value = null
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

// 查看对局记录
const viewRecords = async () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  
  try {
    const res = await fetch('/api/game/records', {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    const data = await res.json()
    
    if (data.success) {
      gameRecords.value = data.records
      currentPage.value = 'records'
    } else {
      alert(data.message)
    }
  } catch (e) {
    alert('获取记录失败')
  }
}

// 获取状态颜色
const getStatusColor = (status) => {
  return status === 'waiting' ? '#00ff88' : '#ff00ff'
}

// 获取等级颜色
const getLevelColor = (level) => {
  const colors = {
    '初级': '#00d4ff',
    '中级': '#00ff88',
    '高级': '#ff00ff',
    '专家': '#ff3366'
  }
  return colors[level] || '#00ff88'
}

onMounted(() => {
  checkLogin()
  checkAIStatus()
})
</script>

<template>
  <div class="game-lobby">
    <div class="grid-background"></div>
    
    <!-- 登录模态框 -->
    <div v-if="showLoginModal" class="login-modal-overlay" @click.self="showLoginModal = false">
      <div class="login-modal">
        <div class="modal-header">
          <h2 class="modal-title">用户登录</h2>
          <button class="close-btn" @click="showLoginModal = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>用户名</label>
            <input v-model="loginForm.username" type="text" placeholder="请输入用户名" @keyup.enter="handleLogin" />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input v-model="loginForm.password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
          </div>
          <div v-if="loginError" class="error-message">{{ loginError }}</div>
          <button class="cyber-button primary login-btn" @click="handleLogin">
            <span class="button-text">登录</span>
          </button>
          <div class="login-hint">
            <p>测试账号: player001 / pass123456</p>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 头部 -->
    <header class="header">
      <div class="logo" @click="currentPage = 'lobby'" style="cursor: pointer">
        <span class="glitch" data-text="GAME ARENA">GAME ARENA</span>
      </div>
      
      <div class="header-right">
        <div v-if="isLoggedIn" class="player-info">
          <div class="player-avatar">
            <div class="avatar-ring"></div>
            <span class="avatar-icon">👤</span>
          </div>
          <div class="player-details">
            <div class="player-name">{{ currentUser?.nickname }}</div>
            <div class="player-stats">
              <span class="stat">Lv.{{ currentUser?.level }}</span>
              <span class="stat rank">{{ currentUser?.rank }}</span>
              <span class="stat">{{ currentUser?.wins }}胜</span>
            </div>
          </div>
          <button class="logout-btn" @click="handleLogout">登出</button>
        </div>
        <button v-else class="cyber-button login-header-btn" @click="showLoginModal = true">
          <span class="button-text">登录</span>
        </button>
      </div>
    </header>

    <!-- 大厅页面 -->
    <main v-if="currentPage === 'lobby'" class="main-content">
      <aside class="side-panel">
        <div class="panel-section">
          <h3 class="panel-title">五子棋对战</h3>
          <button class="cyber-button primary" @click="createGame">
            <span class="button-text">创建房间</span>
          </button>
          <button class="cyber-button secondary" @click="quickMatch">
            <span class="button-text">{{ isMatching ? `匹配中... ${matchTime}s` : '快速匹配' }}</span>
          </button>
          <button v-if="isMatching" class="cyber-button" @click="cancelMatch">
            <span class="button-text">取消匹配</span>
          </button>
          <button v-if="aiEnabled" class="cyber-button ai-btn" @click="playWithAI">
            <span class="button-text">🤖 与AI对战</span>
          </button>
          <button class="cyber-button accent" @click="viewRecords">
            <span class="button-text">对局记录</span>
          </button>
        </div>

        <div v-if="aiEnabled" class="panel-section ai-section">
          <h3 class="panel-title">AI设置</h3>
          <div class="ai-status">
            <span class="status-indicator online"></span>
            <span>AI服务: 已启用</span>
          </div>
          <button 
            class="cyber-button test-btn" 
            @click="testAIConnection"
            :disabled="isTestingAI"
          >
            <span class="button-text">{{ isTestingAI ? '测试中...' : '测试AI连接' }}</span>
          </button>
          <div v-if="aiTestResult" class="test-result" :class="{ success: aiTestResult.startsWith('✓'), error: aiTestResult.startsWith('✗') }">
            {{ aiTestResult }}
          </div>
        </div>

        <div class="panel-section">
          <h3 class="panel-title">加入房间</h3>
          <input 
            v-model="joinRoomCode" 
            type="text" 
            placeholder="输入房间号"
            class="room-input"
            @keyup.enter="joinGame"
          />
          <button class="cyber-button" @click="joinGame">
            <span class="button-text">加入</span>
          </button>
        </div>

        <div class="panel-section">
          <h3 class="panel-title">在线玩家</h3>
          <div class="online-count">
            <span class="count-number">1,234</span>
            <span class="count-label">在线</span>
          </div>
        </div>
      </aside>

      <section class="rooms-section">
        <div class="section-header">
          <h2 class="section-title">游戏大厅</h2>
        </div>
        
        <div class="welcome-card">
          <h3>欢迎来到五子棋对战平台</h3>
          <p>创建房间邀请好友，或快速匹配开始对战！</p>
          <div class="game-rules">
            <h4>游戏规则</h4>
            <ul>
              <li>黑棋先行，双方轮流落子</li>
              <li>先连成五子者获胜</li>
              <li>支持横、竖、斜四个方向</li>
            </ul>
          </div>
        </div>
      </section>
    </main>

    <!-- 游戏页面 -->
    <main v-if="currentPage === 'game'" class="game-content">
      <div class="game-header">
        <button class="cyber-button" @click="backToLobby">
          <span class="button-text">返回大厅</span>
        </button>
        <div class="room-info">
          <span>房间号: {{ roomCode }}</span>
          <span v-if="gameStatus === 'waiting'" class="status-waiting">等待对手加入...</span>
          <span v-else-if="gameStatus === 'playing'" :class="isMyTurn ? 'status-my-turn' : 'status-opponent-turn'">
            {{ isMyTurn ? '轮到你下棋' : '对手思考中' }}
          </span>
          <span v-else class="status-ended">
            {{ winner === currentUser?.id ? '你赢了！' : '你输了' }}
          </span>
        </div>
        <div class="player-color">
          你的棋子: {{ myColor === 1 ? '⚫ 黑棋' : '⚪ 白棋' }}
        </div>
      </div>

      <div class="game-area">
        <div class="game-board-container">
          <div class="game-board">
            <div v-for="(row, y) in gameBoard" :key="y" class="board-row">
              <div 
                v-for="(cell, x) in row" 
                :key="x" 
                class="board-cell"
                :class="{ 'has-piece': cell !== 0 }"
                @click="makeMove(y, x)"
              >
                <div v-if="cell === 1" class="piece black"></div>
                <div v-else-if="cell === 2" class="piece white"></div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- AI聊天框 -->
        <div v-if="isAIGame && aiEnabled" class="ai-chat-panel">
          <div class="chat-header">
            <h3>🤖 AI助手</h3>
            <button class="toggle-chat-btn" @click="showAIChat = !showAIChat">
              {{ showAIChat ? '收起' : '展开' }}
            </button>
          </div>
          <div v-show="showAIChat" class="chat-body">
            <div class="chat-messages">
              <div 
                v-for="(msg, idx) in chatMessages" 
                :key="idx" 
                :class="['chat-message', msg.role]"
              >
                {{ msg.content }}
              </div>
            </div>
            <div class="chat-input-area">
              <input 
                v-model="chatInput" 
                @keyup.enter="sendChatMessage"
                placeholder="与AI对话..."
                class="chat-input"
              />
              <button class="send-btn" @click="sendChatMessage">发送</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="gameStatus === 'waiting'" class="waiting-overlay">
        <div class="waiting-box">
          <h3>等待对手加入</h3>
          <p>房间号: <span class="room-code-display">{{ roomCode }}</span></p>
          <p>分享房间号给好友，或等待其他玩家加入</p>
          <button v-if="aiEnabled" class="cyber-button primary" @click="addAIBot" style="margin-top: 1rem">
            <span class="button-text">🤖 添加AI-Bot</span>
          </button>
        </div>
      </div>

      <div v-if="gameStatus === 'ended'" class="result-overlay">
        <div class="result-box">
          <h2>{{ winner === currentUser?.id ? '🎉 恭喜获胜！' : '😢 很遗憾，再接再厉！' }}</h2>
          <button class="cyber-button primary" @click="backToLobby">
            <span class="button-text">返回大厅</span>
          </button>
        </div>
      </div>
    </main>

    <!-- 对局记录页面 -->
    <main v-if="currentPage === 'records'" class="records-content">
      <div class="records-header">
        <button class="cyber-button" @click="currentPage = 'lobby'">
          <span class="button-text">返回大厅</span>
        </button>
        <h2 class="section-title">对局记录</h2>
      </div>

      <div class="records-list">
        <div v-if="gameRecords.length === 0" class="no-records">
          <p>暂无对局记录</p>
        </div>
        <div v-else v-for="record in gameRecords" :key="record.id" class="record-card">
          <div class="record-header">
            <span class="record-id">#{{ record.id }}</span>
            <span class="record-date">{{ record.date }}</span>
          </div>
          <div class="record-players">
            <span class="player-name-record" :class="{ winner: record.winner === record.player1 }">
              {{ record.player1 }}
            </span>
            <span class="vs">VS</span>
            <span class="player-name-record" :class="{ winner: record.winner === record.player2 }">
              {{ record.player2 }}
            </span>
          </div>
          <div class="record-info">
            <span>回合数: {{ record.moves }}</span>
            <span>时长: {{ Math.floor(record.duration / 60) }}分{{ record.duration % 60 }}秒</span>
            <span class="winner-label">胜者: {{ record.winner }}</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部状态栏 -->
    <footer class="status-bar">
      <div class="status-item">
        <span class="status-dot"></span>
        <span>服务器状态: 正常</span>
      </div>
      <div class="status-item">
        <span>延迟: 23ms</span>
      </div>
    </footer>
  </div>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700;900&family=JetBrains+Mono:wght@400;500;700&display=swap');

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'JetBrains Mono', monospace;
  background: #0a0a0f;
  color: #e0e0e0;
  overflow-x: hidden;
}

.game-lobby {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
}

.grid-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: 
    linear-gradient(rgba(0, 255, 136, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 255, 136, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  pointer-events: none;
  z-index: 0;
}

/* 登录模态框 */
.login-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(5px);
}

.login-modal {
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 2px solid #00ff88;
  border-radius: 8px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 0 30px rgba(0, 255, 136, 0.3);
  animation: modal-appear 0.3s ease-out;
}

@keyframes modal-appear {
  from { opacity: 0; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1); }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid #2a2a3a;
}

.modal-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 1.25rem;
  color: #00ff88;
  text-shadow: 0 0 10px #00ff88;
}

.close-btn {
  background: transparent;
  border: none;
  color: #6b7280;
  font-size: 1.5rem;
  cursor: pointer;
  transition: color 0.2s;
}

.close-btn:hover { color: #ff3366; }

.modal-body { padding: 1.5rem; }

.form-group { margin-bottom: 1rem; }

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
  color: #00ff88;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.form-group input {
  width: 100%;
  padding: 0.75rem 1rem;
  background: #0a0a0f;
  border: 1px solid #2a2a3a;
  border-radius: 4px;
  color: #e0e0e0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.875rem;
  transition: all 0.2s;
}

.form-group input:focus {
  outline: none;
  border-color: #00ff88;
  box-shadow: 0 0 10px rgba(0, 255, 136, 0.3);
}

.error-message {
  color: #ff3366;
  font-size: 0.75rem;
  margin-bottom: 1rem;
  text-align: center;
}

.login-btn { width: 100%; margin-bottom: 1rem; }

.login-hint {
  text-align: center;
  font-size: 0.75rem;
  color: #6b7280;
}

/* 头部 */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem 2rem;
  background: linear-gradient(180deg, #12121a 0%, transparent 100%);
  border-bottom: 1px solid #2a2a3a;
  position: relative;
  z-index: 10;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.logo {
  font-family: 'Orbitron', sans-serif;
  font-size: 2rem;
  font-weight: 900;
  letter-spacing: 0.1em;
}

.glitch {
  position: relative;
  color: #00ff88;
  text-shadow: 0 0 10px #00ff88, 0 0 20px #00ff88, 0 0 30px #00ff88;
  animation: glitch 3s infinite;
}

@keyframes glitch {
  0%, 100% { transform: translate(0); }
  20% { transform: translate(-2px, 2px); }
  40% { transform: translate(2px, -2px); }
  60% { transform: translate(-1px, 1px); }
  80% { transform: translate(1px, -1px); }
}

.player-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.player-avatar {
  position: relative;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
}

.avatar-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 2px solid #00ff88;
  border-radius: 50%;
  animation: rotate 3s linear infinite;
  box-shadow: 0 0 10px #00ff88;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.player-details {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.player-name {
  font-family: 'Orbitron', sans-serif;
  font-weight: 700;
  color: #00ff88;
  text-shadow: 0 0 5px #00ff88;
}

.player-stats {
  display: flex;
  gap: 0.5rem;
  font-size: 0.75rem;
}

.stat { color: #6b7280; }
.stat.rank { color: #00d4ff; text-shadow: 0 0 5px #00d4ff; }

.logout-btn {
  padding: 0.5rem 1rem;
  background: transparent;
  border: 1px solid #ff3366;
  border-radius: 4px;
  color: #ff3366;
  font-family: 'Orbitron', sans-serif;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;
}

.logout-btn:hover {
  background: #ff3366;
  color: #0a0a0f;
  box-shadow: 0 0 10px rgba(255, 51, 102, 0.5);
}

.login-header-btn {
  padding: 0.5rem 1.5rem;
  border-color: #00ff88;
  color: #00ff88;
}

/* 主要内容 */
.main-content {
  flex: 1;
  display: flex;
  gap: 2rem;
  padding: 2rem;
  position: relative;
  z-index: 1;
}

.side-panel {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.panel-section {
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 1px solid #2a2a3a;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 0 20px rgba(0, 255, 136, 0.1);
}

.panel-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 0.875rem;
  color: #00ff88;
  margin-bottom: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.room-input {
  width: 100%;
  padding: 0.75rem;
  margin-bottom: 0.75rem;
  background: #0a0a0f;
  border: 1px solid #2a2a3a;
  border-radius: 4px;
  color: #e0e0e0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.875rem;
}

.room-input:focus {
  outline: none;
  border-color: #00ff88;
}

/* 赛博按钮 */
.cyber-button {
  width: 100%;
  padding: 0.875rem 1.5rem;
  margin-bottom: 0.75rem;
  background: transparent;
  border: 2px solid #2a2a3a;
  border-radius: 4px;
  color: #e0e0e0;
  font-family: 'Orbitron', sans-serif;
  font-size: 0.875rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
}

.cyber-button:last-child { margin-bottom: 0; }

.cyber-button.primary {
  background: linear-gradient(135deg, #00ff88 0%, #00d4ff 100%);
  border-color: #00ff88;
  color: #0a0a0f;
  box-shadow: 0 0 20px rgba(0, 255, 136, 0.5);
}

.cyber-button.secondary {
  border-color: #ff00ff;
  color: #ff00ff;
  box-shadow: 0 0 10px rgba(255, 0, 255, 0.3);
}

.cyber-button.accent {
  border-color: #00d4ff;
  color: #00d4ff;
  box-shadow: 0 0 10px rgba(0, 212, 255, 0.3);
}

.cyber-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 0 30px rgba(0, 255, 136, 0.8);
}

.online-count {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.count-number {
  font-family: 'Orbitron', sans-serif;
  font-size: 2rem;
  font-weight: 900;
  color: #00ff88;
  text-shadow: 0 0 10px #00ff88;
}

.count-label { font-size: 0.875rem; color: #6b7280; }

.rooms-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 1.5rem;
  font-weight: 900;
  color: #00ff88;
  text-shadow: 0 0 10px #00ff88;
  letter-spacing: 0.05em;
}

.welcome-card {
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 1px solid #2a2a3a;
  border-radius: 8px;
  padding: 2rem;
  text-align: center;
}

.welcome-card h3 {
  font-family: 'Orbitron', sans-serif;
  color: #00ff88;
  margin-bottom: 1rem;
}

.welcome-card p { color: #6b7280; margin-bottom: 1.5rem; }

.game-rules {
  text-align: left;
  background: #0a0a0f;
  padding: 1rem;
  border-radius: 4px;
  margin-top: 1rem;
}

.game-rules h4 {
  color: #00ff88;
  margin-bottom: 0.5rem;
}

.game-rules ul {
  list-style: none;
  padding-left: 1rem;
}

.game-rules li {
  color: #6b7280;
  margin: 0.5rem 0;
}

.game-rules li::before {
  content: '▸ ';
  color: #00ff88;
}

/* 游戏页面 */
.game-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
  position: relative;
  z-index: 1;
}

.game-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: 800px;
  margin-bottom: 2rem;
}

.room-info {
  display: flex;
  gap: 1rem;
  align-items: center;
  font-family: 'Orbitron', sans-serif;
}

.status-waiting { color: #ff00ff; }
.status-my-turn { color: #00ff88; font-weight: bold; }
.status-opponent-turn { color: #6b7280; }
.status-ended { color: #ff3366; font-weight: bold; }

.game-area {
  display: flex;
  gap: 1.5rem;
  align-items: flex-start;
}

.ai-chat-panel {
  width: 300px;
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 1px solid #00ff88;
  border-radius: 8px;
  box-shadow: 0 0 20px rgba(0, 255, 136, 0.2);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #2a2a3a;
}

.chat-header h3 {
  font-family: 'Orbitron', sans-serif;
  color: #00ff88;
  font-size: 0.875rem;
}

.toggle-chat-btn {
  background: transparent;
  border: 1px solid #00ff88;
  color: #00ff88;
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
}

.chat-body {
  display: flex;
  flex-direction: column;
  height: 400px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.chat-message {
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  font-size: 0.875rem;
  line-height: 1.4;
}

.chat-message.user {
  background: #00ff88;
  color: #0a0a0f;
  align-self: flex-end;
  max-width: 80%;
}

.chat-message.assistant {
  background: #2a2a3a;
  color: #e0e0e0;
  align-self: flex-start;
  max-width: 80%;
}

.chat-input-area {
  display: flex;
  gap: 0.5rem;
  padding: 1rem;
  border-top: 1px solid #2a2a3a;
}

.chat-input {
  flex: 1;
  padding: 0.5rem;
  background: #0a0a0f;
  border: 1px solid #2a2a3a;
  border-radius: 4px;
  color: #e0e0e0;
  font-size: 0.875rem;
}

.chat-input:focus {
  outline: none;
  border-color: #00ff88;
}

.send-btn {
  padding: 0.5rem 1rem;
  background: #00ff88;
  border: none;
  border-radius: 4px;
  color: #0a0a0f;
  font-weight: bold;
  cursor: pointer;
}

.send-btn:hover {
  background: #00d4ff;
}

.ai-btn {
  border-color: #ff00ff !important;
  color: #ff00ff !important;
  box-shadow: 0 0 10px rgba(255, 0, 255, 0.3) !important;
}

.ai-section {
  border-color: #ff00ff !important;
}

.ai-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-indicator.online {
  background: #00ff88;
  box-shadow: 0 0 10px #00ff88;
  animation: blink 2s infinite;
}

.test-btn {
  border-color: #00d4ff !important;
  color: #00d4ff !important;
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.test-result {
  margin-top: 1rem;
  padding: 0.75rem;
  border-radius: 4px;
  font-size: 0.75rem;
  white-space: pre-wrap;
  word-break: break-all;
}

.test-result.success {
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid #00ff88;
  color: #00ff88;
}

.test-result.error {
  background: rgba(255, 51, 102, 0.1);
  border: 1px solid #ff3366;
  color: #ff3366;
}

.player-color {
  font-family: 'Orbitron', sans-serif;
  color: #00d4ff;
}

.game-board-container {
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 2px solid #00ff88;
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 0 30px rgba(0, 255, 136, 0.3);
}

.game-board {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.board-row {
  display: flex;
}

.board-cell {
  width: 32px;
  height: 32px;
  background: #d4a574;
  border: 1px solid #8b6914;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}

.board-cell:hover {
  background: #e5b884;
}

.piece {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
}

.piece.black {
  background: radial-gradient(circle at 30% 30%, #4a4a4a, #000);
}

.piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ccc);
}

.waiting-overlay, .result-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.waiting-box, .result-box {
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 2px solid #00ff88;
  border-radius: 8px;
  padding: 2rem;
  text-align: center;
  box-shadow: 0 0 30px rgba(0, 255, 136, 0.3);
}

.waiting-box h3, .result-box h2 {
  font-family: 'Orbitron', sans-serif;
  color: #00ff88;
  margin-bottom: 1rem;
}

.room-code-display {
  font-size: 1.5rem;
  color: #00d4ff;
  font-weight: bold;
}

/* 对局记录页面 */
.records-content {
  flex: 1;
  padding: 2rem;
  position: relative;
  z-index: 1;
}

.records-header {
  display: flex;
  align-items: center;
  gap: 2rem;
  margin-bottom: 2rem;
}

.records-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}

.record-card {
  background: linear-gradient(135deg, #12121a 0%, #1c1c2e 100%);
  border: 1px solid #2a2a3a;
  border-radius: 8px;
  padding: 1.5rem;
  transition: all 0.2s;
}

.record-card:hover {
  border-color: #00ff88;
  box-shadow: 0 0 20px rgba(0, 255, 136, 0.2);
}

.record-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 1rem;
  font-size: 0.75rem;
  color: #6b7280;
}

.record-id { color: #00ff88; font-weight: bold; }

.record-players {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  font-family: 'Orbitron', sans-serif;
}

.player-name-record { font-size: 0.875rem; }
.player-name-record.winner { color: #00ff88; }
.vs { color: #6b7280; font-size: 0.75rem; }

.record-info {
  display: flex;
  gap: 1rem;
  font-size: 0.75rem;
  color: #6b7280;
}

.winner-label { color: #ff00ff; }

.no-records {
  text-align: center;
  color: #6b7280;
  padding: 3rem;
}

/* 状态栏 */
.status-bar {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 1rem 2rem;
  background: linear-gradient(0deg, #12121a 0%, transparent 100%);
  border-top: 1px solid #2a2a3a;
  font-size: 0.75rem;
  color: #6b7280;
  position: relative;
  z-index: 10;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #00ff88;
  border-radius: 50%;
  box-shadow: 0 0 10px #00ff88;
  animation: blink 2s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header { flex-direction: column; gap: 1rem; padding: 1rem; }
  .logo { font-size: 1.5rem; }
  .main-content { flex-direction: column; padding: 1rem; gap: 1rem; }
  .side-panel { width: 100%; }
  .game-board-container { padding: 0.5rem; }
  .board-cell { width: 24px; height: 24px; }
  .piece { width: 20px; height: 20px; }
  .game-header { flex-direction: column; gap: 1rem; }
  .records-header { flex-direction: column; align-items: flex-start; }
}
</style>
