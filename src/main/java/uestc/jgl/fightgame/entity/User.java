package uestc.jgl.fightgame.entity;

/**
 * 用户类（复制自控制台版，逻辑不变）
 */
public class User {

    /** 可使用状态（默认） */
    public static final int STATE_ACTIVE = 1;
    /** 不可使用状态（密码连续输错3次后锁定） */
    public static final int STATE_LOCKED = 0;

    /** 用户唯一标识：heima + 5位随机数字 */
    private String id;

    /** 用户名：唯一，3~16 位，字母+数字，不能纯数字 */
    private String username;

    /** 密码：3~8 位，字母+数字组合 */
    private String password;

    /** 手机号：11 位数字，1 开头 */
    private String phone;

    /** 账户状态 */
    private int state;

    public User() {
        this.id = IDGenerator.next();
        this.state = STATE_ACTIVE;
    }

    public User(String username, String password, String phone) {
        this.id = IDGenerator.next();
        this.username = username;
        this.password = password;
        this.phone = phone;
        this.state = STATE_ACTIVE;
    }

    public String getId() {
        return id;
    }

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

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    /** 锁定账户：将状态改为不可使用 */
    public void lock() {
        this.state = STATE_LOCKED;
    }

    /** 判断账户是否可用 */
    public boolean isActive() {
        return this.state == STATE_ACTIVE;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", state=" + (state == STATE_ACTIVE ? "可使用" : "不可使用") +
                '}';
    }
}
