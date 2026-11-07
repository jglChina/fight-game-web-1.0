package uestc.jgl.fightgame.common;

/**
 * 业务异常：Service 校验失败时抛出，
 * 由全局异常处理器统一转成 Result 返回。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() {
        return code;
    }
}
