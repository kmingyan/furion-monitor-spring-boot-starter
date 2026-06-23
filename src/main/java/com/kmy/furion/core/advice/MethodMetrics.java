package com.kmy.furion.core.advice;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单方法调用指标统计
 * 使用 LongAdder 记录调用次数和总耗时（高并发下比 AtomicLong 快），
 * 使用固定大小的环形数组采样记录耗时，用于近似计算 P50/P90/P99。
 */
public class MethodMetrics {

    private static final int MAX_SAMPLES = 1024;

    private final LongAdder callCount = new LongAdder();
    private final LongAdder totalTimeMs = new LongAdder();
    private final AtomicLong maxTimeMs = new AtomicLong(0);
    private final long[] samples = new long[MAX_SAMPLES];
    private final AtomicInteger sampleIndex = new AtomicInteger(0);

    public void record(long durationMs) {
        callCount.increment();
        totalTimeMs.add(durationMs);

        // CAS 更新最大耗时
        long current;
        do {
            current = maxTimeMs.get();
            if (durationMs <= current) break;
        } while (!maxTimeMs.compareAndSet(current, durationMs));

        // 环形采样：超过 MAX_SAMPLES 后覆盖最早的样本
        int idx = sampleIndex.getAndIncrement() % MAX_SAMPLES;
        samples[idx] = durationMs;
    }

    /**
     * 快照当前指标并重置计数器，开始下一个统计周期
     */
    public Snapshot snapshotAndReset() {
        long count = callCount.sumThenReset();
        long total = totalTimeMs.sumThenReset();
        long max = maxTimeMs.getAndSet(0);

        int totalSamples = Math.min(sampleIndex.getAndSet(0), MAX_SAMPLES);
        long[] sorted = Arrays.copyOf(samples, totalSamples);
        Arrays.sort(sorted);

        return new Snapshot(count, total, max, sorted);
    }

    public static class Snapshot {
        public final long count;
        public final long totalMs;
        public final long maxMs;
        public final long p50;
        public final long p90;
        public final long p99;

        Snapshot(long count, long totalMs, long maxMs, long[] sortedSamples) {
            this.count = count;
            this.totalMs = totalMs;
            this.maxMs = maxMs;
            if (sortedSamples.length > 0) {
                this.p50 = percentile(sortedSamples, 50);
                this.p90 = percentile(sortedSamples, 90);
                this.p99 = percentile(sortedSamples, 99);
            } else {
                this.p50 = this.p90 = this.p99 = 0;
            }
        }

        private static long percentile(long[] sorted, int p) {
            if (sorted.length == 0) return 0;
            int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
            index = Math.max(0, Math.min(index, sorted.length - 1));
            return sorted[index];
        }
    }
}
