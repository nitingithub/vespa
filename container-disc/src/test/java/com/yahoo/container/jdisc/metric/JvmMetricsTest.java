package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;
import org.junit.Test;

import java.time.Clock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JvmMetricsTest {
    @Test
    public void jvm_metrics_reported_at_intervals() {
        Clock clock = mock(Clock.class);
        Metric m1 = mock(Metric.class, "initial");
        Metric m2 = mock(Metric.class, "first reported");
        Metric m3 = mock(Metric.class, "unreported");
        Metric m4 = mock(Metric.class, "second reported");

        long iv = JvmMetrics.REPORTING_INTERVAL;
        when(clock.millis()).thenReturn(10L);
        JvmMetrics jvmMetrics = new JvmMetrics(clock);

        when(clock.millis()).thenReturn(iv);
        jvmMetrics.emitMetrics(m1);

        when(clock.millis()).thenReturn(10L + iv);
        jvmMetrics.emitMetrics(m2);

        when(clock.millis()).thenReturn(20L + iv);
        jvmMetrics.emitMetrics(m3);

        when(clock.millis()).thenReturn(20L + iv + iv);
        jvmMetrics.emitMetrics(m4);

        verify(m1, never()).set(anyString(), any(), any());
        verify(m2, atLeast(4)).set(anyString(), any(), any());
        verify(m3, never()).set(anyString(), any(), any());
        verify(m4, atLeast(4)).set(anyString(), any(), any());
    }
}
