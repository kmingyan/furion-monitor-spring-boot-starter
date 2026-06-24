package com.kmy.furion.core.agent;

import java.lang.instrument.Instrumentation;
import java.sql.Statement;

import com.kmy.furion.core.advice.AiDiagnosisAdvice;
import com.kmy.furion.core.advice.ExceptionMonitorAdvice;
import com.kmy.furion.core.advice.InvokeStatAdvice;
import com.kmy.furion.core.advice.SlowSqlJdbcAdvice;
import com.kmy.furion.core.advice.SlowStackAdvice;
import com.kmy.furion.core.advice.TraceLogAdvice;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * @Author: kmy
 * @Description:
 * @Date: create in 2026-06-22 10:35
 */
public class FurionAgentInstaller {

    private static final String SLOW_STACK_ANNOTATION = "com.kmy.furion.annotations.SlowStack";
    private static final String EXCEPTION_MONITOR_ANNOTATION = "com.kmy.furion.annotations.ExceptionMonitor";
    private static final String INVOKE_STAT_ANNOTATION = "com.kmy.furion.annotations.InvokeStat";
    private static final String TRACE_LOG_ANNOTATION = "com.kmy.furion.annotations.TraceLog";
    private static final String AI_DIAGNOSIS_ANNOTATION = "com.kmy.furion.annotations.AiDiagnosis";

    private static volatile boolean installed = false;

    public static void install() {
        if (installed) {
            return;
        }
        synchronized (FurionAgentInstaller.class) {
            if (installed) return;

            try {
                Instrumentation instrumentation = ByteBuddyAgent.install();

                // ========== Advice 定义 ==========
                AsmVisitorWrapper slowStackAdvice = Advice.to(SlowStackAdvice.class)
                        .on(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(SLOW_STACK_ANNOTATION)));

                AsmVisitorWrapper slowSqlAdvice = Advice.to(SlowSqlJdbcAdvice.class)
                        .on(ElementMatchers.nameStartsWith("execute"));

                AsmVisitorWrapper exceptionMonitorAdvice = Advice.to(ExceptionMonitorAdvice.class)
                        .on(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(EXCEPTION_MONITOR_ANNOTATION)));

                AsmVisitorWrapper invokeStatAdvice = Advice.to(InvokeStatAdvice.class)
                        .on(ElementMatchers.isAnnotatedWith(
                                        ElementMatchers.named(INVOKE_STAT_ANNOTATION))
                                .or(ElementMatchers.isDeclaredBy(
                                        ElementMatchers.isAnnotatedWith(
                                                ElementMatchers.named(INVOKE_STAT_ANNOTATION)))));

                AsmVisitorWrapper traceLogAdvice = Advice.to(TraceLogAdvice.class)
                        .on(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(TRACE_LOG_ANNOTATION)));

                AsmVisitorWrapper aiDiagnosisAdvice = Advice.to(AiDiagnosisAdvice.class)
                        .on(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(AI_DIAGNOSIS_ANNOTATION)));

                // ========== AgentBuilder ==========
                new AgentBuilder.Default()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .disableClassFormatChanges()
                        .ignore(ElementMatchers.nameStartsWith("sun.")
                                .or(ElementMatchers.nameStartsWith("com.sun."))
                                .or(ElementMatchers.nameStartsWith("net.bytebuddy."))
                                .or(ElementMatchers.nameStartsWith("com.kmy.furion.")))
                        // --- 链1：@SlowStack 慢方法 ---
                        .type(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(SLOW_STACK_ANNOTATION)
                        ).or(ElementMatchers.declaresMethod(
                                ElementMatchers.isAnnotatedWith(
                                        ElementMatchers.named(SLOW_STACK_ANNOTATION)
                                )
                        )))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(slowStackAdvice))
                        // --- 链2：JDBC Statement 慢 SQL ---
                        .type(ElementMatchers.isSubTypeOf(Statement.class))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(slowSqlAdvice))
                        // --- 链3：@ExceptionMonitor 异常监控 ---
                        .type(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(EXCEPTION_MONITOR_ANNOTATION)
                        ).or(ElementMatchers.declaresMethod(
                                ElementMatchers.isAnnotatedWith(
                                        ElementMatchers.named(EXCEPTION_MONITOR_ANNOTATION)
                                )
                        )))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(exceptionMonitorAdvice))
                        // --- 链4：@InvokeStat 方法调用统计 ---
                        .type(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(INVOKE_STAT_ANNOTATION)
                        ).or(ElementMatchers.declaresMethod(
                                ElementMatchers.isAnnotatedWith(
                                        ElementMatchers.named(INVOKE_STAT_ANNOTATION)
                                )
                        )))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(invokeStatAdvice))
                        // --- 链5：@TraceLog 方法追踪日志 ---
                        .type(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(TRACE_LOG_ANNOTATION)
                        ).or(ElementMatchers.declaresMethod(
                                ElementMatchers.isAnnotatedWith(
                                        ElementMatchers.named(TRACE_LOG_ANNOTATION)
                                )
                        )))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(traceLogAdvice))
                        // --- 链6：@AiDiagnosis AI 诊断 ---
                        .type(ElementMatchers.isAnnotatedWith(
                                ElementMatchers.named(AI_DIAGNOSIS_ANNOTATION)
                        ).or(ElementMatchers.declaresMethod(
                                ElementMatchers.isAnnotatedWith(
                                        ElementMatchers.named(AI_DIAGNOSIS_ANNOTATION)
                                )
                        )))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(aiDiagnosisAdvice))
                        .installOn(instrumentation);

                installed = true;
            } catch (Throwable e) {
                System.err.println("[Furion-Monitor] Agent install failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}
