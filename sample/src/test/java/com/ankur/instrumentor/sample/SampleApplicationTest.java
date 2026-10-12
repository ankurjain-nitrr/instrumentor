package com.ankur.instrumentor.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SampleApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void trackedCallsShowUpOnMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/orders/1")).andExpect(status().isOk());
        mockMvc.perform(get("/orders/99")).andExpect(status().isNotFound());
        mockMvc.perform(get("/orders")).andExpect(status().isOk());

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "events_counter_total{category=\"Orders\",filter_name=\"getOrder\",status=\"SUCCESS\",exception=\"None\",} 1.0")))
                .andExpect(content().string(containsString(
                        "events_counter_total{category=\"Orders\",filter_name=\"getOrder\",status=\"FAILURE\",exception=\"java.util.NoSuchElementException\",} 1.0")))
                .andExpect(content().string(containsString(
                        "events_counter_total{category=\"OrderService\",filter_name=\"listOrders\",status=\"SUCCESS\",exception=\"None\",} 1.0")))
                .andExpect(content().string(containsString(
                        "latency_histogram_count{execution_time=\"getOrder\",} 2.0")));
    }
}
