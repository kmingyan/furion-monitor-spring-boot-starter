package com.kmy.furion.core.advice;

import net.bytebuddy.asm.Advice;

/**
 * 方法追踪日志 Advice
 * 被 ByteBuddy 内联到目标方法中。
 * enter 捕获起始时间 + 入参，exit 计算耗时并捕获返回值，委托 TraceLogMonitor 处理。
 */
public class TraceLogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] enter(@Advice.AllArguments Object[] args) {
        Object[] enter = new Object[args == null ? 1 : args.length + 1];
        enter[0] = System.nanoTime();
        if (args != null) {
            System.arraycopy(args, 0, enter, 1, args.length);
        }
        return enter;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Origin Class<?> declaringClass,
                            @Advice.Origin("#m") String methodName,
                            @Advice.Enter Object[] enterData,
                            @Advice.Return(readOnly = true) Object returnValue,
                            @Advice.Thrown Throwable throwable) {
        long startNanos = (Long) enterData[0];
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        Object[] args = null;
        if (enterData.length > 1) {
            args = new Object[enterData.length - 1];
            System.arraycopy(enterData, 1, args, 0, args.length);
        }

        TraceLogMonitor.onMethodExit(declaringClass, methodName, durationMs, args, returnValue, throwable);
    }
}
