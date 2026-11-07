package uestc.jgl.fightgame.entity;

/**
 * 敌人（怪物）（复制自控制台版，逻辑不变）
 */
public class Enemy {

    /** 敌人特殊技能类型 */
    public enum SpecialSkill {
        /** 猛击：造成 150% 伤害 */
        HEAVY_THRASH(1.5, "猛击"),
        /** 快速攻击：连续攻击 2 次，每次 50% 伤害 */
        QUICK_STRIKE(-1, "快速攻击"),
        /** 防御姿态：本回合不攻击，下回合受到伤害减半 */
        DEFEND(0, "防御姿态"),
        /** 火球术：造成 180% 伤害 */
        FIREBALL(1.8, "火球术");

        private final double multiplier;
        private final String label;

        SpecialSkill(double multiplier, String label) {
            this.multiplier = multiplier;
            this.label = label;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String name;
    private int maxHp;
    private int hp;
    private int atk;
    private int def;
    private final SpecialSkill specialSkill;
    private boolean defending;

    public Enemy(String name, int maxHp, int atk, int def, SpecialSkill specialSkill) {
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.atk = atk;
        this.def = def;
        this.specialSkill = specialSkill;
    }

    // ==================== 预设敌人（基础属性） ====================

    public static Enemy ofType(String type) {
        switch (type) {
            case "糖人豆包":
                return new Enemy("糖人豆包", 80, 15, 10, SpecialSkill.HEAVY_THRASH);
            case "鱼人沃特":
                return new Enemy("鱼人沃特", 60, 20, 5, SpecialSkill.QUICK_STRIKE);
            case "奶娃坦克":
                return new Enemy("奶娃坦克", 120, 10, 20, SpecialSkill.DEFEND);
            case "大肥鱼":
            default:
                return new Enemy("大肥鱼", 70, 25, 8, SpecialSkill.FIREBALL);
        }
    }

    // ==================== 战斗血量 / 增益 ====================

    public boolean isAlive() {
        return hp > 0;
    }

    /** 怪物成长：每连胜一场属性 +10 / +3 / +2，并回满血 */
    public void grow() {
        maxHp += 10;
        atk += 3;
        def += 2;
        hp = maxHp;
    }

    /** 受到伤害：经防御减伤后最低 1 点，HP 最低扣到 0 */
    public void takeDamage(int damage) {
        int real = Math.max(damage - def, 1);
        hp = Math.max(hp - real, 0);
    }

    public boolean isDefending() {
        return defending;
    }

    public void setDefending(boolean defending) {
        this.defending = defending;
    }

    // ==================== Getter ====================

    public String getName() {
        return name;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getHp() {
        return hp;
    }

    public int getAtk() {
        return atk;
    }

    public int getDef() {
        return def;
    }

    public SpecialSkill getSpecialSkill() {
        return specialSkill;
    }

    @Override
    public String toString() {
        return name + " [HP: " + hp + "/" + maxHp + ", ATK: " + atk + ", DEF: " + def + "]";
    }
}
