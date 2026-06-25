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

    /**
     * AI 诊断配置
     */
    private AiDiagnosis aiDiagnosis = new AiDiagnosis();

    /**
     * AI 诊断相关配置属性
     * 对应配置前缀：furion.monitor.ai-diagnosis
     */
    @Data
    public static class AiDiagnosis {

        /**
         * 大模型 API Key
         */
        private String apiKey;

        /**
         * 大模型名称，如 gpt-4o-mini、qwen-plus、deepseek-chat 等
         */
        private String modelName = "gpt-4o-mini";

        /**
         * 大模型 API 基础地址，如 https://api.openai.com/v1
         * 兼容所有 OpenAI 兼容接口（DeepSeek、通义千问、Moonshot 等）
         */
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * 大模型调用超时时间（秒）
         */
        private int timeoutSeconds = 60;

        /**
         * 同一方法+同一异常类型的冷却时间（秒），防止重复调用大模型
         * 设为 0 表示不启用冷却（每次都调用）
         */
        private int cooldownSeconds = 60;
    }

}
