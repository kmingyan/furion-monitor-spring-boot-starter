package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 诊断注解
 * 标注在方法或类上，当方法抛出异常时，自动提取方法源码、入参信息，
 * 为后续调用大模型进行异常分析提供数据支持。
 *
 * 当前版本（Phase 1）：仅打印方法内容和入参
 * 后续版本（Phase 2）：调用大模型 API 分析异常原因并推送诊断结果
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiDiagnosis {

    /**
     * 是否启用 AI 诊断（方法级开关）
     * 设为 false 后该方法的异常仅输出 Phase 1 日志，不调用大模型
     * 默认 true
     */
    boolean enabled() default true;

    /**
     * 是否记录方法入参
     * 默认 true
     */
    boolean logArgs() default true;

    /**
     * 是否记录方法源码
     * 默认 true
     */
    boolean logSourceCode() default true;

    /**
     * 是否记录异常堆栈
     * 默认 true
     */
    boolean logStackTrace() default true;

}
