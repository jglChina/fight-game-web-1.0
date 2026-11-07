package uestc.jgl.fightgame.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.dto.RoleVO;
import uestc.jgl.fightgame.entity.Consumable;
import uestc.jgl.fightgame.entity.Role;

/**
 * 道具服务：战斗外查看背包、使用道具。
 * 战斗内使用道具走 /api/battle/action（actionType=item），不走这里。
 */
@Service
public class ItemService {

    /** 角色服务：取角色 + 复用 toVO 转换器 */
    @Autowired
    private RoleService roleService;

    /**
     * 查看背包（含角色当前状态）。
     *
     * @return 角色视图：packageList 里只列数量 > 0 的道具
     */
    public RoleVO list(String username) {
        return roleService.toVO(requireRole(username));
    }

    /**
     * 使用道具：校验数量 → 扣背包 → 按类型回血/回蓝（实体内部自动不超上限）。
     *
     * @return 使用提示语；最新角色状态由 Controller 再调 list() 一并返回
     */
    public String use(String username, String itemName, int count) {
        Role role = requireRole(username);

        // 1. 基础校验：名称、数量
        if (itemName == null || itemName.isEmpty()) {
            throw new BusinessException(400, "道具名称不能为空");
        }
        if (count <= 0) {
            throw new BusinessException(400, "使用数量必须大于 0");
        }

        // 2. 背包里要有这个道具
        Consumable item = role.findConsumable(itemName);
        if (item == null) {
            throw new BusinessException(400, "背包中没有该道具");
        }

        // 3. 持有数量要够
        int owned = role.countConsumable(itemName);
        if (owned < count) {
            throw new BusinessException(400, "道具数量不足：仅有 " + owned + " 个");
        }

        // 4. 扣减并按类型恢复
        role.removeConsumable(itemName, count);
        int total = item.getValue() * count;
        if (item.getTarget() == Consumable.Target.HP) {
            role.heal(total);
            return "你使用了 " + itemName + " ×" + count + "，回复了 " + total + " 点生命";
        }
        role.restoreMp(total);
        return "你使用了 " + itemName + " ×" + count + "，回复了 " + total + " 点蓝量";
    }

    /** 取角色，未创建统一抛 404 */
    private Role requireRole(String username) {
        Role role = roleService.get(username);
        if (role == null) {
            throw new BusinessException(404, "尚未创建角色，请先创建角色");
        }
        return role;
    }
}
