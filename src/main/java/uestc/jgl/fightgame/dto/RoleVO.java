package uestc.jgl.fightgame.dto;

import java.util.List;

/**
 * 角色信息视图对象（返回给前端）
 * 用于：POST /api/role/create、GET /api/role/info、道具使用后的状态返回
 */
public class RoleVO {

    /** 角色名（用登录用户名当昵称） */
    private String roleName;

    /** 最大生命值 */
    private int maxHp;

    /** 当前生命值 */
    private int hp;

    /** 攻击力 */
    private int atk;

    /** 防御力 */
    private int def;

    /** 最大蓝量 */
    private int maxMp;

    /** 当前蓝量 */
    private int mp;

    /** 胜利场次（每 3 场触发一次成长） */
    private int wins;

    /** 技能名称列表（如：普通攻击、强力一击、生命汲取） */
    private List<String> skills;

    /** 背包汇总：只包含数量 > 0 的道具 */
    private List<PackageItem> packageList;

    /**
     * 背包条目：道具名称 + 数量 + 效果描述
     * （按"名称+数量"汇总，而不是一件件列出）
     */
    public static class PackageItem {

        /** 道具名称 */
        private String name;

        /** 持有数量 */
        private int count;

        /** 效果描述，如 "HP +10" */
        private String effect;

        public PackageItem() {
        }

        public PackageItem(String name, int count, String effect) {
            this.name = name;
            this.count = count;
            this.effect = effect;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getEffect() {
            return effect;
        }

        public void setEffect(String effect) {
            this.effect = effect;
        }
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getAtk() {
        return atk;
    }

    public void setAtk(int atk) {
        this.atk = atk;
    }

    public int getDef() {
        return def;
    }

    public void setDef(int def) {
        this.def = def;
    }

    public int getMaxMp() {
        return maxMp;
    }

    public void setMaxMp(int maxMp) {
        this.maxMp = maxMp;
    }

    public int getMp() {
        return mp;
    }

    public void setMp(int mp) {
        this.mp = mp;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<PackageItem> getPackageList() {
        return packageList;
    }

    public void setPackageList(List<PackageItem> packageList) {
        this.packageList = packageList;
    }
}
