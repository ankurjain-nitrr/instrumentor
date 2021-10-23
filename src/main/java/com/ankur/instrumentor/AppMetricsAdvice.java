package com.ankur.instrumentor;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Slf4j
@Aspect
public class AppMetricsAdvice {

    public static final String NONE = "None";
    public static final String DEFAULT = "default";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";

    public static Counter counter;
    public static Histogram histogram;

    static {
        postInitialization();
    }

    public static void postInitialization() {
        String counterName = "events_counter";
        String counterHelper = "total events count";
        String histogramName = "latency_histogram";
        String histogramHelper = "time taken to execute the events.";
        String category = "category";
        String filterName = "filter_name";
        String status = "status";
        String exception = "exception";
        String latencyLabel = "execution_time";

        counter = Counter.build()
                .name(counterName)
                .labelNames(category, filterName, status, exception)
                .help(counterHelper)
                .register();

        histogram = Histogram.build()
                .name(histogramName)
                .labelNames(latencyLabel)
                .help(histogramHelper)
                .register();
    }

    @Around("@annotation(com.ankur.instrumentor.TrackMetrics) && execution(* *(..))")
    public Object trackMetrics(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        log.debug("TrackMetrics annotation advice called for method {}", proceedingJoinPoint.getSignature());
        MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
        TrackMetrics trackMetrics = signature.getMethod().getAnnotation(TrackMetrics.class);
        final String signatureName = signature.toShortString().split("\\(")[0];
        final String[] names = signatureName.split("\\.");
        final String category = DEFAULT.equals(trackMetrics.category()) ? names[0] : trackMetrics.category();
        final String filterName = DEFAULT.equals(trackMetrics.filterName()) ? names[1] : trackMetrics.filterName();
        return metricsHelper(proceedingJoinPoint, category, filterName);
    }

    private Object metricsHelper(ProceedingJoinPoint proceedingJoinPoint, String category, String filterName)
            throws Throwable {
        Object object;
        Histogram.Timer timer = histogram.labels(filterName).startTimer();
        try {
            object = proceedingJoinPoint.proceed();
            counter.labels(category, filterName, SUCCESS, NONE).inc();
        } catch (Throwable ex) {
            counter.labels(category, filterName, FAILURE, ex.getClass().getName()).inc();
            throw ex;
        } finally {
            timer.observeDuration();
        }
        return object;
    }
}
