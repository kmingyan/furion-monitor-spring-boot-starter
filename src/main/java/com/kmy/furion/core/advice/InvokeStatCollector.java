package com.kmy.furion.core.advice;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 方法调用统计收集器
 * 由 InvokeStatAdvice 在每次方法执行完毕后调用 record()，
 * 将耗时写入对应方法的 MethodMetrics 中。
 * InvokeStatReporter 定时调用 snapshotAllAndReset() 拉取快照并输出日志。
 */
public class InvokeStatCollector {

    private static final ConcurrentMap<String, MethodMetrics> METRICS = new ConcurrentHashMap<>();

    /**
     * 记录一次方法调用耗时，由 Advice 调用
     */
    public static void record(Class<?> declaringClass, String methodName, long durationMs) {
        String key = declaringClass.getName() + "#" + methodName;
        METRICS.computeIfAbsent(key, k -> new MethodMetrics()).record(durationMs);
    }

    /**
     * 获取所有方法的快照并重置计数器，由 Reporter 定时调用
     */
    static ConcurrentMap<String, MethodMetrics.Snapshot> snapshotAllAndReset() {
        ConcurrentMap<String, MethodMetrics.Snapshot> result = new ConcurrentHashMap<>();
        METRICS.forEach((key, metrics) -> {
            MethodMetrics.Snapshot snapshot = metrics.snapshotAndReset();
            if (snapshot.count > 0) {
                result.put(key, snapshot);
            }
        });
        return result;
    }
}
