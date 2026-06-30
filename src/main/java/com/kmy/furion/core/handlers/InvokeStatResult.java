package com.kmy.furion.core.handlers;

import lombok.Data;

import java.util.List;

/**
 * 方法调用统计结果
 * 封装一个统计周期内所有被监控方法的调用指标，通过 {@link InvokeStatResultHandler} 回调给宿主应用
 *
 * @Author: kmy
 */
@Data
public class InvokeStatResult {

    /**
     * 统计周期内各方法的指标列表
     */
    private List<MethodStat> methodStats;

    /**
     * 单方法的统计指标
     */
    @Data
    public static class MethodStat {

        /**
         * 类全限定名
         */
        private String className;

        /**
         * 方法名
         */
        private String methodName;

        /**
         * 调用次数
         */
        private long callCount;

        /**
         * 平均耗时（毫秒）
         */
        private long avgMs;

        /**
         * P50 耗时（毫秒）
         */
        private long p50Ms;

        /**
         * P90 耗时（毫秒）
         */
        private long p90Ms;

        /**
         * P99 耗时（毫秒）
         */
        private long p99Ms;

        /**
         * 最大耗时（毫秒）
         */
        private long maxMs;
    }
}
