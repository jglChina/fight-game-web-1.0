package uestc.jgl.fightgame.dto;

/**
 * 登录请求参数
 * 对应接口：POST /api/user/login
 */
public class LoginRequest {

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 验证码：密码校验之前先校验，错误不消耗密码机会 */
    private String captcha;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}
