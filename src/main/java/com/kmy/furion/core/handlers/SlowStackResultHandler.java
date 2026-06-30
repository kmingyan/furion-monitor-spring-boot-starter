package com.kmy.furion.core.handlers;

/**
 * 慢方法监控结果回调接口
 * 宿主应用实现此接口并注册为 Spring Bean，即可接收慢方法检测结果。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Component
 * public class SlowStackAlertHandler implements SlowStackResultHandler {
 *     &#64;Override
 *     public void onResult(SlowStackResult result) {
 *         // 发送告警、记录到数据库等
 *     }
 * }
 * </pre>
 *
 * @Author: kmy
 */
public interface SlowStackResultHandler {

    /**
     * 当检测到慢方法时回调
     *
     * @param result 包含慢方法的上下文信息（类名、方法名、耗时、阈值、调用栈等）
     */
    void onResult(SlowStackResult result);
}
