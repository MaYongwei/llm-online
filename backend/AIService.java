import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI服务类 - 处理与大模型API的交互
 */
public class AIService {
    
    private static String apiUrl;
    private static String apiKey;
    private static String model;
    private static int maxTokens;
    private static double temperature;
    private static boolean enabled;
    
    // 对话历史缓存 (sessionId -> messages)
    private static Map<String, List<Map<String, String>>> conversationHistory = new HashMap<>();
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        try {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream("ai-config.properties");
            props.load(fis);
            fis.close();
            
            apiUrl = props.getProperty("ai.api.url", "https://api.openai.com/v1");
            apiKey = props.getProperty("ai.api.key", "");
            model = props.getProperty("ai.model", "gpt-3.5-turbo");
            maxTokens = Integer.parseInt(props.getProperty("ai.max.tokens", "2000"));
            temperature = Double.parseDouble(props.getProperty("ai.temperature", "0.7"));
            enabled = Boolean.parseBoolean(props.getProperty("ai.enabled", "false"));
            
            System.out.println("✓ AI配置加载成功 (enabled: " + enabled + ")");
        } catch (Exception e) {
            System.out.println("⚠ AI配置加载失败: " + e.getMessage());
            enabled = false;
        }
    }
    
    public static boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isEmpty() && !apiKey.equals("sk-your-api-key-here");
    }
    
    /**
     * 发送聊天消息
     */
    public static String chat(String sessionId, String userMessage, String systemPrompt) {
        if (!isEnabled()) {
            return "AI功能未启用，请在ai-config.properties中配置API Key";
        }
        
        try {
            // 获取或创建对话历史
            List<Map<String, String>> messages = conversationHistory.computeIfAbsent(
                sessionId, k -> new ArrayList<>()
            );
            
            // 如果是第一次对话，添加系统提示
            if (messages.isEmpty() && systemPrompt != null) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            
            // 添加用户消息
            messages.add(Map.of("role", "user", "content", userMessage));
            
            // 调用API
            String response = callChatAPI(messages);
            
            // 添加助手回复到历史
            if (response != null) {
                messages.add(Map.of("role", "assistant", "content", response));
            }
            
            return response;
        } catch (Exception e) {
            return "AI调用失败: " + e.getMessage();
        }
    }
    
    /**
     * 获取AI下棋建议
     */
    public static int[] getChessMove(String sessionId, int[][] board, int myColor) {
        if (!isEnabled()) {
            // 返回随机位置
            Random rand = new Random();
            int x, y;
            do {
                x = rand.nextInt(15);
                y = rand.nextInt(15);
            } while (board[x][y] != 0);
            return new int[]{x, y};
        }
        
        String boardStr = boardToString(board);
        String colorStr = myColor == 1 ? "黑棋" : "白棋";
        
        String prompt = String.format(
            "你是一个五子棋AI。当前棋盘状态：\n%s\n" +
            "你是%s。请分析棋盘并给出你的下一步落子位置。\n" +
            "只需要返回坐标，格式为：x,y（例如：7,7）\n" +
            "不要有任何其他解释或说明。",
            boardStr, colorStr
        );
        
        String response = chat(sessionId + "_chess", prompt, 
            "你是一个专业的五子棋AI，擅长策略和计算。");
        
        // 解析响应
        try {
            String[] parts = response.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            if (x >= 0 && x < 15 && y >= 0 && y < 15 && board[x][y] == 0) {
                return new int[]{x, y};
            }
        } catch (Exception e) {
            // 解析失败，使用简单策略
        }
        
        // 备用：寻找空位
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 15; j++) {
                if (board[i][j] == 0) {
                    return new int[]{i, j};
                }
            }
        }
        
        return new int[]{7, 7};
    }
    
    /**
     * 清除对话历史
     */
    public static void clearHistory(String sessionId) {
        conversationHistory.remove(sessionId);
    }
    
    private static String callChatAPI(List<Map<String, String>> messages) throws Exception {
        URL url = new URL(apiUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        
        // 构建请求体
        StringBuilder msgJson = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) msgJson.append(",");
            Map<String, String> msg = messages.get(i);
            msgJson.append(String.format(
                "{\"role\":\"%s\",\"content\":\"%s\"}",
                msg.get("role"),
                escapeJson(msg.get("content"))
            ));
        }
        msgJson.append("]");
        
        String requestBody = String.format(
            "{\"model\":\"%s\",\"messages\":%s,\"max_tokens\":%d,\"temperature\":%.1f}",
            model, msgJson.toString(), maxTokens, temperature
        );
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                error.append(line);
            }
            br.close();
            throw new Exception("API错误: " + error.toString());
        }
        
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        
        // 解析响应
        return parseChatResponse(response.toString());
    }
    
    private static String parseChatResponse(String response) {
        try {
            // 简单解析JSON获取content
            int contentStart = response.indexOf("\"content\":\"");
            if (contentStart == -1) return "解析失败";
            
            contentStart += 11;
            int contentEnd = response.indexOf("\"", contentStart);
            
            // 处理转义字符
            String content = response.substring(contentStart, contentEnd);
            return unescapeJson(content);
        } catch (Exception e) {
            return "解析失败: " + e.getMessage();
        }
    }
    
    private static String boardToString(int[][] board) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ");
        for (int i = 0; i < 15; i++) {
            sb.append(String.format("%2d", i));
        }
        sb.append("\n");
        
        for (int i = 0; i < 15; i++) {
            sb.append(String.format("%2d ", i));
            for (int j = 0; j < 15; j++) {
                if (board[i][j] == 0) sb.append("· ");
                else if (board[i][j] == 1) sb.append("● ");
                else sb.append("○ ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private static String unescapeJson(String str) {
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }
}
