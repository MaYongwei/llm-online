import java.util.*;

/**
 * 五子棋规则AI - 基于极大极小值算法+Alpha-Beta剪枝
 * 
 * 算法特点：
 * 1. Alpha-Beta剪枝搜索
 * 2. 启发式评估函数
 * 3. 威胁空间搜索
 * 4. 活棋型识别
 */
public class GomokuRuleAI implements AIPlayer {
    
    private static final int BOARD_SIZE = 15;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    
    private int difficulty = 3; // 默认中等难度
    private String name = "规则AI";
    
    // 评分权重
    private static final int FIVE = 100000;      // 连五
    private static final int LIVE_FOUR = 10000;  // 活四
    private static final int RUSH_FOUR = 1000;   // 冲四
    private static final int LIVE_THREE = 1000;  // 活三
    private static final int SLEEP_THREE = 100;  // 眠三
    private static final int LIVE_TWO = 100;     // 活二
    private static final int SLEEP_TWO = 10;     // 眠二
    
    // 方向向量：横、竖、主对角线、副对角线
    private static final int[][] DIRECTIONS = {
        {0, 1},   // 横向
        {1, 0},   // 纵向
        {1, 1},   // 主对角线
        {1, -1}   // 副对角线
    };
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getType() {
        return "rule";
    }
    
    @Override
    public String calculateMove(String gameState) {
        // 解析游戏状态
        int[][] board = parseBoard(gameState);
        int myColor = parseMyColor(gameState);
        
        // 计算最佳移动
        int[] move = findBestMove(board, myColor);
        
        if (move == null) {
            // 如果没有找到移动，返回中心点
            return "{\"x\":7,\"y\":7}";
        }
        
        return String.format("{\"x\":%d,\"y\":%d}", move[0], move[1]);
    }
    
    @Override
    public void setDifficulty(int level) {
        this.difficulty = Math.max(1, Math.min(5, level));
    }
    
    @Override
    public int getDifficulty() {
        return difficulty;
    }
    
    /**
     * 解析棋盘状态
     */
    private int[][] parseBoard(String gameState) {
        int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
        
        try {
            // 简单的JSON解析
            String content = gameState;
            if (content.contains("\"board\":[")) {
                int start = content.indexOf("\"board\":[") + 9;
                int end = content.indexOf("]", start);
                String boardStr = content.substring(start, end);
                
                String[] rows = boardStr.split("\\],\\[");
                for (int i = 0; i < rows.length && i < BOARD_SIZE; i++) {
                    String row = rows[i].replace("[", "").replace("]", "");
                    String[] cells = row.split(",");
                    for (int j = 0; j < cells.length && j < BOARD_SIZE; j++) {
                        board[i][j] = Integer.parseInt(cells[j].trim());
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，返回空棋盘
        }
        
        return board;
    }
    
    /**
     * 解析我的颜色
     */
    private int parseMyColor(String gameState) {
        try {
            if (gameState.contains("\"myColor\":")) {
                int start = gameState.indexOf("\"myColor\":") + 10;
                int end = start + 1;
                while (end < gameState.length() && Character.isDigit(gameState.charAt(end))) {
                    end++;
                }
                return Integer.parseInt(gameState.substring(start, end).trim());
            }
        } catch (Exception e) {
        }
        return BLACK; // 默认黑棋
    }
    
    /**
     * 找到最佳移动位置
     */
    private int[] findBestMove(int[][] board, int myColor) {
        // 1. 检查是否能直接获胜
        int[] winMove = findWinningMove(board, myColor);
        if (winMove != null) return winMove;
        
        // 2. 检查是否需要防守对方的获胜
        int oppColor = (myColor == BLACK) ? WHITE : BLACK;
        int[] blockMove = findWinningMove(board, oppColor);
        if (blockMove != null) return blockMove;
        
        // 3. 使用Alpha-Beta搜索找最佳位置
        int depth = 2 + difficulty; // 搜索深度随难度增加
        return alphaBetaSearch(board, myColor, depth);
    }
    
    /**
     * 寻找获胜的一步
     */
    private int[] findWinningMove(int[][] board, int color) {
        List<int[]> candidates = getCandidateMoves(board);
        
        for (int[] move : candidates) {
            board[move[0]][move[1]] = color;
            if (checkWin(board, move[0], move[1], color)) {
                board[move[0]][move[1]] = EMPTY;
                return move;
            }
            board[move[0]][move[1]] = EMPTY;
        }
        
        return null;
    }
    
    /**
     * Alpha-Beta搜索
     */
    private int[] alphaBetaSearch(int[][] board, int myColor, int depth) {
        List<int[]> candidates = getCandidateMoves(board);
        
        if (candidates.isEmpty()) {
            return new int[]{7, 7}; // 返回中心点
        }
        
        // 对候选位置评分并排序
        candidates.sort((a, b) -> {
            int scoreA = evaluatePosition(board, a[0], a[1], myColor);
            int scoreB = evaluatePosition(board, b[0], b[1], myColor);
            return scoreB - scoreA;
        });
        
        // 只考虑前N个最佳位置
        int maxCandidates = Math.min(candidates.size(), 10 + difficulty * 5);
        candidates = candidates.subList(0, maxCandidates);
        
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = candidates.get(0);
        
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        
        for (int[] move : candidates) {
            board[move[0]][move[1]] = myColor;
            int score = minValue(board, depth - 1, alpha, beta, myColor, 
                               (myColor == BLACK) ? WHITE : BLACK);
            board[move[0]][move[1]] = EMPTY;
            
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            
            alpha = Math.max(alpha, score);
        }
        
        return bestMove;
    }
    
    /**
     * 极大值节点
     */
    private int maxValue(int[][] board, int depth, int alpha, int beta, 
                        int myColor, int currentColor) {
        if (depth == 0) {
            return evaluateBoard(board, myColor);
        }
        
        List<int[]> candidates = getCandidateMoves(board);
        if (candidates.isEmpty()) {
            return evaluateBoard(board, myColor);
        }
        
        // 检查是否有获胜位置
        int[] winMove = findWinningMove(board, currentColor);
        if (winMove != null) {
            return (currentColor == myColor) ? FIVE : -FIVE;
        }
        
        int maxScore = Integer.MIN_VALUE;
        
        for (int[] move : candidates) {
            board[move[0]][move[1]] = currentColor;
            int score = minValue(board, depth - 1, alpha, beta, myColor,
                               (currentColor == BLACK) ? WHITE : BLACK);
            board[move[0]][move[1]] = EMPTY;
            
            maxScore = Math.max(maxScore, score);
            
            if (maxScore >= beta) {
                return maxScore; // Beta剪枝
            }
            
            alpha = Math.max(alpha, maxScore);
        }
        
        return maxScore;
    }
    
    /**
     * 极小值节点
     */
    private int minValue(int[][] board, int depth, int alpha, int beta,
                        int myColor, int currentColor) {
        if (depth == 0) {
            return evaluateBoard(board, myColor);
        }
        
        List<int[]> candidates = getCandidateMoves(board);
        if (candidates.isEmpty()) {
            return evaluateBoard(board, myColor);
        }
        
        // 检查是否有获胜位置
        int[] winMove = findWinningMove(board, currentColor);
        if (winMove != null) {
            return (currentColor == myColor) ? FIVE : -FIVE;
        }
        
        int minScore = Integer.MAX_VALUE;
        
        for (int[] move : candidates) {
            board[move[0]][move[1]] = currentColor;
            int score = maxValue(board, depth - 1, alpha, beta, myColor,
                               (currentColor == BLACK) ? WHITE : BLACK);
            board[move[0]][move[1]] = EMPTY;
            
            minScore = Math.min(minScore, score);
            
            if (minScore <= alpha) {
                return minScore; // Alpha剪枝
            }
            
            beta = Math.min(beta, minScore);
        }
        
        return minScore;
    }
    
    /**
     * 获取候选移动位置（启发式搜索）
     */
    private List<int[]> getCandidateMoves(int[][] board) {
        List<int[]> candidates = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // 找到所有已有棋子周围的空位
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] != EMPTY) {
                    // 检查周围2格范围内的空位
                    for (int di = -2; di <= 2; di++) {
                        for (int dj = -2; dj <= 2; dj++) {
                            int ni = i + di;
                            int nj = j + dj;
                            String key = ni + "," + nj;
                            
                            if (ni >= 0 && ni < BOARD_SIZE && 
                                nj >= 0 && nj < BOARD_SIZE &&
                                board[ni][nj] == EMPTY && 
                                !visited.contains(key)) {
                                candidates.add(new int[]{ni, nj});
                                visited.add(key);
                            }
                        }
                    }
                }
            }
        }
        
        // 如果棋盘为空，返回中心点
        if (candidates.isEmpty()) {
            candidates.add(new int[]{7, 7});
        }
        
        return candidates;
    }
    
    /**
     * 评估单个位置的价值
     */
    private int evaluatePosition(int[][] board, int row, int col, int color) {
        int score = 0;
        int oppColor = (color == BLACK) ? WHITE : BLACK;
        
        // 临时放置棋子
        board[row][col] = color;
        
        // 评估进攻价值
        score += evaluatePoint(board, row, col, color) * 2;
        
        // 撤回棋子
        board[row][col] = EMPTY;
        
        // 临时放置对手棋子，评估防守价值
        board[row][col] = oppColor;
        score += evaluatePoint(board, row, col, oppColor);
        board[row][col] = EMPTY;
        
        return score;
    }
    
    /**
     * 评估某个点在某个方向上的棋型
     */
    private int evaluatePoint(int[][] board, int row, int col, int color) {
        int totalScore = 0;
        
        for (int[] dir : DIRECTIONS) {
            totalScore += evaluateLine(board, row, col, dir[0], dir[1], color);
        }
        
        return totalScore;
    }
    
    /**
     * 评估一条线上的棋型
     */
    private int evaluateLine(int[][] board, int row, int col, int dr, int dc, int color) {
        int count = 1; // 当前位置算1个
        int block = 0; // 被堵住的端点数
        int empty = 0; // 空位数
        
        // 正向搜索
        for (int i = 1; i <= 4; i++) {
            int r = row + dr * i;
            int c = col + dc * i;
            
            if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) {
                block++;
                break;
            }
            
            if (board[r][c] == color) {
                count++;
            } else if (board[r][c] == EMPTY) {
                empty++;
                break;
            } else {
                block++;
                break;
            }
        }
        
        // 反向搜索
        for (int i = 1; i <= 4; i++) {
            int r = row - dr * i;
            int c = col - dc * i;
            
            if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) {
                block++;
                break;
            }
            
            if (board[r][c] == color) {
                count++;
            } else if (board[r][c] == EMPTY) {
                empty++;
                break;
            } else {
                block++;
                break;
            }
        }
        
        // 根据棋型返回分数
        return getShapeScore(count, block, empty);
    }
    
    /**
     * 根据棋型获取分数
     */
    private int getShapeScore(int count, int block, int empty) {
        if (count >= 5) return FIVE;
        
        if (block == 2) return 0; // 两头都被堵，无价值
        
        switch (count) {
            case 4:
                return (block == 0) ? LIVE_FOUR : RUSH_FOUR;
            case 3:
                return (block == 0) ? LIVE_THREE : SLEEP_THREE;
            case 2:
                return (block == 0) ? LIVE_TWO : SLEEP_TWO;
            default:
                return 0;
        }
    }
    
    /**
     * 评估整个棋盘
     */
    private int evaluateBoard(int[][] board, int myColor) {
        int myScore = 0;
        int oppScore = 0;
        int oppColor = (myColor == BLACK) ? WHITE : BLACK;
        
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == myColor) {
                    myScore += evaluatePoint(board, i, j, myColor);
                } else if (board[i][j] == oppColor) {
                    oppScore += evaluatePoint(board, i, j, oppColor);
                }
            }
        }
        
        return myScore - oppScore;
    }
    
    /**
     * 检查是否获胜
     */
    private boolean checkWin(int[][] board, int row, int col, int color) {
        for (int[] dir : DIRECTIONS) {
            int count = 1;
            
            // 正向
            for (int i = 1; i < 5; i++) {
                int r = row + dir[0] * i;
                int c = col + dir[1] * i;
                if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE &&
                    board[r][c] == color) {
                    count++;
                } else {
                    break;
                }
            }
            
            // 反向
            for (int i = 1; i < 5; i++) {
                int r = row - dir[0] * i;
                int c = col - dir[1] * i;
                if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE &&
                    board[r][c] == color) {
                    count++;
                } else {
                    break;
                }
            }
            
            if (count >= 5) return true;
        }
        
        return false;
    }
}
