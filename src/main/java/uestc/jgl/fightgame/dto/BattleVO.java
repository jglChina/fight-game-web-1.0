package uestc.jgl.fightgame.dto;

import java.util.List;

/**
 * 战斗状态视图对象（返回给前端）
 * 用于：POST /api/battle/start、POST /api/battle/action、GET /api/battle/state
 */
public class BattleVO {

    /** 战斗唯一标识 */
    private String battleId;

    /** 当前回合数 */
    private int round;

    /** 玩家当前状态 */
    private PlayerState player;

    /** 敌人当前状态 */
    private EnemyState enemy;

    /** 战斗结果：ongoing 进行中 / victory 胜利 / defeat 失败 */
    private String battleResult;

    /** 战斗日志（前端逐条渲染，区分颜色） */
    private List<String> logs;

    /** 我方本回合行动详情（action 接口返回） */
    private ActionDetail playerAction;

    /** 敌方本回合行动详情（action 接口返回） */
    private ActionDetail enemyAction;

    /** 胜利奖励（battleResult=victory 时有值，否则为 null） */
    private Rewards rewards;

    /** 战败嘲讽话术（battleResult=defeat 时有值，否则为 null） */
    private String taunt;

    /**
     * 玩家战斗内状态
     */
    public static class PlayerState {

        /** 当前生命值 */
        private int hp;

        /** 最大生命值 */
        private int maxHp;

        /** 当前蓝量 */
        private int mp;

        /** 最大蓝量 */
        private int maxMp;

        /** 当前攻击力（每 3 胜会成长，前端需要每回合拿到最新值） */
        private int atk;

        /** 当前防御力（同上） */
        private int def;

        public PlayerState() {
        }

        public PlayerState(int hp, int maxHp, int mp, int maxMp) {
            this.hp = hp;
            this.maxHp = maxHp;
            this.mp = mp;
            this.maxMp = maxMp;
        }

        public PlayerState(int hp, int maxHp, int mp, int maxMp, int atk, int def) {
            this.hp = hp;
            this.maxHp = maxHp;
            this.mp = mp;
            this.maxMp = maxMp;
            this.atk = atk;
            this.def = def;
        }

        public int getHp() {
            return hp;
        }

        public void setHp(int hp) {
            this.hp = hp;
        }

        public int getMaxHp() {
            return maxHp;
        }

        public void setMaxHp(int maxHp) {
            this.maxHp = maxHp;
        }

        public int getMp() {
            return mp;
        }

        public void setMp(int mp) {
            this.mp = mp;
        }

        public int getMaxMp() {
            return maxMp;
        }

        public void setMaxMp(int maxMp) {
            this.maxMp = maxMp;
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
    }

    /**
     * 敌人战斗内状态
     */
    public static class EnemyState {

        /** 敌人名称 */
        private String name;

        /** 当前生命值 */
        private int hp;

        /** 最大生命值 */
        private int maxHp;

        /** 攻击力 */
        private int atk;

        /** 防御力 */
        private int def;

        /** 是否处于防御姿态（奶娃坦克技能效果，下回合受伤减半） */
        private boolean defending;

        public EnemyState() {
        }

        public EnemyState(String name, int hp, int maxHp, int atk, int def, boolean defending) {
            this.name = name;
            this.hp = hp;
            this.maxHp = maxHp;
            this.atk = atk;
            this.def = def;
            this.defending = defending;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getHp() {
            return hp;
        }

        public void setHp(int hp) {
            this.hp = hp;
        }

        public int getMaxHp() {
            return maxHp;
        }

        public void setMaxHp(int maxHp) {
            this.maxHp = maxHp;
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

        public boolean isDefending() {
            return defending;
        }

        public void setDefending(boolean defending) {
            this.defending = defending;
        }
    }

    /**
     * 单次行动详情（我方或敌方）
     */
    public static class ActionDetail {

        /** 行动类型：attack / skill / item */
        private String type;

        /** 技能或道具名称 */
        private String skillName;

        /** 造成的伤害（攻击/技能时有效） */
        private int damage;

        /** 消耗的蓝量（技能时有效） */
        private int costMp;

        public ActionDetail() {
        }

        public ActionDetail(String type, String skillName, int damage, int costMp) {
            this.type = type;
            this.skillName = skillName;
            this.damage = damage;
            this.costMp = costMp;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public int getDamage() {
            return damage;
        }

        public void setDamage(int damage) {
            this.damage = damage;
        }

        public int getCostMp() {
            return costMp;
        }

        public void setCostMp(int costMp) {
            this.costMp = costMp;
        }
    }

    /**
     * 胜利奖励（仅 victory 时返回）
     */
    public static class Rewards {

        /** 恢复的生命值（20~40 随机） */
        private int healAmount;

        /** 恢复的蓝量（最大蓝量的 20%） */
        private int mpRecover;

        /** 是否触发每 3 胜成长 */
        private boolean growth;

        /** 成长详情（growth=true 时有值）：HP/MP/ATK/DEF 各提升多少 */
        private GrowthDetail growthDetail;

        /** 掉落的道具（无掉落为 null） */
        private DroppedItem droppedItem;

        /** 当前总胜场 */
        private int wins;

        /**
         * 成长详情
         */
        public static class GrowthDetail {

            /** HP 上限提升量 */
            private int hpUp;

            /** MP 上限提升量 */
            private int mpUp;

            /** 攻击提升量 */
            private int atkUp;

            /** 防御提升量 */
            private int defUp;

            public GrowthDetail() {
            }

            public GrowthDetail(int hpUp, int mpUp, int atkUp, int defUp) {
                this.hpUp = hpUp;
                this.mpUp = mpUp;
                this.atkUp = atkUp;
                this.defUp = defUp;
            }

            public int getHpUp() {
                return hpUp;
            }

            public void setHpUp(int hpUp) {
                this.hpUp = hpUp;
            }

            public int getMpUp() {
                return mpUp;
            }

            public void setMpUp(int mpUp) {
                this.mpUp = mpUp;
            }

            public int getAtkUp() {
                return atkUp;
            }

            public void setAtkUp(int atkUp) {
                this.atkUp = atkUp;
            }

            public int getDefUp() {
                return defUp;
            }

            public void setDefUp(int defUp) {
                this.defUp = defUp;
            }
        }

        /**
         * 掉落道具
         */
        public static class DroppedItem {

            /** 道具名称 */
            private String name;

            /** 效果描述，如 "HP +10" */
            private String effect;

            public DroppedItem() {
            }

            public DroppedItem(String name, String effect) {
                this.name = name;
                this.effect = effect;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getEffect() {
                return effect;
            }

            public void setEffect(String effect) {
                this.effect = effect;
            }
        }

        public int getHealAmount() {
            return healAmount;
        }

        public void setHealAmount(int healAmount) {
            this.healAmount = healAmount;
        }

        public int getMpRecover() {
            return mpRecover;
        }

        public void setMpRecover(int mpRecover) {
            this.mpRecover = mpRecover;
        }

        public boolean isGrowth() {
            return growth;
        }

        public void setGrowth(boolean growth) {
            this.growth = growth;
        }

        public GrowthDetail getGrowthDetail() {
            return growthDetail;
        }

        public void setGrowthDetail(GrowthDetail growthDetail) {
            this.growthDetail = growthDetail;
        }

        public DroppedItem getDroppedItem() {
            return droppedItem;
        }

        public void setDroppedItem(DroppedItem droppedItem) {
            this.droppedItem = droppedItem;
        }

        public int getWins() {
            return wins;
        }

        public void setWins(int wins) {
            this.wins = wins;
        }
    }

    public String getBattleId() {
        return battleId;
    }

    public void setBattleId(String battleId) {
        this.battleId = battleId;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public PlayerState getPlayer() {
        return player;
    }

    public void setPlayer(PlayerState player) {
        this.player = player;
    }

    public EnemyState getEnemy() {
        return enemy;
    }

    public void setEnemy(EnemyState enemy) {
        this.enemy = enemy;
    }

    public String getBattleResult() {
        return battleResult;
    }

    public void setBattleResult(String battleResult) {
        this.battleResult = battleResult;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    public ActionDetail getPlayerAction() {
        return playerAction;
    }

    public void setPlayerAction(ActionDetail playerAction) {
        this.playerAction = playerAction;
    }

    public ActionDetail getEnemyAction() {
        return enemyAction;
    }

    public void setEnemyAction(ActionDetail enemyAction) {
        this.enemyAction = enemyAction;
    }

    public Rewards getRewards() {
        return rewards;
    }

    public void setRewards(Rewards rewards) {
        this.rewards = rewards;
    }

    public String getTaunt() {
        return taunt;
    }

    public void setTaunt(String taunt) {
        this.taunt = taunt;
    }
}
