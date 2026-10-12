# instrumentor

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
    id 'io.freefair.aspectj.post-compile-weaving' version '5.3.3.3'
}

dependencies {
    aspect         files('libs/instrumentor-0.0.1-SNAPSHOT.jar')
    implementation files('libs/instrumentor-0.0.1-SNAPSHOT.jar')
    implementation 'io.prometheus:simpleclient:0.12.0'
}
```

Build the jar from this repo with `./gradlew build`; it lands in `build/libs`.

## Notes

- Only methods annotated with `@TrackMetrics` are measured.
- Metrics are registered on the default Prometheus registry when `AppMetricsAdvice` is first loaded.
- Targets Java 8.
