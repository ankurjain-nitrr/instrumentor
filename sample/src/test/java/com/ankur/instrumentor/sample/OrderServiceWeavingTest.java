package com.ankur.instrumentor.sample;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Calls OrderService directly, with no Spring context or proxies. Metrics can only change here if
 * AppMetricsAdvice was woven into the class at compile time.
 */
class OrderServiceWeavingTest {

    private final OrderService orderService = new OrderService();

    @Test
    void explicitNamesRecordSuccessAndLatency() {
        double successes = Metrics.counter("Orders", "getOrder", "SUCCESS", "None");
        double timings = Metrics.histogramCount("getOrder");

        orderService.getOrder(1);

        assertEquals(successes + 1, Metrics.counter("Orders", "getOrder", "SUCCESS", "None"));
        assertEquals(timings + 1, Metrics.histogramCount("getOrder"));
    }

    @Test
    void failureIsRethrownAndRecordedWithExceptionClass() {
        double failures = Metrics.counter("Orders", "getOrder", "FAILURE", "java.util.NoSuchElementException");
        double timings = Metrics.histogramCount("getOrder");

        assertThrows(NoSuchElementException.class, () -> orderService.getOrder(42));

        assertEquals(failures + 1, Metrics.counter("Orders", "getOrder", "FAILURE", "java.util.NoSuchElementException"));
        assertEquals(timings + 1, Metrics.histogramCount("getOrder"));
    }

    @Test
    void defaultsComeFromClassAndMethodName() {
        double successes = Metrics.counter("OrderService", "listOrders", "SUCCESS", "None");

        orderService.listOrders();
        orderService.listOrders();

        assertEquals(successes + 2, Metrics.counter("OrderService", "listOrders", "SUCCESS", "None"));
    }
}
