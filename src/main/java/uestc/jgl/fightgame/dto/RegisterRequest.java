package uestc.jgl.fightgame.dto;

/**
 * 注册请求参数
 * 对应接口：POST /api/user/register
 */
public class RegisterRequest {

    /** 用户名：3~16 位字母数字组合，不能为纯数字 */
    private String username;

    /** 密码：3~8 位，必须同时包含字母和数字 */
    private String password;

    /** 手机号：11 位且以 1 开头，用于忘记密码时核验身份 */
    private String phone;

    /** 验证码：注册时也需要，先校验验证码再校验其他字段 */
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}
