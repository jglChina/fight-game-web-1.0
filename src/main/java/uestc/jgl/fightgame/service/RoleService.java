package uestc.jgl.fightgame.service;

import org.springframework.stereotype.Service;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.dto.CreateRoleRequest;
import uestc.jgl.fightgame.dto.RoleVO;
import uestc.jgl.fightgame.entity.Consumable;
import uestc.jgl.fightgame.entity.Role;
import uestc.jgl.fightgame.entity.Skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色服务：创建角色、查询角色
 * 角色保存在内存 Map 中（重启丢失），一个用户名对应一个角色。
 */
@Service
public class RoleService {

    /** 总属性点上限：原 20 点，加入蓝量后调整为 30 点 */
    private static final int TOTAL_POINTS = 30;

    /** 用户名 -> 角色 */
    private final ConcurrentHashMap<String, Role> USERNAME_TO_ROLE = new ConcurrentHashMap<>();

    /**
     * 创建角色。校验：各项点数 ≥ 0 且总和 = 30。
     * 已创建过时直接覆盖（战败重开也调用本方法）。
     *
     * @return 创建完成后的角色视图
     */
    public RoleVO create(String username, CreateRoleRequest req) {
        int hp = req.getHpPoints();
        int atk = req.getAtkPoints();
        int def = req.getDefPoints();
        int mp = req.getMpPoints();

        // 1. 各项点数不能为负
        if (hp < 0 || atk < 0 || def < 0 || mp < 0) {
            throw new BusinessException(400, "属性点不能为负数");
        }

        // 2. 四项之和必须恰好为总点数
        int sum = hp + atk + def + mp;
        if (sum != TOTAL_POINTS) {
            throw new BusinessException(400, "总属性点必须为 " + TOTAL_POINTS + "，当前为 " + sum);
        }

        // 3. 创建角色（属性公式在 Role 构造器内完成）并覆盖旧角色
        Role role = new Role(username, hp, atk, def, mp);
        USERNAME_TO_ROLE.put(username, role);

        return toVO(role);
    }

    /** 取用户角色，没有返回 null */
    public Role get(String username) {
        return USERNAME_TO_ROLE.get(username);
    }

    /**
     * 实体 -> 前端视图转换器。
     * 技能只返回名称列表；背包按道具名汇总数量，只列数量 > 0 的道具。
     * 本方法 public：ItemService 用完道具后也靠它取最新 RoleVO。
     */
    public RoleVO toVO(Role role) {
        RoleVO vo = new RoleVO();
        vo.setRoleName(role.getName());
        vo.setMaxHp(role.getMaxHp());
        vo.setHp(role.getHp());
        vo.setAtk(role.getAtk());
        vo.setDef(role.getDef());
        vo.setMaxMp(role.getMaxMp());
        vo.setMp(role.getMp());
        vo.setWins(role.getWins());

        // 技能：只取名称列表（如：普通攻击、强力一击、生命汲取）
        List<String> skillNames = new ArrayList<>();
        for (Skill s : role.getSkills()) {
            skillNames.add(s.getName());
        }
        vo.setSkills(skillNames);

        // 背包汇总：按名称分组计数，LinkedHashMap 保持首次获得的顺序
        Map<String, RoleVO.PackageItem> grouped = new LinkedHashMap<>();
        for (Consumable c : role.getPackageList()) {
            RoleVO.PackageItem item = grouped.get(c.getName());
            if (item == null) {
                // 第一次出现：数量 1，效果描述取自 Consumable
                grouped.put(c.getName(), new RoleVO.PackageItem(c.getName(), 1, c.effectDesc()));
            } else {
                item.setCount(item.getCount() + 1);
            }
        }
        vo.setPackageList(new ArrayList<>(grouped.values()));

        return vo;
    }
}
