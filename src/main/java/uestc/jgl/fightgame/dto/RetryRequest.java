package uestc.jgl.fightgame.dto;

/**
 * 战败后选择请求参数
 * 对应接口：POST /api/battle/retry
 */
public class RetryRequest {

    /** 1 = 我不甘心就此失败，重新创建角色再战；2 = 退出到主界面 */
    private int choice;

    public int getChoice() {
        return choice;
    }

    public void setChoice(int choice) {
        this.choice = choice;
    }
}
