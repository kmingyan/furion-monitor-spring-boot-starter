package com.kmy.furion.core.advice;

import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 异常监控核心逻辑
 * 从 Advice 中分离，避免字节码内联问题。
 * 负责：入参安全序列化、采样控制、异步日志输出、异常信息格式化。
 */
public class ExceptionMonitorService {

    private static final Log log = LogFactory.getLog(ExceptionMonitorService.class);

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furion-exception-monitor");
        t.setDaemon(true);
        return t;
    });

    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }

    /**
     * 由 ExceptionMonitorAdvice.exit() 在方法抛出异常时调用。
     * 注意：此方法必须是 public static，因为会被 Advice 内联代码从目标类中调用。
     */
    public static void onException(Class<?> declaringClass, String methodName,
                                    Object[] args, Throwable throwable) {
        FurionProperties properties = getProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (properties.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate()) {
            return;
        }

        // 在当前线程中做入参安全序列化（异步线程中对象状态可能已变）
        String[] argStrings = toSafeStrings(args);

        // 在当前线程捕获堆栈（异步线程中堆栈已不完整）
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        ASYNC_EXECUTOR.execute(() ->
                handleException(declaringClass, methodName, argStrings, throwable, stackTrace, properties.getLogLevel()));
    }

    /**
     * 安全地将入参数组转为字符串数组。
     * 基础类型和 String 打印值，复杂对象只打印类型，避免触发懒加载等副作用。
     * 必须是 public static，因为会被 Advice 内联代码间接调用。
     */
    public static String[] toSafeStrings(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String[] result = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = safeToString(args[i]);
        }
        return result;
    }

    public static String safeToString(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof String || arg instanceof Number || arg instanceof Boolean || arg instanceof Character) {
            String s = String.valueOf(arg);
            if (s.length() > 200) {
                return s.substring(0, 200) + "...(truncated)";
            }
            return s;
        }
        if (arg.getClass().isArray()) {
            return arg.getClass().getComponentType().getSimpleName() + "[]";
        }
        return arg.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(arg));
    }

    private static void handleException(Class<?> declaringClass, String methodName,
                                         String[] argStrings, Throwable throwable,
                                         StackTraceElement[] stackTrace,
                                         String logLevel) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n[FURION-MONITOR] exception caught!\n");
        sb.append("   Class   : ").append(declaringClass.getName()).append('\n');
        sb.append("   Method  : ").append(methodName).append('\n');

        // 入参
        if (argStrings != null && argStrings.length > 0) {
            sb.append("   Args    : [");
            for (int i = 0; i < argStrings.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(argStrings[i]);
            }
            sb.append("]\n");
        }

        // 异常信息
        sb.append("   Exception: ").append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(" - ").append(throwable.getMessage());
        }
        sb.append('\n');

        // 调用栈（过滤框架帧）
        sb.append("   Stack   :\n");
        for (StackTraceElement element : stackTrace) {
            String cn = element.getClassName();
            if (!isFrameworkFrame(cn)) {
                sb.append("      at ").append(element).append('\n');
            }
        }

        SpringContextUtil.log(log, logLevel, sb.toString(), throwable);
    }

    private static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isFrameworkFrame(String className) {
        return className.startsWith("java.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.")
                || className.startsWith("jdk.")
                || className.startsWith("org.apache.catalina.")
                || className.startsWith("org.apache.coyote.")
                || className.startsWith("org.apache.tomcat.")
                || className.startsWith("org.springframework.")
                || className.startsWith("com.kmy.furion.")
                || className.startsWith("net.bytebuddy.");
    }
}
