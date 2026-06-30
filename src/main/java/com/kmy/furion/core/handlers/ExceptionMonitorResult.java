package com.kmy.furion.core.handlers;

import lombok.Data;
import lombok.ToString;

/**
 * 异常监控结果
 * 封装异常监控捕获的上下文信息，通过 {@link ExceptionMonitorResultHandler} 回调给宿主应用
 *
 * @Author: kmy
 */
@Data
@ToString
public class ExceptionMonitorResult {

    /**
     * 类全限定名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 方法入参（安全序列化后的字符串数组）
     */
    private String[] args;

    /**
     * 异常类名（如 java.lang.NullPointerException）
     */
    private String exceptionType;

    /**
     * 异常消息（可能为 null）
     */
    private String exceptionMessage;

    /**
     * 格式化的调用栈字符串（已过滤框架帧）
     */
    private String stackTrace;
}
