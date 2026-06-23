package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法追踪日志注解
 * 标注在方法或类上，每次方法调用时记录执行耗时、入参和返回值。
 * 可通过 logArgs / logResult 灵活控制是否记录入参和出参。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TraceLog {

    /**
     * 是否记录方法入参，默认 true
     */
    boolean logArgs() default true;

    /**
     * 是否记录方法返回值，默认 true
     */
    boolean logResult() default true;
}
