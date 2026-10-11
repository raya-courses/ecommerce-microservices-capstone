# k6 Performance Report

**Run date:** 2026-10-06  
**Target:** `http://localhost:8080`  
**Evidence:** k6 console output supplied for the smoke, order-load, and stress scripts. The stack health at the time of those runs was not independently verified.

## Summary

| Test | Requests | Throughput | P95 | P99 | HTTP failure rate | Result |
|---|---:|---:|---:|---:|---:|---|
| Smoke (`k6/smoke-test.js`) | 7 | 0.66 req/s | 2.41 s | Not reported | 0% | Failed latency threshold |
| Order load (`k6/order-load-test.js`) | 1,317 | 10.97 req/s | 2.68 ms | 5.05 ms | 100% | Failed; no order checks passed |
| Stress (`k6/stress-test.js`) | 1,128,200 | 8,678.45 req/s | 7.93 ms | 11.93 ms | 100% | Failed; no accepted/resilience responses observed |

The order-load and stress latency figures are not valid measures of successful business request performance: every HTTP request failed, so they primarily describe how quickly the requests failed. The stress script's separate `order_latency_ms` metric reported P95 of 8 ms; the table uses built-in `http_req_duration` for consistency.

## Per-test findings

### Smoke

- All 7 requests returned HTTP 200; `http_req_failed` was 0%.
- The JSON array check passed for all responses.
- P95 was 2.41 s, above the configured `p(95)<500 ms` threshold.
- One of seven response-time checks exceeded 1 s. Maximum HTTP request duration was 3.42 s.
- **Outcome:** The endpoint responded, but the latency target was not met. The small sample and unusually slow first requests mean this run should be repeated after the stack has warmed up.

### Order load

- The test ran for 2 minutes, reaching 10 VUs, and issued 1,317 requests.
- Both order checks failed for every iteration; the custom `errors` rate and `http_req_failed` were 100%.
- Reported P95/P99 were 2.68/5.05 ms, and throughput was 10.97 requests/s. These are failed-request measurements, not a successful-load baseline.
- **Outcome:** Failed. The output does not include response status codes or response bodies, so it does not identify the exact failure. Check whether a valid `TEST_JWT` was supplied and inspect the gateway/order-service response or logs before repeating.

### Stress

- The test ran for 2 minutes 10 seconds and reached 59 of 60 configured VUs.
- It issued 1,128,200 requests at 8,678.45 requests/s. `http_req_failed` and the custom `errors` rate were both 100%.
- `http_req_duration` P95/P99 were 7.93/11.93 ms. The “not 5xx” check passed, but the expected success/resilience-response check failed for every request.
- **Outcome:** Failed. Since no request produced a success or expected 429/503 response, this run does not demonstrate the intended resilience behavior. The very high throughput and low latency indicate fast failures rather than meaningful system stress. Capture response codes and verify authentication/routing before rerunning.

## Recommended rerun

1. Confirm the Compose services are healthy and that the gateway and Product API respond as expected.
2. Run the smoke test again after startup has settled. Record the new P95/P99 and throughput.
3. For order-load and stress, pass a valid JWT accepted by this project’s gateway using `-e TEST_JWT=...`; verify one order request succeeds before starting k6.
4. Capture response status-code counts and service logs during the reruns. Only compare load-test latency and throughput once the order test is producing successful or expected resilience responses.

## Test thresholds

- **Smoke:** P95 under 500 ms; HTTP failure rate under 1%.
- **Order load:** P95 under 800 ms; P99 under 2 s; HTTP/custom error rate under 5%.
- **Stress:** P99 under 5 s; custom error rate under 80%. The script expects some stress failures, but this run had 100% failures.

## Evidence: k6 console output

The following summaries are transcribed from the supplied terminal output. ANSI colors and progress bars are omitted; metric values and check outcomes are retained.

### Smoke test

```text
execution: local
script: .\k6\smoke-test.js
output: -

scenarios: (100.00%) 1 scenario, 1 max VUs, 40s max duration (incl. graceful stop):
  * default: 1 looping VUs for 10s (gracefulStop: 30s)

THRESHOLDS
  http_req_duration: FAIL 'p(95)<500' p(95)=2.41s
  http_req_failed:   PASS 'rate<0.01' rate=0.00%

TOTAL RESULTS
  checks_total.......: 21     1.980136/s
  checks_succeeded...: 95.23% 20 out of 21
  checks_failed......: 4.76%  1 out of 21

  PASS status is 200
  FAIL response time < 1s (85% — 6 passed / 1 failed)
  PASS body is JSON array

HTTP
  http_req_duration..............: avg=507.53ms min=11.22ms med=14.54ms max=3.42s p(90)=1.4s p(95)=2.41s
    { expected_response:true }...: avg=507.53ms min=11.22ms med=14.54ms max=3.42s p(90)=1.4s p(95)=2.41s
  http_req_failed................: 0.00% 0 out of 7
  http_reqs......................: 7 0.660045/s

EXECUTION
  iteration_duration.............: avg=1.51s min=1.01s med=1.01s max=4.44s p(90)=2.41s p(95)=3.43s
  iterations.....................: 7 0.660045/s
  vus............................: 1 min=1 max=1
  vus_max........................: 1 min=1 max=1

NETWORK
  data_received..................: 2.0 kB 187 B/s
  data_sent......................: 595 B 56 B/s

running (10.6s), 0/1 VUs, 7 complete and 0 interrupted iterations
ERRO thresholds on metrics 'http_req_duration' have been crossed
```

### Order load test

```text
execution: local
script: .\k6\order-load-test.js
output: -

scenarios: (100.00%) 1 scenario, 10 max VUs, 2m30s max duration (incl. graceful stop):
  * default: Up to 10 looping VUs for 2m0s over 3 stages (gracefulRampDown: 30s, gracefulStop: 30s)

THRESHOLDS
  errors:           FAIL 'rate<0.05' rate=100.00%
  http_req_duration: PASS 'p(95)<800' p(95)=2.68ms
                    PASS 'p(99)<2000' p(99)=5.05ms
  http_req_failed:  FAIL 'rate<0.05' rate=100.00%

TOTAL RESULTS
  checks_total.......: 2634 21.943563/s
  checks_succeeded...: 0.00% 0 out of 2634
  checks_failed......: 100.00% 2634 out of 2634

  FAIL order accepted (200 or 202) (0 passed / 1317 failed)
  FAIL response has orderId (0 passed / 1317 failed)

CUSTOM
  errors...............: 100.00% 1317 out of 1317

HTTP
  http_req_duration....: avg=2.21ms min=501.9µs med=1.56ms max=339.84ms p(90)=2.47ms p(95)=2.68ms
  http_req_failed......: 100.00% 1317 out of 1317
  http_reqs............: 1317 10.971781/s

EXECUTION
  iteration_duration...: avg=502.58ms min=500.57ms med=501.97ms max=840.02ms p(90)=502.81ms p(95)=503.13ms
  iterations...........: 1317 10.971781/s
  vus..................: 1 min=1 max=10
  vus_max..............: 10 min=10 max=10

NETWORK
  data_received........: 234 kB 2.0 kB/s
  data_sent............: 300 kB 2.5 kB/s

running (2m00.0s), 00/10 VUs, 1317 complete and 0 interrupted iterations
ERRO thresholds on metrics 'errors, http_req_failed' have been crossed
```

### Stress test

```text
execution: local
script: .\k6\stress-test.js
output: -

scenarios: (100.00%) 1 scenario, 60 max VUs, 2m40s max duration (incl. graceful stop):
  * default: Up to 60 looping VUs for 2m10s over 5 stages (gracefulRampDown: 30s, gracefulStop: 30s)

THRESHOLDS
  errors:           FAIL 'rate<0.80' rate=100.00%
  http_req_duration: PASS 'p(99)<5000' p(99)=11.93ms

TOTAL RESULTS
  checks_total.......: 2256400 17356.892782/s
  checks_succeeded...: 50.00% 1128200 out of 2256400
  checks_failed......: 50.00% 1128200 out of 2256400

  PASS not 5xx server error
  FAIL resilience response or success (0 passed / 1128200 failed)

CUSTOM
  errors...............: 100.00% 1128200 out of 1128200
  order_latency_ms.....: avg=2.965176 min=0 med=2 max=106 p(90)=6 p(95)=8

HTTP
  http_req_duration....: avg=2.91ms min=0s med=2.12ms max=106.13ms p(90)=6.16ms p(95)=7.93ms
  http_req_failed......: 100.00% 1128200 out of 1128200
  http_reqs............: 1128200 8678.446391/s

EXECUTION
  iteration_duration...: avg=2.99ms min=0s med=2.14ms max=106.13ms p(90)=6.31ms p(95)=8ms
  iterations...........: 1128200 8678.446391/s
  vus..................: 1 min=1 max=59
  vus_max..............: 60 min=60 max=60

NETWORK
  data_received........: 201 MB 1.5 MB/s
  data_sent............: 266 MB 2.0 MB/s

running (2m10.0s), 00/60 VUs, 1128200 complete and 0 interrupted iterations
ERRO thresholds on metrics 'errors' have been crossed
```

