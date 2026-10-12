package com.ankur.instrumentor.sample;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Serves the default Prometheus registry, where AppMetricsAdvice registers its metrics, in the
 * Prometheus text format for scraping.
 */
@RestController
public class MetricsController {

    @GetMapping("/metrics")
    public ResponseEntity<String> metrics() throws IOException {
        StringWriter writer = new StringWriter();
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, TextFormat.CONTENT_TYPE_004)
                .body(writer.toString());
    }
}
