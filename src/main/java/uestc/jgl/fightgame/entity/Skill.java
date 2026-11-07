package uestc.jgl.fightgame.entity;

/**
 * 玩家技能（复制自控制台版，逻辑不变）
 */
public class Skill {

    /** 技能效果类型 */
    public enum SkillType {
        /** 普通攻击：造成基础伤害 */
        NORMAL,
        /** 强力一击：造成 180% 攻击力伤害 */
        POWER_STRIKE,
        /** 生命汲取：恢复 0~20 点生命 */
        LIFESTEAL
    }

    private final String name;

    /** 消耗蓝量，0 表示不消耗 */
    private final int costHp;

    private final SkillType type;

    public Skill(String name, int costHp, SkillType type) {
        this.name = name;
        this.costHp = costHp;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public int getCostHp() {
        return costHp;
    }

    public SkillType getType() {
        return type;
    }

    @Override
    public String toString() {
        return name + (costHp > 0 ? " (消耗" + costHp + "MP)" : "");
    }
}
