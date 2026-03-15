# Grafana 대시보드 필수 관측 항목 (Redis / MySQL / Spring Boot)

## 핵심 결론

- 최우선은 골든 시그널(지연시간, 트래픽, 에러, 포화도)입니다.
- DB/캐시는 애플리케이션 지표와 분리해 병목 지표를 별도 섹션으로 봐야 원인 파악이 빠릅니다.
- 로그 패널은 "원인 확인" 용도로 메트릭 패널과 나란히 배치해야 MTTR이 줄어듭니다.

## 권장 대시보드 구조

1. Overview
   - `up` 상태
   - 총 RPS
   - 총 5xx 비율
   - 전체 p95 latency
2. Spring Boot
   - HTTP RPS, 에러율, p95/p99 latency
   - JVM/GC
   - Tomcat thread
   - HikariCP pool
3. MySQL
   - 가용성
   - threads running, QPS
   - slow query 증가
   - connection 압력
4. Redis
   - 가용성
   - ops/sec, connected clients
   - memory 사용률, evictions, rejected connections
   - cache hit ratio
5. Logs (Loki)
   - 에러 로그 rate
   - 최근 에러 샘플
   - 상위 에러 패턴
6. Alert Status
   - firing/pending 규칙 요약

## 서비스별 필수 지표

### 1) Spring Boot

- 트래픽: `rate(http_server_requests_seconds_count[5m])`
- 에러율: `5xx / total`
- 지연시간:
  - `histogram_quantile(0.95, sum by (le)(rate(http_server_requests_seconds_bucket[5m])))`
  - `histogram_quantile(0.99, sum by (le)(rate(http_server_requests_seconds_bucket[5m])))`
- 포화도:
  - `process_cpu_usage`
  - JVM heap 사용률 (`jvm_memory_used_bytes / jvm_memory_max_bytes`)
  - `hikaricp_connections_active / hikaricp_connections_max`
  - Tomcat busy thread 계열 지표

### 2) MySQL (mysqld_exporter)

- 가용성: `up{job="mysqld-exporter"}`
- 부하:
  - `mysql_global_status_threads_running`
  - `mysql_global_status_questions` (또는 증가율)
- 품질: `mysql_global_status_slow_queries` 증가 추이
- 포화도: `threads_running / max_connections` 비율

### 3) Redis (redis_exporter)

- 가용성: `up{job="redis-exporter"}`
- 부하:
  - `rate(redis_commands_processed_total[5m])`
  - `redis_connected_clients`
- 포화도:
  - `redis_memory_used_bytes / redis_memory_max_bytes`
  - `rate(redis_evicted_keys_total[5m])`
  - `rate(redis_rejected_connections_total[5m])`
- 효율: cache hit ratio (`keyspace_hits`, `keyspace_misses` 기반)

## Loki 로그 기반 필수 패널

- 에러 발생량:
  - `sum(rate({service="spring-app"} |= "ERROR"[5m]))`
- 5xx 로그 탐지(로그 포맷에 맞게 보정):
  - `sum(rate({service="spring-app"} |= " 5" [5m]))`
- 무수집 탐지:
  - `absent_over_time({service="spring-app"}[5m])`
- 상위 에러 패턴:
  - `topk(5, sum by (msg) (count_over_time({...}[10m])))`

## 초기 Alert 기준(권장 시작점)

- `up == 0` 1~2분 지속
- API 5xx rate > 1~2% (5분)
- API p95 latency가 SLO 초과 (5~10분)
- MySQL `threads_running / max_connections > 0.8`
- Redis memory usage > 85%
- Redis evicted/rejected가 지속적으로 증가

## 운영 원칙

- 모니터링은 증상(what) 중심으로 페이지하고, 원인(why)은 대시보드/로그로 추적합니다.
- 평균값만 보지 말고 p95/p99를 함께 봅니다.
- 라벨 카디널리티를 과도하게 늘리지 않습니다.

## 참고 자료

- Google SRE - Monitoring Distributed Systems (Four Golden Signals):
  - https://sre.google/sre-book/monitoring-distributed-systems/
- Prometheus instrumentation best practices:
  - https://prometheus.io/docs/practices/instrumentation/
- Spring Boot Actuator Metrics (Micrometer):
  - https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- mysqld_exporter README:
  - https://github.com/prometheus/mysqld_exporter
- redis_exporter README:
  - https://github.com/oliver006/redis_exporter
- Loki metric queries:
  - https://grafana.com/docs/loki/latest/query/metric_queries/
