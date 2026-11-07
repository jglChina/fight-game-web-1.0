package uestc.jgl.fightgame.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家角色（复制自控制台版，逻辑不变）
 *
 * 人物属性计算公式：初始属性 + 分配点数 * 比例
 * 初始血量 100，攻击 10，防御 0，蓝量 100
 * 每点：生命+10、攻击+2、防御+1、蓝量+10
 */
public class Role {

    private static final int BASE_HP = 100;
    private static final int BASE_ATK = 10;
    private static final int BASE_DEF = 0;
    private static final int BASE_MP = 100;

    private static final int HP_PER_POINT = 10;
    private static final int ATK_PER_POINT = 2;
    private static final int DEF_PER_POINT = 1;
    private static final int MP_PER_POINT = 10;

    /** 每 3 胜的属性成长 */
    public static final int GROW_HP = 30;
    public static final int GROW_ATK = 5;
    public static final int GROW_DEF = 3;
    public static final int GROW_MP = 30;

    private final String name;
    private int maxHp;
    private int hp;
    private int atk;
    private int def;
    private int maxMp;
    private int mp;
    private int wins;
    private final List<Skill> skills;
    private final List<Consumable> packageList;

    public Role(String name, int hpPoints, int atkPoints, int defPoints, int mpPoints) {
        this.name = name;
        this.maxHp = BASE_HP + hpPoints * HP_PER_POINT;
        this.hp = this.maxHp;
        this.atk = BASE_ATK + atkPoints * ATK_PER_POINT;
        this.def = BASE_DEF + defPoints * DEF_PER_POINT;
        this.maxMp = BASE_MP + mpPoints * MP_PER_POINT;
        this.mp = this.maxMp;
        this.wins = 0;
        this.skills = defaultSkills();
        this.packageList = new ArrayList<>();
    }

    /** 玩家默认技能：普通攻击、强力一击、生命汲取 */
    private static List<Skill> defaultSkills() {
        List<Skill> list = new ArrayList<>();
        list.add(new Skill("普通攻击", 0, Skill.SkillType.NORMAL));
        list.add(new Skill("强力一击", 10, Skill.SkillType.POWER_STRIKE));
        list.add(new Skill("生命汲取", 10, Skill.SkillType.LIFESTEAL));
        return list;
    }

    // ==================== 战斗血量操作 ====================

    public boolean isAlive() {
        return hp > 0;
    }

    /** 受到伤害：经防御减伤后最低 1 点，HP 最低扣到 0 */
    public void takeDamage(int damage) {
        int real = Math.max(damage - def, 1);
        hp = Math.max(hp - real, 0);
    }

    /** 恢复生命（不超过最大生命） */
    public void heal(int amount) {
        hp = Math.min(hp + amount, maxHp);
    }

    // ==================== 蓝量操作 ====================

    /** 消耗蓝量，蓝量不足返回 false */
    public boolean consumeMp(int amount) {
        if (mp < amount) {
            return false;
        }
        mp -= amount;
        return true;
    }

    /** 恢复蓝量（不超过最大蓝量） */
    public void restoreMp(int amount) {
        mp = Math.min(mp + amount, maxMp);
    }

    /** 恢复最大蓝量的一定百分比 */
    public void restoreMpPercent(double percent) {
        int amount = (int) (maxMp * percent);
        restoreMp(amount);
    }

    /** 完全恢复 HP 和 MP 到上限（失败后"不服再战"重整旗鼓用） */
    public void fullRecover() {
        this.hp = this.maxHp;
        this.mp = this.maxMp;
    }

    // ==================== 成长系统 ====================

    /** 胜场 +1，并检测是否触发每 3 胜成长 */
    public void addWin() {
        wins++;
        if (wins % 3 == 0) {
            maxHp += GROW_HP;
            atk += GROW_ATK;
            def += GROW_DEF;
            hp += GROW_HP;
            maxMp += GROW_MP;
            mp += GROW_MP;
        }
    }

    /** 重置胜场计数（失败后"不服再战"清零用） */
    public void resetWins() {
        this.wins = 0;
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

    public int getMaxMp() {
        return maxMp;
    }

    public int getMp() {
        return mp;
    }

    public int getWins() {
        return wins;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    // ==================== 道具背包 ====================

    public List<Consumable> getPackageList() {
        return packageList;
    }

    public void addConsumable(Consumable item) {
        packageList.add(item);
    }

    public int countConsumable(String name) {
        int n = 0;
        for (Consumable c : packageList) {
            if (c.getName().equals(name)) {
                n++;
            }
        }
        return n;
    }

    public void removeConsumable(String name, int n) {
        int removed = 0;
        for (int i = packageList.size() - 1; i >= 0 && removed < n; i--) {
            if (packageList.get(i).getName().equals(name)) {
                packageList.remove(i);
                removed++;
            }
        }
    }

    public Consumable findConsumable(String name) {
        for (Consumable c : packageList) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name + " [HP: " + hp + "/" + maxHp + ", MP: " + mp + "/" + maxMp
                + ", ATK: " + atk + ", DEF: " + def + "]";
    }
}
