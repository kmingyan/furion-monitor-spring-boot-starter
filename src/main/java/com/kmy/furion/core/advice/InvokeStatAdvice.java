package com.kmy.furion.core.advice;

import net.bytebuddy.asm.Advice;

/**
 * 方法调用统计 Advice
 * 被 ByteBuddy 内联到目标方法中，记录每次调用耗时，
 * 委托给 InvokeStatCollector 进行指标累积。
 */
public class InvokeStatAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Origin Class<?> declaringClass,
                            @Advice.Origin("#m") String methodName,
                            @Advice.Enter long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        InvokeStatCollector.record(declaringClass, methodName, durationMs);
    }
}
