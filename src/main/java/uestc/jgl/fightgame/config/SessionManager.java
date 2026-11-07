package uestc.jgl.fightgame.config;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器：用 token 识别"当前登录用户"
 * 替代控制台版"内存里保存当前用户"的做法，支撑 Web 多用户并发访问。
 *
 * 工作方式：登录成功 → 生成 token 返回给前端 → 前端之后每次请求
 * 把 token 放在 Authorization 请求头里带给后端 → 后端用它查用户名。
 *
 * 加 @Component 交给 Spring 管理为单例 Bean，
 * 后续 UserService 等通过 @Autowired 注入使用。
 */
@Component
public class SessionManager {

    /** token -> 用户名：收到请求后根据 token 查出"这个人是谁" */
    private final ConcurrentHashMap<String, String> TOKEN_TO_USERNAME = new ConcurrentHashMap<>();

    /** 用户名 -> token：防重复登录，同一账号再次登录时踢掉旧 token */
    private final ConcurrentHashMap<String, String> USERNAME_TO_TOKEN = new ConcurrentHashMap<>();

    /**
     * 登录成功后调用：生成新 token 并建立双向映射。
     * 若该账号之前已登录过，旧 token 立即失效（后登录踢先登录）。
     *
     * @param username 登录成功的用户名
     * @return 新生成的 token
     */
    public String createToken(String username) {
        // 1. 如果该账号已有旧 token，先从正向映射里清掉（旧客户端即刻失效）
        String oldToken = USERNAME_TO_TOKEN.get(username);
        if (oldToken != null) {
            TOKEN_TO_USERNAME.remove(oldToken);
        }

        // 2. 生成一个全局唯一、不可猜测的 token
        String token = UUID.randomUUID().toString();

        // 3. 写入双向映射
        TOKEN_TO_USERNAME.put(token, username);
        USERNAME_TO_TOKEN.put(username, token);

        return token;
    }

    /**
     * 根据 token 查用户名（Controller 每次收到请求都会调用）。
     *
     * @param token 请求头 Authorization 带来的 token
     * @return 对应用户名；token 无效或已过期返回 null（Controller 据此返回 401）
     */
    public String getUsername(String token) {
        if (token == null) {
            return null;
        }
        return TOKEN_TO_USERNAME.get(token);
    }

    /**
     * 退出登录时调用：把两个 Map 里的映射都清理掉。
     *
     * @param token 要注销的 token
     */
    public void removeToken(String token) {
        // 先查出用户名，再同时清理两个方向的映射
        String username = TOKEN_TO_USERNAME.remove(token);
        if (username != null) {
            USERNAME_TO_TOKEN.remove(username);
        }
    }
}
