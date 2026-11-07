package uestc.jgl.fightgame.dto;

/**
 * 创建角色请求参数
 * 对应接口：POST /api/role/create
 */
public class CreateRoleRequest {

    /** 生命分配点数（每点 +10 HP） */
    private int hpPoints;

    /** 攻击分配点数（每点 +2 ATK） */
    private int atkPoints;

    /** 防御分配点数（每点 +1 DEF） */
    private int defPoints;

    /** 蓝量分配点数（每点 +10 MP） */
    private int mpPoints;

    public int getHpPoints() {
        return hpPoints;
    }

    public void setHpPoints(int hpPoints) {
        this.hpPoints = hpPoints;
    }

    public int getAtkPoints() {
        return atkPoints;
    }

    public void setAtkPoints(int atkPoints) {
        this.atkPoints = atkPoints;
    }

    public int getDefPoints() {
        return defPoints;
    }

    public void setDefPoints(int defPoints) {
        this.defPoints = defPoints;
    }

    public int getMpPoints() {
        return mpPoints;
    }

    public void setMpPoints(int mpPoints) {
        this.mpPoints = mpPoints;
    }
}
