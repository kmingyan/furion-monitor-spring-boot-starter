package com.kmy.furion.core.advice;

import com.kmy.furion.annotations.SlowSql;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 慢 SQL 监控核心逻辑
 * 从 Advice 中分离，避免字节码内联问题。
 * 负责：阈值解析、调用栈分析、异步日志输出。
 */
public class SlowSqlMonitor {

    private static final Log log = LogFactory.getLog(SlowSqlMonitor.class);

    /**
     * 去重机制：记录 [sqlHashCode, nanoTime]，
     * 同一线程上相同 SQL 在 50ms 内只记录一次，
     * 防止 ByteBuddy 对继承链中多个 execute* 方法重复拦截
     */
    private static final ThreadLocal<long[]> LAST_SQL = new ThreadLocal<>();
    private static final long DEDUP_WINDOW_NS = 50_000_000L; // 50ms

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furion-sql-monitor");
        t.setDaemon(true);
        return t;
    });

    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }

    /**
     * 由 SlowSqlJdbcAdvice.exit() 调用，每次 SQL 执行都会进入
     */
    public static void onSqlExecute(String sql, long durationMs) {
        // 去重：同一条 SQL 在同一线程上 50ms 内只处理一次
        int sqlHash = sql != null ? sql.hashCode() : 0;
        long nowNanos = System.nanoTime();
        long[] last = LAST_SQL.get();
        if (last != null && last[0] == sqlHash && (nowNanos - last[1]) < DEDUP_WINDOW_NS) {
            return;
        }
        LAST_SQL.set(new long[]{sqlHash, nowNanos});

        doOnSqlExecute(sql, durationMs);
    }

    private static void doOnSqlExecute(String sql, long durationMs) {
        FurionProperties properties = getProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (properties.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate()) {
            return;
        }

        // 先用全局阈值做快速判断，避免每次都走堆栈分析
        long globalThreshold = properties.getSlowSqlThresholdMs();
        if (durationMs <= globalThreshold) {
            return;
        }

        // 超过全局阈值，进一步分析堆栈以获取精确阈值和调用信息
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackAnalysisResult result = analyzeStackTrace(stackTrace);

        // 用精确阈值再次判断
        long threshold = resolveThreshold(result, properties);
        if (durationMs <= threshold) {
            return;
        }

        ASYNC_EXECUTOR.execute(() ->
                handleSlowSql(sql, durationMs, threshold, result, stackTrace, properties.getLogLevel()));
    }

    /**
     * 分析调用栈：找到调用者信息和 @SlowSql 注解位置
     */
    private static StackAnalysisResult analyzeStackTrace(StackTraceElement[] stackTrace) {
        StackAnalysisResult result = new StackAnalysisResult();

        for (int i = 0; i < stackTrace.length; i++) {
            String cn = stackTrace[i].getClassName();

            // 跳过 JDK、JDBC 驱动、连接池、Spring 框架、自身代码
            if (isFrameworkFrame(cn)) {
                continue;
            }

            // 第一个非框架帧 = 调用者（通常是 DAO/Repository 方法）
            if (result.callerClass == null) {
                result.callerClass = cn;
                result.callerMethod = stackTrace[i].getMethodName();
                result.callerLineNumber = stackTrace[i].getLineNumber();
            }

            // 继续向上查找 @SlowSql 注解
            if (result.slowSqlClass == null) {
                try {
                    Class<?> clazz = Class.forName(cn);
                    // 检查方法级注解
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.getName().equals(stackTrace[i].getMethodName())) {
                            SlowSql anno = m.getAnnotation(SlowSql.class);
                            if (anno != null) {
                                result.slowSqlClass = clazz;
                                result.slowSqlMethod = m;
                                break;
                            }
                        }
                    }
                    // 检查类级注解
                    if (result.slowSqlClass == null) {
                        SlowSql classAnno = clazz.getAnnotation(SlowSql.class);
                        if (classAnno != null) {
                            result.slowSqlClass = clazz;
                        }
                    }
                } catch (Throwable ignored) {
                    // Class.forName 可能失败（类加载器问题），忽略
                }
            }
        }

        return result;
    }

    private static boolean isFrameworkFrame(String className) {
        return className.startsWith("java.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.")
                || className.startsWith("jdk.")
                || className.startsWith("com.mysql.")
                || className.startsWith("com.mysql.cj.")
                || className.startsWith("org.postgresql.")
                || className.startsWith("oracle.jdbc.")
                || className.startsWith("com.zaxxer.hikari.")
                || className.startsWith("org.apache.catalina.")
                || className.startsWith("org.apache.coyote.")
                || className.startsWith("org.apache.tomcat.")
                || className.startsWith("org.springframework.jdbc.")
                || className.startsWith("org.springframework.transaction.")
                || className.startsWith("org.mybatis.")
                || className.startsWith("org.apache.ibatis.")
                || className.startsWith("com.kmy.furion.")
                || className.startsWith("net.bytebuddy.");
    }

    private static long resolveThreshold(StackAnalysisResult result, FurionProperties properties) {
        // 方法级注解
        if (result.slowSqlMethod != null) {
            SlowSql anno = result.slowSqlMethod.getAnnotation(SlowSql.class);
            if (anno != null && anno.thresholdMs() > 0) {
                return anno.thresholdMs();
            }
        }
        // 类级注解
        if (result.slowSqlClass != null) {
            SlowSql classAnno = result.slowSqlClass.getAnnotation(SlowSql.class);
            if (classAnno != null && classAnno.thresholdMs() > 0) {
                return classAnno.thresholdMs();
            }
        }
        // 全局配置
        return properties.getSlowSqlThresholdMs();
    }

    private static void handleSlowSql(String sql, long durationMs, long threshold,
                                       StackAnalysisResult result,
                                       StackTraceElement[] stackTrace,
                                       String logLevel) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n[FURION-MONITOR] slow sql detected!\n");
        sb.append("   SQL    : ").append(truncateSql(sql)).append('\n');
        sb.append("   Cost   : ").append(durationMs).append(" ms (Threshold: ").append(threshold).append(" ms)\n");
        if (result.callerClass != null) {
            sb.append("   Caller : ").append(result.callerClass)
                    .append('#').append(result.callerMethod)
                    .append("(line:").append(result.callerLineNumber).append(")\n");
        }
        sb.append("   Stack  :\n");
        for (StackTraceElement element : stackTrace) {
            String cn = element.getClassName();
            if (!isFrameworkFrame(cn)) {
                sb.append("      at ").append(element).append('\n');
            }
        }
        SpringContextUtil.log(log, logLevel, sb.toString());
    }

    /**
     * 截断过长的 SQL，避免日志爆炸
     */
    private static String truncateSql(String sql) {
        if (sql == null) return "[null]";
        String trimmed = sql.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 500) {
            return trimmed.substring(0, 500) + "... (truncated)";
        }
        return trimmed;
    }

    private static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 堆栈分析结果
     */
    private static class StackAnalysisResult {
        String callerClass;
        String callerMethod;
        int callerLineNumber;
        Class<?> slowSqlClass;
        Method slowSqlMethod;
    }
}
