package com.kmy.furion.core.handlers;

import lombok.Data;
import lombok.ToString;

/**
 * 慢方法监控结果
 * 封装慢方法检测的上下文信息，通过 {@link SlowStackResultHandler} 回调给宿主应用
 *
 * @Author: kmy
 */
@Data
@ToString
public class SlowStackResult {

    /**
     * 类全限定名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 实际执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 阈值（毫秒）
     */
    private long thresholdMs;

    /**
     * 异常类名（方法未抛异常时为 null）
     */
    private String exceptionType;

    /**
     * 异常消息（方法未抛异常时为 null）
     */
    private String exceptionMessage;

    /**
     * 格式化的调用栈字符串（已过滤框架帧）
     */
    private String stackTrace;
}
