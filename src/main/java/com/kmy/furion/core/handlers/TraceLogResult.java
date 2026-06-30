package com.kmy.furion.core.handlers;

import lombok.Data;

/**
 * 方法追踪日志结果
 * 封装方法追踪的上下文信息，通过 {@link TraceLogResultHandler} 回调给宿主应用
 *
 * @Author: kmy
 */
@Data
public class TraceLogResult {

    /**
     * 类全限定名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 方法入参（安全序列化后的字符串数组，受 @TraceLog(logArgs=true) 控制）
     */
    private String[] args;

    /**
     * 返回值（安全序列化后的字符串，受 @TraceLog(logResult=true) 控制）
     */
    private String returnValue;

    /**
     * 异常类名（方法未抛异常时为 null）
     */
    private String exceptionType;

    /**
     * 异常消息（方法未抛异常时为 null）
     */
    private String exceptionMessage;
}
