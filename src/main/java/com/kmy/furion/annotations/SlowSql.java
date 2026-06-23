package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 慢 SQL 监控注解
 * 标注在执行 SQL 的方法或类上，当 SQL 执行耗时超过阈值时自动记录日志。
 * 阈值优先级：方法级 > 类级 > 全局配置 (furion.monitor.slow-sql-threshold-ms)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlowSql {

    /**
     * 慢 SQL 阈值（毫秒）
     * 默认 -1 表示使用全局配置 (furion.monitor.slow-sql-threshold-ms)
     */
    long thresholdMs() default -1;

}
