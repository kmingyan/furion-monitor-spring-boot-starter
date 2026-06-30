package com.kmy.furion.core.handlers;

/**
 * 慢 SQL 监控结果回调接口
 * 宿主应用实现此接口并注册为 Spring Bean，即可接收慢 SQL 检测结果。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Component
 * public class SlowSqlAlertHandler implements SlowSqlResultHandler {
 *     &#64;Override
 *     public void onResult(SlowSqlResult result) {
 *         // 发送告警、记录到数据库等
 *     }
 * }
 * </pre>
 *
 * @Author: kmy
 */
public interface SlowSqlResultHandler {

    /**
     * 当检测到慢 SQL 时回调
     *
     * @param result 包含慢 SQL 的上下文信息（SQL 语句、耗时、阈值、调用者、调用栈等）
     */
    void onResult(SlowSqlResult result);
}
