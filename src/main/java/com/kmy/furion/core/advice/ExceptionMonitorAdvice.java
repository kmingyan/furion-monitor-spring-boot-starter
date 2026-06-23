package com.kmy.furion.core.advice;

import net.bytebuddy.asm.Advice;

/**
 * 异常监控 Advice
 * 被 ByteBuddy 内联到目标方法中。
 * 注意：Advice 代码内联后运行在目标类的上下文中，
 * 因此不能调用本类的 private/package 方法，所有逻辑必须委托给 public 的 Service 类。
 */
public class ExceptionMonitorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] enter(@Advice.AllArguments Object[] args) {
        return args;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Origin Class<?> declaringClass,
                            @Advice.Origin("#m") String methodName,
                            @Advice.Enter Object[] args,
                            @Advice.Thrown Throwable throwable) {
        if (throwable == null) {
            return;
        }
        // 直接委托给 public 方法，避免内联后访问权限问题
        ExceptionMonitorService.onException(declaringClass, methodName, args, throwable);
    }
}
