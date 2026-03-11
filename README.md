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
