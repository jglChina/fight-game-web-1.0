package uestc.jgl.fightgame.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.common.Result;
import uestc.jgl.fightgame.config.SessionManager;
import uestc.jgl.fightgame.dto.BattleActionRequest;
import uestc.jgl.fightgame.dto.RoleVO;
import uestc.jgl.fightgame.service.ItemService;

/**
 * 道具模块接口：战斗外查看背包、使用道具。
 * 所有接口都需要登录；Controller 只负责取 token、调 Service、包 Result。
 */
@RestController
@RequestMapping("/api/item")
public class ItemController {

    /** 道具业务服务 */
    @Autowired
    private ItemService itemService;

    /** 会话管理器：token -> 用户名 */
    @Autowired
    private SessionManager sessionManager;

    /**
     * 查看背包：GET /api/item/list
     * 请求头：Authorization: <token>
     */
    @GetMapping("/list")
    public Result<RoleVO> list(
            @RequestHeader(value = "Authorization", required = false) String token) {
        String username = requireUsername(token);
        return Result.success("查询成功", itemService.list(username));
    }

    /**
     * 使用道具：POST /api/item/use
     * 请求体复用 BattleActionRequest（只取 itemName + count 两个字段，count 默认为 1）
     * data 返回使用后的最新角色状态（血量/蓝量/背包数量已更新）
     */
    @PostMapping("/use")
    public Result<RoleVO> use(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody BattleActionRequest req) {
        String username = requireUsername(token);
        int count = req.getCount() == null ? 1 : req.getCount();
        // Service 完成扣减与恢复，返回提示语；再取最新状态一起返回
        String message = itemService.use(username, req.getItemName(), count);
        return Result.success(message, itemService.list(username));
    }

    /**
     * 鉴权辅助：从 Authorization 头读 token，解析出用户名。
     * token 缺失或无效统一抛 401。
     */
    private String requireUsername(String token) {
        String username = sessionManager.getUsername(token);
        if (username == null) {
            throw new BusinessException(401, "未登录或登录已过期，请重新登录");
        }
        return username;
    }
}
