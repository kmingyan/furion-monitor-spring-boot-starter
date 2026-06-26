package com.kmy.furion.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 诊断注解
 * 标注在方法或类上，当方法抛出异常时：
 * - Phase 1（始终执行）：自动提取方法源码、入参、堆栈，输出格式化日志
 * - Phase 2（需 enabled=true + 配置 apiKey）：调用大模型 API 分析异常原因，通过 AiDiagnosisResultHandler 回调诊断结果
 *
 * @author kmy
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
