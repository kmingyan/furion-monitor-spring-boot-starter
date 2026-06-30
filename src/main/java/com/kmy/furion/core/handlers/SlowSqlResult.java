package com.kmy.furion.core.handlers;

import lombok.Data;
import lombok.ToString;

/**
 * 慢 SQL 监控结果
 * 封装慢 SQL 检测的上下文信息，通过 {@link SlowSqlResultHandler} 回调给宿主应用
 *
 * @Author: kmy
 */
@Data
@ToString
public class SlowSqlResult {

    /**
     * SQL 语句（已截断、归一化空白）
     */
    private String sql;

    /**
     * 实际执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 阈值（毫秒）
     */
    private long thresholdMs;

    /**
     * 调用者类名（DAO/Repository 层）
     */
    private String callerClass;

    /**
     * 调用者方法名
     */
    private String callerMethod;

    /**
     * 调用者行号
     */
    private int callerLineNumber;

    /**
     * 格式化的调用栈字符串（已过滤框架帧）
     */
    private String stackTrace;
}
