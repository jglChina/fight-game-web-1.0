package uestc.jgl.fightgame.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.config.BattleManager;
import uestc.jgl.fightgame.config.BattleManager.Battle;
import uestc.jgl.fightgame.dto.BattleActionRequest;
import uestc.jgl.fightgame.dto.BattleVO;
import uestc.jgl.fightgame.entity.Consumable;
import uestc.jgl.fightgame.entity.Enemy;
import uestc.jgl.fightgame.entity.Role;

import java.util.ArrayList;
import java.util.Random;

/**
 * 战斗服务：回合制战斗核心逻辑（移植自己验证过的控制台版 Game.java）。
 * 一回合 = 前端调用一次 action：我方行动 → 敌人未死则敌人行动 → 返回最新状态。
 * 所有战斗文案写入 logs 数组，前端负责渲染。
 */
@Service
public class BattleService {

    /** 释放技能消耗的蓝量（MP） */
    private static final int SKILL_COST = 10;

    /** 胜利后恢复生命区间：20~40 */
    private static final int HEAL_MIN = 20;
    private static final int HEAL_RANGE = 21;

    /** 胜利后恢复最大蓝量的比例：20% */
    private static final double MP_RECOVER_PERCENT = 0.2;

    /** 道具掉落：每场胜利 30% 概率；掉落时 60% 回血 / 40% 回蓝 */
    private static final int DROP_RATE_PERCENT = 30;
    private static final int HP_TYPE_PERCENT = 60;

    /** 敌人战败玩家后的嘲讽话术池（与控制台版完全一致） */
    private static final String[] TAUNTS = {
            "哼，就这？我还没热身你就倒了，奶龙都比你强。",
            "看见你头顶那点血条了吗？我闭着眼都能数清它们还剩几格，连蓝条都快落灰了吧小兄弟。",
            "老弟实力不行哇，送你一个带派雨姐回家多练练吧",
            "回去给你的属性面板上柱香吧——它死得比你快，你跟它的差距，约等于我跟满分之间隔了一个你。",
            "下辈子记得把 30 点全加在'脸皮厚'上，兴许还能在我手里多撑两个回合。"
    };

    /** 可挑战的敌人类型池 */
    private static final String[] ENEMY_TYPES = {"糖人豆包", "鱼人沃特", "奶娃坦克", "大肥鱼"};

    /** 角色服务：取玩家角色 */
    @Autowired
    private RoleService roleService;

    /** 战斗管理器：存取战斗与连胜 */
    @Autowired
    private BattleManager battleManager;

    private final Random rnd = new Random();

    // ==================== 开始战斗 ====================

    /**
     * 开始一场新战斗：随机敌人，按当前连胜次数让敌人成长。
     */
    public BattleVO start(String username) {
        // 1. 必须先创建角色
        Role role = roleService.get(username);
        if (role == null) {
            throw new BusinessException(404, "尚未创建角色，请先创建角色");
        }

        // 2. 随机一种敌人
        Enemy enemy = Enemy.ofType(ENEMY_TYPES[rnd.nextInt(ENEMY_TYPES.length)]);

        // 3. 怪物成长：每连胜一场，敌人 +10HP/+3攻/+2防（循环 grow）
        int streak = battleManager.getStreak(username);
        for (int i = 0; i < streak; i++) {
            enemy.grow();
        }

        // 4. 登记战斗（旧战斗自动废弃）
        Battle battle = battleManager.create(username, role, enemy);

        // 5. 写入开战日志
        battle.getLogs().add("⚔️ 遭遇敌人：" + enemy.getName()
                + " [HP:" + enemy.getHp() + " ATK:" + enemy.getAtk() + " DEF:" + enemy.getDef() + "]");
        if (streak > 0) {
            battle.getLogs().add("📢 你已连续胜利 " + streak + " 场，敌人已被强化过 " + streak + " 次（每次强化 HP+10 / 攻击+3 / 防御+2）！");
        }

        return toVO(battle);
    }

    // ==================== 回合行动 ====================

    /**
     * 执行一个回合：我方行动 → 敌人未死则敌人行动 → 返回最新状态。
     */
    public BattleVO action(String username, BattleActionRequest req) {
        // 1. 战斗存在且属于本人且仍在进行中
        Battle battle = requireBattle(username, req.getBattleId());
        if (!"ongoing".equals(battle.getResult())) {
            throw new BusinessException(400, "本场战斗已结束，请重新开始");
        }
        Role player = battle.getPlayer();
        Enemy enemy = battle.getEnemy();

        // 2. 我方行动
        BattleVO.ActionDetail playerAction = doPlayerAction(battle, player, enemy, req);

        // 3. 判定敌人是否死亡 → 先结算胜利改状态，再快照 VO（顺序不能反，否则 battleResult/日志是旧的）
        if (!enemy.isAlive()) {
            BattleVO.Rewards rewards = settleVictory(battle, player, enemy);
            BattleVO vo = toVO(battle);
            vo.setPlayerAction(playerAction);
            vo.setRewards(rewards);
            return vo;
        }

        // 4. 敌人行动
        BattleVO.ActionDetail enemyAction = doEnemyAction(battle, player, enemy);

        // 5. 判定我方是否死亡 → 同样先结算再快照
        String taunt = null;
        if (!player.isAlive()) {
            taunt = settleDefeat(battle, username, enemy);
        } else {
            // 双方都存活 → 回合数 +1
            battle.nextRound();
        }
        BattleVO vo = toVO(battle);
        vo.setPlayerAction(playerAction);
        vo.setEnemyAction(enemyAction);
        vo.setTaunt(taunt);
        return vo;
    }

    /** 我方行动：attack 普攻 / skill 技能 / item 道具 */
    private BattleVO.ActionDetail doPlayerAction(Battle battle, Role player, Enemy enemy, BattleActionRequest req) {
        String type = req.getActionType();
        if (type == null) {
            throw new BusinessException(400, "行动类型不能为空");
        }

        switch (type) {
            case "attack":
                // 普通攻击：基础伤害，不耗蓝
                return playerAttack(battle, player, enemy, "普通攻击", player.getAtk(), 0, "attack");

            case "skill": {
                Integer idx = req.getSkillIndex();
                if (idx == null || idx < 0 || idx > 2) {
                    throw new BusinessException(400, "技能下标必须为 0~2");
                }
                if (idx == 0) {
                    // 下标 0 = 普通攻击
                    return playerAttack(battle, player, enemy, "普通攻击", player.getAtk(), 0, "attack");
                }
                // 技能先扣蓝，蓝不足直接报错
                String skillName = idx == 1 ? "强力一击" : "生命汲取";
                if (!player.consumeMp(SKILL_COST)) {
                    throw new BusinessException(400, "蓝量不足，无法释放" + skillName);
                }
                if (idx == 1) {
                    // 强力一击：攻击 × 1.8
                    int raw = (int) (player.getAtk() * 1.8);
                    return playerAttack(battle, player, enemy, skillName, raw, SKILL_COST, "skill");
                }
                // 生命汲取：恢复 0~20 HP，无伤害
                int heal = rnd.nextInt(21);
                player.heal(heal);
                battle.getLogs().add("💚 你释放生命汲取，恢复 " + heal + " 点生命（消耗 " + SKILL_COST + "MP）");
                return new BattleVO.ActionDetail("skill", skillName, 0, SKILL_COST);
            }

            case "item": {
                // 使用道具：扣背包数量，按类型回血/回蓝，本回合不造成伤害
                String name = req.getItemName();
                int count = req.getCount() == null ? 1 : req.getCount();
                if (name == null || name.isEmpty()) {
                    throw new BusinessException(400, "道具名称不能为空");
                }
                if (count <= 0) {
                    throw new BusinessException(400, "使用数量必须大于 0");
                }
                Consumable item = player.findConsumable(name);
                if (item == null) {
                    throw new BusinessException(400, "背包中没有该道具");
                }
                int owned = player.countConsumable(name);
                if (owned < count) {
                    throw new BusinessException(400, "道具数量不足：仅有 " + owned + " 个");
                }
                // 扣道具并按类型恢复
                player.removeConsumable(name, count);
                int total = item.getValue() * count;
                if (item.getTarget() == Consumable.Target.HP) {
                    player.heal(total);
                    battle.getLogs().add("🧪 你使用了 " + name + " ×" + count + "，恢复 " + total + " 点生命");
                } else {
                    player.restoreMp(total);
                    battle.getLogs().add("🧪 你使用了 " + name + " ×" + count + "，恢复 " + total + " 点蓝量");
                }
                return new BattleVO.ActionDetail("item", name, 0, 0);
            }

            default:
                throw new BusinessException(400, "未知的行动类型：" + type);
        }
    }

    /**
     * 我方造成一次伤害（普攻/强力一击共用）：
     * 敌人防御姿态 → 伤害减半并清标志；再经敌人防御减伤（最低 1 点）。
     */
    private BattleVO.ActionDetail playerAttack(Battle battle, Role player, Enemy enemy,
                                               String skillName, int rawDamage, int costMp, String type) {
        // 防御姿态：本回合伤害减半
        if (enemy.isDefending()) {
            rawDamage = rawDamage / 2;
            enemy.setDefending(false);
            battle.getLogs().add("🛡️ " + enemy.getName() + " 的防御姿态使伤害减半！");
        }
        int before = enemy.getHp();
        enemy.takeDamage(rawDamage); // 内部公式：max(dmg - def, 1)
        int real = before - enemy.getHp();

        battle.getLogs().add(type.equals("skill")
                ? "💥 消耗 " + costMp + "MP，你对 " + enemy.getName() + " 使用了" + skillName + "，造成 " + real + " 点伤害"
                : "💥 你对 " + enemy.getName() + " 发起" + skillName + "，造成 " + real + " 点伤害");
        return new BattleVO.ActionDetail(type, skillName, real, costMp);
    }

    /** 敌方行动：50% 普攻 / 50% 专属技能 */
    private BattleVO.ActionDetail doEnemyAction(Battle battle, Role player, Enemy enemy) {
        if (rnd.nextBoolean()) {
            // 50% 普通攻击
            int dmg = hurtPlayer(player, enemy.getAtk());
            battle.getLogs().add("👊 " + enemy.getName() + " 对你发起普通攻击，造成 " + dmg + " 点伤害");
            return new BattleVO.ActionDetail("attack", "普通攻击", dmg, 0);
        }

        // 50% 专属技能
        String label = enemy.getSpecialSkill().getLabel();
        switch (enemy.getSpecialSkill()) {
            case HEAVY_THRASH: { // 猛击：150% 伤害
                int dmg = hurtPlayer(player, (int) (enemy.getAtk() * 1.5));
                battle.getLogs().add("🔥 " + enemy.getName() + " 使用" + label + "，造成 " + dmg + " 点伤害");
                return new BattleVO.ActionDetail("skill", label, dmg, 0);
            }
            case QUICK_STRIKE: { // 快速攻击：连续 2 次，每次 50% 伤害
                int half = (int) (enemy.getAtk() * 0.5);
                int dmg = hurtPlayer(player, half) + hurtPlayer(player, half);
                battle.getLogs().add("⚡ " + enemy.getName() + " 使用" + label + "，连续攻击 2 次，共造成 " + dmg + " 点伤害");
                return new BattleVO.ActionDetail("skill", label, dmg, 0);
            }
            case DEFEND: { // 防御姿态：本回合不攻击，下回合受伤减半
                enemy.setDefending(true);
                battle.getLogs().add("🛡️ " + enemy.getName() + " 摆出防御姿态，下回合受到的伤害减半！");
                return new BattleVO.ActionDetail("skill", label, 0, 0);
            }
            case FIREBALL: { // 火球术：180% 伤害
                int dmg = hurtPlayer(player, (int) (enemy.getAtk() * 1.8));
                battle.getLogs().add("🔥 " + enemy.getName() + " 使用" + label + "，造成 " + dmg + " 点伤害");
                return new BattleVO.ActionDetail("skill", label, dmg, 0);
            }
            default:
                throw new BusinessException(500, "未知的敌人技能");
        }
    }

    /** 敌人对我方造成一次伤害，返回实际伤害值（经我方防御减伤，最低 1 点） */
    private int hurtPlayer(Role player, int rawDamage) {
        int before = player.getHp();
        player.takeDamage(rawDamage);
        return before - player.getHp();
    }

    // ==================== 结算 ====================

    /** 胜利结算：恢复 → 成长 → 掉落 → 连胜，返回奖励对象（由调用方放入 VO） */
    private BattleVO.Rewards settleVictory(Battle battle, Role player, Enemy enemy) {
        battle.setResult("victory");
        battle.getLogs().add("🏆 你击败了 " + enemy.getName() + "！");

        BattleVO.Rewards rewards = new BattleVO.Rewards();

        // 1. 恢复 20~40 HP
        int heal = HEAL_MIN + rnd.nextInt(HEAL_RANGE);
        player.heal(heal);
        rewards.setHealAmount(heal);
        battle.getLogs().add("💗 战后休整，恢复 " + heal + " 点生命");

        // 2. 恢复最大蓝量的 20%
        int mpRecover = (int) (player.getMaxMp() * MP_RECOVER_PERCENT);
        player.restoreMp(mpRecover);
        rewards.setMpRecover(mpRecover);
        battle.getLogs().add("💙 战后休整，恢复 " + mpRecover + " 点蓝量");

        // 3. 胜场 +1，检测每 3 胜成长（成长值取 Role 的公开常量）
        player.addWin();
        boolean growth = player.getWins() % 3 == 0;
        rewards.setGrowth(growth);
        rewards.setWins(player.getWins());
        if (growth) {
            rewards.setGrowthDetail(new BattleVO.Rewards.GrowthDetail(
                    Role.GROW_HP, Role.GROW_MP, Role.GROW_ATK, Role.GROW_DEF));
            battle.getLogs().add("🌟 恭喜！已累计胜利 " + player.getWins() + " 场，触发成长："
                    + "HP+" + Role.GROW_HP + " / MP+" + Role.GROW_MP
                    + " / 攻击+" + Role.GROW_ATK + " / 防御+" + Role.GROW_DEF);
        }

        // 4. 掉落判定：30% → 60%回血/40%回蓝 → 类内权重（数值越大越稀有）→ 自动入背包
        if (rnd.nextInt(100) < DROP_RATE_PERCENT) {
            Consumable drop = rollDrop();
            player.addConsumable(drop);
            rewards.setDroppedItem(new BattleVO.Rewards.DroppedItem(drop.getName(), drop.effectDesc()));
            battle.getLogs().add("🎁 运气不错！掉落道具：" + drop.getName() + "（" + drop.effectDesc() + "），已放入背包");
        }

        // 5. 连胜 +1（影响下一场敌人成长）
        battleManager.incStreak(battle.getUsername());

        return rewards;
    }

    /** 失败结算：随机嘲讽，连胜清零，角色数据保留等 retry，返回嘲讽话术 */
    private String settleDefeat(Battle battle, String username, Enemy enemy) {
        battle.setResult("defeat");
        battleManager.resetStreak(username);

        String taunt = TAUNTS[rnd.nextInt(TAUNTS.length)];
        battle.getLogs().add("💀 你被 " + enemy.getName() + " 击败了，征程到此结束……");
        battle.getLogs().add("🗯️ " + enemy.getName() + "：" + taunt);
        return taunt;
    }

    /** 掉落抽取：先定类型（60% 回血），再按"数值越大越稀有"的权重抽具体道具 */
    private Consumable rollDrop() {
        if (rnd.nextInt(100) < HP_TYPE_PERCENT) {
            // 回血道具（权重 40/30/18/9/3）
            int r = rnd.nextInt(100);
            if (r < 40) {
                return new Consumable("桃子", 10, Consumable.Target.HP);
            }
            if (r < 70) {
                return new Consumable("煎蛋", 20, Consumable.Target.HP);
            }
            if (r < 88) {
                return new Consumable("花酿鸡", 30, Consumable.Target.HP);
            }
            if (r < 97) {
                return new Consumable("黑背鲈鱼", 40, Consumable.Target.HP);
            }
            return new Consumable("白玉汤", 50, Consumable.Target.HP);
        }
        // 回蓝道具（权重 60/30/10）
        int r = rnd.nextInt(100);
        if (r < 60) {
            return new Consumable("蓝莓汁", 15, Consumable.Target.MP);
        }
        if (r < 90) {
            return new Consumable("魔力鸡尾酒", 30, Consumable.Target.MP);
        }
        return new Consumable("星辉圣水", 50, Consumable.Target.MP);
    }

    // ==================== 查询 / 放弃 / 重开 ====================

    /** 查询战斗状态（鉴权 + 归属校验） */
    public BattleVO getState(String username, String battleId) {
        return toVO(requireBattle(username, battleId));
    }

    /** 放弃战斗：删除战斗，连胜清零 */
    public void abandon(String username, String battleId) {
        requireBattle(username, battleId);
        battleManager.remove(username, battleId);
        battleManager.resetStreak(username);
    }

    /**
     * 战败后的选择：1 重新战斗（清战场，前端去创建角色）/ 2 退出主界面。
     * 两种选择都会删除战斗并清连胜。
     *
     * @return 给前端的提示语
     */
    public String retry(String username, int choice) {
        String battleId = battleManager.getBattleIdOf(username);
        battleManager.remove(username, battleId);
        battleManager.resetStreak(username);

        // 失败重整：血量蓝量回归初始 + 胜场清零（choice 1/2 都做；
        // choice:1 反正接下来要 create 覆盖角色，重置无副作用；choice:2 不服再战正好要满状态）
        Role role = roleService.get(username);
        if (role != null) {
            role.fullRecover();
            role.resetWins();
        }

        if (choice == 1) {
            return "战场已清空，去重新创建角色再战吧！";
        }
        return "已退出到主界面";
    }

    // ==================== 私有工具 ====================

    /** 校验战斗存在、属于本人、且仍在进行中（查询类调用时允许已结束） */
    private Battle requireBattle(String username, String battleId) {
        Battle battle = battleManager.get(battleId);
        if (battle == null) {
            throw new BusinessException(404, "战斗不存在或已结束");
        }
        if (!battle.getUsername().equals(username)) {
            throw new BusinessException(403, "无权操作他人的战斗");
        }
        return battle;
    }

    /** 实体 -> 前端视图：只组装状态，不含行动/奖励（由调用方按需补充） */
    private BattleVO toVO(Battle battle) {
        BattleVO vo = new BattleVO();
        vo.setBattleId(battle.getBattleId());
        vo.setRound(battle.getRound());
        Role p = battle.getPlayer();
        vo.setPlayer(new BattleVO.PlayerState(p.getHp(), p.getMaxHp(), p.getMp(), p.getMaxMp(),
                p.getAtk(), p.getDef()));
        Enemy e = battle.getEnemy();
        vo.setEnemy(new BattleVO.EnemyState(e.getName(), e.getHp(), e.getMaxHp(),
                e.getAtk(), e.getDef(), e.isDefending()));
        vo.setBattleResult(battle.getResult());
        vo.setLogs(new ArrayList<>(battle.getLogs()));
        return vo;
    }
}
