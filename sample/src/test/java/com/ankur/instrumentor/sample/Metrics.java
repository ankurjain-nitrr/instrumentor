package com.ankur.instrumentor.sample;

import io.prometheus.client.CollectorRegistry;

/**
 * Reads AppMetricsAdvice's metrics from the default registry. Every test class in the JVM shares
 * that registry, so tests compare values before and after a call instead of expecting absolutes.
 */
final class Metrics {

    private static final String[] COUNTER_LABELS = {"category", "filter_name", "status", "exception"};
    private static final String[] HISTOGRAM_LABELS = {"execution_time"};

    private Metrics() {}

    static double counter(String category, String filterName, String status, String exception) {
        return orZero(CollectorRegistry.defaultRegistry.getSampleValue(
                "events_counter_total", COUNTER_LABELS, new String[]{category, filterName, status, exception}));
    }

    static double histogramCount(String filterName) {
        return orZero(CollectorRegistry.defaultRegistry.getSampleValue(
                "latency_histogram_count", HISTOGRAM_LABELS, new String[]{filterName}));
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
