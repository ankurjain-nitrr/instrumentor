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

## Notes

- Only methods annotated with `@TrackMetrics` are measured.
- Metrics are registered on the default Prometheus registry when `AppMetricsAdvice` is first loaded.
- Targets Java 8.

## Development

Building needs JDK 17 or newer (Gradle 9); JDK 21 is what CI uses. The published classes are compiled with `--release 8`, and the build runs `ajc` over them so the aspect works on a consumer's `-aspectpath`.

```sh
./gradlew build
```

This compiles, runs the unit tests in `src/test` (JUnit 6 and Mockito), and writes the jar to `build/libs`. Test reports go to `build/reports/tests/test`.

CI (`.github/workflows/ci.yml`) runs the same build on every push and pull request to `main` and uploads the reports and jar as workflow artifacts. It does not publish anything.
