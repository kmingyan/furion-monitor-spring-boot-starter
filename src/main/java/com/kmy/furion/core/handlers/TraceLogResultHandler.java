package com.kmy.furion.core.handlers;

/**
 * 方法追踪日志结果回调接口
 * 宿主应用实现此接口并注册为 Spring Bean，即可接收方法追踪日志结果。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Component
 * public class TraceLogHandler implements TraceLogResultHandler {
 *     &#64;Override
 *     public void onResult(TraceLogResult result) {
 *         // 写入日志系统、发送到链路追踪平台等
 *     }
 * }
 * </pre>
 *
 * @Author: kmy
 */
public interface TraceLogResultHandler {

    /**
     * 当方法追踪日志触发时回调
     *
     * @param result 包含方法调用的上下文信息（类名、方法名、耗时、入参、返回值等）
     */
    void onResult(TraceLogResult result);
}
