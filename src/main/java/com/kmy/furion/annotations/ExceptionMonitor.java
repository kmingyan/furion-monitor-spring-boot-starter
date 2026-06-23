package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 异常监控注解
 * 标注在方法或类上，当方法抛出异常时自动记录日志，
 * 包含异常类型、消息、方法入参和调用栈，方便线上排障。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExceptionMonitor {

}
