package uestc.jgl.fightgame.config;

import org.springframework.stereotype.Component;
import uestc.jgl.fightgame.entity.Enemy;
import uestc.jgl.fightgame.entity.Role;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 战斗管理器：保存所有进行中的战斗与玩家连胜记录。
 * 替代控制台版 Game 里的局部变量（Scanner 循环 → Web 每次请求一回合）。
 */
@Component
public class BattleManager {

    /**
     * 一场战斗的内存状态（静态嵌套类，只服务于 BattleManager/BattleService）
     */
    public static class Battle {

        /** 战斗唯一标识 */
        private final String battleId;

        /** 战斗归属的玩家用户名（鉴权校验用） */
        private final String username;

        /** 玩家角色：与 RoleService 中保存的是同一个对象，战斗成长直接落到持久角色上 */
        private final Role player;

        /** 本场敌人 */
        private final Enemy enemy;

        /** 当前回合数（双方各行动一次后 +1） */
        private int round = 1;

        /** 战斗日志：所有文案都写这里，前端逐条渲染；写少读多，用 CopyOnWriteArrayList 保证并发读安全 */
        private final List<String> logs = new CopyOnWriteArrayList<>();

        /** 战斗结果：ongoing 进行中 / victory 胜利 / defeat 失败 */
        private String result = "ongoing";

        public Battle(String battleId, String username, Role player, Enemy enemy) {
            this.battleId = battleId;
            this.username = username;
            this.player = player;
            this.enemy = enemy;
        }

        public String getBattleId() {
            return battleId;
        }

        public String getUsername() {
            return username;
        }

        public Role getPlayer() {
            return player;
        }

        public Enemy getEnemy() {
            return enemy;
        }

        public int getRound() {
            return round;
        }

        /** 回合数 +1（双方各行动完一次后调用） */
        public void nextRound() {
            this.round++;
        }

        public List<String> getLogs() {
            return logs;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }

    /** battleId -> 战斗 */
    private final ConcurrentHashMap<String, Battle> BATTLES = new ConcurrentHashMap<>();

    /** 用户名 -> battleId：保证一个玩家同时只有一场战斗 */
    private final ConcurrentHashMap<String, String> USERNAME_TO_BATTLE = new ConcurrentHashMap<>();

    /** 用户名 -> 当前连胜数（跨战斗存续，影响下场敌人成长；战败/放弃/退出时清零） */
    private final ConcurrentHashMap<String, Integer> WIN_STREAK = new ConcurrentHashMap<>();

    /**
     * 创建一场新战斗。若该玩家有旧战斗（无论是否结束）直接废弃。
     *
     * @return 新建的 Battle
     */
    public Battle create(String username, Role player, Enemy enemy) {
        // 先清理该玩家的旧战斗，保证唯一
        String oldId = USERNAME_TO_BATTLE.get(username);
        if (oldId != null) {
            BATTLES.remove(oldId);
        }
        Battle battle = new Battle(UUID.randomUUID().toString(), username, player, enemy);
        BATTLES.put(battle.getBattleId(), battle);
        USERNAME_TO_BATTLE.put(username, battle.getBattleId());
        return battle;
    }

    /** 按 battleId 查战斗，不存在返回 null */
    public Battle get(String battleId) {
        return battleId == null ? null : BATTLES.get(battleId);
    }

    /** 查某玩家当前的 battleId，没有返回 null */
    public String getBattleIdOf(String username) {
        return USERNAME_TO_BATTLE.get(username);
    }

    /** 删除战斗并解除玩家占用 */
    public void remove(String username, String battleId) {
        if (battleId != null) {
            BATTLES.remove(battleId);
        }
        USERNAME_TO_BATTLE.remove(username);
    }

    // ==================== 连胜记录 ====================

    /** 当前连胜数，没有记录返回 0 */
    public int getStreak(String username) {
        return WIN_STREAK.getOrDefault(username, 0);
    }

    /** 连胜 +1（每场胜利后调用，影响下一场敌人成长） */
    public void incStreak(String username) {
        WIN_STREAK.merge(username, 1, Integer::sum);
    }

    /** 连胜清零（战败、放弃战斗、退出界面时调用） */
    public void resetStreak(String username) {
        WIN_STREAK.remove(username);
    }
}
