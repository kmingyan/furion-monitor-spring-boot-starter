package com.kmy.furion.core.advice;

import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 方法调用统计定时报告器
 * 定时从 InvokeStatCollector 拉取快照，格式化输出到日志，然后重置计数器。
 */
public class InvokeStatReporter {

    private static final Log log = LogFactory.getLog("FURION-INVOKE-STAT");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "furion-stat-reporter");
        t.setDaemon(true);
        return t;
    });

    public static void start(long intervalSeconds) {
        SCHEDULER.scheduleAtFixedRate(InvokeStatReporter::report,
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
    }

    private static void report() {
        try {
            FurionProperties properties = getProperties();
            if (properties == null || !properties.isEnabled()) {
                return;
            }
            String logLevel = properties.getLogLevel();

            ConcurrentMap<String, MethodMetrics.Snapshot> snapshots = InvokeStatCollector.snapshotAllAndReset();
            if (snapshots.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            StringBuilder sb = new StringBuilder(snapshots.size() * 100 + 64);
            sb.append("\n[FURION-STAT] ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd ")))
                    .append(now.format(TIME_FMT))
                    .append(" (").append(snapshots.size()).append(" methods)\n");

            snapshots.forEach((key, snapshot) -> {
                long avg = snapshot.count > 0 ? snapshot.totalMs / snapshot.count : 0;
                sb.append("   ").append(key).append('\n')
                        .append("     Calls: ").append(snapshot.count)
                        .append(" | Avg: ").append(avg).append("ms")
                        .append(" | P50: ").append(snapshot.p50).append("ms")
                        .append(" | P90: ").append(snapshot.p90).append("ms")
                        .append(" | P99: ").append(snapshot.p99).append("ms")
                        .append(" | Max: ").append(snapshot.maxMs).append("ms\n");
            });

            SpringContextUtil.log(log, logLevel, sb.toString());
        } catch (Throwable e) {
            log.warn("[Furion-Monitor] stat report error: " + e.getMessage());
        }
    }

    private static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }
}
