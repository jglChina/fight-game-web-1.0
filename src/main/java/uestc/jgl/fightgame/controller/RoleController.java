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
import uestc.jgl.fightgame.dto.CreateRoleRequest;
import uestc.jgl.fightgame.dto.RoleVO;
import uestc.jgl.fightgame.entity.Role;
import uestc.jgl.fightgame.service.RoleService;

/**
 * 角色模块接口：创建角色、查询角色信息
 * 本模块起所有接口都需要登录：从 Authorization 请求头解析 token。
 */
@RestController
@RequestMapping("/api/role")
public class RoleController {

    /** 角色业务服务 */
    @Autowired
    private RoleService roleService;

    /** 会话管理器：token -> 用户名 */
    @Autowired
    private SessionManager sessionManager;

    /**
     * 创建角色：POST /api/role/create
     * 请求头：Authorization: <token>
     * 请求体：{ hpPoints, atkPoints, defPoints, mpPoints }，总和必须 30
     */
    @PostMapping("/create")
    public Result<RoleVO> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody CreateRoleRequest req) {
        String username = requireUsername(token);
        RoleVO vo = roleService.create(username, req);
        return Result.success("角色创建成功", vo);
    }

    /**
     * 查询当前角色信息：GET /api/role/info
     * 请求头：Authorization: <token>
     */
    @GetMapping("/info")
    public Result<RoleVO> info(
            @RequestHeader(value = "Authorization", required = false) String token) {
        String username = requireUsername(token);
        Role role = roleService.get(username);
        if (role == null) {
            throw new BusinessException(404, "尚未创建角色，请先创建角色");
        }
        return Result.success("查询成功", roleService.toVO(role));
    }

    /**
     * 鉴权辅助：从 Authorization 头读 token，解析出用户名。
     * token 缺失或无效统一抛 401（各受保护 Controller 共用这一模式）。
     */
    private String requireUsername(String token) {
        String username = sessionManager.getUsername(token);
        if (username == null) {
            throw new BusinessException(401, "未登录或登录已过期，请重新登录");
        }
        return username;
    }
}
