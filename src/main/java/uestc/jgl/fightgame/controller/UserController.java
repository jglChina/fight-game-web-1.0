package uestc.jgl.fightgame.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uestc.jgl.fightgame.common.Result;
import uestc.jgl.fightgame.dto.ForgotPasswordRequest;
import uestc.jgl.fightgame.dto.LoginRequest;
import uestc.jgl.fightgame.dto.RegisterRequest;
import uestc.jgl.fightgame.service.UserService;

/**
 * 用户模块接口：注册、登录、验证码、忘记密码
 * 本类只做"接收请求 → 调用 Service → 包装 Result"，业务逻辑全在 UserService。
 * Service 抛出的 BusinessException 由全局异常处理器统一转成 Result。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /** 用户业务服务 */
    @Autowired
    private UserService userService;

    /**
     * 获取验证码：GET /api/user/captcha
     * 前端拿到后展示给用户输入，注册/登录时随表单提交
     */
    @GetMapping("/captcha")
    public Result<String> captcha() {
        String code = userService.generateCaptcha();
        return Result.success("验证码获取成功", code);
    }

    /**
     * 注册：POST /api/user/register
     * 请求体：{ username, password, phone, captcha }
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.success("恭喜您注册成功，请登录后开始游戏");
    }

    /**
     * 登录：POST /api/user/login
     * 请求体：{ username, password, captcha }
     * 成功时 data 返回 token，前端后续请求放在 Authorization 请求头
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest req) {
        String token = userService.login(req);
        return Result.success("登录成功，欢迎回来，" + req.getUsername(), token);
    }

    /**
     * 忘记密码：POST /api/user/forgot-password
     * 请求体：{ username, phone, newPassword }
     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@RequestBody ForgotPasswordRequest req) {
        userService.forgotPassword(req);
        return Result.success("密码修改成功，请使用新密码登录");
    }
}
