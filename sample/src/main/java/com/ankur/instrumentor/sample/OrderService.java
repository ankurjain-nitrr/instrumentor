package com.ankur.instrumentor.sample;

import com.ankur.instrumentor.TrackMetrics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class OrderService {

    private final Map<Long, Order> orders = Map.of(
            1L, new Order(1, "Coffee"),
            2L, new Order(2, "Tea"));

    // Recorded as category="Orders", filter_name="getOrder". Unknown ids are recorded as failures.
    @TrackMetrics(category = "Orders", filterName = "getOrder")
    public Order getOrder(long id) {
        return Optional.ofNullable(orders.get(id))
                .orElseThrow(() -> new NoSuchElementException("No order " + id));
    }

    // Defaults: category="OrderService" (class name), filter_name="listOrders" (method name).
    @TrackMetrics
    public List<Order> listOrders() {
        return List.copyOf(orders.values());
    }
}
