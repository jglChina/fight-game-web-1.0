package uestc.jgl.fightgame.common;

/**
 * 统一响应结构：所有接口都返回 { code, message, data }
 *
 * code 约定：
 *   200 成功
 *   400 参数错误（格式校验失败、点数不足等）
 *   401 未登录 / token 无效
 *   403 账号锁定
 *   404 资源不存在
 *   500 服务器错误
 */
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（无提示语） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功（带提示语） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 成功（仅提示语，无数据） */
    public static <T> Result<T> success(String message) {
        return new Result<>(200, message, null);
    }

    /** 失败 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
