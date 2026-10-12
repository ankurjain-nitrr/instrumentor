#!/usr/bin/env bash
# Starts the sample image, makes tracked calls, and checks that /metrics reports them.
# Usage: scripts/docker-smoke-test.sh [image]   (default image: instrumentor-sample)
set -euo pipefail

image="${1:-instrumentor-sample}"
name="instrumentor-smoke-$$"
port="${PORT:-18080}"
base="http://localhost:${port}"

cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "--- container logs" >&2
    docker logs "$name" >&2 || true
  fi
  docker rm -f "$name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

docker run -d --name "$name" -p "${port}:8080" "$image" >/dev/null

for _ in $(seq 1 60); do
  curl -sf "$base/orders" >/dev/null && break
  sleep 1
done
curl -sf "$base/orders" >/dev/null || fail "app did not start on $base"

java_version=$(docker exec "$name" java -version 2>&1 | grep ' version ' || true)
echo "runtime: $java_version"
[[ "$java_version" == *'version "25.'* ]] || fail "expected a Java 25 runtime"

curl -sf "$base/orders/1" >/dev/null || fail "GET /orders/1"
code=$(curl -s -o /dev/null -w '%{http_code}' "$base/orders/99")
[ "$code" = 404 ] || fail "GET /orders/99 returned $code, expected 404"

metrics=$(curl -sf "$base/metrics") || fail "GET /metrics"

expect() {
  grep -qF "$1" <<<"$metrics" || fail "/metrics is missing: $1"
  echo "ok: $1"
}
# The container is fresh, so counts are exact: /orders was called by the readiness loop and the
# check after it; /orders/1 and /orders/99 once each.
expect 'events_counter_total{category="OrderService",filter_name="listOrders",status="SUCCESS",exception="None",} 2.0'
expect 'events_counter_total{category="Orders",filter_name="getOrder",status="SUCCESS",exception="None",} 1.0'
expect 'events_counter_total{category="Orders",filter_name="getOrder",status="FAILURE",exception="java.util.NoSuchElementException",} 1.0'
expect 'latency_histogram_count{execution_time="getOrder",} 2.0'

echo "Smoke test passed."
