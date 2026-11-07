package uestc.jgl.fightgame.dto;

/**
 * 战斗行动请求参数
 * 对应接口：POST /api/battle/action
 */
public class BattleActionRequest {

    /** 战斗唯一标识（/api/battle/start 返回） */
    private String battleId;

    /** 行动类型：attack 普通攻击 / skill 技能 / item 道具 */
    private String actionType;

    /** 技能下标（actionType=skill 时有效）：0 普通攻击 1 强力一击 2 生命汲取 */
    private Integer skillIndex;

    /** 道具名称（actionType=item 时有效） */
    private String itemName;

    /** 道具使用数量（actionType=item 时有效，默认 1） */
    private Integer count;

    public String getBattleId() {
        return battleId;
    }

    public void setBattleId(String battleId) {
        this.battleId = battleId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Integer getSkillIndex() {
        return skillIndex;
    }

    public void setSkillIndex(Integer skillIndex) {
        this.skillIndex = skillIndex;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
