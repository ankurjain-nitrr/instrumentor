package com.ankur.instrumentor.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SampleApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestsAreCountedByTheAdvice() throws Exception {
        double getSuccesses = Metrics.counter("Orders", "getOrder", "SUCCESS", "None");
        double getFailures = Metrics.counter("Orders", "getOrder", "FAILURE", "java.util.NoSuchElementException");
        double listSuccesses = Metrics.counter("OrderService", "listOrders", "SUCCESS", "None");
        double getTimings = Metrics.histogramCount("getOrder");

        mockMvc.perform(get("/orders/1")).andExpect(status().isOk());
        mockMvc.perform(get("/orders/99")).andExpect(status().isNotFound());
        mockMvc.perform(get("/orders")).andExpect(status().isOk());

        assertEquals(getSuccesses + 1, Metrics.counter("Orders", "getOrder", "SUCCESS", "None"));
        assertEquals(getFailures + 1, Metrics.counter("Orders", "getOrder", "FAILURE", "java.util.NoSuchElementException"));
        assertEquals(listSuccesses + 1, Metrics.counter("OrderService", "listOrders", "SUCCESS", "None"));
        assertEquals(getTimings + 2, Metrics.histogramCount("getOrder"));
    }

    @Test
    void metricsEndpointServesPrometheusText() throws Exception {
        mockMvc.perform(get("/orders/1")).andExpect(status().isOk());

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("version=0.0.4")))
                .andExpect(content().string(containsString("# TYPE events_counter_total counter")))
                .andExpect(content().string(containsString(
                        "events_counter_total{category=\"Orders\",filter_name=\"getOrder\",status=\"SUCCESS\",exception=\"None\",}")))
                .andExpect(content().string(containsString("# TYPE latency_histogram histogram")))
                .andExpect(content().string(containsString(
                        "latency_histogram_count{execution_time=\"getOrder\",}")));
    }
}
