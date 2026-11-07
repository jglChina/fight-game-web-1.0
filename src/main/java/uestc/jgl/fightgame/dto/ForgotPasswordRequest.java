package uestc.jgl.fightgame.dto;

/**
 * 忘记密码请求参数
 * 对应接口：POST /api/user/forgot-password
 */
public class ForgotPasswordRequest {

    /** 用户名：不存在则直接返回失败（前端回到主界面） */
    private String username;

    /** 手机号：必须与注册时绑定的手机号一致才允许重置 */
    private String phone;

    /** 新密码：复用注册时的密码规则 */
    private String newPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
