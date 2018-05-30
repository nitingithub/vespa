package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class JvmMetrics {
    private static final String GC_PREFIX = "jdisc.gc.";
    private static final String GC_COUNT_SUFFIX = ".count";
    private static final String GC_TIME_SUFFIX = ".ms";
    private static final String GC_TOTAL = "total";

    public static final long REPORTING_INTERVAL = Duration.ofMinutes(1).toMillis();

    private Map<String, Long> reportedGcMetrics;
    private long nextReportTime;

    private final Clock clock;

    public JvmMetrics(Clock clock) {
        this.clock = clock;
        this.reportedGcMetrics = new HashMap<>();
        this.nextReportTime = clock.millis() + REPORTING_INTERVAL;
    }

    public void emitMetrics(Metric metric) {
        if(clock.millis() >= nextReportTime) {
            collectGcMetrics(metric);
            nextReportTime = clock.millis() + REPORTING_INTERVAL;
        }
    }

    private void collectGcMetrics(Metric metric) {
        Map<String, Long> gcMetrics = new HashMap<>();

        for(GarbageCollectorMXBean gcBean: ManagementFactory.getGarbageCollectorMXBeans()) {
            gcMetrics.merge(GC_PREFIX + GC_TOTAL + GC_COUNT_SUFFIX, gcBean.getCollectionCount(), (x,y) -> x+y);
            gcMetrics.merge(GC_PREFIX + GC_TOTAL + GC_TIME_SUFFIX, gcBean.getCollectionTime(), (x,y) -> x+y);

            String gcName = convertGcName(gcBean.getName());
            gcMetrics.put(GC_PREFIX + gcName + GC_COUNT_SUFFIX, gcBean.getCollectionCount());
            gcMetrics.put(GC_PREFIX + gcName + GC_TIME_SUFFIX, gcBean.getCollectionTime());
        }

        for(Map.Entry<String, Long> item: gcMetrics.entrySet()) {
            long previouslyReported = reportedGcMetrics.getOrDefault(item.getKey(), 0L);
            metric.set(item.getKey(), item.getValue() - previouslyReported, null);
        }
        reportedGcMetrics = gcMetrics;
    }

    private static String convertGcName(String gcName) {
        return gcName.replace(' ', '_').toLowerCase();
    }
}
