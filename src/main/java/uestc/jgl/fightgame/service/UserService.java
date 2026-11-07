package uestc.jgl.fightgame.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.config.SessionManager;
import uestc.jgl.fightgame.dto.ForgotPasswordRequest;
import uestc.jgl.fightgame.dto.LoginRequest;
import uestc.jgl.fightgame.dto.RegisterRequest;
import uestc.jgl.fightgame.entity.User;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 用户服务：注册、登录、验证码、忘记密码的业务逻辑
 * 校验失败统一抛 BusinessException，由全局异常处理器转成 Result 返回。
 */
@Service
public class UserService {

    /** 会话管理器：登录成功后签发 token */
    @Autowired
    private SessionManager sessionManager;

    /** 客服电话：从 application.properties 的 service.phone 注入，用于锁定提示语 */
    @Value("${service.phone}")
    private String servicePhone;

    /** 用户列表：内存版，重启丢失；读写并发安全 */
    private final List<User> users = new CopyOnWriteArrayList<>();

    /** 各用户的密码连续错误次数（密码错时累计，登录成功或锁定后清零） */
    private final ConcurrentHashMap<String, Integer> loginFailCount = new ConcurrentHashMap<>();

    /** 当前验证码（单玩家场景：全服只保留一个，与控制台版行为一致） */
    private String captchaCode;

    /** 验证码过期时间戳（毫秒） */
    private long captchaExpireAt;

    /** 验证码有效期：120 秒 */
    private static final long CAPTCHA_TTL_MS = 120_000;

    /** 密码允许的最大连续错误次数 */
    private static final int MAX_FAIL = 3;

    private final Random random = new Random();

    // ==================== 验证码 ====================

    /**
     * 生成验证码：4 个大小写混合字母 + 1 个数字，数字位置随机
     * 每次调用都会覆盖旧验证码，有效期 120 秒
     */
    public String generateCaptcha() {
        StringBuilder sb = new StringBuilder();
        // 1. 生成 4 个字母：每个随机大写或小写
        for (int i = 0; i < 4; i++) {
            char letter = (char) (random.nextBoolean()
                    ? 'A' + random.nextInt(26)
                    : 'a' + random.nextInt(26));
            sb.append(letter);
        }
        // 2. 生成 1 个数字，插入到随机位置（0~4）
        int insertPos = random.nextInt(5);
        sb.insert(insertPos, (char) ('0' + random.nextInt(10)));

        // 3. 保存并设置过期时间
        this.captchaCode = sb.toString();
        this.captchaExpireAt = System.currentTimeMillis() + CAPTCHA_TTL_MS;
        return this.captchaCode;
    }

    /**
     * 校验验证码：忽略大小写比对，一次性使用（比对通过即作废）
     * 校验顺序中它永远排第一，验证码错误不会消耗密码机会
     */
    private void checkCaptcha(String input) {
        // 未获取过或已超时
        if (captchaCode == null || System.currentTimeMillis() > captchaExpireAt) {
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }
        // 忽略大小写比对
        if (input == null || !captchaCode.equalsIgnoreCase(input.trim())) {
            throw new BusinessException(400, "验证码错误");
        }
        // 一次性使用：比对通过后立即作废
        captchaCode = null;
    }

    // ==================== 注册 ====================

    /**
     * 注册。校验顺序固定：验证码 → 用户名 → 密码 → 手机号 → 唯一性
     * 任何一步失败立即抛出带明确提示的异常
     */
    public void register(RegisterRequest req) {
        // 1. 验证码
        checkCaptcha(req.getCaptcha());

        // 2. 用户名：3~16 位，仅字母数字，不能纯数字
        String username = req.getUsername();
        if (username == null || !username.matches("^[a-zA-Z0-9]{3,16}$")) {
            throw new BusinessException(400, "用户名必须为 3~16 位字母或数字");
        }
        if (username.matches("\\d+")) {
            throw new BusinessException(400, "用户名不能为纯数字");
        }

        // 3. 密码：3~8 位，仅字母数字，且必须同时包含字母和数字
        if (!isPasswordValid(req.getPassword())) {
            throw new BusinessException(400, "密码必须为 3~8 位，且同时包含字母和数字");
        }

        // 4. 手机号：11 位纯数字，以 1 开头
        if (req.getPhone() == null || !req.getPhone().matches("^1\\d{10}$")) {
            throw new BusinessException(400, "手机号必须为 11 位且以 1 开头");
        }

        // 5. 唯一性：用户名不能重复
        if (findByUsername(username) != null) {
            throw new BusinessException(400, "该用户名已被注册");
        }

        // 全部通过 → 创建用户（id 由 IDGenerator 自动生成）并加入列表
        users.add(new User(username, req.getPassword(), req.getPhone()));
    }

    // ==================== 登录 ====================

    /**
     * 登录。成功返回 token；失败抛异常。
     * 顺序：用户存在 → 账号锁定 → 验证码 → 密码
     */
    public String login(LoginRequest req) {
        // 1. 用户不存在
        User user = findByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException(400, "用户名未注册");
        }

        // 2. 账号已锁定
        if (!user.isActive()) {
            throw new BusinessException(403, "账号已锁定，请联系客服 " + servicePhone);
        }

        // 3. 验证码（在密码之前校验，错误不消耗密码机会）
        checkCaptcha(req.getCaptcha());

        // 4. 密码校验：错误则累计次数，满 3 次锁定并返回 403
        if (!user.getPassword().equals(req.getPassword())) {
            int fails = loginFailCount.merge(user.getUsername(), 1, Integer::sum);
            if (fails >= MAX_FAIL) {
                user.lock();
                loginFailCount.remove(user.getUsername());
                throw new BusinessException(403, "密码连续错误 3 次，账号已锁定，请联系客服 " + servicePhone);
            }
            throw new BusinessException(400, "密码错误，剩余 " + (MAX_FAIL - fails) + " 次机会");
        }

        // 5. 登录成功：清零错误次数，签发 token
        loginFailCount.remove(user.getUsername());
        return sessionManager.createToken(user.getUsername());
    }

    // ==================== 忘记密码 ====================

    /**
     * 忘记密码：凭绑定手机号重置密码（此接口不需要验证码）
     */
    public void forgotPassword(ForgotPasswordRequest req) {
        // 1. 用户名必须存在（不存在直接失败，前端回主界面）
        User user = findByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException(400, "用户名未注册");
        }

        // 2. 手机号必须与绑定手机号一致
        if (!user.getPhone().equals(req.getPhone())) {
            throw new BusinessException(400, "手机号与绑定手机号不一致");
        }

        // 3. 新密码复用注册密码规则
        if (!isPasswordValid(req.getNewPassword())) {
            throw new BusinessException(400, "新密码必须为 3~8 位，且同时包含字母和数字");
        }

        // 4. 新密码不能与原密码相同
        if (user.getPassword().equals(req.getNewPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }

        // 5. 修改密码
        user.setPassword(req.getNewPassword());
    }

    // ==================== 私有工具 ====================

    /** 按用户名查用户，找不到返回 null */
    private User findByUsername(String username) {
        if (username == null) {
            return null;
        }
        for (User u : users) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }

    /** 密码规则（注册/忘记密码共用）：3~8 位，仅字母数字，必须同时包含字母和数字 */
    private boolean isPasswordValid(String password) {
        if (password == null || !password.matches("^[a-zA-Z0-9]{3,8}$")) {
            return false;
        }
        // 必须至少含一个字母和一个数字
        return password.matches(".*[a-zA-Z].*") && password.matches(".*\\d.*");
    }
}
