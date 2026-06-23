package com.kmy.furion.core.advice;

import net.bytebuddy.asm.Advice;

/**
 * @Author: kmy
 * @Description: Advice 方法体被 ByteBuddy 内联到目标方法中，因此只做最小委托
 * @Date: create in 2026-06-22 10:11
 */
public class SlowStackAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Origin Class<?> declaringClass,
                            @Advice.Origin("#m") String methodName,
                            @Advice.Enter long startNanos,
                            @Advice.Thrown Throwable throwable) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        SlowStackMonitor.onMethodExit(declaringClass, methodName, durationMs, throwable);
    }
}
