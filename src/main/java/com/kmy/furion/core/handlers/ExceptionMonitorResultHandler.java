package com.kmy.furion.core.handlers;

/**
 * 异常监控结果回调接口
 * 宿主应用实现此接口并注册为 Spring Bean，即可接收异常监控结果。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Component
 * public class ExceptionAlertHandler implements ExceptionMonitorResultHandler {
 *     &#64;Override
 *     public void onResult(ExceptionMonitorResult result) {
 *         // 发送告警、记录到数据库等
 *     }
 * }
 * </pre>
 *
 * @Author: kmy
 */
public interface ExceptionMonitorResultHandler {

    /**
     * 当捕获到异常时回调
     *
     * @param result 包含异常的上下文信息（类名、方法名、入参、异常类型、调用栈等）
     */
    void onResult(ExceptionMonitorResult result);
}
