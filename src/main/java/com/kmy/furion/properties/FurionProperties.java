package com.kmy.furion.properties;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author: kmy
 * @Description:
 * @Date: create in 2026-06-22 09:53
 */
@ConfigurationProperties(prefix = "furion.monitor")
@Data
@ToString
public class FurionProperties {

    /**
     * 是否开启慢请求监控
     */
    private boolean enabled = true;

    /**
     * 慢方法耗时阈值（毫秒），超过该时间视为慢方法
     */
    private long slowThresholdMs = 2000L;

    /**
     * 慢 SQL 耗时阈值（毫秒），超过该时间视为慢 SQL
     */
    private long slowSqlThresholdMs = 1000L;

    /**
     * 采样率 (0.0 - 1.0)，例如 0.5 表示 50% 的请求会被监控
     */
    private double sampleRate = 1.0;

    /**
     * 日志输出级别或目标（可选）
     */
    private String logLevel = "WARN";

    /**
     * 方法调用统计报告间隔（秒）
     */
    private long invokeStatIntervalSeconds = 60L;

}
