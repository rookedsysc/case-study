# 인프라 구성 요약

이 프로젝트는 Docker Compose 기준으로 애플리케이션, 데이터 스토어, Redis Cluster, 관측 스택을 한 번에 실행한다.

## 핵심 서비스

- 애플리케이션: `app-1~3`(Spring Boot) + `nginx`(로드밸런싱)
- 데이터 스토어: `mysql`, `redis`(단일)
- Redis Cluster: `redis-cluster-node-1~3`, `redis-cluster-init`, `redis-cluster-proxy`
- 관측(Observability): `prometheus`, `grafana`, `loki`, `otel-collector`, `node-exporter`, `mysqld-exporter`, `redis-exporter`

## 주요 포트

- 앱 진입: `80` (`nginx`)
- MySQL: `3306`
- Redis 단일: `6379`
- Redis Cluster 노드: `7001~7003`
- Redis Cluster Proxy: `7777` (기본값, `.env`에서 `REDIS_CLUSTER_PROXY_HOST_PORT`로 변경 가능)
- Grafana/Prometheus/Loki: `33001` / `39090` / `33100` (`.env` 기본값 기준)

## 앱 스케일링과 관측

- 일반 `docker compose` 환경에서는 `app.deploy.replicas` 값만 바꿔도 인스턴스가 늘어나지 않는다.
- 앱 인스턴스 수를 늘릴 때는 `docker compose up -d --scale app=5`처럼 `--scale` 옵션을 사용한다.
- `nginx`는 Docker DNS를 통해 `app` 서비스를 동적으로 해석하도록 설정되어 있어, 스케일된 `app` 인스턴스로 트래픽을 분산한다.
- `prometheus`는 `app` 서비스 DNS 기반으로 메트릭 대상을 발견하므로, 앱 스케일 이후에도 별도 수정 없이 메트릭 수집을 유지한다.
- `nginx`의 `access.log`, `error.log`는 공유 볼륨을 통해 `otel-collector`가 수집하고 Loki로 전달한다.
- `grafana`는 Prometheus/Loki를 데이터 소스로 사용하므로, 앱 수 증가만으로 별도 설정 변경은 필요하지 않다.

## k6 결과를 Grafana에서 보기

- 이 저장소는 이미 `prometheus` Remote Write 수신이 켜지도록 설정해두었으므로, k6 결과를 바로 Grafana에서 볼 수 있다.

유저 사전 데이터 채우기:

```bash
k6 run -e BASE_URL=http://localhost:38080 -e TARGET_USER_COUNT=20000 k6/shopping-mall/fill-users.js
```

- `k6/shopping-mall/fill-users.js`는 현재 `/api/users/ids`로 전체 유저 수를 먼저 조회한 뒤, 부족한 수만 `/api/users/bulk`로 최대 1000명씩 나눠 생성한다.
- `TARGET_USER_COUNT`는 추가 생성 수가 아니라 최종적으로 맞추고 싶은 전체 유저 수다.
- 기본값은 `BASE_URL=http://localhost:38080`, `TARGET_USER_COUNT=20000`, `READ_PAGE_SIZE=2000`, `CREATE_BATCH_SIZE=1000`, `PARALLEL_REQUESTS=10`이다.
- 큰 부하를 주는 목적이 아니라 빠르게 테스트용 유저 수를 맞추는 용도라서 `vus=1`, `iterations=1`로 한 번만 실행된다.

적용 후 재시작:

```bash
docker compose up -d prometheus grafana
```

k6 실행 예시:

```bash
TEST_ID=$(date +%Y%m%d-%H%M%S)

K6_PROMETHEUS_RW_PUSH_INTERVAL=10s \
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:39090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(90),p(95),avg,max" \
k6 run -o experimental-prometheus-rw \
  --tag testid=${TEST_ID} \
  -e BASE_URL=http://localhost:38080 \
  k6/shopping-mall/coupon-redis-lock-v2-only-load.js
```

- `BASE_URL`은 `.env`의 `NGINX_HOST_PORT` 기본값 기준으로 `http://localhost:38080`을 사용했다.
- `K6_PROMETHEUS_RW_SERVER_URL`은 `.env`의 `PROMETHEUS_HOST_PORT` 기본값 기준으로 `http://localhost:39090/api/v1/write`를 사용해야 한다.
- `K6_PROMETHEUS_RW_PUSH_INTERVAL`은 Remote Write flush 간격이다. 기본값 `5s`에서 `10s`로 올리면 Prometheus OOM을 방지할 수 있다.
- 실행이 끝나면 `bash .claude/hooks/kanvibe-stop-hook.sh`까지 이어서 호출되므로 한 줄로 바로 확인하기 좋다.
- 다른 스크립트를 실행할 때는 마지막 파일 경로만 바꾸면 된다.

스크립트별 실행 예시:

```bash
TEST_ID=$(date +%Y%m%d-%H%M%S)

K6_PROMETHEUS_RW_PUSH_INTERVAL=10s \
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:39090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(90),p(95),avg,max" \
k6 run -o experimental-prometheus-rw \
  --tag testid=${TEST_ID} \
  -e BASE_URL=http://localhost:38080 \
  k6/shopping-mall/coupon-redis-lock-v2-only-load.js; bash .claude/hooks/kanvibe-stop-hook.sh
```

```bash
TEST_ID=$(date +%Y%m%d-%H%M%S)

K6_PROMETHEUS_RW_PUSH_INTERVAL=10s \
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:39090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(90),p(95),avg,max" \
k6 run -o experimental-prometheus-rw \
  --tag testid=${TEST_ID} \
  -e BASE_URL=http://localhost:38080 \
  k6/shopping-mall/coupon-redis-lock-only-load.js; bash .claude/hooks/kanvibe-stop-hook.sh
```

```bash
TEST_ID=$(date +%Y%m%d-%H%M%S)

K6_PROMETHEUS_RW_PUSH_INTERVAL=10s \
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:39090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(90),p(95),avg,max" \
k6 run -o experimental-prometheus-rw \
  --tag testid=${TEST_ID} \
  -e BASE_URL=http://localhost:38080 \
  k6/shopping-mall/coupon-pessimistic-lock-only-load.js; bash .claude/hooks/kanvibe-stop-hook.sh
```

Grafana 확인 방법:

- 접속 주소: `http://localhost:33001`
- 기본 로그인: `admin / ${GRAFANA_PASSWORD:-admin}`
- `Connections > Data sources`에서 `Prometheus`가 연결되어 있는지 확인한다.
- `Dashboards`에서 프로비저닝된 `k6 Load Test` 대시보드를 열면 k6 메트릭과 애플리케이션 메트릭을 같이 볼 수 있다.
- `k6 Load Test` 대시보드에서는 `k6 HTTP 요청 수`, `k6 체크 성공률`, `k6 HTTP 응답 시간`, `k6 실패율`, `k6 활성 VUs`, `k6 네트워크 처리량`과 함께 `애플리케이션 HTTP 요청 수`, `애플리케이션 HTTP 응답 시간`, `프로세스 CPU 사용률`, `HikariCP Pool 사용률`을 같이 확인할 수 있다.

바로 확인하기 좋은 PromQL 예시:

```promql
sum(rate(k6_http_reqs[1m]))
```

```promql
avg(k6_http_req_duration_avg)
```

```promql
avg(k6_checks_rate)
```

## Redis Cluster 연결

- Docker 내부: `redis://:redispassword@redis-cluster-proxy:7777`
- localhost: `redis-cli -h localhost -p ${REDIS_CLUSTER_PROXY_HOST_PORT:-7777} -a redispassword`

## 코드 포맷과 pre-commit

- Kotlin 서버 포맷 검사: `./gradlew spotlessCheck` (`kotlin-server` 디렉터리에서 실행)
- Kotlin 서버 자동 포맷: `./gradlew spotlessApply` (`kotlin-server` 디렉터리에서 실행)
- pre-commit 설치: `pip install pre-commit` 또는 `brew install pre-commit`
- Git hook 등록: 저장소 루트에서 `pre-commit install`
- 수동 훅 실행: 저장소 루트에서 `pre-commit run --all-files`

pre-commit 훅은 `kotlin-server`의 `*.kt`, `*.kts` 파일이 변경된 커밋에서 `spotlessApply`와 `spotlessCheck`를 순서대로 실행합니다.
