package com.ankur.instrumentor;

import io.prometheus.client.CollectorRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppMetricsAdviceTest {

    private static final String[] COUNTER_LABELS = {"category", "filter_name", "status", "exception"};
    private static final String[] HISTOGRAM_LABELS = {"execution_time"};

    private final AppMetricsAdvice advice = new AppMetricsAdvice();

    /** Annotated methods the advice reads {@link TrackMetrics} from. Each test uses its own filter name. */
    static class SampleService {
        @TrackMetrics(category = "DynamoDB", filterName = "explicitSuccess")
        public void explicitSuccess() {}

        @TrackMetrics(category = "DynamoDB", filterName = "explicitFailure")
        public void explicitFailure() {}

        @TrackMetrics(category = "Elastic", filterName = "accumulated")
        public void accumulated() {}

        @TrackMetrics
        public void defaultsFromNames() {}

        @TrackMetrics(category = "Explicit")
        public void defaultFilterNameOnly() {}
    }

    @Test
    void successIncrementsSuccessCounterAndRecordsLatency() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("explicitSuccess");
        Object result = new Object();
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, advice.trackMetrics(joinPoint));

        assertEquals(1.0, counter("DynamoDB", "explicitSuccess", "SUCCESS", "None"));
        assertNull(counter("DynamoDB", "explicitSuccess", "FAILURE", "None"));
        assertEquals(1.0, histogramCount("explicitSuccess"));
    }

    @Test
    void failureIsRethrownAndRecordedWithExceptionClass() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("explicitFailure");
        IOException failure = new IOException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        IOException thrown = assertThrows(IOException.class, () -> advice.trackMetrics(joinPoint));

        assertSame(failure, thrown);
        assertEquals(1.0, counter("DynamoDB", "explicitFailure", "FAILURE", "java.io.IOException"));
        assertNull(counter("DynamoDB", "explicitFailure", "SUCCESS", "None"));
        assertEquals(1.0, histogramCount("explicitFailure"));
    }

    @Test
    void defaultsToClassAndMethodName() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("defaultsFromNames");
        when(joinPoint.proceed()).thenReturn(null);

        advice.trackMetrics(joinPoint);

        assertEquals(1.0, counter("SampleService", "defaultsFromNames", "SUCCESS", "None"));
        assertEquals(1.0, histogramCount("defaultsFromNames"));
    }

    @Test
    void explicitCategoryWithDefaultFilterName() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("defaultFilterNameOnly");
        when(joinPoint.proceed()).thenReturn(null);

        advice.trackMetrics(joinPoint);

        assertEquals(1.0, counter("Explicit", "defaultFilterNameOnly", "SUCCESS", "None"));
        assertNull(counter("SampleService", "defaultFilterNameOnly", "SUCCESS", "None"));
    }

    @Test
    void repeatedCallsAccumulate() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("accumulated");
        when(joinPoint.proceed())
                .thenReturn("a")
                .thenReturn("b")
                .thenThrow(new IllegalStateException("c"))
                .thenReturn("d");

        advice.trackMetrics(joinPoint);
        advice.trackMetrics(joinPoint);
        assertThrows(IllegalStateException.class, () -> advice.trackMetrics(joinPoint));
        advice.trackMetrics(joinPoint);

        assertEquals(3.0, counter("Elastic", "accumulated", "SUCCESS", "None"));
        assertEquals(1.0, counter("Elastic", "accumulated", "FAILURE", "java.lang.IllegalStateException"));
        assertEquals(4.0, histogramCount("accumulated"));
        Double sum = CollectorRegistry.defaultRegistry.getSampleValue(
                "latency_histogram_sum", HISTOGRAM_LABELS, new String[]{"accumulated"});
        assertNotNull(sum);
    }

    /**
     * Mocks a join point for a {@link SampleService} method. {@code toShortString()} matches the
     * format AspectJ uses for method signatures, which the advice parses for default names.
     */
    private static ProceedingJoinPoint joinPoint(String methodName) throws NoSuchMethodException {
        Method method = SampleService.class.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("SampleService." + methodName + "(..)");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private static Double counter(String category, String filterName, String status, String exception) {
        return CollectorRegistry.defaultRegistry.getSampleValue(
                "events_counter_total", COUNTER_LABELS, new String[]{category, filterName, status, exception});
    }

    private static Double histogramCount(String filterName) {
        return CollectorRegistry.defaultRegistry.getSampleValue(
                "latency_histogram_count", HISTOGRAM_LABELS, new String[]{filterName});
    }
}
