package uestc.jgl.fightgame.entity;

/**
 * 消耗品（道具）（复制自控制台版，逻辑不变）
 */
public class Consumable {

    /** 回复目标：HP 生命 / MP 蓝量 */
    public enum Target { HP, MP }

    private final String name;
    private final int value;
    private final Target target;

    public Consumable(String name, int value, Target target) {
        this.name = name;
        this.value = value;
        this.target = target;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public Target getTarget() {
        return target;
    }

    /** 效果描述，例如 "HP +10" */
    public String effectDesc() {
        return target + " +" + value;
    }

    @Override
    public String toString() {
        return name + "(" + effectDesc() + ")";
    }
}
