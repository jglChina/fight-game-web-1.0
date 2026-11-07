package uestc.jgl.fightgame.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.common.Result;
import uestc.jgl.fightgame.config.SessionManager;
import uestc.jgl.fightgame.dto.BattleActionRequest;
import uestc.jgl.fightgame.dto.BattleVO;
import uestc.jgl.fightgame.dto.RetryRequest;
import uestc.jgl.fightgame.service.BattleService;

/**
 * 战斗模块接口：开始战斗、回合行动、查询状态、放弃、战败重开。
 * 所有接口都需要登录；Controller 只负责取 token、调 Service、包 Result。
 */
@RestController
@RequestMapping("/api/battle")
public class BattleController {

    /** 战斗业务服务 */
    @Autowired
    private BattleService battleService;

    /** 会话管理器：token -> 用户名 */
    @Autowired
    private SessionManager sessionManager;

    /**
     * 开始战斗：POST /api/battle/start
     * 请求头：Authorization: <token>；无请求体
     */
    @PostMapping("/start")
    public Result<BattleVO> start(
            @RequestHeader(value = "Authorization", required = false) String token) {
        String username = requireUsername(token);
        return Result.success("战斗开始", battleService.start(username));
    }

    /**
     * 回合行动：POST /api/battle/action
     * 请求体：{ battleId, actionType: attack|skill|item, skillIndex?, itemName?, count? }
     */
    @PostMapping("/action")
    public Result<BattleVO> action(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody BattleActionRequest req) {
        String username = requireUsername(token);
        return Result.success("行动完成", battleService.action(username, req));
    }

    /**
     * 查询战斗状态：GET /api/battle/state/{battleId}
     * 用于前端刷新页面后恢复战斗画面
     */
    @GetMapping("/state/{battleId}")
    public Result<BattleVO> state(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable("battleId") String battleId) {
        String username = requireUsername(token);
        return Result.success("查询成功", battleService.getState(username, battleId));
    }

    /**
     * 放弃战斗：POST /api/battle/abandon
     * 请求体复用 BattleActionRequest（只取其中的 battleId 字段）
     */
    @PostMapping("/abandon")
    public Result<Void> abandon(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody BattleActionRequest req) {
        String username = requireUsername(token);
        battleService.abandon(username, req.getBattleId());
        return Result.success("已放弃当前战斗");
    }

    /**
     * 战败后选择：POST /api/battle/retry
     * 请求体：{ choice } —— 1 重新创建角色再战 / 2 退出到主界面
     */
    @PostMapping("/retry")
    public Result<Void> retry(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody RetryRequest req) {
        String username = requireUsername(token);
        String message = battleService.retry(username, req.getChoice());
        return Result.success(message);
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
