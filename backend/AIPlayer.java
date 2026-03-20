/**
 * AI玩家接口 - 所有AI的基类接口
 * 用于支持不同类型的AI（规则AI、深度学习AI等）
 */
public interface AIPlayer {
    
    /**
     * 获取AI名称
     */
    String getName();
    
    /**
     * 获取AI类型
     * @return "rule" 表示规则AI, "llm" 表示大模型AI
     */
    String getType();
    
    /**
     * 计算下一步移动
     * @param gameState 当前游戏状态JSON
     * @return 移动数据JSON
     */
    String calculateMove(String gameState);
    
    /**
     * 设置AI难度等级
     * @param level 难度等级 1-5
     */
    void setDifficulty(int level);
    
    /**
     * 获取AI难度等级
     */
    int getDifficulty();
}
