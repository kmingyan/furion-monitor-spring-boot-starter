package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法调用统计注解
 * 标注在方法或类上，自动统计调用次数、平均耗时、P50/P90/P99 耗时、最大耗时。
 * 定时汇总输出到日志，输出后计数器归零，开始下一个周期统计。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InvokeStat {

}
