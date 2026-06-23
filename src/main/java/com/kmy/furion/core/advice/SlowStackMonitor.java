package com.kmy.furion.core.advice;

import com.kmy.furion.annotations.SlowStack;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: kmy
 * @Description: 慢方法监控核心逻辑，从 Advice 中分离，避免字节码内联问题
 * @Date: create in 2026-06-22
 */
public class SlowStackMonitor {

    private static final Log log = LogFactory.getLog(SlowStackMonitor.class);

    private static final long CACHE_TTL_MS = 60_000L;

    private static final int CLEANUP_INTERVAL = 1000;

    private static final ConcurrentMap<String, ThresholdEntry> THRESHOLD_CACHE = new ConcurrentHashMap<>();

    private static final AtomicInteger CACHE_ACCESS_COUNT = new AtomicInteger(0);

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furion-monitor");
        t.setDaemon(true);
        return t;
    });

    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }

    public static void onMethodExit(Class<?> declaringClass, String methodName,
                                    long durationMs, Throwable throwable) {
        FurionProperties properties = getProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (properties.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate()) {
            return;
        }

        long threshold = resolveThreshold(declaringClass, methodName, properties);
        if (durationMs > threshold) {
            String className = declaringClass.getName();
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            ASYNC_EXECUTOR.execute(() ->
                    handleSlowMethod(className, methodName, durationMs, threshold, throwable, stackTrace, properties.getLogLevel()));
        }
    }

    private static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static long resolveThreshold(Class<?> declaringClass, String methodName,
                                         FurionProperties properties) {
        String key = declaringClass.getName() + "#" + methodName;
        long now = System.currentTimeMillis();

        // 每 1000 次访问清理一次过期条目，防止内存泄漏
        if (CACHE_ACCESS_COUNT.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanExpiredCache(now);
        }

        ThresholdEntry entry = THRESHOLD_CACHE.get(key);
        if (entry != null && (now - entry.timestamp) < CACHE_TTL_MS) {
            return entry.threshold;
        }

        long threshold = computeThreshold(declaringClass, methodName, properties);
        THRESHOLD_CACHE.put(key, new ThresholdEntry(threshold, now));
        return threshold;
    }

    private static void cleanExpiredCache(long now) {
        THRESHOLD_CACHE.entrySet().removeIf(entry ->
                (now - entry.getValue().timestamp) >= CACHE_TTL_MS);
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

    private static long computeThreshold(Class<?> declaringClass, String methodName,
                                         FurionProperties properties) {
        try {
            for (java.lang.reflect.Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    SlowStack methodAnno = m.getAnnotation(SlowStack.class);
                    if (methodAnno != null && methodAnno.thresholdMs() > 0) {
                        return methodAnno.thresholdMs();
                    }
                    break;
                }
            }
            SlowStack classAnno = declaringClass.getAnnotation(SlowStack.class);
            if (classAnno != null && classAnno.thresholdMs() > 0) {
                return classAnno.thresholdMs();
            }
        } catch (Exception ignored) {
        }
        return properties.getSlowThresholdMs();
    }

    private static void handleSlowMethod(String className, String methodName,
                                         long durationMs, long threshold,
                                         Throwable throwable, StackTraceElement[] stackTrace,
                                         String logLevel) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("\n[FURION-MONITOR] slow method detected!\n");
        sb.append("   Class : ").append(className).append('\n');
        sb.append("   Method: ").append(methodName).append('\n');
        sb.append("   Cost  : ").append(durationMs).append(" ms (Threshold: ").append(threshold).append(" ms)\n");
        if (throwable != null) {
            sb.append("   Error : ").append(throwable.getMessage()).append('\n');
        }
        sb.append("   Stack :\n");
        for (StackTraceElement element : stackTrace) {
            String cn = element.getClassName();
            if (!isFrameworkFrame(cn)) {
                sb.append("      at ").append(element).append('\n');
            }
        }
        SpringContextUtil.log(log, logLevel, sb.toString());
    }

    private static class ThresholdEntry {
        final long threshold;
        final long timestamp;

        ThresholdEntry(long threshold, long timestamp) {
            this.threshold = threshold;
            this.timestamp = timestamp;
        }
    }
}
