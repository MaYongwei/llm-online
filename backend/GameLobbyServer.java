import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏大厅服务器 - Java 21
 * 支持五子棋多人联机对战（HTTP轮询方式）
 */
public class GameLobbyServer {
    
    private static final int PORT = 80;
    private static String STATIC_DIR;
    private static final String DB_PATH = "game_lobby.db";
    private static HttpServer server;
    
    // 存储登录会话 (token -> userId)
    private static Map<String, Integer> sessions = new ConcurrentHashMap<>();
    
    // 存储用户当前token (userId -> token) 用于单设备登录
    private static Map<Integer, String> userTokens = new ConcurrentHashMap<>();
    
    // 存储游戏房间 (roomCode -> GameRoom)
    private static Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    
    // 等待匹配的玩家队列
    private static List<Integer> waitingPlayers = Collections.synchronizedList(new ArrayList<>());
    
    // 消息队列 (userId -> List<Message>)
    private static Map<Integer, List<Map<String, Object>>> messageQueues = new ConcurrentHashMap<>();
    
    public static void main(String[] args) throws IOException {
        System.out.println("====================================");
        System.out.println("   游戏大厅服务器启动中...");
        System.out.println("   端口: " + PORT);
        System.out.println("   Java版本: " + System.getProperty("java.version"));
        System.out.println("====================================");
        
        // 初始化静态文件目录（获取绝对路径）
        STATIC_DIR = new File("../../frontend/target").getCanonicalPath();
        System.out.println("✓ 静态文件目录: " + STATIC_DIR);
        
        initDatabase();
        
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/login", new LoginApiHandler());
        server.createContext("/api/logout", new LogoutApiHandler());
        server.createContext("/api/check-login", new CheckLoginApiHandler());
        server.createContext("/api/game/create", new CreateGameHandler());
        server.createContext("/api/game/join", new JoinGameHandler());
        server.createContext("/api/game/move", new GameMoveHandler());
        server.createContext("/api/game/status", new GameStatusHandler());
        server.createContext("/api/game/records", new GameRecordsHandler());
        server.createContext("/api/game/quick-match", new QuickMatchHandler());
        server.createContext("/api/game/poll", new PollHandler());
        server.createContext("/api/ai/chat", new AIChatHandler());
        server.createContext("/api/ai/add-bot", new AddAIBotHandler());
        server.createContext("/api/ai/status", new AIStatusHandler());
        server.createContext("/api/ai/test", new AITestHandler());
        
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("✓ 服务器启动成功!");
        System.out.println("✓ 访问地址: http://localhost:" + PORT);
        System.out.println("✓ 按 Ctrl+C 停止服务器");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n正在关闭服务器...");
            server.stop(0);
            System.out.println("服务器已关闭");
        }));
    }
    
    private static void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("SQLite JDBC驱动未找到");
            return;
        }
        System.out.println("✓ 数据库初始化成功");
    }
    
    // 游戏房间类
    static class GameRoom {
        String roomCode;
        int player1Id;
        Integer player2Id;
        int[][] board = new int[15][15];
        int currentTurn = 1;
        String status = "waiting";
        List<int[]> moves = new ArrayList<>();
        long startTime;
        Integer winnerId = null;
        
        GameRoom(String roomCode, int player1Id) {
            this.roomCode = roomCode;
            this.player1Id = player1Id;
            this.startTime = System.currentTimeMillis();
        }
        
        synchronized boolean makeMove(int x, int y, int playerId) {
            if (board[x][y] != 0) return false;
            
            int player = (playerId == player1Id) ? 1 : 2;
            if (player != currentTurn) return false;
            
            board[x][y] = player;
            moves.add(new int[]{x, y, player});
            currentTurn = (currentTurn == 1) ? 2 : 1;
            
            return true;
        }
        
        int checkWin(int x, int y) {
            int player = board[x][y];
            if (player == 0) return 0;
            
            int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};
            
            for (int[] dir : directions) {
                int count = 1;
                for (int i = 1; i < 5; i++) {
                    int nx = x + dir[0] * i;
                    int ny = y + dir[1] * i;
                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && board[nx][ny] == player) {
                        count++;
                    } else break;
                }
                for (int i = 1; i < 5; i++) {
                    int nx = x - dir[0] * i;
                    int ny = y - dir[1] * i;
                    if (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && board[nx][ny] == player) {
                        count++;
                    } else break;
                }
                if (count >= 5) return player;
            }
            return 0;
        }
    }
    
    // 添加消息到队列
    private static void addMessage(int userId, Map<String, Object> message) {
        messageQueues.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>())).add(message);
    }
    
    // 获取并清空消息队列
    private static List<Map<String, Object>> getMessages(int userId) {
        List<Map<String, Object>> messages = messageQueues.getOrDefault(userId, new ArrayList<>());
        List<Map<String, Object>> result = new ArrayList<>(messages);
        messages.clear();
        return result;
    }
    
    // 通知房间内所有玩家
    private static void notifyRoom(GameRoom room, Map<String, Object> message) {
        addMessage(room.player1Id, message);
        if (room.player2Id != null) {
            addMessage(room.player2Id, message);
        }
    }
    
    private static void saveGameResult(GameRoom room, int winnerId) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            
            String movesJson = movesToJson(room.moves);
            int duration = (int) ((System.currentTimeMillis() - room.startTime) / 1000);
            
            String sql = "INSERT INTO game_record (room_id, player1_id, player2_id, winner_id, moves, total_moves, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, 0);
            pstmt.setInt(2, room.player1Id);
            pstmt.setInt(3, room.player2Id);
            pstmt.setInt(4, winnerId);
            pstmt.setString(5, movesJson);
            pstmt.setInt(6, room.moves.size());
            pstmt.setInt(7, duration);
            pstmt.executeUpdate();
            
            pstmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static String movesToJson(List<int[]> moves) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < moves.size(); i++) {
            if (i > 0) sb.append(",");
            int[] m = moves.get(i);
            sb.append("{\"x\":").append(m[0]).append(",\"y\":").append(m[1]).append(",\"p\":").append(m[2]).append("}");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private static String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private static Map<String, Object> queryUser(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            String sql = "SELECT * FROM user WHERE username = ? AND password = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                result.put("success", true);
                result.put("id", rs.getInt("id"));
                result.put("username", rs.getString("username"));
                result.put("nickname", rs.getString("nickname"));
                result.put("level", rs.getInt("level"));
                result.put("wins", rs.getInt("wins"));
                result.put("rank", rs.getString("rank"));
            } else {
                result.put("success", false);
                result.put("message", "用户名或密码错误");
            }
            
            rs.close();
            pstmt.close();
            conn.close();
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "数据库错误: " + e.getMessage());
        }
        return result;
    }
    
    private static Map<String, Object> getUserById(int userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            String sql = "SELECT * FROM user WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                result.put("success", true);
                result.put("id", rs.getInt("id"));
                result.put("username", rs.getString("username"));
                result.put("nickname", rs.getString("nickname"));
                result.put("level", rs.getInt("level"));
                result.put("wins", rs.getInt("wins"));
                result.put("rank", rs.getString("rank"));
            } else {
                result.put("success", false);
            }
            
            rs.close();
            pstmt.close();
            conn.close();
        } catch (Exception e) {
            result.put("success", false);
        }
        return result;
    }
    
    // 静态文件处理器
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            if (path.equals("/") || path.equals("")) {
                path = "/index.html";
            }
            
            Path filePath = Paths.get(STATIC_DIR, path);
            
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                filePath = Paths.get(STATIC_DIR, "index.html");
                if (!Files.exists(filePath)) {
                    sendError(exchange, 404, "File not found");
                    return;
                }
            }
            
            String contentType = getContentType(filePath.toString());
            byte[] fileContent = Files.readAllBytes(filePath);
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            
            exchange.sendResponseHeaders(200, fileContent.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }
        }
        
        private String getContentType(String fileName) {
            if (fileName.endsWith(".html")) return "text/html; charset=utf-8";
            if (fileName.endsWith(".css")) return "text/css; charset=utf-8";
            if (fileName.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (fileName.endsWith(".json")) return "application/json; charset=utf-8";
            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".svg")) return "image/svg+xml";
            if (fileName.endsWith(".ico")) return "image/x-icon";
            if (fileName.endsWith(".woff") || fileName.endsWith(".woff2")) return "font/woff2";
            return "application/octet-stream";
        }
        
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes();
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    // 登录处理器
    static class LoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            String body = sb.toString();
            String username = extractValue(body, "username");
            String password = extractValue(body, "password");
            
            Map<String, Object> userResult = queryUser(username, password);
            String response;
            
            if ((Boolean) userResult.get("success")) {
                int userId = (Integer) userResult.get("id");
                
                // 检查是否已在其他设备登录，如果是则踢出旧设备
                String oldToken = userTokens.get(userId);
                if (oldToken != null) {
                    sessions.remove(oldToken);
                }
                
                String token = generateToken();
                sessions.put(token, userId);
                userTokens.put(userId, token);
                
                response = String.format(
                    "{\"success\":true,\"token\":\"%s\",\"user\":{\"id\":%d,\"username\":\"%s\",\"nickname\":\"%s\",\"level\":%d,\"wins\":%d,\"rank\":\"%s\"}}",
                    token,
                    userId,
                    userResult.get("username"),
                    userResult.get("nickname"),
                    userResult.get("level"),
                    userResult.get("wins"),
                    userResult.get("rank")
                );
            } else {
                response = String.format("{\"success\":false,\"message\":\"%s\"}", userResult.get("message"));
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 登出处理器
    static class LogoutApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.remove(token);
                if (userId != null) {
                    userTokens.remove(userId);
                }
            }
            
            sendJsonResponse(exchange, "{\"success\":true,\"message\":\"登出成功\"}");
        }
    }
    
    // 检查登录处理器
    static class CheckLoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    Map<String, Object> userResult = getUserById(userId);
                    if ((Boolean) userResult.get("success")) {
                        response = String.format(
                            "{\"loggedIn\":true,\"user\":{\"id\":%d,\"username\":\"%s\",\"nickname\":\"%s\",\"level\":%d,\"wins\":%d,\"rank\":\"%s\"}}",
                            userResult.get("id"),
                            userResult.get("username"),
                            userResult.get("nickname"),
                            userResult.get("level"),
                            userResult.get("wins"),
                            userResult.get("rank")
                        );
                    } else {
                        response = "{\"loggedIn\":false}";
                    }
                } else {
                    response = "{\"loggedIn\":false}";
                }
            } else {
                response = "{\"loggedIn\":false}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 创建游戏处理器
    static class CreateGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    String roomCode = generateRoomCode();
                    GameRoom room = new GameRoom(roomCode, userId);
                    gameRooms.put(roomCode, room);
                    
                    response = String.format("{\"success\":true,\"roomCode\":\"%s\",\"playerId\":%d,\"color\":1}", roomCode, userId);
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 加入游戏处理器
    static class JoinGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            String roomCode = extractValue(body, "roomCode");
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    GameRoom room = gameRooms.get(roomCode);
                    if (room != null && room.player2Id == null) {
                        room.player2Id = userId;
                        room.status = "playing";
                        
                        // 通知双方游戏开始
                        Map<String, Object> startMsg = new HashMap<>();
                        startMsg.put("type", "gameStart");
                        startMsg.put("roomCode", roomCode);
                        startMsg.put("player1", room.player1Id);
                        startMsg.put("player2", userId);
                        notifyRoom(room, startMsg);
                        
                        response = String.format("{\"success\":true,\"roomCode\":\"%s\",\"playerId\":%d,\"color\":2}", roomCode, userId);
                    } else {
                        response = "{\"success\":false,\"message\":\"房间不存在或已满\"}";
                    }
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 游戏移动处理器
    static class GameMoveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            
            String roomCode = extractValue(body, "roomCode");
            int x = Integer.parseInt(extractValue(body, "x"));
            int y = Integer.parseInt(extractValue(body, "y"));
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    GameRoom room = gameRooms.get(roomCode);
                    if (room != null && room.makeMove(x, y, userId)) {
                        int player = room.board[x][y];
                        int winner = room.checkWin(x, y);
                        
                        Map<String, Object> moveMsg = new HashMap<>();
                        moveMsg.put("type", "move");
                        moveMsg.put("x", x);
                        moveMsg.put("y", y);
                        moveMsg.put("player", player);
                        moveMsg.put("nextTurn", room.currentTurn);
                        
                        if (winner > 0) {
                            int winnerId = (winner == 1) ? room.player1Id : room.player2Id;
                            moveMsg.put("winner", winnerId);
                            room.status = "ended";
                            room.winnerId = winnerId;
                            saveGameResult(room, winnerId);
                        }
                        
                        notifyRoom(room, moveMsg);
                        
                        // 如果对手是AI-Bot且游戏未结束，AI自动下棋
                        if (winner == 0 && room.player2Id != null && room.player2Id == 11) {
                            // AI回合
                            new Thread(() -> {
                                try {
                                    Thread.sleep(500); // 延迟500ms让玩家看到自己的落子
                                    
                                    int aiColor = 2;
                                    if (room.currentTurn == aiColor && room.status.equals("playing")) {
                                        int[] aiMove = AIService.getChessMove(roomCode, room.board, aiColor);
                                        
                                        synchronized (room) {
                                            if (room.makeMove(aiMove[0], aiMove[1], 11)) {
                                                int aiWinner = room.checkWin(aiMove[0], aiMove[1]);
                                                
                                                Map<String, Object> aiMoveMsg = new HashMap<>();
                                                aiMoveMsg.put("type", "move");
                                                aiMoveMsg.put("x", aiMove[0]);
                                                aiMoveMsg.put("y", aiMove[1]);
                                                aiMoveMsg.put("player", aiColor);
                                                aiMoveMsg.put("nextTurn", room.currentTurn);
                                                
                                                if (aiWinner > 0) {
                                                    aiMoveMsg.put("winner", 11);
                                                    room.status = "ended";
                                                    room.winnerId = 11;
                                                    saveGameResult(room, 11);
                                                }
                                                
                                                notifyRoom(room, aiMoveMsg);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                        
                        response = "{\"success\":true}";
                    } else {
                        response = "{\"success\":false,\"message\":\"无效移动\"}";
                    }
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 游戏状态处理器
    static class GameStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String query = exchange.getRequestURI().getQuery();
            String roomCode = extractQueryParam(query, "roomCode");
            
            GameRoom room = gameRooms.get(roomCode);
            String response;
            
            if (room != null) {
                StringBuilder boardJson = new StringBuilder("[");
                for (int i = 0; i < 15; i++) {
                    if (i > 0) boardJson.append(",");
                    boardJson.append("[");
                    for (int j = 0; j < 15; j++) {
                        if (j > 0) boardJson.append(",");
                        boardJson.append(room.board[i][j]);
                    }
                    boardJson.append("]");
                }
                boardJson.append("]");
                
                response = String.format(
                    "{\"success\":true,\"status\":\"%s\",\"currentTurn\":%d,\"player1\":%d,\"player2\":%s,\"board\":%s,\"winner\":%s}",
                    room.status, room.currentTurn, room.player1Id, 
                    room.player2Id != null ? room.player2Id : "null",
                    boardJson.toString(),
                    room.winnerId != null ? room.winnerId : "null"
                );
            } else {
                response = "{\"success\":false,\"message\":\"房间不存在\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
        
        private String extractQueryParam(String query, String key) {
            if (query == null) return "";
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && kv[0].equals(key)) {
                    return kv[1];
                }
            }
            return "";
        }
    }
    
    // 游戏记录处理器
    static class GameRecordsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    try {
                        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                        String sql = "SELECT gr.*, u1.nickname as player1_name, u2.nickname as player2_name, w.nickname as winner_name " +
                                    "FROM game_record gr " +
                                    "LEFT JOIN user u1 ON gr.player1_id = u1.id " +
                                    "LEFT JOIN user u2 ON gr.player2_id = u2.id " +
                                    "LEFT JOIN user w ON gr.winner_id = w.id " +
                                    "WHERE gr.player1_id = ? OR gr.player2_id = ? " +
                                    "ORDER BY gr.created_at DESC LIMIT 20";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, userId);
                        pstmt.setInt(2, userId);
                        ResultSet rs = pstmt.executeQuery();
                        
                        StringBuilder records = new StringBuilder("[");
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) records.append(",");
                            records.append(String.format(
                                "{\"id\":%d,\"player1\":\"%s\",\"player2\":\"%s\",\"winner\":\"%s\",\"moves\":%d,\"duration\":%d,\"date\":\"%s\"}",
                                rs.getInt("id"),
                                rs.getString("player1_name"),
                                rs.getString("player2_name"),
                                rs.getString("winner_name") != null ? rs.getString("winner_name") : "无",
                                rs.getInt("total_moves"),
                                rs.getInt("duration_seconds"),
                                rs.getString("created_at")
                            ));
                            first = false;
                        }
                        records.append("]");
                        
                        rs.close();
                        pstmt.close();
                        conn.close();
                        
                        response = String.format("{\"success\":true,\"records\":%s}", records.toString());
                    } catch (Exception e) {
                        response = "{\"success\":false,\"message\":\"数据库错误\"}";
                    }
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 快速匹配处理器
    static class QuickMatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    // 检查等待队列
                    synchronized (waitingPlayers) {
                        if (waitingPlayers.size() > 0) {
                            Integer opponentId = null;
                            for (Integer pid : waitingPlayers) {
                                if (!pid.equals(userId)) {
                                    opponentId = pid;
                                    waitingPlayers.remove(pid);
                                    break;
                                }
                            }
                            
                            if (opponentId != null) {
                                String roomCode = generateRoomCode();
                                GameRoom room = new GameRoom(roomCode, opponentId);
                                room.player2Id = userId;
                                room.status = "playing";
                                gameRooms.put(roomCode, room);
                                
                                // 通知对手
                                Map<String, Object> startMsg = new HashMap<>();
                                startMsg.put("type", "gameStart");
                                startMsg.put("roomCode", roomCode);
                                startMsg.put("player1", opponentId);
                                startMsg.put("player2", userId);
                                addMessage(opponentId, startMsg);
                                
                                response = String.format(
                                    "{\"success\":true,\"matched\":true,\"roomCode\":\"%s\",\"opponent\":%d,\"color\":2}",
                                    roomCode, opponentId
                                );
                            } else {
                                if (!waitingPlayers.contains(userId)) {
                                    waitingPlayers.add(userId);
                                }
                                response = "{\"success\":true,\"matched\":false,\"message\":\"等待匹配中...\"}";
                            }
                        } else {
                            if (!waitingPlayers.contains(userId)) {
                                waitingPlayers.add(userId);
                            }
                            response = "{\"success\":true,\"matched\":false,\"message\":\"等待匹配中...\"}";
                        }
                    }
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // 轮询处理器
    static class PollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    List<Map<String, Object>> messages = getMessages(userId);
                    StringBuilder msgJson = new StringBuilder("[");
                    for (int i = 0; i < messages.size(); i++) {
                        if (i > 0) msgJson.append(",");
                        msgJson.append(mapToJson(messages.get(i)));
                    }
                    msgJson.append("]");
                    
                    response = String.format("{\"success\":true,\"messages\":%s}", msgJson.toString());
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append("\"").append(v).append("\"");
            } else {
                sb.append(v);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    private static String extractValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"?([^,}\"]+)\"?";
            java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = r.matcher(json);
            if (m.find()) return m.group(1).trim();
        } catch (Exception e) {}
        return "";
    }
    
    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    private static void sendJsonResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] responseBytes = response.getBytes();
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    // AI聊天处理器
    static class AIChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    InputStream is = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String body = sb.toString();
                    String message = extractValue(body, "message");
                    String sessionId = extractValue(body, "sessionId");
                    
                    String aiResponse = AIService.chat(
                        sessionId,
                        message,
                        "你是AI助手，正在与用户进行五子棋对战。你是一个友好、专业的AI，可以讨论棋局、提供策略建议，也可以闲聊。"
                    );
                    
                    response = String.format("{\"success\":true,\"response\":\"%s\"}", 
                        escapeJsonString(aiResponse));
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
        
        private String escapeJsonString(String str) {
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r");
        }
    }
    
    // 添加AI-Bot处理器
    static class AddAIBotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String response;
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Integer userId = sessions.get(token);
                
                if (userId != null) {
                    // 获取当前房间
                    InputStream is = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String body = sb.toString();
                    String roomCode = extractValue(body, "roomCode");
                    
                    GameRoom room = gameRooms.get(roomCode);
                    if (room != null && room.player2Id == null) {
                        // AI-Bot的固定用户ID为11
                        int aiBotId = 11;
                        room.player2Id = aiBotId;
                        room.status = "playing";
                        
                        // 通知玩家游戏开始
                        Map<String, Object> startMsg = new HashMap<>();
                        startMsg.put("type", "gameStart");
                        startMsg.put("roomCode", roomCode);
                        startMsg.put("player1", room.player1Id);
                        startMsg.put("player2", aiBotId);
                        startMsg.put("isAI", true);
                        addMessage(room.player1Id, startMsg);
                        
                        response = String.format(
                            "{\"success\":true,\"message\":\"AI-Bot已加入\",\"playerId\":%d,\"color\":2}",
                            aiBotId
                        );
                    } else {
                        response = "{\"success\":false,\"message\":\"房间不存在或已满\"}";
                    }
                } else {
                    response = "{\"success\":false,\"message\":\"未登录\"}";
                }
            } else {
                response = "{\"success\":false,\"message\":\"未登录\"}";
            }
            
            sendJsonResponse(exchange, response);
        }
    }
    
    // AI状态处理器
    static class AIStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            boolean enabled = AIService.isEnabled();
            String response = String.format("{\"success\":true,\"enabled\":%b}", enabled);
            sendJsonResponse(exchange, response);
        }
    }
    
    // AI测试处理器
    static class AITestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String response;
            
            if (!AIService.isEnabled()) {
                response = "{\"success\":false,\"message\":\"AI功能未启用，请检查配置文件\"}";
            } else {
                try {
                    // 发送测试消息
                    String testResponse = AIService.chat(
                        "test_session",
                        "你好，这是一个测试消息，请回复'连接成功'",
                        "你是一个测试助手，请简短回复。"
                    );
                    
                    if (testResponse != null && !testResponse.contains("失败") && !testResponse.contains("未启用")) {
                        response = String.format(
                            "{\"success\":true,\"message\":\"AI连接成功\",\"response\":\"%s\"}",
                            escapeJsonString(testResponse)
                        );
                    } else {
                        response = String.format(
                            "{\"success\":false,\"message\":\"AI响应异常: %s\"}",
                            escapeJsonString(testResponse)
                        );
                    }
                } catch (Exception e) {
                    response = String.format(
                        "{\"success\":false,\"message\":\"AI连接失败: %s\"}",
                        escapeJsonString(e.getMessage())
                    );
                }
            }
            
            sendJsonResponse(exchange, response);
        }
        
        private String escapeJsonString(String str) {
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r");
        }
    }
}
