package uestc.jgl.fightgame.config;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uestc.jgl.fightgame.common.BusinessException;
import uestc.jgl.fightgame.common.Result;

/**
 * 全局异常处理器：把 Controller/Service 抛出的异常统一拦截，
 * 转成 {code, message, data} 的 Result 结构返回给前端，
 * 避免 Spring 默认错误页（Whitelabel）泄漏给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：Service 校验失败主动抛出，带明确的 code 和提示语。
     * 例如 400 参数错误、401 未登录、403 账号锁定、404 资源不存在。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 兜底异常：所有没预料到的错误（含请求体 JSON 格式错误等）。
     * 统一返回 500，具体原因只留在服务端日志，不泄漏给前端。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 服务端控制台留一份堆栈，方便排查
        e.printStackTrace();
        return Result.error(500, "服务器内部错误，请稍后再试");
    }
}
