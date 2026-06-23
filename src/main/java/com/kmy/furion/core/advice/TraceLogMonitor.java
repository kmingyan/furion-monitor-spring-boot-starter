package com.kmy.furion.core.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmy.furion.annotations.TraceLog;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 方法追踪日志核心逻辑
 * 从 Advice 中分离，避免字节码内联问题。
 * 负责：注解属性解析、入参/出参安全序列化、采样控制、异步日志输出。
 */
public class TraceLogMonitor {

    //处理结果序列化
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Log log = LogFactory.getLog(TraceLogMonitor.class);

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furion-trace-log");
        t.setDaemon(true);
        return t;
    });

    /** 缓存注解属性：key = "className#methodName"，value = [logArgs, logResult] */
    private static final ConcurrentMap<String, boolean[]> ATTR_CACHE = new ConcurrentHashMap<>();
    private static long accessCount = 0;
    private static final long CLEANUP_INTERVAL = 1000;

    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }

    /**
     * 由 TraceLogAdvice.exit() 调用。
     * 必须是 public static，因为会被 Advice 内联代码从目标类中调用。
     */
    public static void onMethodExit(Class<?> declaringClass, String methodName,
                                     long durationMs, Object[] args,
                                     Object returnValue, Throwable throwable) {
        FurionProperties properties = getProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (properties.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate()) {
            return;
        }

        // 解析注解属性
        boolean[] attrs = resolveAttributes(declaringClass, methodName);
        boolean logArgs = attrs[0];
        boolean logResult = attrs[1];

        // 在当前线程中做安全序列化（异步线程中对象状态可能已变）
        String[] argStrings = logArgs ? toSafeStrings(args) : null;
        String resultString = logResult ? safeToString(returnValue) : null;

        ASYNC_EXECUTOR.execute(() ->
                handleTraceLog(declaringClass, methodName, durationMs,
                        argStrings, resultString, throwable, properties.getLogLevel()));
    }

    // ========================= 注解属性解析 =========================

    private static boolean[] resolveAttributes(Class<?> declaringClass, String methodName) {
        String key = declaringClass.getName() + "#" + methodName;
        boolean[] cached = ATTR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        // 定期清理缓存，防止类卸载后内存泄漏
        if (++accessCount % CLEANUP_INTERVAL == 0) {
            ATTR_CACHE.clear();
        }

        boolean[] attrs = computeAttributes(declaringClass, methodName);
        ATTR_CACHE.put(key, attrs);
        return attrs;
    }

    private static boolean[] computeAttributes(Class<?> declaringClass, String methodName) {
        // 优先取方法级注解
        try {
            for (Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    TraceLog tl = m.getAnnotation(TraceLog.class);
                    if (tl != null) {
                        return new boolean[]{tl.logArgs(), tl.logResult()};
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 再取类级注解
        TraceLog tl = declaringClass.getAnnotation(TraceLog.class);
        if (tl != null) {
            return new boolean[]{tl.logArgs(), tl.logResult()};
        }

        // 默认全部记录
        return new boolean[]{true, true};
    }

    // ========================= 安全序列化 =========================

    private static String[] toSafeStrings(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String[] result = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = safeToString(args[i]);
        }
        return result;
    }

    private static String safeToString(Object arg) {
        if (arg == null) {
            return "null";
        }
        // 处理集合类型（List, Set, Map 等）
        if (arg instanceof Collection || arg instanceof Map) {
            try {
                return objectMapper.writeValueAsString(arg);
            } catch (JsonProcessingException e) {
                return "JSON_ERROR: " + e.getMessage();
            }
        }

        //处理数组
        if (arg.getClass().isArray()) {
            try {
                return objectMapper.writeValueAsString(arg);
            } catch (JsonProcessingException e) {
                return "ARRAY_JSON_ERROR: " + e.getMessage();
            }
        }

        if (arg instanceof String || arg instanceof Number || arg instanceof Boolean || arg instanceof Character) {
            String s = String.valueOf(arg);
            if (s.length() > 200) {
                return s.substring(0, 200) + "...(truncated)";
            }
            return s;
        }
        return arg.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(arg));
    }

    // ========================= 异步日志格式化 =========================

    private static void handleTraceLog(Class<?> declaringClass, String methodName,
                                        long durationMs, String[] argStrings,
                                        String resultString, Throwable throwable,
                                        String logLevel) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n[FURION-TRACE] method invoked\n");
        sb.append("   Class   : ").append(declaringClass.getName()).append('\n');
        sb.append("   Method  : ").append(methodName).append('\n');
        sb.append("   Duration: ").append(durationMs).append("ms\n");

        // 入参
        if (argStrings != null && argStrings.length > 0) {
            sb.append("   Args    : [");
            for (int i = 0; i < argStrings.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(argStrings[i]);
            }
            sb.append("]\n");
        }

        // 返回值
        if (resultString != null) {
            sb.append("   Return  : ").append(resultString).append('\n');
        }

        // 异常
        if (throwable != null) {
            sb.append("   Exception: ").append(throwable.getClass().getName());
            if (throwable.getMessage() != null) {
                sb.append(" - ").append(throwable.getMessage());
            }
            sb.append('\n');
        }

        SpringContextUtil.log(log, logLevel, sb.toString());
    }

    private static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }
}
