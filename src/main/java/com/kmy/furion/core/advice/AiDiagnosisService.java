package com.kmy.furion.core.advice;

import com.kmy.furion.annotations.AiDiagnosis;
import com.kmy.furion.core.extract.ExtractMethodContent;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 诊断核心服务
 * 从 Advice 中分离，避免字节码内联问题。
 * 负责：提取方法源码、入参安全序列化、采样控制、异步日志输出。
 *
 * Phase 1：仅打印方法内容和入参
 * Phase 2：调用大模型 API 分析异常原因
 */
public class AiDiagnosisService {

    private static final Log log = LogFactory.getLog(AiDiagnosisService.class);

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furion-ai-diagnosis");
        t.setDaemon(true);
        return t;
    });

    /**
     * 注解缓存：方法 -> @AiDiagnosis 注解属性
     * key: Method, value: AiDiagnosis 注解
     */
    private static final ConcurrentHashMap<Method, AiDiagnosis> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    /**
     * 方法源码缓存：Method -> 反编译后的源码
     * 避免重复反编译
     */
    private static final ConcurrentHashMap<Method, String> SOURCE_CODE_CACHE = new ConcurrentHashMap<>();

    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }

    /**
     * 由 AiDiagnosisAdvice.exit() 在方法抛出异常时调用。
     * 注意：此方法必须是 public static，因为会被 Advice 内联代码从目标类中调用。
     */
    public static void onException(Class<?> declaringClass, Method method,
                                    Object[] args, Throwable throwable) {
        FurionProperties properties = getProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (properties.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate()) {
            return;
        }

        // 获取注解属性（带缓存）
        AiDiagnosis annotation = getAnnotation(method, declaringClass);
        if (annotation == null) {
            return;
        }

        // 在当前线程中做入参安全序列化（异步线程中对象状态可能已变）
        String[] argStrings = annotation.logArgs() ? toSafeStrings(args) : null;

        // 在当前线程捕获堆栈（异步线程中堆栈已不完整）
        StackTraceElement[] stackTrace = annotation.logStackTrace()
                ? Thread.currentThread().getStackTrace()
                : null;

        // 提取注解属性（避免在异步线程中访问 Method 对象）
        boolean logSourceCode = annotation.logSourceCode();
        boolean logStackTrace = annotation.logStackTrace();

        ASYNC_EXECUTOR.execute(() ->
                handleException(declaringClass, method, argStrings, throwable,
                        stackTrace, logSourceCode, logStackTrace, properties.getLogLevel()));
    }

    /**
     * 获取 @AiDiagnosis 注解（方法级 > 类级）
     * 带缓存，每 1000 次访问清理一次过期条目
     */
    private static AiDiagnosis getAnnotation(Method method, Class<?> declaringClass) {
        return ANNOTATION_CACHE.compute(method, (k, existing) -> {
            if (existing != null) {
                return existing;
            }
            // 优先方法级
            AiDiagnosis annotation = method.getAnnotation(AiDiagnosis.class);
            if (annotation == null) {
                // 降级到类级
                annotation = declaringClass.getAnnotation(AiDiagnosis.class);
            }
            return annotation;
        });
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
            if (s.length() > 500) {
                return s.substring(0, 500) + "...(truncated)";
            }
            return s;
        }
        if (arg.getClass().isArray()) {
            return arg.getClass().getComponentType().getSimpleName() + "[]";
        }
        return arg.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(arg));
    }

    private static void handleException(Class<?> declaringClass, Method method,
                                         String[] argStrings, Throwable throwable,
                                         StackTraceElement[] stackTrace,
                                         boolean logSourceCode, boolean logStackTrace,
                                         String logLevel) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("\n[FURION-AI-DIAGNOSIS] exception caught!\n");
        sb.append("   Class   : ").append(declaringClass.getName()).append('\n');
        sb.append("   Method  : ").append(method.getName()).append('\n');

        // 入参
        if (argStrings != null && argStrings.length > 0) {
            sb.append("   Args    : [\n");
            for (int i = 0; i < argStrings.length; i++) {
                sb.append("      [").append(i).append("] ").append(argStrings[i]).append('\n');
            }
            sb.append("   ]\n");
        }

        // 异常信息
        sb.append("   Exception: ").append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(" - ").append(throwable.getMessage());
        }
        sb.append('\n');

        // 调用栈（过滤框架帧）
        if (logStackTrace && stackTrace != null) {
            sb.append("   Stack   :\n");
            for (StackTraceElement element : stackTrace) {
                String cn = element.getClassName();
                if (!isFrameworkFrame(cn)) {
                    sb.append("      at ").append(element).append('\n');
                }
            }
        }

        // 方法源码（反编译）
        if (logSourceCode) {
            try {
                String sourceCode = getSourceCode(declaringClass, method);
                sb.append("\n   Method Source Code:\n");
                sb.append("   =================\n");
                for (String line : sourceCode.split("\n")) {
                    sb.append("   ").append(line).append('\n');
                }
                sb.append("   =================\n");
            } catch (Exception e) {
                sb.append("   [Failed to extract source code: ").append(e.getMessage()).append("]\n");
            }
        }

        SpringContextUtil.log(log, logLevel, sb.toString(), throwable);
    }

    /**
     * 获取方法源码（带缓存）
     */
    private static String getSourceCode(Class<?> declaringClass, Method method) {
        return SOURCE_CODE_CACHE.computeIfAbsent(method, k -> {
            try {
                return ExtractMethodContent.getMethodContent(declaringClass, method);
            } catch (Exception e) {
                return "// Failed to decompile: " + e.getMessage();
            }
        });
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
