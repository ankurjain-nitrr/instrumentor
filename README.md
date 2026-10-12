# instrumentor

[![CI](https://github.com/ankurjain-nitrr/instrumentor/actions/workflows/ci.yml/badge.svg)](https://github.com/ankurjain-nitrr/instrumentor/actions/workflows/ci.yml)

Annotation-driven metrics for Java methods. Put `@TrackMetrics` on a method and get a Prometheus call counter (split by success or failure and exception type) plus a latency histogram, without touching the method body.

Built on AspectJ `@Around` advice and the Prometheus Java client.

## What it records

| Metric | Labels | Meaning |
|---|---|---|
| `events_counter` | `category`, `filter_name`, `status`, `exception` | Number of calls. `status` is `SUCCESS` or `FAILURE`; `exception` is the exception class on failure, otherwise `None`. |
| `latency_histogram` | `execution_time` | Time taken per call, in seconds. The label value is the `filter_name`. |

The Prometheus client exposes the counter with a `_total` suffix (`events_counter_total`).

## Usage

Annotate the methods you want to track:

```java
import com.ankur.instrumentor.TrackMetrics;

public class UserRepository {

    // Explicit category and name
    @TrackMetrics(category = "DynamoDB", filterName = "getUser")
    public User getUser(String id) {
        // ...
    }

    // Defaults: category = class name ("UserRepository"), filterName = method name ("findAll")
    @TrackMetrics
    public List<User> findAll() {
        // ...
    }
}
```

Expose the default Prometheus registry however your service already does, for example:

```java
new io.prometheus.client.exporter.HTTPServer(9100); // needs simpleclient_httpserver
```

Scrape output for the first method (illustrative):

```
events_counter_total{category="DynamoDB",filter_name="getUser",status="SUCCESS",exception="None"} 42.0
events_counter_total{category="DynamoDB",filter_name="getUser",status="FAILURE",exception="java.io.IOException"} 1.0
latency_histogram_bucket{execution_time="getUser",le="0.05"} 40.0
```

## Setup

The advice (`AppMetricsAdvice`) has to be woven into your classes, either at compile time or with the AspectJ load-time weaver. With Gradle and compile-time weaving:

```gradle
plugins {
    id 'java'
    id 'io.freefair.aspectj.post-compile-weaving' version '9.8.0'
}

dependencies {
    aspect         files('libs/instrumentor-0.0.1-SNAPSHOT.jar')
    implementation files('libs/instrumentor-0.0.1-SNAPSHOT.jar')
    implementation 'org.aspectj:aspectjrt:1.9.25.1'
    implementation 'io.prometheus:simpleclient:0.16.0'
    implementation 'org.slf4j:slf4j-api:2.0.20'
}
```

Build the jar from this repo with `./gradlew build`; it lands in `build/libs`.

## Sample application

[`sample/`](sample) is a Spring Boot 4 app that uses the library the way the Setup section describes: the freefair plugin weaves `AppMetricsAdvice` in at compile time, and a `/metrics` endpoint serves the default Prometheus registry.

```sh
./gradlew :sample:bootRun

curl localhost:8080/orders      # @TrackMetrics with defaults: OrderService / listOrders
curl localhost:8080/orders/1    # @TrackMetrics(category = "Orders", filterName = "getOrder")
curl localhost:8080/orders/7    # unknown id: recorded as a FAILURE, returns 404
curl localhost:8080/metrics
```

After those calls, `/metrics` includes:

```
events_counter_total{category="OrderService",filter_name="listOrders",status="SUCCESS",exception="None",} 1.0
events_counter_total{category="Orders",filter_name="getOrder",status="FAILURE",exception="java.util.NoSuchElementException",} 1.0
events_counter_total{category="Orders",filter_name="getOrder",status="SUCCESS",exception="None",} 1.0
latency_histogram_count{execution_time="getOrder",} 2.0
```

Spring Boot Actuator's `/actuator/prometheus` won't show these metrics. Micrometer uses a different registry from the `simpleclient` default registry this library writes to, so the sample serves that registry itself in `MetricsController`.

## Notes

- Only methods annotated with `@TrackMetrics` are measured.
- Metrics are registered on the default Prometheus registry when `AppMetricsAdvice` is first loaded.
- Requires Java 21 or newer at runtime.

## Development

Building needs JDK 21. The build uses a Gradle Java 21 toolchain, so Gradle itself can run on JDK 17 or newer as long as a JDK 21 is installed. The build runs `ajc` over the compiled classes so the aspect works on a consumer's `-aspectpath`.

```sh
./gradlew build
```

This compiles, runs the unit tests in `src/test` (JUnit 6 and Mockito) and the sample's end-to-end test, and writes the jar to `build/libs`. Test reports go to `build/reports/tests/test`.

CI (`.github/workflows/ci.yml`) runs the same build on every push and pull request to `main` and uploads the reports and jars as workflow artifacts. It does not publish anything.
