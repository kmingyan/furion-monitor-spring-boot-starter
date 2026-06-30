package com.kmy.furion.core.handlers;

/**
 * 方法调用统计结果回调接口
 * 宿主应用实现此接口并注册为 Spring Bean，即可接收方法调用统计报告。
 * 每次回调包含一个统计周期内所有被监控方法的调用指标。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Component
 * public class MetricsExportHandler implements InvokeStatResultHandler {
 *     &#64;Override
 *     public void onResult(InvokeStatResult result) {
 *         for (InvokeStatResult.MethodStat stat : result.getMethodStats()) {
 *             // 推送到 Prometheus、InfluxDB 等指标平台
 *         }
 *     }
 * }
 * </pre>
 *
 * @Author: kmy
 */
public interface InvokeStatResultHandler {

    /**
     * 当一个统计周期结束时回调
     *
     * @param result 包含该周期内所有方法的调用统计指标
     */
    void onResult(InvokeStatResult result);
}
