/**
 * 游戏接口 - 所有游戏的基类接口
 * 用于支持多种游戏的扩展
 */
public interface GameInterface {
    
    /**
     * 获取游戏名称
     */
    String getGameName();
    
    /**
     * 获取游戏ID
     */
    String getGameId();
    
    /**
     * 初始化游戏
     * @param config 游戏配置JSON
     */
    void init(String config);
    
    /**
     * 重置游戏状态
     */
    void reset();
    
    /**
     * 执行移动
     * @param playerId 玩家ID
     * @param move 移动数据JSON
     * @return 移动结果JSON
     */
    String makeMove(int playerId, String move);
    
    /**
     * 获取游戏状态
     * @return 游戏状态JSON
     */
    String getState();
    
    /**
     * 检查游戏是否结束
     * @return 是否结束
     */
    boolean isGameOver();
    
    /**
     * 获取获胜者
     * @return 获胜者ID，-1表示无获胜者
     */
    int getWinner();
    
    /**
     * 验证移动是否合法
     * @param playerId 玩家ID
     * @param move 移动数据JSON
     * @return 是否合法
     */
    boolean isValidMove(int playerId, String move);
}
