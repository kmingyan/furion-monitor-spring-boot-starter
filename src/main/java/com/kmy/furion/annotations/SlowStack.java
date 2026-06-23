package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要监控的方法或类
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlowStack {

    /**
     * 慢请求阈值(毫秒)
     * 默认 -1 表示使用全局配置 (furion.monitor.slow-threshold-ms)
     */
    long thresholdMs() default -1;

}
