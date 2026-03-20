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
- Grafana/Prometheus/Loki: `3000` / `9090` / `3100`

## 앱 스케일링과 관측

- 일반 `docker compose` 환경에서는 `app.deploy.replicas` 값만 바꿔도 인스턴스가 늘어나지 않는다.
- 앱 인스턴스 수를 늘릴 때는 `docker compose up -d --scale app=5`처럼 `--scale` 옵션을 사용한다.
- `nginx`는 Docker DNS를 통해 `app` 서비스를 동적으로 해석하도록 설정되어 있어, 스케일된 `app` 인스턴스로 트래픽을 분산한다.
- `prometheus`는 `app` 서비스 DNS 기반으로 메트릭 대상을 발견하므로, 앱 스케일 이후에도 별도 수정 없이 메트릭 수집을 유지한다.
- `nginx`의 `access.log`, `error.log`는 공유 볼륨을 통해 `otel-collector`가 수집하고 Loki로 전달한다.
- `grafana`는 Prometheus/Loki를 데이터 소스로 사용하므로, 앱 수 증가만으로 별도 설정 변경은 필요하지 않다.

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
